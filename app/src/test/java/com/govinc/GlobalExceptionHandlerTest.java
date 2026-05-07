package com.govinc;

import com.govinc.authorization.UnauthorizedException;
import com.govinc.entity.LayoutConfiguration;
import com.govinc.entity.LayoutConfigurationRepository;
import com.govinc.service.ErrorLogService;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.extension.ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GlobalExceptionHandlerTest {

    @Mock
    private Environment env;

    @Mock
    private LayoutConfigurationRepository layoutConfigurationRepository;

    @Mock
    private ErrorLogService errorLogService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private AsyncContext asyncContext;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        lenient().when(env.getActiveProfiles()).thenReturn(new String[0]);
        lenient().when(layoutConfigurationRepository.findAll()).thenReturn(List.of());
        handler = new GlobalExceptionHandler(env, layoutConfigurationRepository, errorLogService);
    }

    private void mockAsyncStartedRequest() {
        when(request.isAsyncStarted()).thenReturn(true);
        when(request.getAsyncContext()).thenReturn(asyncContext);
    }

    private static Stream<String> disconnectMessages() {
        return Stream.of(
                "Broken pipe",
                "broken pipe while flushing output",
                "Connection reset by peer",
                "connection reset during write",
                "An existing connection was forcibly closed by the remote host",
                "Eine bestehende Verbindung wurde softwaregesteuert durch den Hostcomputer abgebrochen");
    }

    private static Stream<Throwable> ioNestedDisconnectCauses() {
        return Stream.of(
                new IOException("outer", new IOException("Broken pipe")),
                new IOException("outer", new IOException("Connection reset by peer")),
                new IOException("outer", new IOException("forcibly closed by the remote host")),
                new IOException("outer", new IOException("durch den Hostcomputer abgebrochen")),
                new IOException("outer", new ClientAbortException("client abort")));
    }

    private static Stream<Throwable> genericNestedDisconnectCauses() {
        return Stream.of(
                new RuntimeException("outer", new IOException("Broken pipe")),
                new RuntimeException("outer", new IOException("Connection reset by peer")),
                new RuntimeException("outer", new IOException("forcibly closed by the remote host")),
                new RuntimeException("outer", new IOException("durch den Hostcomputer abgebrochen")),
                new RuntimeException("outer", new ClientAbortException("client abort")));
    }

    private static Stream<String> apiAcceptHeaders() {
        return Stream.of("application/json", "application/json;charset=UTF-8");
    }

    private static void assertNoContentResponse(Object response) {
        assertThat(response).isInstanceOf(ResponseEntity.class);
        ResponseEntity<?> entity = (ResponseEntity<?>) response;
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void handleAsyncRequestNotUsable_completesAsyncRequestWhenStarted() {
        mockAsyncStartedRequest();

        ResponseEntity<Void> response = handler.handleAsyncRequestNotUsable(
                new AsyncRequestNotUsableException("not usable"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(asyncContext).complete();
        verifyNoInteractions(errorLogService);
    }

    @Test
    void handleAsyncRequestNotUsable_doesNothingWhenAsyncNotStarted() {
        when(request.isAsyncStarted()).thenReturn(false);

        ResponseEntity<Void> response = handler.handleAsyncRequestNotUsable(
                new AsyncRequestNotUsableException("not usable"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(request, never()).getAsyncContext();
        verifyNoInteractions(errorLogService);
    }

    @ParameterizedTest(name = "handleIOException returns 204 for disconnect message [{0}]")
    @MethodSource("disconnectMessages")
    void handleIOException_returnsNoContentForDisconnectMessages(String message) {
        mockAsyncStartedRequest();

        Object response = handler.handleIOException(new IOException(message), request);

        assertNoContentResponse(response);
        verify(asyncContext).complete();
        verifyNoInteractions(errorLogService);
    }

    @ParameterizedTest(name = "handleException returns 204 for disconnect message [{0}]")
    @MethodSource("disconnectMessages")
    void handleException_returnsNoContentForDisconnectMessages(String message) {
        mockAsyncStartedRequest();

        Object response = handler.handleException(request, new RuntimeException(message));

        assertNoContentResponse(response);
        verify(asyncContext).complete();
        verifyNoInteractions(errorLogService);
    }

    @ParameterizedTest(name = "handleIOException returns 204 for nested disconnect cause [{index}]")
    @MethodSource("ioNestedDisconnectCauses")
    void handleIOException_returnsNoContentForNestedDisconnectCauses(Throwable throwable) {
        mockAsyncStartedRequest();

        Object response = handler.handleIOException((IOException) throwable, request);

        assertNoContentResponse(response);
        verify(asyncContext).complete();
        verifyNoInteractions(errorLogService);
    }

    @ParameterizedTest(name = "handleException returns 204 for nested disconnect cause [{index}]")
    @MethodSource("genericNestedDisconnectCauses")
    void handleException_returnsNoContentForNestedDisconnectCauses(Throwable throwable) {
        mockAsyncStartedRequest();

        Object response = handler.handleException(request, (Exception) throwable);

        assertNoContentResponse(response);
        verify(asyncContext).complete();
        verifyNoInteractions(errorLogService);
    }

    @Test
    void handleIOException_returnsNoContentForDirectClientAbortException() throws Exception {
        mockAsyncStartedRequest();

        Object response = handler.handleIOException(new ClientAbortException("client abort"), request);

        assertNoContentResponse(response);
        verify(asyncContext).complete();
        verifyNoInteractions(errorLogService);
    }

    @Test
    void handleException_returnsNoContentForAsyncRequestNotUsableException() {
        mockAsyncStartedRequest();

        Object response = handler.handleException(request, new AsyncRequestNotUsableException("dead request"));

        assertNoContentResponse(response);
        verify(asyncContext).complete();
        verifyNoInteractions(errorLogService);
    }

    @Test
    void handleIOException_returnsNoContentForBrokenPipeWithNullRequest() {
        Object response = handler.handleIOException(new IOException("Broken pipe"), null);

        assertNoContentResponse(response);
        verifyNoInteractions(errorLogService);
    }

    @Test
    void handleException_returnsNoContentForBrokenPipeWithNullRequest() {
        Object response = handler.handleException(null, new RuntimeException("Broken pipe"));

        assertNoContentResponse(response);
        verifyNoInteractions(errorLogService);
    }

    @Test
    void handleException_handlesAsyncContextCompletionFailuresGracefully() {
        when(request.isAsyncStarted()).thenReturn(true);
        when(request.getAsyncContext()).thenReturn(asyncContext);
        doThrow(new IllegalStateException("already complete")).when(asyncContext).complete();

        Object response = handler.handleException(request, new RuntimeException("Broken pipe"));

        assertNoContentResponse(response);
        verify(asyncContext).complete();
        verifyNoInteractions(errorLogService);
    }

    @Test
    void handleIOException_nonDisconnectReturnsErrorViewAndLogsException() {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/test");

        Object response = handler.handleIOException(new IOException("disk full"), request);

        assertThat(response).isInstanceOf(ModelAndView.class);
        ModelAndView mav = (ModelAndView) response;
        assertThat(mav.getViewName()).isEqualTo("error");
        verify(errorLogService).logException(any(IOException.class), any(HttpServletRequest.class));
    }

    @Test
    void handleException_nonDisconnectReturnsErrorViewAndLogsException() {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/failure");

        Object response = handler.handleException(request, new IllegalStateException("boom"));

        assertThat(response).isInstanceOf(ModelAndView.class);
        ModelAndView mav = (ModelAndView) response;
        assertThat(mav.getViewName()).isEqualTo("error");
        verify(errorLogService).logException(any(IllegalStateException.class), any(HttpServletRequest.class));
    }

    @Test
    void handleException_inDevelopmentProfileShowsStackTraceDetails() {
        when(env.getActiveProfiles()).thenReturn(new String[] {"dev"});
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/dev");

        handler = new GlobalExceptionHandler(env, layoutConfigurationRepository, errorLogService);
        ModelAndView mav = (ModelAndView) handler.handleException(request, new IllegalArgumentException("boom"));

        assertThat(mav.getModel().get("showDetails")).isEqualTo(true);
        assertThat((String) mav.getModel().get("details")).contains("IllegalArgumentException");
    }

    @Test
    void handleException_inProductionProfileOmitsStackTraceDetails() {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/prod");

        ModelAndView mav = (ModelAndView) handler.handleException(request, new IllegalArgumentException("boom"));

        assertThat(mav.getModel().get("showDetails")).isEqualTo(false);
        assertThat(mav.getModel()).doesNotContainKey("details");
    }

    @Test
    void handleException_usesDefaultLayoutWhenRepositoryIsEmpty() {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/layout");

        ModelAndView mav = (ModelAndView) handler.handleException(request, new RuntimeException("boom"));

        assertThat(mav.getModel()).containsKey("layoutConfig");
        LayoutConfiguration layout = (LayoutConfiguration) mav.getModel().get("layoutConfig");
        assertThat(layout.getPrimaryColor()).isEqualTo("#007bff");
    }

    @Test
    void handleException_usesDefaultLayoutWhenRepositoryThrows() {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/layout-db");
        when(layoutConfigurationRepository.findAll()).thenThrow(new RuntimeException("db down"));

        ModelAndView mav = (ModelAndView) handler.handleException(request, new RuntimeException("boom"));

        assertThat(mav.getModel()).containsKey("layoutConfig");
        LayoutConfiguration layout = (LayoutConfiguration) mav.getModel().get("layoutConfig");
        assertThat(layout.getAccentColor()).isEqualTo("#28a745");
    }

    @ParameterizedTest(name = "handleUnauthorizedException treats Accept header as API [{0}]")
    @MethodSource("apiAcceptHeaders")
    void handleUnauthorizedException_acceptHeaderReturnsJson(String acceptHeader) {
        when(request.getHeader("Accept")).thenReturn(acceptHeader);
        when(request.getHeader("Content-Type")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/secured");
        when(request.getHeader("X-Requested-With")).thenReturn(null);

        ResponseEntity<?> response = handler.handleUnauthorizedException(new UnauthorizedException("forbidden"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isInstanceOf(Map.class);
    }

    @Test
    void handleUnauthorizedException_contentTypeJsonReturnsJson() {
        when(request.getHeader("Accept")).thenReturn("text/html");
        when(request.getHeader("Content-Type")).thenReturn("application/json");
        when(request.getRequestURI()).thenReturn("/secured");
        when(request.getHeader("X-Requested-With")).thenReturn(null);

        ResponseEntity<?> response = handler.handleUnauthorizedException(new UnauthorizedException("forbidden"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isInstanceOf(Map.class);
    }

    @Test
    void handleUnauthorizedException_apiUriReturnsJson() {
        when(request.getHeader("Accept")).thenReturn("text/html");
        when(request.getHeader("Content-Type")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/secured");
        when(request.getHeader("X-Requested-With")).thenReturn(null);

        ResponseEntity<?> response = handler.handleUnauthorizedException(new UnauthorizedException("forbidden"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isInstanceOf(Map.class);
    }

    @Test
    void handleUnauthorizedException_xhrReturnsJson() {
        when(request.getHeader("Accept")).thenReturn("text/html");
        when(request.getHeader("Content-Type")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/secured");
        when(request.getHeader("X-Requested-With")).thenReturn("XMLHttpRequest");

        ResponseEntity<?> response = handler.handleUnauthorizedException(new UnauthorizedException("forbidden"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isInstanceOf(Map.class);
    }

    @Test
    void handleUnauthorizedException_pageRequestReturnsNotAuthorizedView() {
        when(request.getHeader("Accept")).thenReturn("text/html");
        when(request.getHeader("Content-Type")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/secured/page");
        when(request.getHeader("X-Requested-With")).thenReturn(null);

        ResponseEntity<?> response = handler.handleUnauthorizedException(new UnauthorizedException("forbidden"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isInstanceOf(ModelAndView.class);
        assertThat(((ModelAndView) response.getBody()).getViewName()).isEqualTo("not-authorized");
    }
}