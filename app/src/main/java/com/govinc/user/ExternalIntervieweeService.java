package com.govinc.user;

import com.govinc.assessment.Assessment;
import com.govinc.assessment.AssessmentRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExternalIntervieweeService {
    private final ExternalIntervieweeRepository repository;
    private final UserRepository userRepository;
    private final AssessmentRepository assessmentRepository;

    public ExternalIntervieweeService(ExternalIntervieweeRepository repository, UserRepository userRepository,
                                    AssessmentRepository assessmentRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.assessmentRepository = assessmentRepository;
    }

    @Transactional
    public void recordRecipients(Long assessmentId, List<String> emails) {
        Assessment assessment = assessmentRepository.findById(assessmentId).orElseThrow();
        for (String email : emails) {
            if (userRepository.existsByEmailIgnoreCase(email)) continue;
            ExternalInterviewee interviewee = repository.findByEmailIgnoreCase(email)
                    .orElseGet(() -> repository.save(new ExternalInterviewee(email)));
            assessment.getExternalInterviewees().add(interviewee);
        }
        assessmentRepository.save(assessment);
    }

    @Transactional(readOnly = true)
    public List<String> getEmails(Long assessmentId) {
        return assessmentRepository.findById(assessmentId).orElseThrow().getExternalInterviewees().stream()
                .map(ExternalInterviewee::getEmail).sorted().toList();
    }

    @Transactional
    public void delete(Long id) {
        repository.removeAssessmentLinks(id);
        repository.deleteById(id);
    }
}