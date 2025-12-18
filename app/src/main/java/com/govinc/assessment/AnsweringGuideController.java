package com.govinc.assessment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
@RequestMapping("/assessment")
public class AnsweringGuideController {

    @Autowired
    private AnsweringGuideService answeringGuideService;

    /**
     * Generate answering guide questions for a security control.
     */
    @PostMapping("/generate-answering-guide-questions")
    @ResponseBody
    public Map<String, Object> generateAnsweringGuideQuestions(@RequestBody Map<String, Object> request) {
        Object controlIdObj = request.get("controlId");
        Long controlId = null;
        
        // Parse controlId safely
        if (controlIdObj instanceof Number) {
            controlId = ((Number) controlIdObj).longValue();
        } else if (controlIdObj instanceof String) {
            try {
                controlId = Long.parseLong((String) controlIdObj);
            } catch (NumberFormatException e) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Invalid control ID format");
                return errorResponse;
            }
        }
        
        String controlName = (String) request.get("controlName");
        String controlDetail = (String) request.get("controlDetail");

        if (controlName == null || controlName.trim().isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Control name is required");
            return errorResponse;
        }

        if (controlId == null) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Control ID is required");
            return errorResponse;
        }

        // Use service to get questions (cached or newly generated)
        return answeringGuideService.getAnsweringGuide(controlId, controlName, controlDetail);
    }

    /**
     * Generate answer proposal based on user's yes/no answers.
     * The proposal is constrained to maturity model answers provided in the request.
     * This ensures the proposed answer fits to the assessment's maturity model.
     */
    @PostMapping("/generate-answer-from-guide")
    @ResponseBody
    public Map<String, Object> generateAnswerFromGuide(@RequestBody Map<String, Object> request) {
        Object controlIdObj = request.get("controlId");
        Long controlId = null;
        
        // Parse controlId safely
        if (controlIdObj instanceof Number) {
            controlId = ((Number) controlIdObj).longValue();
        } else if (controlIdObj instanceof String) {
            try {
                controlId = Long.parseLong((String) controlIdObj);
            } catch (NumberFormatException e) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Invalid control ID format");
                return errorResponse;
            }
        }
        
        Object catalogIdObj = request.get("securityCatalogId");
        Long securityCatalogId = null;
        
        // Parse catalogId safely
        if (catalogIdObj instanceof Number) {
            securityCatalogId = ((Number) catalogIdObj).longValue();
        } else if (catalogIdObj instanceof String) {
            try {
                securityCatalogId = Long.parseLong((String) catalogIdObj);
            } catch (NumberFormatException e) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Invalid catalog ID format");
                return errorResponse;
            }
        }
        
        @SuppressWarnings("unchecked")
        List<String> questions = (List<String>) request.get("questions");
        @SuppressWarnings("unchecked")
        List<String> answers = (List<String>) request.get("answers");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> maturityModelAnswers = (List<Map<String, Object>>) request.get("maturityModelAnswers");

        if (controlId == null) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Control ID is required");
            return errorResponse;
        }

        // Use service to analyze answers and propose maturity level
        // The proposed answer will be constrained to the provided maturity model answers
        return answeringGuideService.proposeAnswerFromGuide(controlId, securityCatalogId, questions, answers, maturityModelAnswers);
    }
}
