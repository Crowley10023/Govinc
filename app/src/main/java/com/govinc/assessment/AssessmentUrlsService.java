package com.govinc.assessment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
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

    /**
     * Generate new obfuscated url, removing previous for this assessment if present.
     * Returns the new AssessmentUrls entity and links it to the Assessment entity.
     */
    @Transactional
    public AssessmentUrls createOrReplaceUrl(Long assessmentId) {
        Assessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found"));

        // Remove any existing URL for this assessment (safer, atomic)
        urlsRepository.deleteByAssessmentId(assessmentId);

        // Generate obfuscated string
        String obfuscated = generateObfuscatedUrl();
        AssessmentUrls newUrl = new AssessmentUrls();
        newUrl.setUrl(obfuscated);
        newUrl.setAssessment(assessment);
        assessment.setAssessmentUrls(newUrl);
        // The direct URL path (if needed in a response):
        // String directUrl = "/assessment/" + assessment.getId() + "/" + obfuscated;
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
            // Prolong by 5 days
            Integer currentLifetime = url.getLifetime();
            if (currentLifetime == null) currentLifetime = 0;
            url.setLifetime(currentLifetime + 5);
            urlsRepository.save(url);
        }
    }

    public void deleteUrl(Long id) {
        Optional<AssessmentUrls> optionalUrl = urlsRepository.findById(id);
        if (optionalUrl.isPresent()) {
            AssessmentUrls url = optionalUrl.get();
            Assessment assessment = url.getAssessment();
            if (assessment != null) {
                assessment.setAssessmentUrls(null);
                // assessment.setAssessmentUrl(null); // obsolete, removed
                assessmentRepository.save(assessment);
            }
            urlsRepository.deleteById(id);
        }
    }

    public List<AssessmentUrls> findAll() {
        return urlsRepository.findAll();
    }
}
