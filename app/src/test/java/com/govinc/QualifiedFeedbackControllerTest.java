package com.govinc;

import com.govinc.assessment.*;
import com.govinc.authorization.AuthorizationService;
import com.govinc.catalog.SecurityCatalog;
import com.govinc.catalog.SecurityControl;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QualifiedFeedbackControllerTest {
    @Mock AssessmentRepository assessments;
    @Mock QualifiedFeedbackService feedback;
    @Mock AuthorizationService authorization;
    QualifiedFeedbackController controller;

    @BeforeEach
    void setUp() {
        controller = new QualifiedFeedbackController(assessments, feedback, authorization);
    }

    @Test
    void deniesAssessors() {
        assertThat(controller.save(1L, 2L, Map.of("rating", "ACCEPTED")).getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(feedback);
    }

    @Test
    void validatesCatalogMembershipAndRating() {
        when(authorization.isInformationSecurityManager()).thenReturn(true);
        when(authorization.canAccessAssessment(1L)).thenReturn(true);
        SecurityControl control = new SecurityControl();
        control.setId(2L);
        control.setName("Control");
        SecurityCatalog catalog = new SecurityCatalog();
        catalog.setSecurityControls(Set.of(control));
        Assessment assessment = new Assessment();
        assessment.setId(1L);
        assessment.setStatus(AssessmentStatus.OPEN);
        assessment.setSecurityCatalog(catalog);
        when(assessments.findById(1L)).thenReturn(Optional.of(assessment));
        assertThat(controller.save(1L, 2L, Map.of("rating", "ACCEPTED")).getStatusCode().value()).isEqualTo(409);
        catalog.setQualifiedFeedbackEnabled(true);
        assertThat(controller.save(1L, 3L, Map.of("rating", "ACCEPTED")).getStatusCode().value()).isEqualTo(404);
        assertThat(controller.save(1L, 2L, Map.of("rating", "INVALID")).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.save(1L, 2L, Map.of("rating", "ACCEPTED")).getStatusCode().value()).isEqualTo(200);
        verify(feedback).save(assessment, control, QualifiedFeedback.Rating.ACCEPTED);
        assessment.setStatus(AssessmentStatus.CLOSED);
        assertThat(controller.save(1L, 2L, Map.of("rating", "ACCEPTED")).getStatusCode().value()).isEqualTo(409);
    }
}