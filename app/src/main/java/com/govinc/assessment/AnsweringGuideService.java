package com.govinc.assessment;

import com.govinc.util.OpenAIUtil;
import com.govinc.maturity.MaturityAnswer;
import com.govinc.maturity.MaturityAnswerRepository;
import com.govinc.catalog.SecurityControl;
import com.govinc.catalog.SecurityControlRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class AnsweringGuideService {
    
    @Autowired
    private OpenAIUtil openAIUtil;
    
    @Autowired
    private MaturityAnswerRepository maturityAnswerRepository;
    
    @Autowired
    private SecurityControlRepository securityControlRepository;
    
    /**
     * Generate answering guide questions for a security control.
     */
    public Map<String, Object> getAnsweringGuide(Long controlId, String controlName, String controlDetail) {
        return generateAnsweringGuide(controlId, controlName, controlDetail);
    }
    
    /**
     * Generate new answering guide questions via AI and cache them
     */
    private Map<String, Object> generateAnsweringGuide(Long controlId, String controlName, String controlDetail) {
        Map<String, Object> response = new HashMap<>();
        
        String prompt = "You are a security assessment expert. Based on the following security control, generate 5 specific, practical YES/NO questions that would help a security professional assess this control. ensure, that the questions if all answered with yes, would yield a 100% result for the question in terms of maturity.\n\n" +
                "Security Control: " + controlName + "\n" +
                "Description: " + (controlDetail != null ? controlDetail : "") + "\n\n" +
                "Generate exactly 5 yes/no questions. Return them as a JSON array of strings.\n" +
                "Format: [\"Is [something] implemented?\", \"Does [something] exist?\", ...]\n\n" +
                "Return ONLY the JSON array, no other text.";
        
        String aiResponse = openAIUtil.askAI(prompt);
        
        try {
            aiResponse = aiResponse.trim();
            int startIdx = aiResponse.indexOf('[');
            int endIdx = aiResponse.lastIndexOf(']');
            if (startIdx != -1 && endIdx != -1) {
                aiResponse = aiResponse.substring(startIdx, endIdx + 1);
            }
            JSONArray jsonArray = new JSONArray(aiResponse);
            List<String> questions = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                questions.add(jsonArray.getString(i));
            }
            
            response.put("success", true);
            response.put("questions", questions);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error parsing AI response: " + e.getMessage());
            response.put("aiResponse", aiResponse);
        }
        
        return response;
    }
    
    /**
     * Analyze user answers and propose a maturity level based on the maturity model answers.
     * The proposed answer is constrained to answers available in the maturity model,
     * ensuring the result always fits to the assessment's maturity model.
     */
    public Map<String, Object> proposeAnswerFromGuide(Long controlId, Long securityCatalogId, 
                                                       List<String> questions, List<String> answers,
                                                       List<Map<String, Object>> maturityModelAnswers) {
        Map<String, Object> response = new HashMap<>();
        
        // Validate maturity model answers are provided
        if (maturityModelAnswers == null || maturityModelAnswers.isEmpty()) {
            response.put("success", false);
            response.put("message", "Maturity model answers are required for proposing an answer");
            return response;
        }
        
        if (questions == null || answers == null || questions.isEmpty() || answers.isEmpty()) {
            // Fallback to the first valid answer from maturity model (most conservative)
            if (!maturityModelAnswers.isEmpty()) {
                Map<String, Object> defaultAnswer = maturityModelAnswers.get(0);
                response.put("success", true);
                response.put("proposedAnswer", defaultAnswer.get("answer"));
                response.put("proposedAnswerId", defaultAnswer.get("id"));
                response.put("matchedRating", defaultAnswer.get("rating"));
                response.put("message", "Using default answer due to invalid input");
            } else {
                response.put("success", false);
                response.put("message", "Questions and answers are required");
            }
            return response;
        }
        
        if (questions.size() != answers.size()) {
            // Fallback to first answer from maturity model
            if (!maturityModelAnswers.isEmpty()) {
                Map<String, Object> defaultAnswer = maturityModelAnswers.get(0);
                response.put("success", true);
                response.put("proposedAnswer", defaultAnswer.get("answer"));
                response.put("proposedAnswerId", defaultAnswer.get("id"));
                response.put("matchedRating", defaultAnswer.get("rating"));
                response.put("message", "Using default answer due to question/answer mismatch");
            } else {
                response.put("success", false);
                response.put("message", "Number of questions must match number of answers");
            }
            return response;
        }
        
        // Calculate yes percentage
        long yesCount = answers.stream().filter(a -> "Yes".equalsIgnoreCase(a)).count();
        long totalCount = answers.size();
        int yesPercentage = (int) Math.round((totalCount > 0) ? (yesCount * 100.0 / totalCount) : 0);
        
        // Build Q&A matrix for logging
        StringBuilder qandAMatrix = new StringBuilder();
        for (int i = 0; i < questions.size(); i++) {
            qandAMatrix.append("Q").append(i + 1).append(": ").append(questions.get(i)).append(" → ");
            qandAMatrix.append(answers.get(i)).append("\n");
        }
        
        // Build list of available maturity answers for AI constraint
        StringBuilder maturityAnswersStr = new StringBuilder();
        for (Map<String, Object> ma : maturityModelAnswers) {
            maturityAnswersStr.append("- ").append(ma.get("answer"))
                    .append(" (Rating: ").append(ma.get("rating")).append("%");
            if (ma.get("description") != null && !ma.get("description").toString().isEmpty()) {
                maturityAnswersStr.append(" - ").append(ma.get("description"));
            }
            maturityAnswersStr.append(")\n");
        }
        
        // Call AI to analyze and select the appropriate maturity answer
        String prompt = "You are a security maturity assessment expert. Based on the following yes/no Q&A responses, determine which maturity level best fits the control's current state.\n\n" +
                "Q&A Responses:\n" +
                qandAMatrix.toString() + "\n" +
                "Available Maturity Answers for this control:\n" +
                maturityAnswersStr.toString() + "\n" +
                "Based on the Q&A responses, select the most appropriate maturity answer and return it EXACTLY as written in the list above. Return ONLY the answer text, nothing else.";
        
        String aiResponse = openAIUtil.askAI(prompt).trim();
        
        // Find the proposed answer in the maturity model answers
        Map<String, Object> proposedMaturityAnswer = null;
        
        // Try exact match first
        for (Map<String, Object> ma : maturityModelAnswers) {
            if (aiResponse.equalsIgnoreCase(ma.get("answer").toString())) {
                proposedMaturityAnswer = ma;
                break;
            }
        }
        
        // If no exact match, try partial match or default to first answer
        if (proposedMaturityAnswer == null) {
            for (Map<String, Object> ma : maturityModelAnswers) {
                if (ma.get("answer").toString().toLowerCase().contains(aiResponse.toLowerCase()) ||
                    aiResponse.toLowerCase().contains(ma.get("answer").toString().toLowerCase())) {
                    proposedMaturityAnswer = ma;
                    break;
                }
            }
        }
        
        // Fallback to first answer (most conservative) if still no match
        if (proposedMaturityAnswer == null) {
            proposedMaturityAnswer = maturityModelAnswers.get(0);
        }
        
        response.put("success", true);
        response.put("proposedAnswer", proposedMaturityAnswer.get("answer"));
        response.put("proposedAnswerId", proposedMaturityAnswer.get("id"));
        response.put("aiResponse", aiResponse);
        response.put("yesPercentage", yesPercentage);
        response.put("yesCount", yesCount);
        response.put("totalCount", totalCount);
        response.put("matchedRating", proposedMaturityAnswer.get("rating"));
        
        return response;
    }
}
