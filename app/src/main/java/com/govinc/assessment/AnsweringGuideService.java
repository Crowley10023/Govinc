package com.govinc.assessment;

import com.govinc.util.OpenAIUtil;
import com.govinc.maturity.MaturityAnswer;
import com.govinc.maturity.MaturityAnswerRepository;
import com.govinc.catalog.SecurityCatalog;
import com.govinc.catalog.SecurityCatalogRepository;
import com.govinc.catalog.SecurityControl;
import com.govinc.catalog.SecurityControlRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AnsweringGuideService {
    
    @Autowired
    private OpenAIUtil openAIUtil;
    
    @Autowired
    private MaturityAnswerRepository maturityAnswerRepository;
    
    @Autowired
    private SecurityControlRepository securityControlRepository;

    @Autowired
    private SecurityCatalogRepository securityCatalogRepository;

    // ---- Wizard order cache (keyed by securityCatalogId) ----
    private static class WizardOrderCacheEntry {
        final List<Integer> order;
        final String controlsHash;
        WizardOrderCacheEntry(List<Integer> order, String controlsHash) {
            this.order = Collections.unmodifiableList(new ArrayList<>(order));
            this.controlsHash = controlsHash;
        }
    }
    private final Map<Long, WizardOrderCacheEntry> wizardOrderCache = new ConcurrentHashMap<>();

    /** Evict the wizard order cache for a given catalog (call when controls change). */
    public void evictWizardOrderCache(Long securityCatalogId) {
        if (securityCatalogId != null) {
            wizardOrderCache.remove(securityCatalogId);
            securityCatalogRepository.findById(securityCatalogId).ifPresent(catalog -> {
                catalog.setWizardControlOrder(null);
                catalog.setWizardControlOrderHash(null);
                securityCatalogRepository.save(catalog);
            });
        }
    }
    
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
        
        // When there are more than 2 possible answers, exclude the highest-rated answer from
        // the suggestions - the guide should never propose the maximum achievable rating.
        List<Map<String, Object>> effectiveAnswers = maturityModelAnswers.size() > 2
                ? maturityModelAnswers.subList(0, maturityModelAnswers.size() - 1)
                : maturityModelAnswers;

        // Build list of available maturity answers for AI constraint
        StringBuilder maturityAnswersStr = new StringBuilder();
        for (Map<String, Object> ma : effectiveAnswers) {
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
                "Yes percentage: " + yesPercentage + "% (" + yesCount + " of " + totalCount + " answered Yes)\n\n" +
                "Available Maturity Answers for this control (ordered from lowest to highest maturity):\n" +
                maturityAnswersStr.toString() + "\n" +
                "Selection rules:\n" +
                "- If ALL answers are 'Yes' (100%), you MUST select the HIGHEST-rated maturity answer from the list above.\n" +
                "- If NO answers are 'Yes' (0%), you MUST select the LOWEST-rated maturity answer.\n" +
                "- Otherwise, select the maturity answer whose rating percentage is closest to the yes percentage (" + yesPercentage + "%).\n" +
                "- When in doubt between two answers, prefer the higher-rated one.\n\n" +
                "Based on the rules above, select the most appropriate maturity answer and return it EXACTLY as written in the list above. Return ONLY the answer text, nothing else.";
        
        String aiResponse = openAIUtil.askAI(prompt).trim();
        
        // Find the proposed answer in the effective answers
        Map<String, Object> proposedMaturityAnswer = null;
        
        // Try exact match first
        for (Map<String, Object> ma : effectiveAnswers) {
            if (aiResponse.equalsIgnoreCase(ma.get("answer").toString())) {
                proposedMaturityAnswer = ma;
                break;
            }
        }
        
        // If no exact match, try partial match or default to first answer
        if (proposedMaturityAnswer == null) {
            for (Map<String, Object> ma : effectiveAnswers) {
                if (ma.get("answer").toString().toLowerCase().contains(aiResponse.toLowerCase()) ||
                    aiResponse.toLowerCase().contains(ma.get("answer").toString().toLowerCase())) {
                    proposedMaturityAnswer = ma;
                    break;
                }
            }
        }
        
        // Fallback to first answer (most conservative) if still no match
        if (proposedMaturityAnswer == null) {
            proposedMaturityAnswer = effectiveAnswers.get(0);
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

    /**
     * Generate a concise AI summary of the Q&A answers to be placed in the security control's comment field.
     */
    public Map<String, Object> generateAnswerSummary(String controlName, List<String> questions, List<String> answers, String proposedAnswer) {
        Map<String, Object> response = new HashMap<>();

        if (questions == null || answers == null || questions.isEmpty()) {
            response.put("success", false);
            response.put("message", "Questions and answers are required");
            return response;
        }

        StringBuilder qaBuilder = new StringBuilder();
        for (int i = 0; i < questions.size(); i++) {
            qaBuilder.append("Q").append(i + 1).append(": ").append(questions.get(i))
                    .append(" → ").append(i < answers.size() ? answers.get(i) : "N/A").append("\n");
        }

        String prompt = "You are a security assessment expert. Based on the following yes/no Q&A answers for the security control \"" + controlName + "\", " +
                "write a concise 2-3 sentence summary of the current state of this control. " +
                "The proposed maturity rating is: " + (proposedAnswer != null ? proposedAnswer : "N/A") + ".\n\n" +
                "Q&A:\n" + qaBuilder + "\n" +
                "Write the summary in a neutral, professional tone suitable for an assessment comment. " +
                "Return ONLY the summary text, no headings or extra formatting.";

        String summary = openAIUtil.askAI(prompt);
        response.put("success", true);
        response.put("summary", summary != null ? summary.trim() : "");
        return response;
    }

    /**
     * Suggest an optimal order for assessing security controls using AI.
     * Groups semantically similar controls and orders by logical dependency flow.
     * Results are cached per security catalog and invalidated when the control list changes.
     */
    public Map<String, Object> suggestWizardOrder(Long securityCatalogId, List<Map<String, Object>> controls) {
        Map<String, Object> response = new HashMap<>();

        // Compute a hash of the control list to detect catalog changes
        String controlsHash = computeControlsHash(controls);

        // Return cached order if catalog and controls are unchanged (in-memory first)
        if (securityCatalogId != null && securityCatalogId > 0) {
            WizardOrderCacheEntry cached = wizardOrderCache.get(securityCatalogId);
            if (cached != null && cached.controlsHash.equals(controlsHash)) {
                response.put("success", true);
                response.put("order", cached.order);
                response.put("cached", true);
                return response;
            }

            // Fallback: check DB (populates in-memory cache too)
            Optional<SecurityCatalog> catalogOpt = securityCatalogRepository.findById(securityCatalogId);
            if (catalogOpt.isPresent()) {
                SecurityCatalog catalog = catalogOpt.get();
                if (controlsHash.equals(catalog.getWizardControlOrderHash())
                        && catalog.getWizardControlOrder() != null
                        && !catalog.getWizardControlOrder().isBlank()) {
                    try {
                        JSONArray stored = new JSONArray(catalog.getWizardControlOrder());
                        List<Integer> order = new ArrayList<>();
                        for (int i = 0; i < stored.length(); i++) order.add(stored.getInt(i));
                        wizardOrderCache.put(securityCatalogId, new WizardOrderCacheEntry(order, controlsHash));
                        response.put("success", true);
                        response.put("order", order);
                        response.put("cached", true);
                        return response;
                    } catch (Exception ignored) {}
                }
            }
        }

        StringBuilder controlsList = new StringBuilder();
        for (int i = 0; i < controls.size(); i++) {
            Map<String, Object> ctrl = controls.get(i);
            controlsList.append(i).append(": ")
                    .append(ctrl.get("name"))
                    .append(" [Domain: ").append(ctrl.get("domainName") != null ? ctrl.get("domainName") : "Unknown").append("]")
                    .append(" [Ref: ").append(ctrl.get("reference") != null ? ctrl.get("reference") : "").append("]");
            Object detail = ctrl.get("detail");
            if (detail != null && !detail.toString().isEmpty()) {
                String d = detail.toString();
                controlsList.append(" [Desc: ").append(d.length() > 120 ? d.substring(0, 120) + "..." : d).append("]");
            }
            if (Boolean.TRUE.equals(ctrl.get("answered"))) {
                controlsList.append(" [ALREADY ANSWERED]");
            }
            controlsList.append("\n");
        }

        String prompt = "You are a security assessment expert. Determine the optimal order to assess the following security controls.\n\n" +
                "Controls:\n" + controlsList + "\n" +
                "Ordering rules (apply in priority order):\n" +
                "1. SIMILARITY GROUPING: Identify semantically related controls (e.g. all password/authentication controls, all encryption controls, all logging controls, all network controls) and place them adjacent to each other — the assessor builds context and answers faster when similar topics are consecutive.\n" +
                "2. DEPENDENCY FLOW: Foundational governance controls (policies, roles, awareness, risk management) come first because they provide context for everything else.\n" +
                "3. LOGICAL PROGRESSION: After governance → identity & access management → infrastructure & network → data protection → operational processes → monitoring & incident response → compliance/audit.\n" +
                "4. ANSWERED LAST: Controls already answered should appear at the very end of the list.\n\n" +
                "Return ONLY a JSON array of the original 0-based indices in the suggested order. Example: [3,1,0,2,4]\n" +
                "Return ONLY the JSON array, no explanation.";

        try {
            String aiResponse = openAIUtil.askAI(prompt).trim();
            int startIdx = aiResponse.indexOf('[');
            int endIdx = aiResponse.lastIndexOf(']');
            if (startIdx != -1 && endIdx != -1) {
                aiResponse = aiResponse.substring(startIdx, endIdx + 1);
            }
            JSONArray jsonArray = new JSONArray(aiResponse);
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                order.add(jsonArray.getInt(i));
            }
            // Cache result per catalog (in-memory and DB)
            if (securityCatalogId != null && securityCatalogId > 0) {
                wizardOrderCache.put(securityCatalogId, new WizardOrderCacheEntry(order, controlsHash));
                securityCatalogRepository.findById(securityCatalogId).ifPresent(catalog -> {
                    JSONArray arr = new JSONArray(order);
                    catalog.setWizardControlOrder(arr.toString());
                    catalog.setWizardControlOrderHash(controlsHash);
                    securityCatalogRepository.save(catalog);
                });
            }
            response.put("success", true);
            response.put("order", order);
        } catch (Exception e) {
            List<Integer> defaultOrder = new ArrayList<>();
            for (int i = 0; i < controls.size(); i++) {
                defaultOrder.add(i);
            }
            response.put("success", true);
            response.put("order", defaultOrder);
            response.put("fallback", true);
        }

        return response;
    }

    /** Compute a lightweight hash of control names + count for cache invalidation. */
    private String computeControlsHash(List<Map<String, Object>> controls) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> ctrl : controls) {
            sb.append(ctrl.get("name")).append("|");
        }
        return Integer.toHexString(sb.toString().hashCode());
    }

    /**
     * AI-based answer guessing for a security control.
     * Analyzes the control context and any previous answers to estimate the most likely maturity level.
     */
    public Map<String, Object> guessAnswer(String controlName, String controlDetail, String domainName,
                                            List<Map<String, Object>> maturityModelAnswers,
                                            List<Map<String, Object>> previousAnswers) {
        Map<String, Object> response = new HashMap<>();
        
        StringBuilder maturityStr = new StringBuilder();
        for (Map<String, Object> ma : maturityModelAnswers) {
            maturityStr.append("- ").append(ma.get("answer"))
                    .append(" (Rating: ").append(ma.get("rating")).append("%");
            if (ma.get("description") != null && !ma.get("description").toString().isEmpty()) {
                maturityStr.append(", ").append(ma.get("description"));
            }
            maturityStr.append(")\n");
        }
        
        StringBuilder contextStr = new StringBuilder();
        if (previousAnswers != null && !previousAnswers.isEmpty()) {
            contextStr.append("Previously assessed controls in this assessment:\n");
            for (Map<String, Object> prev : previousAnswers) {
                contextStr.append("- ").append(prev.get("controlName"))
                        .append(": ").append(prev.get("answer")).append("\n");
            }
        }
        
        String prompt = "You are a security assessment expert. Based on the following security control, estimate the most likely maturity level that a typical organization would have.\n\n" +
                "Security Control: " + controlName + "\n" +
                "Description: " + (controlDetail != null ? controlDetail : "N/A") + "\n" +
                "Domain: " + (domainName != null ? domainName : "N/A") + "\n\n" +
                (contextStr.length() > 0 ? contextStr + "\n" : "") +
                "Available maturity levels (ordered lowest to highest):\n" + maturityStr + "\n" +
                "Based on typical organizational maturity patterns, suggest the most likely answer and your confidence level (0-100%).\n\n" +
                "Return ONLY a JSON object like: {\"answer\": \"<exact answer text>\", \"confidence\": <number 0-100>, \"reasoning\": \"<brief 1-sentence reasoning>\"}\n" +
                "Return ONLY the JSON object, no other text.";
        
        try {
            String aiResponse = openAIUtil.askAI(prompt).trim();
            int startIdx = aiResponse.indexOf('{');
            int endIdx = aiResponse.lastIndexOf('}');
            if (startIdx != -1 && endIdx != -1) {
                aiResponse = aiResponse.substring(startIdx, endIdx + 1);
            }
            JSONObject json = new JSONObject(aiResponse);
            String suggestedAnswer = json.getString("answer");
            int confidence = json.getInt("confidence");
            String reasoning = json.optString("reasoning", "");
            
            // Match to available maturity answers
            Map<String, Object> matchedAnswer = null;
            for (Map<String, Object> ma : maturityModelAnswers) {
                if (suggestedAnswer.equalsIgnoreCase(ma.get("answer").toString())) {
                    matchedAnswer = ma;
                    break;
                }
            }
            if (matchedAnswer == null) {
                for (Map<String, Object> ma : maturityModelAnswers) {
                    if (ma.get("answer").toString().toLowerCase().contains(suggestedAnswer.toLowerCase()) ||
                        suggestedAnswer.toLowerCase().contains(ma.get("answer").toString().toLowerCase())) {
                        matchedAnswer = ma;
                        break;
                    }
                }
            }
            
            if (matchedAnswer != null) {
                response.put("success", true);
                response.put("suggestedAnswer", matchedAnswer.get("answer"));
                response.put("suggestedAnswerId", matchedAnswer.get("id"));
                response.put("confidence", confidence);
                response.put("reasoning", reasoning);
            } else {
                // Fallback to middle answer
                int midIdx = maturityModelAnswers.size() / 2;
                Map<String, Object> fallback = maturityModelAnswers.get(midIdx);
                response.put("success", true);
                response.put("suggestedAnswer", fallback.get("answer"));
                response.put("suggestedAnswerId", fallback.get("id"));
                response.put("confidence", 30);
                response.put("reasoning", "Could not determine specific level - defaulting to moderate estimate.");
            }
        } catch (Exception e) {
            // Fallback to middle answer with low confidence
            int midIdx = maturityModelAnswers.size() / 2;
            Map<String, Object> fallback = maturityModelAnswers.get(midIdx);
            response.put("success", true);
            response.put("suggestedAnswer", fallback.get("answer"));
            response.put("suggestedAnswerId", fallback.get("id"));
            response.put("confidence", 20);
            response.put("reasoning", "AI estimation unavailable - showing moderate estimate.");
        }
        
        return response;
    }
}
