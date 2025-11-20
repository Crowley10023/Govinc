# Role-Based Access Control - Quick Reference

## Roles Matrix

| Feature | ADMIN | ISM | OTL | AD |
|---------|-------|-----|-----|-----|
| **Configuration** | ✅ | ❌ | ❌ | ❌ |
| **Security Framework** | ✅ | ✅ | ❌ | ❌ |
| **Organization Mgmt** | ✅ | ✅ | ❌ | ❌ |
| **Create Assessment** | ✅ | ✅ | ❌ | ❌ |
| **View All Assessments** | ✅ | ✅ | ❌ | ❌ |
| **View Own Org Assessments** | ✅ | ✅ | ✅ | ❌ |
| **View Assigned Assessments** | ✅ | ✅ | ✅ | ✅ |
| **Edit Assessment Answers** | ✅ | ✅ | ✅ | ✅ |
| **Delete Assessment** | ✅ | ✅ | ❌ | ❌ |
| **View Compliance** | ✅ | ✅ | ❌ | ❌ |
| **View Statistics** | ✅ | ✅ | ❌ | ❌ |
| **Manage Users** | ✅ | ✅ | ❌ | ❌ |
| **assessment-direct** | ✅ | ✅ | ✅ | ✅ |

Legend: ✅ = Allowed, ❌ = Denied

## Role Descriptions

### ADMIN (Administrator)
- **Access**: Everything
- **Use Case**: System administrators and compliance officers
- **Cannot be restricted**

### ISM (Information Security Manager)
- **Access**: Everything except Configuration
- **Use Case**: Security managers, compliance leads
- **Config restricted**: Cannot modify system settings

### OTL (Organisation Team Leader)
- **Access**: Their organization and children assessments
- **Use Case**: Department heads, team leaders
- **Organization-scoped**: Only sees assessments in their org tree

### AD (Assessment Delegate)
- **Access**: Assigned assessments only
- **Use Case**: Assessment participants, subject matter experts
- **Assessment-scoped**: Only assessments they're explicitly assigned to

## Database Schema

```sql
-- User table changes
ALTER TABLE user ADD role VARCHAR(50) NOT NULL DEFAULT 'ASSESSMENT_DELEGATE';
ALTER TABLE user ADD organisation_unit_id BIGINT;
ALTER TABLE user ADD FOREIGN KEY (organisation_unit_id) REFERENCES org_unit(id);

-- Example: Set admin user
UPDATE user SET role = 'ADMIN' WHERE id = 1;

-- Example: Set team leader
UPDATE user SET role = 'ORGANISATION_TEAM_LEADER', organisation_unit_id = 5 WHERE id = 2;
```

## Common Tasks

### Assign Admin Role
```sql
UPDATE user SET role = 'ADMIN' WHERE name = 'john_admin';
```

### Assign ISM Role
```sql
UPDATE user SET role = 'INFORMATION_SECURITY_MANAGER' WHERE name = 'security_lead';
```

### Assign Team Leader
```sql
UPDATE user SET role = 'ORGANISATION_TEAM_LEADER', organisation_unit_id = 1 WHERE name = 'dept_head';
```

### Assign Assessment Delegate
```sql
UPDATE user SET role = 'ASSESSMENT_DELEGATE' WHERE name = 'assessor@example.com';
```

### Check User's Role
```sql
SELECT id, name, email, role, organisation_unit_id FROM user WHERE name = 'username';
```

### View Organization Assignment
```sql
SELECT u.name, u.role, ou.name as org_unit 
FROM user u 
LEFT JOIN org_unit ou ON u.organisation_unit_id = ou.id 
WHERE u.role = 'ORGANISATION_TEAM_LEADER';
```

## Authorization Service Methods

### Check User Role
```java
authorizationService.getCurrentUser();           // Get current user
authorizationService.getCurrentUserRole();       // Get role (ADMIN, ISM, OTL, AD)
authorizationService.isAdmin();                  // Check if ADMIN
authorizationService.isInformationSecurityManager();  // Check if ISM
authorizationService.isOrganisationTeamLeader();     // Check if OTL
authorizationService.isAssessmentDelegate();    // Check if AD
```

### Check Permissions
```java
authorizationService.canAccessConfig();          // Only ADMIN
authorizationService.canAccessSecurityFramework();   // ADMIN + ISM
authorizationService.canAccessOrganization();   // ADMIN + ISM
authorizationService.canAccessCompliance();     // ADMIN + ISM
authorizationService.canAccessStatistics();     // ADMIN + ISM
authorizationService.canCreateAssessment();     // ADMIN + ISM
authorizationService.canAccessAssessment(id);   // Role-based + assignment
authorizationService.canModifyAssessment(id);   // Role-based + assignment
authorizationService.canDeleteAssessment(id);   // ADMIN + ISM only
```

## Adding Authorization to Controllers

### Minimal Example
```java
@Autowired
private AuthorizationService authorizationService;

@GetMapping("/config")
public String showConfig() {
    if (!authorizationService.canAccessConfig()) {
        throw new UnauthorizedException("Access denied");
    }
    // ... rest of method
}
```

### List Filtering Example
```java
List<Assessment> all = assessmentRepository.findAll();
List<Assessment> filtered = all.stream()
    .filter(a -> authorizationService.canAccessAssessment(a.getId()))
    .collect(Collectors.toList());
```

## Frontend JavaScript

### Handling 403 Errors (Automatic)
```javascript
// Automatically handled by /authorization.js
// 403 responses show friendly modal dialog
```

### Manual Error Display
```javascript
window.showAuthorizationError("Custom message");
```

## Navigation Conditional Display

```html
<!-- Only visible to authorized users -->
<div th:if="${canAccessConfig}">Configuration</div>
<div th:if="${canAccessSecurityFramework}">Security Framework</div>
<div th:if="${canAccessOrganization}">Organization</div>
<div th:if="${canCreateAssessment}">Create Assessment</div>
<div th:if="${canAccessCompliance}">Compliance</div>
<div th:if="${canAccessStatistics}">Statistics</div>
```

## Error Responses

### 403 Forbidden - User Friendly
```json
{
  "error": "Forbidden",
  "message": "You do not have permission to access this assessment.",
  "status": 403
}
```

### 403 Forbidden - Page Request
```html
<!-- Error page displayed with message -->
You do not have permission to perform this action.
```

## Testing Checklist

- [ ] Admin can access all features
- [ ] ISM cannot access Config
- [ ] OTL cannot access other org assessments
- [ ] AD cannot access unassigned assessments
- [ ] 403 errors show friendly message
- [ ] Navigation filters correctly
- [ ] API calls return 403
- [ ] assessment-direct works publicly

## Configuration Files

| File | Purpose | Location |
|------|---------|----------|
| Role.java | Role enum | `app/src/main/java/com/govinc/user/` |
| AuthorizationService.java | Auth logic | `app/src/main/java/com/govinc/authorization/` |
| UnauthorizedException.java | Exception | `app/src/main/java/com/govinc/authorization/` |
| authorization.js | Frontend handler | `app/src/main/resources/static/` |
| navigation.html | Conditionals | `app/src/main/resources/templates/` |

## Troubleshooting

### User Cannot Access Feature
1. Check database: `SELECT role FROM user WHERE id = ?`
2. Check organization: `SELECT organisation_unit_id FROM user WHERE id = ?`
3. Check assignments: `SELECT * FROM assessment_user WHERE user_id = ?`
4. Clear browser cache: Ctrl+Shift+Delete

### Config Visible to Non-Admin
1. Clear cache (hard refresh)
2. Verify role in database
3. Check `GlobalUserSessionAdvice` is running
4. Restart application

### 403 Not Showing Modal
1. Check authorization.js is loaded: F12 → Network tab
2. Check browser console for errors: F12 → Console
3. Verify Bootstrap CSS/JS is loaded
4. Check response is actually 403

### authorization-direct Not Working
1. Verify URLs in `SecurityConfig.EXCLUDED_URLS`
2. Check endpoint path matches exactly
3. Verify no authentication filters applied
4. Check database has assessment URL record

## Performance Tips

1. **Minimize Permission Checks**: Check once, not in loop
2. **Cache Results**: For same assessment multiple times in request
3. **Use Database Efficiently**: Filter at DB level if possible
4. **Avoid N+1 Queries**: Eager load org units for team leaders

## Security Best Practices

1. ✅ Always check on backend
2. ✅ Hide unauthorized UI elements
3. ✅ Use specific error messages (don't leak info)
4. ✅ Log all authorization failures
5. ✅ Never trust frontend authorization
6. ✅ Validate role on every request
7. ✅ Use HTTPS only
8. ✅ Implement CSRF protection

## API Examples

### Check Access via API
```bash
# Get current user info
curl -H "Authorization: Bearer TOKEN" \
  http://localhost:8080/users/me

# Try restricted endpoint (should get 403)
curl -H "Authorization: Bearer TOKEN" \
  http://localhost:8080/config/openai
```

### Error Response Example
```bash
$ curl http://localhost:8080/assessment/1 \
  -H "Accept: application/json"

HTTP/1.1 403 Forbidden
Content-Type: application/json

{
  "error": "Forbidden",
  "message": "You do not have permission to access this assessment.",
  "status": 403
}
```

## Documentation Links

- **Full Guide**: `ROLE_BASED_ACCESS_CONTROL.md`
- **Implementation Details**: `IMPLEMENTATION_SUMMARY.md`
- **Migration Steps**: `RBAC_MIGRATION_GUIDE.md`
- **This Quick Ref**: `RBAC_QUICK_REFERENCE.md`

---

**For Support**: Contact your system administrator
**Last Updated**: [Current Date]
**Version**: 1.0
