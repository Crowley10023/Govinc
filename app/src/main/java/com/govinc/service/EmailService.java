package com.govinc.service;

import com.govinc.entity.EmailConfiguration;
import com.govinc.repository.EmailConfigurationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Properties;

/**
 * Sends emails via a dynamically configured SMTP server.
 * The SMTP settings are loaded from {@link EmailConfiguration} stored in the database.
 * Recipient addresses are validated against the configured allowed domain.
 */
@Service
public class EmailService {

    @Autowired
    private EmailConfigurationRepository emailConfigurationRepository;

    /**
     * Retrieve the active email configuration (first record in the table).
     * Returns {@code null} when no configuration exists yet.
     */
    public EmailConfiguration getConfiguration() {
        return emailConfigurationRepository.findAll().stream().findFirst().orElse(null);
    }

    /**
     * Send an HTML email.
     *
     * @param from       sender address (must be a valid e-mail)
     * @param recipients list of recipient addresses to send to
     * @param subject    email subject
     * @param body       email body (plain text; newlines rendered as-is)
     * @throws IllegalStateException    when no SMTP configuration is present
     * @throws IllegalArgumentException when a recipient violates the allowed domain
     * @throws Exception                on SMTP transport errors
     */
    public void sendEmail(String from, List<String> recipients, String subject, String body) throws Exception {
        EmailConfiguration config = getConfiguration();
        if (config == null || config.getSmtpHost() == null || config.getSmtpHost().isBlank()) {
            throw new IllegalStateException("E-Mail is not configured. Please set up SMTP settings in E-Mail Configuration.");
        }

        // Domain validation
        String allowedDomain = config.getAllowedDomain();
        if (allowedDomain != null && !allowedDomain.isBlank()) {
            String domain = allowedDomain.trim().toLowerCase();
            if (!domain.startsWith("@")) {
                domain = "@" + domain;
            }
            for (String recipient : recipients) {
                if (!recipient.trim().toLowerCase().endsWith(domain)) {
                    throw new IllegalArgumentException(
                            "Recipient address '" + recipient + "' is not in the allowed domain '" + allowedDomain + "'.");
                }
            }
        }

        JavaMailSenderImpl mailSender = buildMailSender(config);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(from);
        helper.setTo(recipients.toArray(new String[0]));
        helper.setSubject(subject);
        // Send as plain text with HTML <br> newlines for basic formatting
        helper.setText(body.replace("\n", "<br/>"), true);

        mailSender.send(message);
    }

    private JavaMailSenderImpl buildMailSender(EmailConfiguration config) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(config.getSmtpHost());
        mailSender.setPort(config.getSmtpPort() != null ? config.getSmtpPort() : 587);

        if (config.getSmtpUsername() != null && !config.getSmtpUsername().isBlank()) {
            mailSender.setUsername(config.getSmtpUsername());
        }
        if (config.getSmtpPassword() != null && !config.getSmtpPassword().isBlank()) {
            mailSender.setPassword(config.getSmtpPassword());
        }

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", (config.getSmtpUsername() != null && !config.getSmtpUsername().isBlank()) ? "true" : "false");

        boolean tls = Boolean.TRUE.equals(config.getSmtpTls());
        if (tls) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }

        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        return mailSender;
    }
}
