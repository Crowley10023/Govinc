package com.govinc;

import com.govinc.assessment.Assessment;
import com.govinc.assessment.AssessmentRepository;
import com.govinc.user.ExternalInterviewee;
import com.govinc.user.ExternalIntervieweeRepository;
import com.govinc.user.ExternalIntervieweeService;
import com.govinc.user.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalIntervieweeServiceTest {
    @Mock ExternalIntervieweeRepository repository;
    @Mock UserRepository userRepository;
    @Mock AssessmentRepository assessmentRepository;

    @Test
    void recordsOnlyExternalRecipientsAndReusesDirectoryEntries() {
        Assessment assessment = new Assessment();
        ExternalInterviewee existing = new ExternalInterviewee("outside@example.com");
        when(assessmentRepository.findById(1L)).thenReturn(Optional.of(assessment));
        when(userRepository.existsByEmailIgnoreCase("inside@example.com")).thenReturn(true);
        when(repository.findByEmailIgnoreCase("outside@example.com")).thenReturn(Optional.of(existing));
        ExternalIntervieweeService service = new ExternalIntervieweeService(repository, userRepository, assessmentRepository);

        service.recordRecipients(1L, List.of("inside@example.com", "outside@example.com"));
        service.recordRecipients(1L, List.of("outside@example.com"));

        assertThat(assessment.getExternalInterviewees()).containsExactly(existing);
        verify(repository, never()).save(any());
    }
}