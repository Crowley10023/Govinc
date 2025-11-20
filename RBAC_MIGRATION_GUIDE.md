# Role-Based Access Control - Migration Guide

## Pre-Migration Checklist

- [ ] Backup database
- [ ] Read ROLE_BASED_ACCESS_CONTROL.md
- [ ] Identify user roles in your organization
- [ ] Map organizational structure if needed
- [ ] Plan rollout strategy
- [ ] Notify users of changes

## Step 1: Database Migration

### Create New Columns

```sql
-- Add role column with default
ALTER TABLE user ADD COLUMN role VARCHAR(50) NOT NULL DEFAULT 'ASSESSMENT_DELEGATE';

-- Add organization unit foreign key
ALTER TABLE user ADD COLUMN organisation_unit_id BIGINT;

-- Add foreign key constraint
ALTER TABLE user ADD CONSTRAINT fk_user_organisation_unit 
    FOREIGN KEY (organisation_unit_id) REFERENCES org_unit(id) ON DELETE SET NULL;
```

## Step 2: Assign Roles to Existing Users

### Option A: SQL Script (All Users)

```sql
-- 1. Identify and set ADMIN users (typically the first user or admin user)
UPDATE user 
SET role = 'ADMIN' 
WHERE name = 'admin' OR email LIKE '%admin%';

-- 2. Set Information Security Manager users
UPDATE user 
SET role = 'INFORMATION_SECURITY_MANAGER' 
WHERE name IN ('ism_user', 'security_manager', 'information_officer');

-- 3. Set Organization Team Leaders (if applicable)
UPDATE user 
SET role = 'ORGANISATION_TEAM_LEADER', 
    organisation_unit_id = 1 -- or appropriate org unit ID
WHERE name IN ('team_lead_1', 'team_lead_2');

-- 4. Verify assignments
SELECT id, name, email, role, organisation_unit_id FROM user;
```

### Option B: Gradual Assignment (Recommended)

```sql
-- Start with ADMIN only
UPDATE user SET role = 'ADMIN' WHERE id = 1;

-- Verify ADMIN can see everything
-- Then gradually promote other users:
UPDATE user SET role = 'INFORMATION_SECURITY_MANAGER' WHERE id = 2;
UPDATE user SET role = 'ORGANISATION_TEAM_LEADER', organisation_unit_id = 1 WHERE id = 3;
UPDATE user SET role = 'ASSESSMENT_DELEGATE' WHERE id = 4;
```

### Option C: REST API (Programmatic)

```java
@PostMapping("/admin/migrate-roles")
public void migrateRoles() {
    // Get all users
    List<User> users = userRepository.findAll();
    
    for (User user : users) {
        if (user.getId() == 1) {
            // First user becomes admin
            user.setRole(Role.ADMIN);
        } else if (isSecurityManager(user)) {
            // Based on your business logic
            user.setRole(Role.INFORMATION_SECURITY_MANAGER);
        } else {
            // Default
            user.setRole(Role.ASSESSMENT_DELEGATE);
        }
        userRepository.save(user);
    }
}
```

## Step 3: Set Organization Units for Team Leaders

If you have Organization Team Leaders, assign them to organizational units:

```sql
-- Example: Assign user with ID 5 to org unit with ID 10
UPDATE user 
SET organisation_unit_id = 10 
WHERE id = 5 AND role = 'ORGANISATION_TEAM_LEADER';

-- Verify the assignment
SELECT u.id, u.name, u.role, ou.name as org_unit_name 
FROM user u 
LEFT JOIN org_unit ou ON u.organisation_unit_id = ou.id 
WHERE u.role = 'ORGANISATION_TEAM_LEADER';
```

## Step 4: Application Deployment

1. **Backup current application**
   ```bash
   cp -r /path/to/app /path/to/app-backup
   ```

2. **Deploy new version with RBAC code**
   ```bash
   # Stop application
   ./stop.sh
   
   # Deploy new JAR
   cp compliance-incubator.jar /path/to/app/
   
   # Start application
   ./start.sh
   ```

3. **Verify application starts successfully**
   ```bash
   tail -f /path/to/app/logs/application.log
   ```

## Step 5: Testing

### Test Each Role

#### Admin User
```
1. Login as admin
2. Verify access to:
   - Config tab
   - Security Framework
   - Organization Management
   - All assessments
3. Verify can create, edit, delete assessments
```

#### Information Security Manager
```
1. Login as ISM user
2. Verify access to:
   - Security Framework
   - Organization Management
   - All assessments
   - Compliance Checks
   - Statistics
3. Verify Config tab is NOT visible
4. Verify can create, edit, delete assessments
```

#### Organization Team Leader
```
1. Login as team leader
2. Verify access to:
   - Only assessments in assigned organization or children
   - Organization unit assigned to them
3. Verify NO access to:
   - Config tab
   - Security Framework
   - Organization Management (other orgs)
   - Cannot create new assessments
```

#### Assessment Delegate
```
1. Login as delegate
2. Verify access to:
   - Only assigned assessments (as a user)
   - Can view and comment on assessments
3. Verify NO access to:
   - Config tab
   - Security Framework
   - Organization Management
   - Assessments not assigned to them
   - Cannot create assessments
```

#### assessment-direct
```
1. Access /assessment-direct/{id} without authentication
2. Verify page loads successfully
3. Verify can view assessment data
4. Verify can answer questions
5. Verify can comment
```

### Test Authorization Errors

1. **403 Forbidden Response**
   ```
   - Try to access /config/openai as non-admin
   - Should see friendly "Access Denied" modal
   - No raw error messages visible
   ```

2. **API Call Rejection**
   ```
   - Try POST /assessment from non-ISM account
   - Should return JSON 403 response
   - Check browser console for error handling
   ```

3. **Navigation Filtering**
   ```
   - Login as different roles
   - Verify navigation items appear/disappear
   - Reload page, items should still be correct
   ```

## Step 6: User Notification

### Email Template

```
Subject: New Role-Based Access Control System

Dear [User Name],

Our system has been updated with a new role-based access control system. 
Your access has been configured as follows:

Role: [ROLE_NAME]
Permissions: [Description]

Important Changes:
- Some navigation items may be hidden based on your permissions
- You can only view/manage assessments you have access to
- Unauthorized actions will be blocked with a clear message

If you cannot access features you previously used, please contact 
[Administrator Email]

Thank you,
Administration Team
```

### In-Application Notification

Add a banner on login:
```html
<div class="alert alert-info">
    System has been updated with new access controls. 
    <a href="/help/rbac">Learn more about your permissions</a>
</div>
```

## Step 7: Rollback Procedure (If Needed)

### Quick Rollback

```bash
# Stop application
./stop.sh

# Restore from backup
cp -r /path/to/app-backup/* /path/to/app/

# Start application
./start.sh
```

### Database Rollback

```sql
-- If needed to restore previous state
DROP COLUMN role FROM user;
DROP COLUMN organisation_unit_id FROM user;
```

### Partial Rollback

If only certain users have issues:

```sql
-- Reset specific user to full access (not recommended for production)
UPDATE user SET role = 'ADMIN' WHERE id = [problem_user_id];
```

## Common Issues and Solutions

### Issue 1: Users Can't Access Previous Features

**Cause**: User role was set incorrectly

**Solution**:
```sql
-- Check user's current role
SELECT id, name, role FROM user WHERE id = ?;

-- Update if needed
UPDATE user SET role = 'INFORMATION_SECURITY_MANAGER' WHERE id = ?;
```

### Issue 2: Team Leader Can't See Child Organization Units

**Cause**: Child org units not set up correctly in hierarchy

**Solution**:
```sql
-- Verify org unit hierarchy
SELECT id, name, parent_id FROM org_unit;

-- Verify user assignment
SELECT id, name, role, organisation_unit_id FROM user WHERE id = ?;

-- Update if needed
UPDATE user SET organisation_unit_id = [parent_org_id] WHERE id = ?;
```

### Issue 3: Config Tab Still Visible to ISM

**Cause**: Browser cache not cleared

**Solution**:
1. Clear browser cache
2. Hard refresh page (Ctrl+Shift+R or Cmd+Shift+R)
3. Verify role in database
4. Check Authorization Service is injected correctly

### Issue 4: Authorization Errors in Console

**Cause**: Usually expected behavior when user tries restricted action

**Solution**:
1. Check application logs: `logs/application.log`
2. Verify user role matches their actions
3. If legitimate access needed, adjust role assignments

### Issue 5: assessment-direct Not Accessible

**Cause**: assessment-direct not in EXCLUDED_URLS

**Solution**:
```java
// Verify in SecurityConfig.java
private static final String[] EXCLUDED_URLS = {
    "/assessment-direct/*/alldata",
    "/assessment-direct/*/data",
    "/assessment-direct/*/answer",
    "/assessment-direct/*/control/*/comment",
    "/assessment-direct.html",
    "/assessment-direct/*",
    // ... other URLs
};
```

## Post-Migration Verification

### Week 1 Checklist

- [ ] All users can login
- [ ] Users see correct navigation
- [ ] Users can access appropriate features
- [ ] No unexpected 403 errors
- [ ] Assess ment-direct works publicly
- [ ] Reports are filtered correctly
- [ ] API calls return correct data

### Week 2 Checklist

- [ ] No authorization bypass attempts detected
- [ ] User feedback collected
- [ ] Performance acceptable
- [ ] Logs show no errors
- [ ] Audit trail working

## Monitoring and Logging

### Check for Authorization Errors

```bash
# View authorization exceptions
tail -f logs/application.log | grep "UnauthorizedException"

# Count by user
grep "UnauthorizedException" logs/application.log | grep -o "user=[^,]*" | sort | uniq -c

# Check specific user
grep "userId=123" logs/application.log | grep "Forbidden"
```

### Performance Monitoring

```sql
-- Check authorization service call performance
-- Ensure no N+1 queries on permission checks
EXPLAIN ANALYZE SELECT * FROM user WHERE id = 1;
```

## Support Resources

### Documentation
- `ROLE_BASED_ACCESS_CONTROL.md` - Complete RBAC guide
- `IMPLEMENTATION_SUMMARY.md` - Technical implementation details

### Quick Reference
- **ADMIN**: Full access to everything
- **ISM**: All except configuration
- **OTL**: Their organization's assessments only
- **AD**: Assigned assessments only
- **assessment-direct**: Public, no auth required

### Emergency Contact
- Administrator: [email]
- Support: [support email]
- Issue: Create GitHub issue with `[RBAC]` label

## Rollout Strategy Options

### Option A: Big Bang (All Users at Once)
- **Pros**: Consistent experience across organization
- **Cons**: Higher risk, more support needed
- **Best For**: Small organizations

### Option B: Gradual Rollout (By Department)
- **Pros**: Can identify issues before full deployment
- **Cons**: Takes longer, mixed experience
- **Best For**: Medium organizations

### Option C: Opt-In (Users Choose)
- **Pros**: Users can test new system
- **Cons**: Long transition period
- **Best For**: Large organizations with advanced users

## Success Metrics

1. **User Adoption**: 90%+ users using new system without issues
2. **Support Tickets**: Decrease in access-related requests
3. **Audit Trail**: 100% of actions properly logged
4. **Performance**: No degradation from authorization checks
5. **Security**: Zero unauthorized access incidents

---

**Last Updated**: [Date]
**Version**: 1.0
**Status**: Ready for Production
