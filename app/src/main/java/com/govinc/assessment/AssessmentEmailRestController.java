package com.govinc.assessment;

import com.govinc.authorization.AuthorizationService;
import com.govinc.authorization.UnauthorizedException;
import com.govinc.service.EmailService;
import com.govinc.user.User;
import com.govinc.user.UserRepository;
import com.govinc.util.OpenAIUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST endpoints for sending e-mails related to an assessment.
 *
 * <ul>
 *   <li>POST /assessment/{id}/email/generate — generate AI-drafted email content</li>
 *   <li>POST /assessment/{id}/email/send    — send the email via configured SMTP</li>
 * </ul>
 */
@RestController
@RequestMapping("/assessment")
public class AssessmentEmailRestController {

    @Autowired
    private AssessmentRepository assessmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private OpenAIUtil openAIUtil;

    @Autowired
    private EmailService emailService;

    @Autowired
    private AssessmentUrlsService assessmentUrlsService;

    /**
     * Generate an AI-drafted e-mail subject and body for an assessment.
     *
     * Request body:
     * {
     *   "purpose": "assessment" | "checking",
     *   "baseUrl": "https://app.example.com"   // optional, used to build the assessment link
     * }
     *
     * Response:
     * { "subject": "...", "body": "..." }
     */
    @PostMapping("/{id}/email/generate")
    public ResponseEntity<?> generateEmailContent(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {

        if (!authorizationService.canCreateAssessment()) {
            throw new UnauthorizedException("You do not have permission to send e-mails for this assessment.");
        }

        Assessment assessment = assessmentRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Assessment not found"));

        String purpose = payload.containsKey("purpose") ? String.valueOf(payload.get("purpose")) : "assessment";
        String baseUrl = payload.containsKey("baseUrl") ? String.valueOf(payload.get("baseUrl")) : "";
        if ("null".equals(baseUrl)) baseUrl = "";

        // Resolve sender name from logged-in user
        User currentUser = authorizationService.getCurrentUser();
        String senderName = currentUser != null ? currentUser.getName() : null;
        if (senderName == null || senderName.isBlank()) senderName = null;

        // Resolve recipient names from supplied user IDs (filtered to assigned users)
        List<String> recipientNames = new java.util.ArrayList<>();
        Object rawIdsObj = payload.get("recipientUserIds");
        if (rawIdsObj instanceof List<?> rawIds && !rawIds.isEmpty()) {
            List<Long> assignedUserIds = assessment.getUsers().stream()
                    .map(User::getId).collect(Collectors.toList());
            for (Object o : rawIds) {
                long uid = o instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(o));
                if (assignedUserIds.contains(uid)) {
                    userRepository.findById(uid).ifPresent(u -> {
                        String name = u.getName();
                        if (name != null && !name.isBlank()) recipientNames.add(name);
                    });
                }
            }
        }

        String linkType = payload.containsKey("linkType") ? String.valueOf(payload.get("linkType")) : "internal";
        if ("null".equals(linkType)) linkType = "internal";

        String assessmentLink;
        if ("external".equalsIgnoreCase(linkType)) {
            // Ensure external URL exists; auto-create if missing
            if (assessment.getAssessmentUrls() == null || assessment.getAssessmentUrls().getUrl() == null) {
                AssessmentUrls created = assessmentUrlsService.createOrReplaceUrl(id);
                // Refresh assessment from DB so getAssessmentUrls() returns the new record
                assessment = assessmentRepository.findById(id).orElse(assessment);
            }
            String directUrl = assessment.getAssessmentUrls().getUrl();
            assessmentLink = directUrl.startsWith("http")
                    ? directUrl
                    : (baseUrl.isBlank() ? "/assessment-direct/" : baseUrl.stripTrailing() + "/assessment-direct/") + directUrl;
        } else {
            assessmentLink = baseUrl.isBlank()
                    ? "/assessmentdetails/details/" + id
                    : baseUrl.stripTrailing() + "/assessmentdetails/details/" + id;
        }

        String assessmentName = assessment.getName() != null ? assessment.getName() : "Assessment #" + id;
        String catalogName = assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "";

        // Pre-build the HTML hyperlink so the AI embeds it consistently
        String linkLabel = "checking".equalsIgnoreCase(purpose) ? "View Assessment Results" : "Access the Assessment";
        String htmlLink = "<a href=\"" + assessmentLink + "\">" + linkLabel + "</a>";

        // Build greeting/salutation lines
        String recipientLine = recipientNames.isEmpty()
                ? ""
                : "Recipient name(s): " + String.join(", ", recipientNames) + "\n";
        String senderLine = senderName != null ? "Sender name: " + senderName + "\n" : "";
        String personalisationNote = "Use the recipient's given name in the salutation and sign off with the sender's name.\n" +
                                     "The body must be valid HTML (use <p>, <br>, etc.). Include the assessment link exactly as this HTML anchor: " + htmlLink + "\n";

        String prompt;
        if ("checking".equalsIgnoreCase(purpose)) {
            prompt = "Write a professional and concise e-mail inviting the recipient to review and check the results of a " +
                     "completed security assessment.\n" +
                     recipientLine + senderLine +
                     "Assessment name: " + assessmentName + "\n" +
                     (catalogName.isBlank() ? "" : "Security catalog: " + catalogName + "\n") +
                     "\n" + personalisationNote +
                     "The e-mail should:\n" +
                     "- Thank the recipient for their involvement\n" +
                     "- Invite them to review the assessment results via the provided HTML link\n" +
                     "- Ask them to verify correctness and provide feedback if needed\n" +
                     "- Be polite and professional\n\n" +
                     "Return ONLY a JSON object with two keys: \"subject\" and \"body\". No markdown, no code fences.";
        } else {
            prompt = "Write a professional and motivating e-mail inviting the recipient to participate in a security assessment.\n" +
                     recipientLine + senderLine +
                     "Assessment name: " + assessmentName + "\n" +
                     (catalogName.isBlank() ? "" : "Security catalog: " + catalogName + "\n") +
                     "\n" + personalisationNote +
                     "The e-mail should:\n" +
                     "- Explain the purpose of the assessment briefly\n" +
                     "- Ask the recipient to complete their part of the assessment via the provided HTML link\n" +
                     "- Mention the importance of timely completion\n" +
                     "- Be polite and professional\n\n" +
                     "Return ONLY a JSON object with two keys: \"subject\" and \"body\". No markdown, no code fences.";
        }

        String aiResponse = openAIUtil.askAI(prompt, false);

        // Try to parse JSON from AI response
        try {
            // Strip potential markdown code fences
            String cleaned = aiResponse.trim();
            if (cleaned.startsWith("```")) {
                int start = cleaned.indexOf('\n') + 1;
                int end = cleaned.lastIndexOf("```");
                if (end > start) cleaned = cleaned.substring(start, end).trim();
            }
            org.json.JSONObject json = new org.json.JSONObject(cleaned);
            Map<String, String> result = new LinkedHashMap<>();
            result.put("subject", json.optString("subject", "Assessment Notification"));
            result.put("body", json.optString("body", aiResponse));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            // AI didn't return valid JSON — return the raw text as body with a default subject
            Map<String, String> result = new LinkedHashMap<>();
            result.put("subject", "checking".equalsIgnoreCase(purpose)
                    ? "Please Review: " + assessmentName
                    : "Action Required: " + assessmentName);
            result.put("body", aiResponse);
            return ResponseEntity.ok(result);
        }
    }

    /**
     * Send an e-mail for the given assessment.
     *
     * Request body:
     * {
     *   "recipientUserIds": [1, 2, 3],   // user IDs from assessment.users
     *   "subject": "...",
     *   "body": "..."
     * }
     */
    @PostMapping("/{id}/email/send")
    public ResponseEntity<?> sendEmail(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {

        if (!authorizationService.canCreateAssessment()) {
            throw new UnauthorizedException("You do not have permission to send e-mails for this assessment.");
        }

        Assessment assessment = assessmentRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Assessment not found"));

        // Determine sender from currently logged-in user
        User currentUser = authorizationService.getCurrentUser();
        if (currentUser == null || currentUser.getEmail() == null || currentUser.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Your account does not have a valid e-mail address configured as sender."));
        }
        String from = currentUser.getEmail();

        // Resolve recipient e-mail addresses from provided user IDs
        @SuppressWarnings("unchecked")
        List<Object> rawIds = (List<Object>) payload.get("recipientUserIds");
        if (rawIds == null || rawIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No recipients selected."));
        }

        List<Long> userIds = rawIds.stream()
                .map(o -> o instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(o)))
                .collect(Collectors.toList());

        // Only allow selecting users that are assigned to this assessment (security check)
        List<Long> assignedUserIds = assessment.getUsers().stream()
                .map(User::getId)
                .collect(Collectors.toList());

        List<String> recipientEmails = userIds.stream()
                .filter(assignedUserIds::contains)
                .map(uid -> userRepository.findById(uid))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(User::getEmail)
                .filter(e -> e != null && !e.isBlank())
                .collect(Collectors.toList());

        if (recipientEmails.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "None of the selected users have a valid e-mail address or are assigned to this assessment."));
        }

        String subject = String.valueOf(payload.getOrDefault("subject", ""));
        String body = String.valueOf(payload.getOrDefault("body", ""));

        if (subject.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Subject must not be empty."));
        }
        if (body.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "E-mail body must not be empty."));
        }

        try {
            emailService.sendEmail(from, recipientEmails, subject, body);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "E-mail sent to " + recipientEmails.size() + " recipient(s)."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Failed to send e-mail: " + e.getMessage()));
        }
    }
}
