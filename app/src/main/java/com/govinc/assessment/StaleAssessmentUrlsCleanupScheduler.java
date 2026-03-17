package com.govinc.assessment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Scheduled component that runs once per day at midnight.
 * Decrements the remaining lifetime of each assessment URL by 1 day.
 * URLs whose lifetime reaches 0 or below are automatically deleted.
 */
@Component
public class StaleAssessmentUrlsCleanupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(StaleAssessmentUrlsCleanupScheduler.class);

    @Autowired
    private AssessmentUrlsRepository urlsRepository;

    @Autowired
    private AssessmentUrlsService assessmentUrlsService;

    /**
     * Runs every day at midnight (00:00:00) to decrement lifetime counters and
     * remove expired assessment URLs.
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void cleanupStaleUrls() {
        logger.info("Daily assessment URL cleanup starting");

        List<AssessmentUrls> allUrls = urlsRepository.findAll();
        List<Long> toDelete = new ArrayList<>();

        for (AssessmentUrls url : allUrls) {
            int remaining = url.getLifetime() - 1;
            if (remaining <= 0) {
                toDelete.add(url.getId());
            } else {
                url.setLifetime(remaining);
                urlsRepository.save(url);
                logger.debug("Assessment URL id={} decremented to {} remaining day(s)", url.getId(), remaining);
            }
        }

        for (Long id : toDelete) {
            try {
                assessmentUrlsService.deleteUrl(id);
                logger.info("Deleted expired assessment URL id={}", id);
            } catch (Exception e) {
                logger.warn("Failed to delete assessment URL id={}: {}", id, e.getMessage());
            }
        }

        logger.info("Daily assessment URL cleanup completed: {} deleted, {} updated",
                toDelete.size(), allUrls.size() - toDelete.size());
    }
}
