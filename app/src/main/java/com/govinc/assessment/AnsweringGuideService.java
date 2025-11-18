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
     * Analyze user answers and propose a maturity level based on percentage.
     * Always returns a valid maturity answer, never returns "IDONOTKNOW" or invalid values.
     */
    public Map<String, Object> proposeAnswerFromGuide(Long controlId, Long securityCatalogId, 
                                                       List<String> questions, List<String> answers) {
        Map<String, Object> response = new HashMap<>();
        System.out.println("01 : ");
        if (questions == null || answers == null || questions.isEmpty() || answers.isEmpty()) {
            // Fallback to a default valid answer
            MaturityAnswer defaultAnswer = findDefaultMaturityAnswer();
            if (defaultAnswer != null) {
                response.put("success", true);
                response.put("proposedAnswer", defaultAnswer.getAnswer());
                response.put("proposedAnswerId", defaultAnswer.getId());
                response.put("matchedRating", defaultAnswer.getRating());
                response.put("message", "Using default answer due to invalid input");
            } else {
                response.put("success", false);
                response.put("message", "Questions and answers are required");
            }
            return response;
        }
        
        if (questions.size() != answers.size()) {
            // Fallback to a default valid answer
            MaturityAnswer defaultAnswer = findDefaultMaturityAnswer();
            if (defaultAnswer != null) {
                response.put("success", true);
                response.put("proposedAnswer", defaultAnswer.getAnswer());
                response.put("proposedAnswerId", defaultAnswer.getId());
                response.put("matchedRating", defaultAnswer.getRating());
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
        
        // Call AI to analyze and return percentage
        String prompt = "You are a security maturity assessment expert. Based on the following yes/no Q&A responses, determine the maturity level as a percentage (0-100).\n\n" +
                "Q&A Responses:\n" +
                qandAMatrix.toString() + "\n" +
                "Guidelines:\n" +
                "- 0-20: Not Implemented (little to no practices)\n" +
                "- 21-40: Informal (ad-hoc practices, not standardized)\n" +
                "- 41-70: Repeatable (processes defined and repeatable)\n" +
                "- 71-95: Managed (processes managed and monitored)\n" +
                "- 96-100: Optimized (fully optimized with continuous improvement)\n\n" +
                "Analyze the answers and return ONLY a single number between 0 and 100 (e.g., '75' or '45'). No text, just the number.";
        
        String aiResponse = openAIUtil.askAI(prompt).trim();
        System.out.println("ai answer::" + aiResponse);
        
        int aiPercentage = yesPercentage; // Default to yes percentage if AI fails
        
        try {
            // Extract percentage from response
            String numericOnly = aiResponse.replaceAll("[^0-9]", "");
            if (!numericOnly.isEmpty()) {
                aiPercentage = Integer.parseInt(numericOnly);
            }
        } catch (NumberFormatException e) {
            System.err.println("Failed to parse AI response as percentage: " + aiResponse);
            // Use yesPercentage as fallback
        }
        
        // Clamp between 0-100
        if (aiPercentage < 0) aiPercentage = 0;
        if (aiPercentage > 100) aiPercentage = 100;
        
        // Find closest maturity answer by rating
        MaturityAnswer closestAnswer = findClosestMaturityAnswer(securityCatalogId, aiPercentage);
        System.out.println("02 : " + closestAnswer);
        
        if (closestAnswer == null) {
            // Fallback to default answer if no closest match found
            closestAnswer = findDefaultMaturityAnswer();
            if (closestAnswer == null) {
                response.put("success", false);
                response.put("message", "No maturity answers found for this catalog");
                response.put("percentage", aiPercentage);
                response.put("yesPercentage", yesPercentage);
                return response;
            }
        }
        System.out.println("03 : " + closestAnswer.getAnswer());
        response.put("success", true);
        response.put("proposedAnswer", closestAnswer.getAnswer());
        response.put("proposedAnswerId", closestAnswer.getId());
        response.put("aiPercentage", aiPercentage);
        response.put("yesPercentage", yesPercentage);
        response.put("yesCount", yesCount);
        response.put("totalCount", totalCount);
        response.put("matchedRating", closestAnswer.getRating());
        
        return response;
    }
    
    /**
     * Find the closest maturity answer by rating to the given percentage
     */
    private MaturityAnswer findClosestMaturityAnswer(Long securityCatalogId, int targetPercentage) {
        try {
            // Get all maturity answers for this catalog
            List<MaturityAnswer> answers = maturityAnswerRepository.findAll();
            
            if (answers.isEmpty()) {
                return null;
            }
            
            // Find answer with closest rating to target percentage
            MaturityAnswer closest = answers.get(0);
            int minDiff = Math.abs(closest.getRating() - targetPercentage);
            
            for (MaturityAnswer answer : answers) {
                int diff = Math.abs(answer.getRating() - targetPercentage);
                if (diff < minDiff) {
                    minDiff = diff;
                    closest = answer;
                }
            }
            
            return closest;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Find a default maturity answer to use as fallback.
     * Returns the lowest rating (most conservative) answer if available.
     */
    private MaturityAnswer findDefaultMaturityAnswer() {
        try {
            List<MaturityAnswer> answers = maturityAnswerRepository.findAll();
            
            if (answers.isEmpty()) {
                return null;
            }
            
            // Return the answer with the lowest rating as default (most conservative)
            MaturityAnswer defaultAnswer = answers.get(0);
            for (MaturityAnswer answer : answers) {
                if (answer.getRating() < defaultAnswer.getRating()) {
                    defaultAnswer = answer;
                }
            }
            
            return defaultAnswer;
        } catch (Exception e) {
            return null;
        }
    }
}
