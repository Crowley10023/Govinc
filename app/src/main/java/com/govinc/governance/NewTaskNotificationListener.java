package com.govinc.governance;

import com.govinc.entity.EmailConfiguration;
import com.govinc.service.EmailService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;

@Component
public class NewTaskNotificationListener {
    private static final Logger logger = LoggerFactory.getLogger(NewTaskNotificationListener.class);
    private final EmailService emailService;

    public NewTaskNotificationListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onNewTask(NewTaskNotification notification) {
        try {
            String sender = notification.sender();
            if (sender == null || sender.isBlank()) {
                EmailConfiguration config = emailService.getConfiguration();
                sender = config != null ? config.getSmtpUsername() : null;
            }
            if (sender == null || sender.isBlank()) {
                throw new IllegalStateException("No sender address is available for the task notification");
            }
            String title = notification.title() != null ? notification.title() : "";
            emailService.sendEmail(sender, List.of(notification.recipient()), "New task assigned",
                    "A new task has been assigned to you: " + HtmlUtils.htmlEscape(title));
        } catch (Exception exception) {
            logger.warn("Could not send new task notification to {}", notification.recipient(), exception);
        }
    }
}