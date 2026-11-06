package com.govinc;

import com.govinc.assessment.Assessment;
import com.govinc.assessment.AssessmentEmailRestController;
import com.govinc.assessment.AssessmentRepository;
import com.govinc.authorization.AuthorizationService;
import com.govinc.service.EmailService;
import com.govinc.user.ExternalIntervieweeService;
import com.govinc.user.User;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssessmentEmailIntervieweeTest {
    @Mock AssessmentRepository assessments;
    @Mock AuthorizationService authorization;
    @Mock EmailService emailService;
    @Mock ExternalIntervieweeService externalInterviewees;
    AssessmentEmailRestController controller;
    Map<String, Object> payload = Map.of("recipientEmails", List.of("Guest@Example.test"),
            "linkType", "external", "subject", "Subject", "body", "Body");

    @BeforeEach
    void setUp() {
        controller = new AssessmentEmailRestController();
        ReflectionTestUtils.setField(controller, "assessmentRepository", assessments);
        ReflectionTestUtils.setField(controller, "authorizationService", authorization);
        ReflectionTestUtils.setField(controller, "emailService", emailService);
        ReflectionTestUtils.setField(controller, "externalIntervieweeService", externalInterviewees);
        when(authorization.canCreateAssessment()).thenReturn(true);
        when(authorization.getCurrentUser()).thenReturn(new User("Sender", "", "sender@example.test"));
        when(assessments.findById(1L)).thenReturn(Optional.of(new Assessment()));
    }

    @Test
    void recordsRecipientsAfterSuccessfulSend() throws Exception {
        assertThat(controller.sendEmail(1L, payload).getStatusCode().value()).isEqualTo(200);
        var order = inOrder(emailService, externalInterviewees);
        order.verify(emailService).sendEmail(eq("sender@example.test"), eq(List.of("guest@example.test")), anyString(), anyString());
        order.verify(externalInterviewees).recordRecipients(1L, List.of("guest@example.test"));
    }

    @Test
    void doesNotRecordRecipientsWhenSendFails() throws Exception {
        doThrow(new IllegalStateException("SMTP unavailable")).when(emailService).sendEmail(anyString(), anyList(), anyString(), anyString());
        assertThat(controller.sendEmail(1L, payload).getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(externalInterviewees);
    }
}