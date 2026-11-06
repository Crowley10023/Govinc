package com.govinc;

import com.govinc.assessment.Assessment;
import com.govinc.authorization.AuthorizationService;
import com.govinc.entity.EmailConfiguration;
import com.govinc.governance.GovernanceTask;
import com.govinc.governance.GovernanceTaskRepository;
import com.govinc.governance.GovernanceTaskService;
import com.govinc.governance.NewTaskNotification;
import com.govinc.governance.NewTaskNotificationListener;
import com.govinc.repository.EmailConfigurationRepository;
import com.govinc.service.EmailService;
import com.govinc.user.PersonalSettingsController;
import com.govinc.user.User;
import com.govinc.user.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonalSettingsAndTaskNotificationTest {
    @Mock GovernanceTaskRepository taskRepository;
    @Mock UserRepository userRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock AuthorizationService authorizationService;
    @Mock EmailService emailService;
    private GovernanceTaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new GovernanceTaskService();
        ReflectionTestUtils.setField(taskService, "taskRepository", taskRepository);
        ReflectionTestUtils.setField(taskService, "userRepository", userRepository);
        ReflectionTestUtils.setField(taskService, "eventPublisher", eventPublisher);
    }

    @Test
    void savesOnlyTheCurrentUsersPreference() {
        User user = new User("First", "User", "first@example.com");
        when(authorizationService.getCurrentUser()).thenReturn(user);
        PersonalSettingsController controller = new PersonalSettingsController(authorizationService, userRepository);
        assertThat(controller.getSettings().mailForNewTasks()).isFalse();
        assertThat(controller.saveSettings(new PersonalSettingsController.Settings(true)).mailForNewTasks()).isTrue();
        assertThat(controller.getSettings().mailForNewTasks()).isTrue();
        controller.saveSettings(new PersonalSettingsController.Settings(false));
        assertThat(user.isMailForNewTasks()).isFalse();
        verify(userRepository, times(2)).save(user);
        assertThatThrownBy(() -> controller.saveSettings(new PersonalSettingsController.Settings(null)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsSettingsWhenUserIsMissing() {
        PersonalSettingsController controller = new PersonalSettingsController(authorizationService, userRepository);
        assertThatThrownBy(controller::getSettings).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void notifiesOnlyOptedInAssigneesForNewTasks() {
        User assignee = new User("Assigned", "User", "assigned@example.com");
        User creator = new User("Creator", "User", "creator@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(assignee));
        when(taskRepository.save(any(GovernanceTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        taskService.createTask("Off", null, 1L, null, null, null, null, null, creator);
        verifyNoInteractions(eventPublisher);
        assignee.setMailForNewTasks(true);
        taskService.createTask("On", null, 1L, null, null, null, null, null, creator);
        verify(eventPublisher).publishEvent(new NewTaskNotification("assigned@example.com", "creator@example.com", "On"));
        taskService.createTask("Unassigned", null, null, null, null, null, null, null, creator);
        verifyNoMoreInteractions(eventPublisher);
    }

    @Test
    void reviewTaskAlsoNotifiesItsCreator() {
        User creator = new User("Creator", "User", "creator@example.com");
        creator.setMailForNewTasks(true);
        Assessment assessment = new Assessment();
        assessment.setName("Example");
        assessment.setCreatedBy(creator);
        when(taskRepository.save(any(GovernanceTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        taskService.createAssessmentReviewTask(assessment);
        verify(eventPublisher).publishEvent(new NewTaskNotification("creator@example.com", "creator@example.com", "Assessment ready for review: Example"));
    }

    @Test
    void emailUsesExistingServiceAndEscapesTaskTitle() throws Exception {
        NewTaskNotificationListener listener = new NewTaskNotificationListener(emailService);
        listener.onNewTask(new NewTaskNotification("assigned@example.com", "creator@example.com", "<Task>"));
        verify(emailService).sendEmail("creator@example.com", List.of("assigned@example.com"),
                "New task assigned", "A new task has been assigned to you: &lt;Task&gt;");
    }

    @Test
    void emailFailureDoesNotEscapeListenerAndFallsBackToConfiguredSender() throws Exception {
        EmailConfiguration config = new EmailConfiguration();
        config.setSmtpUsername("notifications@example.com");
        when(emailService.getConfiguration()).thenReturn(config);
        doThrow(new IllegalStateException("SMTP unavailable")).when(emailService).sendEmail(anyString(), anyList(), anyString(), anyString());
        NewTaskNotificationListener listener = new NewTaskNotificationListener(emailService);
        assertThatCode(() -> listener.onNewTask(new NewTaskNotification("assigned@example.com", null, "Task")))
                .doesNotThrowAnyException();
        verify(emailService).sendEmail(eq("notifications@example.com"), anyList(), anyString(), anyString());
    }

    @Test
    void notificationWaitsForCommitAndIsDiscardedOnRollback() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(NotificationTestConfig.class)) {
            EmailService mail = context.getBean(EmailService.class);
            TransactionTemplate transaction = new TransactionTemplate(context.getBean(AbstractPlatformTransactionManager.class));
            NewTaskNotification event = new NewTaskNotification("assigned@example.com", "creator@example.com", "Task");
            transaction.executeWithoutResult(status -> {
                context.publishEvent(event);
                verifyNoInteractions(mail);
                status.setRollbackOnly();
            });
            verifyNoInteractions(mail);
            transaction.executeWithoutResult(status -> {
                context.publishEvent(event);
                verifyNoInteractions(mail);
            });
            verify(mail).sendEmail(eq("creator@example.com"), eq(List.of("assigned@example.com")), anyString(), anyString());
        }
    }

    @Test
    void notificationIsDeliveredWithoutAnOuterTransaction() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(NotificationTestConfig.class)) {
            EmailService mail = context.getBean(EmailService.class);
            context.publishEvent(new NewTaskNotification("assigned@example.com", "creator@example.com", "Task"));
            verify(mail).sendEmail(eq("creator@example.com"), eq(List.of("assigned@example.com")), anyString(), anyString());
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class NotificationTestConfig {
        @Bean
        EmailConfigurationRepository emailConfigurationRepository() {
            return mock(EmailConfigurationRepository.class);
        }

        @Bean
        EmailService emailService() {
            return mock(EmailService.class);
        }

        @Bean
        NewTaskNotificationListener listener(EmailService mail) {
            return new NewTaskNotificationListener(mail);
        }

        @Bean
        AbstractPlatformTransactionManager transactionManager() {
            return new AbstractPlatformTransactionManager() {
                @Override protected Object doGetTransaction() { return new Object(); }
                @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
                @Override protected void doCommit(DefaultTransactionStatus status) { }
                @Override protected void doRollback(DefaultTransactionStatus status) { }
            };
        }
    }
}