package com.govinc.assessment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AssessmentUrlsService {
    @Autowired
    private AssessmentUrlsRepository urlsRepository;

    @Autowired
    private AssessmentDetailsRepository detailsRepository;

    @Autowired
    private AssessmentRepository assessmentRepository;

    private static final int OBFUSCATED_LENGTH = 100;
    private static final String URL_CHARS = "123456789";
    private static final SecureRandom random = new SecureRandom();
    /** Default lifetime (days) for a newly created direct URL. */
    private static final int DEFAULT_URL_LIFETIME_DAYS = 30;

    /**
     * Generate new obfuscated url, removing previous for this assessment if present.
     * Returns the new AssessmentUrls entity and links it to the Assessment entity.
     */
    @Transactional
    public AssessmentUrls createOrReplaceUrl(Long assessmentId) {
        // Verify assessment exists before the delete
        if (!assessmentRepository.existsById(assessmentId)) {
            throw new IllegalArgumentException("Assessment not found");
        }

        // Bulk DELETE — executes immediately, bypassing Hibernate's insert-before-delete
        // flush ordering, and clears the 1st-level cache so the assessment is re-fetched fresh.
        urlsRepository.deleteByAssessmentId(assessmentId);

        // Re-fetch after cache clear (clearAutomatically = true on deleteByAssessmentId)
        Assessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found"));

        // Generate obfuscated string
        String obfuscated = generateObfuscatedUrl();
        AssessmentUrls newUrl = new AssessmentUrls();
        newUrl.setUrl(obfuscated);
        newUrl.setAssessment(assessment);
        assessment.setAssessmentUrls(newUrl);
        // Set the absolute expiration date on the assessment entity
        assessment.setUrlExpirationDate(LocalDate.now().plusDays(DEFAULT_URL_LIFETIME_DAYS));
        // Only save the parent (Assessment); CascadeType.ALL on assessmentUrls will persist the child as well
        assessmentRepository.save(assessment);
        // Do NOT save newUrl directly to prevent duplicate entries
        return newUrl;
    }

    public Optional<AssessmentUrls> findByObfuscated(String obfuscated) {
        return urlsRepository.findByUrl(obfuscated);
    }

    private String generateObfuscatedUrl() {
        StringBuilder sb = new StringBuilder(OBFUSCATED_LENGTH);
        for (int i = 0; i < OBFUSCATED_LENGTH; i++) {
            int idx = random.nextInt(URL_CHARS.length());
            sb.append(URL_CHARS.charAt(idx));
        }
        return sb.toString();
    }

    // --- Required Methods for Controller ---
    public void prolongLifetime(Long id) {
        Optional<AssessmentUrls> optionalUrl = urlsRepository.findById(id);
        if (optionalUrl.isPresent()) {
            AssessmentUrls url = optionalUrl.get();
            Assessment assessment = url.getAssessment();
            if (assessment != null) {
                LocalDate current = assessment.getUrlExpirationDate();
                if (current == null) current = LocalDate.now();
                assessment.setUrlExpirationDate(current.plusDays(5));
                assessmentRepository.save(assessment);
            }
        }
    }

    public void deleteUrl(Long id) {
        Optional<AssessmentUrls> optionalUrl = urlsRepository.findById(id);
        if (optionalUrl.isPresent()) {
            AssessmentUrls url = optionalUrl.get();
            Assessment assessment = url.getAssessment();
            if (assessment != null) {
                assessment.setAssessmentUrls(null);
                assessment.setUrlExpirationDate(null);
                assessmentRepository.save(assessment);
            }
            urlsRepository.deleteById(id);
        }
    }

    public List<AssessmentUrls> findAll() {
        return urlsRepository.findAll();
    }

    /**
     * Removes all assessment URLs whose expiration date has been reached.
     * Called by the scheduled cleanup and by the manual "Check Expiration" button.
     */
    @Transactional
    public int cleanupExpiredUrls() {
        LocalDate today = LocalDate.now();
        List<AssessmentUrls> allUrls = urlsRepository.findAll();
        List<Long> toDelete = new ArrayList<>();
        for (AssessmentUrls url : allUrls) {
            Assessment assessment = url.getAssessment();
            if (assessment != null && assessment.getUrlExpirationDate() != null
                    && !today.isBefore(assessment.getUrlExpirationDate())) {
                toDelete.add(url.getId());
            }
        }
        for (Long urlId : toDelete) {
            deleteUrl(urlId);
        }
        return toDelete.size();
    }
}
