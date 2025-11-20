# Role-Based Access Control (RBAC) Implementation Summary

## Overview
This document summarizes all changes made to implement comprehensive role-based access control in the Compliance Incubator application.

## Files Created

### 1. **Role Enumeration**
- **File**: `app/src/main/java/com/govinc/user/Role.java`
- **Purpose**: Defines the four roles (ADMIN, INFORMATION_SECURITY_MANAGER, ORGANISATION_TEAM_LEADER, ASSESSMENT_DELEGATE)
- **Features**: 
  - Display name and description for each role
  - Type-safe role assignment

### 2. **Authorization Service (Centralized)**
- **File**: `app/src/main/java/com/govinc/authorization/AuthorizationService.java`
- **Purpose**: Single source of truth for all authorization logic
- **Key Responsibilities**:
  - Get current user and role
  - Check specific permissions (config access, framework access, org access, etc.)
  - Validate assessment access based on role and organization hierarchy
  - Manage organization unit accessibility for team leaders
- **Methods**: 30+ authorization check methods
- **Design**: All authorization logic centralized here for easy modification

### 3. **Authorization Exception**
- **File**: `app/src/main/java/com/govinc/authorization/UnauthorizedException.java`
- **Purpose**: Custom exception for authorization failures
- **HTTP Status**: 403 Forbidden
- **Usage**: Throw when users lack permission

### 4. **Frontend Error Handler**
- **File**: `app/src/main/resources/static/authorization.js`
- **Purpose**: Global JavaScript handler for 403 responses
- **Features**:
  - Intercepts fetch, jQuery AJAX, and XMLHttpRequest calls
  - Displays user-friendly modal dialog on 403 errors
  - Automatic error message extraction from response
  - Bootstrap 4/5 compatible modal support
  - Fallback to alert() if Bootstrap not available
- **Auto-loaded**: Included in navigation template

### 5. **RBAC Documentation**
- **File**: `ROLE_BASED_ACCESS_CONTROL.md`
- **Purpose**: Complete implementation guide and reference
- **Contents**:
  - Roles and permissions matrix
  - Architecture overview
  - Implementation details
  - Integration guide for new features
  - Testing scenarios
  - Troubleshooting guide

## Files Modified

### 1. **User Entity** 
- **File**: `app/src/main/java/com/govinc/user/User.java`
- **Changes**:
  - Added `Role role` field with `@Enumerated(EnumType.STRING)` annotation
  - Added `OrgUnit organisationUnit` field for organization team leaders
  - Default role: `ASSESSMENT_DELEGATE`
  - Getter/setter methods for both new fields
- **Database Impact**: Migration will add two new columns

### 2. **Global Exception Handler**
- **File**: `app/src/main/java/com/govinc/GlobalExceptionHandler.java`
- **Changes**:
  - New handler method: `handleUnauthorizedException()`
  - Detects API calls vs page requests
  - Returns JSON for AJAX/API calls
  - Returns HTML error page for page requests
  - Refactored layout config initialization to reduce code duplication

### 3. **Global User Session Advice**
- **File**: `app/src/main/java/com/govinc/GlobalUserSessionAdvice.java`
- **Changes**:
  - Injected `AuthorizationService`
  - Added 8 new model attributes for conditional navigation display
  - Attributes provide authorization flags to all Thymeleaf templates
  - Models: `canAccessConfig`, `canAccessSecurityFramework`, `canAccessOrganization`, etc.

### 4. **Navigation Template**
- **File**: `app/src/main/resources/templates/navigation.html`
- **Changes**:
  - Wrapped major navigation sections with authorization conditionals
  - Security Framework menu: Hidden from non-ISM/ADMIN roles
  - Organization menu: Hidden from non-ISM/ADMIN roles
  - Config menu: Visible only to ADMIN
  - Assessment-related items: Role-based visibility
  - Added `<script src="/authorization.js"></script>` for error handling
- **Impact**: UI now reflects user permissions - unauthorized features completely hidden

### 5. **Assessment Controller**
- **File**: `app/src/main/java/com/govinc/assessment/AssessmentController.java`
- **Changes**:
  - Injected `AuthorizationService`
  - Added authorization checks to:
    - `GET /assessment/create`: Check `canCreateAssessment()`
    - `GET /assessment/list`: Filter assessments by role, check `canViewAssessmentList()`
    - `GET /assessment/{id}`: Check `canAccessAssessment()`
    - `POST /assessment/{id}/answer`: Check `canModifyAssessment()`
    - `PUT /assessment/{id}/control/{controlId}/comment`: Check `canModifyAssessment()`
    - `POST /assessment/{id}/delete`: Check `canDeleteAssessment()`
    - `GET /assessment/{id}/report`: Check `canAccessAssessment()`
    - `GET /assessment/{id}/excel`: Check `canAccessAssessment()`
    - `GET /assessment/{id}/word-report`: Check `canAccessAssessment()`
  - Assessment list is now filtered based on user role and organization
  - Throws `UnauthorizedException` on permission denial

### 6. **Organization Unit Controller**
- **File**: `app/src/main/java/com/govinc/organization/OrgUnitController.java`
- **Changes**:
  - Injected `AuthorizationService`
  - Added authorization checks to:
    - `GET /orgunits/list`: Check `canViewOrgUnits()`
    - `GET /orgunits/create`: Check `canAccessOrganization()`
    - `GET /orgunits/edit/{id}`: Check `canAccessOrganization()`
    - `POST /orgunits/save`: Check `canAccessOrganization()`
    - `DELETE /orgunits/{id}`: Check `canAccessOrganization()`
  - Throws `UnauthorizedException` on permission denial

### 7. **User Controller**
- **File**: `app/src/main/java/com/govinc/user/UserController.java`
- **Changes**:
  - Injected `AuthorizationService`
  - Added authorization checks to:
    - `GET /users`: Check `canAccessOrganization()`
    - `GET /users/new`: Check `canAccessOrganization()`
    - `POST /users`: Check `canAccessOrganization()`
    - `GET /users/edit/{id}`: Check `canAccessOrganization()`
    - `POST /users/update/{id}`: Check `canAccessOrganization()`
    - `GET /users/delete/{id}`: Check `canAccessOrganization()`
    - `GET /users/api`: Check `canAccessOrganization()`
  - Throws `UnauthorizedException` on permission denial

### 8. **Security Configuration**
- **File**: `app/src/main/java/com/govinc/configuration/SecurityConfig.java`
- **Changes**:
  - Updated comments to clarify role assignment
  - Changed default local user role assignment to ADMIN (for backward compatibility)
  - Improved logging messages
  - Note: Role assignment should be done via database User entity, not Spring Security roles

## Architecture Decisions

### 1. **Centralized Authorization**
- All authorization logic in `AuthorizationService`
- Single point of modification for security policies
- Easy to audit and test

### 2. **Exception-Based Authorization**
- Controllers throw `UnauthorizedException` on failure
- Centrally handled by `GlobalExceptionHandler`
- Clear separation of concerns

### 3. **Organization Hierarchy Support**
- Team leaders can access their org and children
- Recursive tree traversal for permission checking
- Supports multi-level organizational structures

### 4. **Frontend and Backend Alignment**
- Navigation hidden based on `AuthorizationService` checks
- Backend enforces the same checks
- Double protection: UI hides unauthorized actions, backend prevents them

### 5. **Special Case: assessment-direct**
- Explicitly excluded from authentication
- Remains fully public and unrestricted
- Configured in `SecurityConfig.EXCLUDED_URLS`

## Database Schema Changes

### New Columns for User Table

```sql
ALTER TABLE user ADD COLUMN role VARCHAR(50) NOT NULL DEFAULT 'ASSESSMENT_DELEGATE';
ALTER TABLE user ADD COLUMN organisation_unit_id BIGINT;
ALTER TABLE user ADD CONSTRAINT fk_user_org_unit 
    FOREIGN KEY (organisation_unit_id) REFERENCES org_unit(id);
```

### Migration Script for Existing Users

```sql
-- Assuming first user is admin
UPDATE user SET role = 'ADMIN' WHERE id = (SELECT MIN(id) FROM user);

-- Update other users as needed
-- UPDATE user SET role = 'INFORMATION_SECURITY_MANAGER' WHERE name = 'ism_user';
-- UPDATE user SET role = 'ORGANISATION_TEAM_LEADER', organisation_unit_id = 1 WHERE name = 'team_lead';
-- UPDATE user SET role = 'ASSESSMENT_DELEGATE' WHERE role IS NULL;
```

## Testing Checklist

- [ ] Admin user can access all features
- [ ] Config tab hidden from non-admin
- [ ] ISM user can access all except config
- [ ] OTL user can only access their org assessments
- [ ] AD user can only access assigned assessments
- [ ] 403 responses show friendly error modal
- [ ] assessment-direct endpoints work without auth
- [ ] Navigation items hidden correctly per role
- [ ] List views filtered appropriately by role
- [ ] Backend rejects unauthorized API calls

## Integration with Existing Features

### Backward Compatibility
- Existing users default to `ASSESSMENT_DELEGATE`
- Existing database users maintain access after migration
- SecurityConfig still supports form-based auth
- OAuth2 and local users both supported

### Future Integration Points
- Catalog/Control CRUD operations should use `canAccessSecurityFramework()`
- Compliance checks should use `canAccessCompliance()`
- Statistics should use `canAccessStatistics()`
- New configuration features should use `canAccessConfig()`

## Performance Considerations

1. **AuthorizationService Methods**: Called once per request
2. **Database Lookups**: Minimal - only loads current user
3. **Organization Hierarchy**: Cached in memory during request
4. **Frontend**: authorization.js intercepts at network level

## Security Considerations

1. **Never Trust Frontend**: Backend always enforces
2. **Assessment-direct Exception**: Explicitly allowed public endpoint
3. **Error Messages**: Vague enough to not leak information
4. **Role Elevation**: Only ADMIN can change user roles
5. **Organization Isolation**: Team leaders only see their tree

## Deployment Steps

1. Create Role.java enum
2. Update User.java entity
3. Create AuthorizationService
4. Create UnauthorizedException
5. Update GlobalExceptionHandler
6. Update GlobalUserSessionAdvice
7. Update navigation template
8. Update AssessmentController
9. Update OrgUnitController
10. Update UserController
11. Create authorization.js
12. Run database migration
13. Assign roles to existing users
14. Test all scenarios
15. Deploy application

## Support and Maintenance

### Adding New Authorization Checks
1. Add method to `AuthorizationService`
2. Inject service in controller
3. Call method and throw exception if needed
4. Add navigation check if UI element

### Modifying Existing Rules
1. Edit `AuthorizationService` method
2. All calls automatically use updated logic
3. No controller changes needed

### Debugging Authorization Issues
1. Check user role: `SELECT role FROM user WHERE id = ?;`
2. Check org unit: `SELECT organisation_unit_id FROM user WHERE id = ?;`
3. Check application logs for `UnauthorizedException`
4. Enable debug logging in `AuthorizationService`

## References and Files

| File | Purpose | Lines |
|------|---------|-------|
| Role.java | Role enumeration | 20 |
| User.java | Enhanced entity | 70 |
| AuthorizationService.java | Central auth logic | 300+ |
| UnauthorizedException.java | Custom exception | 15 |
| authorization.js | Frontend error handler | 150+ |
| GlobalExceptionHandler.java | Exception handling | 90 |
| GlobalUserSessionAdvice.java | Model attributes | 60 |
| navigation.html | Conditional navigation | + conditionals |
| AssessmentController.java | Auth checks | + 9 checks |
| OrgUnitController.java | Auth checks | + 5 checks |
| UserController.java | Auth checks | + 7 checks |

## Summary Statistics

- **Files Created**: 5
- **Files Modified**: 8
- **New Java Classes**: 3
- **New JavaScript Files**: 1
- **Authorization Methods**: 30+
- **Authorization Checks Added**: 25+
- **Documentation Pages**: 2
