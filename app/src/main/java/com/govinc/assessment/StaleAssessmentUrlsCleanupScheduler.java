package com.govinc.assessment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled component that runs once per day at midnight.
 * Removes assessment URLs whose expiration date has been reached.
 */
@Component
public class StaleAssessmentUrlsCleanupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(StaleAssessmentUrlsCleanupScheduler.class);

    @Autowired
    private AssessmentUrlsService assessmentUrlsService;

    /**
     * Runs every day at midnight (00:00:00) to remove expired assessment URLs.
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void cleanupStaleUrls() {
        logger.info("Daily assessment URL cleanup starting");
        int deleted = assessmentUrlsService.cleanupExpiredUrls();
        logger.info("Daily assessment URL cleanup completed: {} expired URL(s) deleted", deleted);
    }
}
