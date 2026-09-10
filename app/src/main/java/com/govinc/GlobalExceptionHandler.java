package com.govinc;

import com.govinc.entity.LayoutConfiguration;
import com.govinc.entity.LayoutConfigurationRepository;
import com.govinc.entity.OrganisationDetails;
import com.govinc.entity.OrganisationDetailsRepository;
import com.govinc.authorization.AuthorizationService;
import com.govinc.authorization.UnauthorizedException;
import com.govinc.service.ErrorLogService;
import com.govinc.service.GeneralConfigService;
import com.govinc.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private final Environment env;
    private final LayoutConfigurationRepository layoutConfigurationRepository;
    private final ErrorLogService errorLogService;
    private final OrganisationDetailsRepository organisationDetailsRepository;
    private final AuthorizationService authorizationService;
    private final GeneralConfigService generalConfigService;

    @Value("${app.version:2.1.0}")
    private String versionFile;

    public GlobalExceptionHandler(Environment env,
                                  LayoutConfigurationRepository layoutConfigurationRepository,
                                  ErrorLogService errorLogService,
                                  OrganisationDetailsRepository organisationDetailsRepository,
                                  AuthorizationService authorizationService,
                                  GeneralConfigService generalConfigService) {
        this.env = env;
        this.layoutConfigurationRepository = layoutConfigurationRepository;
        this.errorLogService = errorLogService;
        this.organisationDetailsRepository = organisationDetailsRepository;
        this.authorizationService = authorizationService;
        this.generalConfigService = generalConfigService;
    }

    /**
     * SSE client disconnected (browser navigated away, tab closed) — do nothing.
     * Without this, the catch-all handler tries to render an error page on a dead connection,
     * causing a secondary "Response not usable after response errors" exception.
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<Void> handleAsyncRequestNotUsable(AsyncRequestNotUsableException ex,
                                                             HttpServletRequest request) {
        completeAsyncRequest(request);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @ExceptionHandler(IOException.class)
    public Object handleIOException(IOException ex, HttpServletRequest request) {
        if (isClientDisconnectException(ex)) {
            completeAsyncRequest(request);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }
        return handleException(request, ex);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public Object handleUnauthorizedException(UnauthorizedException ex, HttpServletRequest request) {
        // Return JSON response for AJAX/API calls
        if (isApiCall(request)) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Forbidden");
            response.put("message", ex.getMessage() != null ? ex.getMessage() : "You do not have permission to perform this action");
            response.put("status", 403);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        // Return HTML response for page requests
        ModelAndView mav = new ModelAndView();
        mav.setViewName("not-authorized");
        mav.setStatus(HttpStatus.FORBIDDEN);
        mav.addObject("message", ex.getMessage() != null ? ex.getMessage() : "You do not have permission to access this page or perform this action");
        addLayoutConfigToView(mav);
        addNavigationAttributes(mav);
        return mav;
    }

    @ExceptionHandler(Exception.class)
    public Object handleException(HttpServletRequest request, Exception ex) {
        if (isClientDisconnectException(ex)) {
            // Connection closed by client while writing response (SSE/reload/tab close).
            // This is expected and should not pollute error logs or trigger stack traces.
            completeAsyncRequest(request);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }
        errorLogService.logException(ex, request);
        ex.printStackTrace();
        boolean showDetails = false;
        // You can configure this with a specific property or use Spring's built-in
        // profile
        for (String profile : env.getActiveProfiles()) {
            if (profile.equalsIgnoreCase("dev") || profile.equalsIgnoreCase("development")) {
                showDetails = true;
                break;
            }
        }

        ModelAndView mav = new ModelAndView();
        mav.setViewName("error");
        mav.addObject("message", ex.getMessage());
        mav.addObject("showDetails", showDetails);

        addLayoutConfigToView(mav);
        addNavigationAttributes(mav);

        if (showDetails) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            mav.addObject("details", sw.toString());
        }
        return mav;
    }
    
    /**
     * Add layout configuration to a view to avoid Thymeleaf binding errors.
     * Falls back to safe defaults if the DB is unavailable (e.g. during a connection pool outage)
     * so the error page itself can always be rendered.
     */
    private void addLayoutConfigToView(ModelAndView mav) {
        LayoutConfiguration layoutConfig;
        try {
            layoutConfig = layoutConfigurationRepository.findAll().stream()
                    .findFirst().orElse(new LayoutConfiguration());
        } catch (Exception dbEx) {
            layoutConfig = new LayoutConfiguration();
        }

        if (layoutConfig != null) {
            if (layoutConfig.getPrimaryColor() == null) layoutConfig.setPrimaryColor("#2274A5");
            if (layoutConfig.getPrimaryColorDark() == null) layoutConfig.setPrimaryColorDark("#164666");
            if (layoutConfig.getAccentColor() == null) layoutConfig.setAccentColor("#FF9505");
            if (layoutConfig.getBackgroundColor() == null) layoutConfig.setBackgroundColor("#F5F9FC");
            if (layoutConfig.getBorderColor() == null) layoutConfig.setBorderColor("#dae1e7");
            if (layoutConfig.getNavViolet() == null) layoutConfig.setNavViolet("#ebe8fc");
            if (layoutConfig.getTextMain() == null) layoutConfig.setTextMain("#222E3A");
            if (layoutConfig.getShineGlare() == null) layoutConfig.setShineGlare("rgba(255,255,255,0.60)");
            if (layoutConfig.getShineHighlight() == null) layoutConfig.setShineHighlight("rgba(255,255,255,0.33)");
            if (layoutConfig.getSecondaryColor() == null) layoutConfig.setSecondaryColor("#9596AE");
            if (layoutConfig.getFontFamily() == null) layoutConfig.setFontFamily("Segoe UI, Arial, sans-serif");
            if (layoutConfig.getFontSizeNav() == null) layoutConfig.setFontSizeNav("1em");
            if (layoutConfig.getFontSizeHeadline() == null) layoutConfig.setFontSizeHeadline("1.5em");
            if (layoutConfig.getOrgNameColor() == null) layoutConfig.setOrgNameColor("#2274A5");
            if (layoutConfig.getOrgNameFontSize() == null) layoutConfig.setOrgNameFontSize("1.18em");
            if (layoutConfig.getToolNameColor() == null) layoutConfig.setToolNameColor("#164666");
            if (layoutConfig.getToolNameFontSize() == null) layoutConfig.setToolNameFontSize("1em");
        }
        mav.addObject("layoutConfig", layoutConfig);
    }

    /**
     * Populate the same navigation model attributes (user info, role-based menu
     * visibility flags, org details, app version) that GlobalUserSessionAdvice /
     * GlobalOrganisationDetailsAdvice normally contribute via @ModelAttribute.
     * Those advice methods are NOT invoked for views rendered from an
     * @ExceptionHandler, so without this the nav fragment renders with every
     * menu hidden and "no user" / "No role" shown.
     */
    private void addNavigationAttributes(ModelAndView mav) {
        String userName = null;
        String userId = null;
        String userRole = null;
        boolean canAccessConfig = false;
        boolean canAccessSecurityFramework = false;
        boolean canAccessOrganization = false;
        boolean canCreateAssessment = false;
        boolean canViewAssessmentList = false;
        boolean canAccessCompliance = false;
        boolean canAccessStatistics = false;
        boolean canAccessAssessmentUrls = false;
        boolean canAccessGovernance = false;
        try {
            if (authorizationService != null) {
                User currentUser = authorizationService.getCurrentUser();
                if (currentUser != null) {
                    userName = currentUser.getName();
                    userId = String.valueOf(currentUser.getId());
                }
                com.govinc.user.Role role = authorizationService.getCurrentUserRole();
                if (role != null) userRole = role.getDisplayName();
                canAccessConfig = authorizationService.canAccessConfig();
                canAccessSecurityFramework = authorizationService.canAccessSecurityFramework();
                canAccessOrganization = authorizationService.canAccessOrganization();
                canCreateAssessment = authorizationService.canCreateAssessment();
                canViewAssessmentList = authorizationService.canViewAssessmentList();
                canAccessCompliance = authorizationService.canAccessCompliance();
                canAccessStatistics = authorizationService.canAccessStatistics();
                canAccessAssessmentUrls = authorizationService.canAccessAssessmentUrls();
                canAccessGovernance = authorizationService.canAccessGovernance();
            }
        } catch (Exception ex) {
            // Fall back to the "no access" defaults declared above
        }
        mav.addObject("userName", userName);
        mav.addObject("userId", userId);
        mav.addObject("userRole", userRole);
        mav.addObject("canAccessConfig", canAccessConfig);
        mav.addObject("canAccessSecurityFramework", canAccessSecurityFramework);
        mav.addObject("canAccessOrganization", canAccessOrganization);
        mav.addObject("canCreateAssessment", canCreateAssessment);
        mav.addObject("canViewAssessmentList", canViewAssessmentList);
        mav.addObject("canAccessCompliance", canAccessCompliance);
        mav.addObject("canAccessStatistics", canAccessStatistics);
        mav.addObject("canAccessAssessmentUrls", canAccessAssessmentUrls);
        mav.addObject("canAccessGovernance", canAccessGovernance);

        int sessionTimeoutMinutes = 30;
        try {
            if (generalConfigService != null) sessionTimeoutMinutes = generalConfigService.getSessionTimeoutMinutes();
        } catch (Exception ex) {
            // keep default
        }
        mav.addObject("sessionTimeoutMinutes", sessionTimeoutMinutes);

        OrganisationDetails organisationDetails;
        try {
            organisationDetails = organisationDetailsRepository.findAll().stream().findFirst().orElse(new OrganisationDetails());
        } catch (Exception ex) {
            organisationDetails = new OrganisationDetails();
        }
        mav.addObject("organisationDetails", organisationDetails);
        mav.addObject("appVersion", getApplicationVersion());
    }

    private String getApplicationVersion() {
        try {
            String content = new String(Files.readAllBytes(Paths.get(versionFile))).trim();
            if (!content.isEmpty()) return content;
        } catch (Exception ex) {
            // fall through to default
        }
        return "2.1.0";
    }

    /**
     * Check if the request is an API call (JSON response expected) or page request.
     */
    private boolean isApiCall(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        String contentType = request.getHeader("Content-Type");
        String requestUri = request.getRequestURI();
        
        // Check if it's an AJAX request or API endpoint
        return (accept != null && accept.contains("application/json")) ||
               (contentType != null && contentType.contains("application/json")) ||
               (requestUri != null && requestUri.contains("/api/")) ||
               "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
    }

    private boolean isClientDisconnectException(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            String className = current.getClass().getName();
            if ("org.apache.catalina.connector.ClientAbortException".equals(className)
                    || current instanceof AsyncRequestNotUsableException) {
                return true;
            }

            String msg = current.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase(Locale.ROOT);
                if (lower.contains("broken pipe")
                        || lower.contains("connection reset")
                        || lower.contains("forcibly closed by the remote host")
                        || lower.contains("durch den hostcomputer abgebrochen")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private void completeAsyncRequest(HttpServletRequest request) {
        if (request == null) {
            return;
        }
        try {
            if (request.isAsyncStarted()) {
                request.getAsyncContext().complete();
            }
        } catch (Exception ignored) {
        }
    }
}
