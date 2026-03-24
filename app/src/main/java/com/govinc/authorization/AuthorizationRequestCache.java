package com.govinc.authorization;

import com.govinc.user.Role;
import com.govinc.user.User;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Request-scoped cache for the current authenticated user and their role.
 * Prevents repeated DB lookups within a single HTTP request when AuthorizationService
 * is called many times (e.g., GlobalUserSessionAdvice + per-assessment access checks).
 */
@Component
@RequestScope
public class AuthorizationRequestCache {

    private boolean userResolved = false;
    private User cachedUser = null;

    private boolean roleResolved = false;
    private Role cachedRole = null;

    public boolean isUserResolved() {
        return userResolved;
    }

    public User getCachedUser() {
        return cachedUser;
    }

    public void setUser(User user) {
        this.cachedUser = user;
        this.userResolved = true;
    }

    public boolean isRoleResolved() {
        return roleResolved;
    }

    public Role getCachedRole() {
        return cachedRole;
    }

    public void setRole(Role role) {
        this.cachedRole = role;
        this.roleResolved = true;
    }
}
