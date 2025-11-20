# Role-Based Access Control (RBAC) Implementation Guide

## Overview

This document describes the role-based access control system implemented in the Compliance Incubator application. The system enforces authorization at the backend to ensure secure and consistent access control across all features.

## Roles and Permissions

### 1. **ADMIN**
- **Description**: Full system access
- **Permissions**:
  - Full access to all features
  - Create, read, update, delete assessments
  - Access security framework (controls, catalogs, domains, maturity models)
  - Access organization management (users, org units, org services)
  - Access configuration
  - Access compliance checks
  - Access statistics
  - Manage assessment URLs

### 2. **INFORMATION_SECURITY_MANAGER (ISM)**
- **Description**: Full access except configuration
- **Permissions**:
  - Create, read, update, delete assessments
  - Access security framework (controls, catalogs, domains, maturity models)
  - Access organization management (users, org units, org services)
  - Access compliance checks
  - Access statistics
  - Manage assessment URLs
  - **Denied**: Configuration access (Config tab is hidden)

### 3. **ORGANISATION_TEAM_LEADER (OTL)**
- **Description**: Manage organization units and assessments
- **Permissions**:
  - View and manage assessments in their assigned organization or child organizations
  - View organization units in their tree
  - Update assessment answers and comments
  - Download assessment reports
  - **Denied**: Create new assessments, delete assessments, access configuration, security framework, compliance checks

### 4. **ASSESSMENT_DELEGATE (AD)**
- **Description**: Answer assigned assessments
- **Permissions**:
  - View and update assessments where they are assigned as users
  - View, update, and comment on control answers
  - Download assessment reports
  - **Denied**: Create assessments, delete assessments, view other assessments, access any other feature

### **Special Case: assessment-direct**
- **Public/Unrestricted Access**: The `assessment-direct` endpoints are globally accessible without authentication
- **Endpoints**:
  - `/assessment-direct/{id}/alldata`
  - `/assessment-direct/{id}/data`
  - `/assessment-direct/{id}/answer`
  - `/assessment-direct/{id}/control/{controlId}/comment`

## Implementation Architecture

### Backend Components

#### 1. **AuthorizationService** (`com.govinc.authorization.AuthorizationService`)
Centralized service for all authorization checks. All authorization logic is implemented here for easy maintenance.

**Key Methods**:
- `getCurrentUser()`: Get the currently authenticated user
- `getCurrentUserRole()`: Get the current user's role
- `canAccessConfig()`: Check config access
- `canAccessSecurityFramework()`: Check security framework access
- `canAccessOrganization()`: Check organization management access
- `canCreateAssessment()`: Check assessment creation permission
- `canAccessAssessment(Long assessmentId)`: Check access to specific assessment
- `canModifyAssessment(Long assessmentId)`: Check modification permission
- `canDeleteAssessment(Long assessmentId)`: Check deletion permission
- `canAccessCompliance()`: Check compliance check access
- `canAccessStatistics()`: Check statistics access
- `canAccessAssessmentUrls()`: Check assessment URLs access
- `getAccessibleOrgUnits()`: Get organization units accessible to user

**Usage in Controllers**:
```java
if (!authorizationService.canAccessAssessment(id)) {
    throw new UnauthorizedException("You do not have permission to access this assessment.");
}
```

#### 2. **UnauthorizedException** (`com.govinc.authorization.UnauthorizedException`)
Custom exception thrown when a user lacks permission for an operation. Results in HTTP 403 Forbidden.

```java
throw new UnauthorizedException("You do not have permission to perform this action.");
```

#### 3. **GlobalExceptionHandler** (Enhanced)
Updated to handle `UnauthorizedException` with user-friendly error messages:
- Returns JSON for API/AJAX calls
- Returns HTML error page for page requests
- HTTP status: 403 Forbidden

#### 4. **GlobalUserSessionAdvice** (Enhanced)
Adds authorization flags to all model attributes for conditional navigation display:
- `canAccessConfig`
- `canAccessSecurityFramework`
- `canAccessOrganization`
- `canCreateAssessment`
- `canViewAssessmentList`
- `canAccessCompliance`
- `canAccessStatistics`
- `canAccessAssessmentUrls`

### Frontend Components

#### 1. **authorization.js** (`/static/authorization.js`)
Global JavaScript handler for authorization errors on the frontend:

**Features**:
- Intercepts HTTP requests (fetch, jQuery AJAX, XMLHttpRequest)
- Displays user-friendly modal for 403 Forbidden responses
- Automatically loaded by navigation template

**Usage**:
```javascript
// Automatic handling of 403 responses via:
// - Fetch API
// - jQuery AJAX
// - XMLHttpRequest

// Manual error display:
window.showAuthorizationError("Custom error message");
```

#### 2. **Navigation Template** (Enhanced)
Conditional display of navigation items based on user role:

```html
<!-- Only ADMIN can see Config -->
<div class="nav-entry dropdown" th:if="${canAccessConfig}">
    ...
</div>

<!-- ADMIN and ISM can see Security Framework -->
<div class="nav-entry dropdown" th:if="${canAccessSecurityFramework}">
    ...
</div>
```

### Database Schema Changes

#### User Entity Enhancement
The `User` entity now includes:

```java
@Enumerated(EnumType.STRING)
private Role role = Role.ASSESSMENT_DELEGATE;

@ManyToOne
private OrgUnit organisationUnit;
```

**Default Role**: Users default to `ASSESSMENT_DELEGATE`

**Migration**: Existing users maintain backward compatibility. To update user roles:

```sql
UPDATE user SET role = 'ADMIN' WHERE name = 'admin';
UPDATE user SET role = 'INFORMATION_SECURITY_MANAGER' WHERE name = 'ism_user';
UPDATE user SET role = 'ORGANISATION_TEAM_LEADER', organisation_unit_id = ? WHERE name = 'team_lead';
UPDATE user SET role = 'ASSESSMENT_DELEGATE' WHERE role IS NULL;
```

## Security Checks by Feature

### Assessments
- **List**: Filtered based on user role and organization access
- **View**: Checked against `canAccessAssessment()`
- **Create**: Requires `canCreateAssessment()`
- **Modify**: Requires `canModifyAssessment()`
- **Delete**: Requires `canDeleteAssessment()`
- **Download Reports**: Requires `canAccessAssessment()`

### Organization Management
- **View Org Units**: Requires `canViewOrgUnits()`
- **Create/Edit/Delete Org Units**: Requires `canAccessOrganization()`

### Security Framework
- **All Features**: Require `canAccessSecurityFramework()`

### Configuration
- **All Config Features**: Require `canAccessConfig()` (ADMIN only)

### Compliance & Statistics
- **Compliance Checks**: Require `canAccessCompliance()`
- **Statistics**: Require `canAccessStatistics()`

## Frontend Authorization

### Navigation Visibility
The navigation bar automatically hides items based on user permissions using Thymeleaf conditional attributes:

```html
<div th:if="${canAccessConfig}">Config</div>
```

### Error Handling
When a user receives a 403 response, the `authorization.js` script displays a user-friendly modal dialog instead of a raw error.

## Integration with Controllers

### Example: AssessmentController

```java
@GetMapping("/{id}")
public String getAssessmentById(@PathVariable Long id, Model model) {
    // Authorization check
    if (!authorizationService.canAccessAssessment(id)) {
        throw new UnauthorizedException("You do not have permission to access this assessment.");
    }
    
    // Rest of the logic...
}
```

## Adding Authorization to New Features

### Step 1: Inject AuthorizationService
```java
@Autowired
private AuthorizationService authorizationService;
```

### Step 2: Add Authorization Check
```java
if (!authorizationService.canAccessFeature()) {
    throw new UnauthorizedException("Permission denied.");
}
```

### Step 3: Add to Navigation (if applicable)
```html
<div th:if="${canAccessFeature}">
    <!-- Feature link -->
</div>
```

### Step 4: Add Authorization Method to Service (if needed)
```java
public boolean canAccessFeature() {
    Role role = getCurrentUserRole();
    return role == Role.ADMIN || role == Role.INFORMATION_SECURITY_MANAGER;
}
```

## Testing Authorization

### Test Scenarios

1. **Admin User**
   - Can access all features
   - Config tab is visible
   - Can create, edit, delete any assessment

2. **ISM User**
   - Can access all features except Config
   - Config tab is hidden
   - Can create, edit, delete any assessment

3. **OTL User**
   - Can only access assessments in their org tree
   - Cannot access Config, Security Framework, Organization Management
   - Cannot create new assessments

4. **AD User**
   - Can only access assessments where assigned
   - Cannot access any other features
   - Cannot create assessments
   - Can only update their assigned assessments

5. **assessment-direct endpoints**
   - Should be accessible without authentication
   - Use `/assessment-direct/{id}` paths

## Configuration

### Setting User Roles

#### Via Database (Recommended)
```sql
UPDATE user SET role = 'INFORMATION_SECURITY_MANAGER' 
WHERE id = 1;
```

#### Programmatically
```java
User user = userRepository.findById(1L).get();
user.setRole(Role.INFORMATION_SECURITY_MANAGER);
userRepository.save(user);
```

#### Setting Organization Unit for OTL
```java
User teamLead = userRepository.findById(orgId).get();
teamLead.setOrganisationUnit(orgUnitService.getOrgUnit(orgUnitId).get());
userRepository.save(teamLead);
```

## Error Messages

### Common Authorization Error Messages

1. **Config Access Denied**
   ```
   You do not have permission to access configuration.
   Only administrators can modify system configuration.
   ```

2. **Assessment Access Denied**
   ```
   You do not have permission to access this assessment.
   ```

3. **Creation Denied**
   ```
   You do not have permission to create assessments.
   Only administrators and information security managers can create assessments.
   ```

## Troubleshooting

### User Cannot Access Expected Feature

1. Check user's role: `SELECT * FROM user WHERE id = ?;`
2. For OTL users, verify `organisation_unit_id` is set
3. For AD users, verify they are assigned to assessments
4. Clear browser cache and session
5. Check application logs for authorization errors

### assessment-direct Returning 403

1. Verify the endpoint is in `EXCLUDED_URLS` in `SecurityConfig`
2. Ensure no authentication filters are applied to these endpoints
3. Check database for the assessment URL record

## Future Enhancements

1. **Audit Logging**: Log all authorization checks
2. **Fine-Grained Permissions**: Add assessment-level permissions
3. **Role Management UI**: Add admin interface to assign roles
4. **Permission Caching**: Cache authorization decisions for performance
5. **Delegation**: Allow users to delegate permissions temporarily

## References

- AuthorizationService: `app/src/main/java/com/govinc/authorization/AuthorizationService.java`
- UnauthorizedException: `app/src/main/java/com/govinc/authorization/UnauthorizedException.java`
- Authorization JS: `app/src/main/resources/static/authorization.js`
- User Entity: `app/src/main/java/com/govinc/user/User.java`
- Role Enum: `app/src/main/java/com/govinc/user/Role.java`
