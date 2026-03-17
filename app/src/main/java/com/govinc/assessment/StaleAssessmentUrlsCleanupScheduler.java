package com.govinc.assessment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled component that periodically checks for and removes stale assessment URLs.
 * A URL is considered stale if the current time exceeds its creation time plus lifetime (in days).
 */
@Component
public class StaleAssessmentUrlsCleanupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(StaleAssessmentUrlsCleanupScheduler.class);

    @Autowired
    private AssessmentUrlsRepository urlsRepository;

    /**
     * Runs every hour to check for and delete stale assessment URLs.
     * Can be configured via application.properties or application.yml
     */
    @Scheduled(fixedRate = 3600000) // 1 hour in milliseconds
    @Transactional
    public void cleanupStaleUrls() {
        logger.debug("Starting cleanup of stale assessment URLs");

        List<AssessmentUrls> allUrls = urlsRepository.findAll();
        LocalDateTime now = LocalDateTime.now();

        for (AssessmentUrls url : allUrls) {
            // If createdAt is missing (null), set it to now and persist it so stale logic has a baseline.
            if (url.getCreatedAt() == null) {
                logger.info("Assessment URL id {} has null createdAt; setting to now", url.getId());
                urlsRepository.updateCreatedAtById(url.getId(), now);
                // update in-memory value so subsequent checks use the new timestamp
                url.setCreatedAt(now);
            }

            if (isStale(url, now)) {
                logger.info("Removing stale assessment URL with id: {} (created at: {}, lifetime: {})",
                        url.getId(), url.getCreatedAt(), url.getLifetime());
                urlsRepository.deleteById(url.getId());
                }
            }

        logger.debug("Cleanup of stale assessment URLs completed");
    }

    /**
     * Checks if an assessment URL has expired based on its creation time and lifetime.
     *
     * @param url the AssessmentUrls entity to check
     * @param now the current time
     * @return true if the URL is stale (expired), false otherwise
     */
    private boolean isStale(AssessmentUrls url, LocalDateTime now) {
        LocalDateTime expirationTime = url.getCreatedAt().plusDays(url.getLifetime());
        return now.isAfter(expirationTime);
    }
}
