package com.govinc.assessment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

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
     * The response also includes a pre-generated AI comment based on the Q&A answers.
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
        String controlName = (String) request.get("controlName");

        if (controlId == null) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Control ID is required");
            return errorResponse;
        }

        // Use service to analyze answers and propose maturity level
        Map<String, Object> result = answeringGuideService.proposeAnswerFromGuide(controlId, securityCatalogId, questions, answers, maturityModelAnswers);

        // Also generate a comment based on the Q&A answers and proposed answer, so the client
        // can pre-populate the comment field without a separate round-trip.
        if (Boolean.TRUE.equals(result.get("success"))
                && controlName != null && !controlName.isBlank()
                && questions != null && !questions.isEmpty()) {
            String proposedAnswerText = result.get("proposedAnswer") != null ? result.get("proposedAnswer").toString() : null;
            Map<String, Object> commentResult = answeringGuideService.generateAnswerSummary(
                    controlName, questions, answers, proposedAnswerText);
            if (Boolean.TRUE.equals(commentResult.get("success"))) {
                result.put("comment", commentResult.get("summary"));
            }
        }

        return result;
    }

    /**
     * Generate an AI summary comment from guide Q&A answers.
     */
    @PostMapping("/generate-answer-summary")
    @ResponseBody
    public Map<String, Object> generateAnswerSummary(@RequestBody Map<String, Object> request) {
        String controlName = (String) request.get("controlName");
        @SuppressWarnings("unchecked")
        List<String> questions = (List<String>) request.get("questions");
        @SuppressWarnings("unchecked")
        List<String> answers = (List<String>) request.get("answers");
        String proposedAnswer = (String) request.get("proposedAnswer");
        if (controlName == null || controlName.isBlank()) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "controlName is required");
            return err;
        }
        return answeringGuideService.generateAnswerSummary(controlName, questions, answers, proposedAnswer);
    }

    /**
     * AI-based ordering of controls for the Assessment Wizard.
     * Suggests the best order to assess security controls based on similarity and logical grouping.
     * Results are cached per security catalog and re-calculated when the catalog changes.
     */
    @PostMapping("/wizard-order-controls")
    @ResponseBody
    public Map<String, Object> wizardOrderControls(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> controls = (List<Map<String, Object>>) request.get("controls");
        
        if (controls == null || controls.isEmpty()) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Controls list is required");
            return err;
        }

        Long securityCatalogId = null;
        Object catalogIdObj = request.get("securityCatalogId");
        if (catalogIdObj instanceof Number) {
            securityCatalogId = ((Number) catalogIdObj).longValue();
        } else if (catalogIdObj instanceof String) {
            try { securityCatalogId = Long.parseLong((String) catalogIdObj); } catch (NumberFormatException ignored) { }
        }
        
        return answeringGuideService.suggestWizardOrder(securityCatalogId, controls);
    }

    /**
     * AI-based answer guessing for a single control in the Assessment Wizard.
     * Provides a confidence-rated pre-assessment before the user answers.
     */
    @PostMapping("/wizard-guess-answer")
    @ResponseBody
    public Map<String, Object> wizardGuessAnswer(@RequestBody Map<String, Object> request) {
        String controlName = (String) request.get("controlName");
        String controlDetail = (String) request.get("controlDetail");
        String domainName = (String) request.get("domainName");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> maturityModelAnswers = (List<Map<String, Object>>) request.get("maturityModelAnswers");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> previousAnswers = (List<Map<String, Object>>) request.get("previousAnswers");
        
        if (controlName == null || controlName.isBlank()) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "controlName is required");
            return err;
        }
        
        if (maturityModelAnswers == null || maturityModelAnswers.isEmpty()) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "maturityModelAnswers are required");
            return err;
        }
        
        return answeringGuideService.guessAnswer(controlName, controlDetail, domainName, maturityModelAnswers, previousAnswers);
    }
}
