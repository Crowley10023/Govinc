package com.govinc;

import com.govinc.assessment.*;
import com.govinc.catalog.SecurityControl;
import com.govinc.governance.GovernanceTaskService;
import com.govinc.maturity.MaturityAnswer;
import com.govinc.user.User;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssessmentDirectReviewTaskTest {
    @Mock AssessmentUrlsService urlsService;
    @Mock AssessmentDetailsService detailsService;
    @Mock AssessmentRepository assessmentRepository;
    @Mock GovernanceTaskService taskService;

    @Test
    void createsOneCreatorTaskOnlyOnSuccessfulReviewTransition() {
        AssessmentDirectController controller = new AssessmentDirectController();
        ReflectionTestUtils.setField(controller, "assessmentUrlsService", urlsService);
        ReflectionTestUtils.setField(controller, "assessmentDetailsService", detailsService);
        ReflectionTestUtils.setField(controller, "assessmentRepository", assessmentRepository);
        ReflectionTestUtils.setField(controller, "governanceTaskService", taskService);
        User creator = new User();
        creator.setId(7L);
        Assessment assessment = new Assessment();
        assessment.setId(1L);
        assessment.setName("Example");
        assessment.setCreatedBy(creator);
        assessment.setStatus(AssessmentStatus.OPEN);
        AssessmentUrls url = new AssessmentUrls();
        url.setAssessment(assessment);
        AssessmentDetails details = new AssessmentDetails();
        details.getControlAnswers().add(new AssessmentControlAnswer(new SecurityControl(), new MaturityAnswer()));
        when(urlsService.findByObfuscated("link")).thenReturn(Optional.of(url));
        when(assessmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(assessment));
        when(detailsService.findByAssessmentId(1L)).thenReturn(Optional.of(details));

        assertThat(controller.finalizeAssessmentDirect("link", new MockHttpSession()).getStatusCode().value()).isEqualTo(200);
        assertThat(controller.finalizeAssessmentDirect("link", new MockHttpSession()).getStatusCode().value()).isEqualTo(409);
        verify(taskService).createAssessmentReviewTask(assessment);
    }
}