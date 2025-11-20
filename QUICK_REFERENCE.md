# Quick Reference Guide

## What Changed?

### ✨ Frontend (UI)
**File:** `user_form.html`
- Modern card-based layout
- Sections for organization
- Checkboxes for multiple org unit selection
- Dynamic role descriptions
- Fully responsive design

### 🔗 Data Model (Database)
**Files:** `User.java`, `OrgUnit.java`
- Users → Multiple org units (many-to-many)
- Org units → Multiple leaders (many-to-many)
- 2 new join tables created automatically

### ⚙️ Backend (Logic)
**Files:** `UserController.java`, `AuthorizationService.java`
- Form accepts multiple org unit selections
- Authorization checks all user org units
- Role-based validation

---

## Files Modified (5 total)

```
✅ app/src/main/resources/templates/user_form.html
✅ app/src/main/java/com/govinc/user/User.java
✅ app/src/main/java/com/govinc/organization/OrgUnit.java
✅ app/src/main/java/com/govinc/user/UserController.java
✅ app/src/main/java/com/govinc/authorization/AuthorizationService.java
```

---

## Usage Examples

### Create Team Leader (Multiple Orgs)
```
1. Click "Add User"
2. Name: John Smith
3. Email: john@company.com
4. Role: Organisation Team Leader
5. Check: ☑ Sales, ☑ Marketing
6. Click Create
→ John leads 2 teams
```

### Edit Existing Leader
```
1. Click Edit on user
2. Check additional: ☑ Operations
3. Click Update
→ User now leads 3 teams
```

### Check Authorization
```
Team Leader "John" leads: Sales, Marketing
- Assessment in Sales → Can access ✓
- Assessment in Sales > Northeast → Can access ✓
- Assessment in Marketing → Can access ✓
- Assessment in Finance → Cannot access ✗
```

---

## Form Structure

```html
<!-- Org unit checkboxes (multiple selection) -->
<input type="checkbox" name="organisationUnitIds" value="1"> Sales
<input type="checkbox" name="organisationUnitIds" value="2"> Marketing
<input type="checkbox" name="organisationUnitIds" value="3"> Finance
```

---

## Backend API

### User Entity
```java
// Get all org units
Set<OrgUnit> orgs = user.getOrganisationUnits();

// Add org unit
user.addOrganisationUnit(salesOrg);

// Remove org unit
user.removeOrganisationUnit(salesOrg);

// Set all at once
user.setOrganisationUnits(new HashSet<>(Arrays.asList(sales, marketing)));
```

### OrgUnit Entity
```java
// Get all leaders
Set<User> leaders = orgUnit.getLeaders();

// Add leader
orgUnit.addLeader(john);

// Remove leader
orgUnit.removeLeader(john);

// Set all at once
orgUnit.setLeaders(new HashSet<>(Arrays.asList(john, sarah)));
```

### Authorization Service
```java
// Check if can access assessment
boolean canAccess = authService.canAccessAssessment(assessmentId);

// Get all accessible orgs
Set<OrgUnit> accessibleOrgs = authService.getAccessibleOrgUnits();
```

---

## Database Schema

### New Tables
```sql
-- Links users to their org units
CREATE TABLE user_organisation_units (
    user_id BIGINT,
    org_unit_id BIGINT,
    PRIMARY KEY (user_id, org_unit_id)
);

-- Links org units to their leaders
CREATE TABLE org_unit_leaders (
    org_unit_id BIGINT,
    user_id BIGINT,
    PRIMARY KEY (org_unit_id, user_id)
);
```

### Example Data
```sql
-- User leads 2 org units
INSERT INTO user_organisation_units VALUES (5, 1);  -- John → Sales
INSERT INTO user_organisation_units VALUES (5, 2);  -- John → Marketing

-- Org unit has 2 leaders
INSERT INTO org_unit_leaders VALUES (1, 5);  -- Sales → John
INSERT INTO org_unit_leaders VALUES (1, 6);  -- Sales → Sarah
```

---

## Form Submission

### Single Org Unit
```
POST /users
organisationUnitIds=1
```

### Multiple Org Units
```
POST /users
organisationUnitIds=1&organisationUnitIds=2&organisationUnitIds=3
```

### No Org Units
```
POST /users
(organisationUnitIds parameter omitted)
```

---

## Authorization Rules

```javascript
canAccessAssessment(assessmentId) {
    // Team Leader can access if assessment org is in ANY of:
    // 1. Their assigned org units
    // 2. Child org units of their assigned orgs
    
    FOR EACH userOrgUnit IN user.organisationUnits {
        IF isOrgUnitInTree(assessmentOrg, userOrgUnit) {
            RETURN true;  // Can access
        }
    }
    RETURN false;  // Cannot access
}
```

---

## UI Components

### Form Sections
```
┌─ Header (Title + Subtitle)
├─ Basic Information
│  ├─ Name input
│  └─ Email input
├─ Permissions & Role
│  ├─ Role dropdown
│  ├─ Role info box
│  └─ Organization Units (checkboxes)
└─ Form Actions
   ├─ Cancel button
   └─ Submit button
```

### Conditional Display
```javascript
// Show org units only for Team Leaders
if (role === 'ORGANISATION_TEAM_LEADER') {
    orgUnitSection.classList.add('show');
} else {
    orgUnitSection.classList.remove('show');
}
```

---

## Styling Classes

```css
.user-form-container    /* Main form card */
.form-header           /* Title area */
.form-section          /* Section divider */
.form-group            /* Field container */
.form-actions          /* Submit/Cancel area */
.org-unit-checkboxes   /* Checkbox grid */
.checkbox-item         /* Single checkbox */
.checkbox-label        /* Checkbox label */
.role-info            /* Role description box */
.field-hint           /* Help text */
```

---

## Testing Checklist

### Create User
- [ ] Without org units
- [ ] With 1 org unit
- [ ] With 3+ org units

### Edit User
- [ ] Add org units
- [ ] Remove org units
- [ ] Change role (clears org units)

### Authorization
- [ ] Can access assigned org assessments
- [ ] Can access child org assessments
- [ ] Cannot access unassigned org assessments

### UI
- [ ] Form renders correctly
- [ ] Org units hidden for non-Team-Leaders
- [ ] Checkboxes appear for Team Leaders
- [ ] Pre-selection works

---

## Backward Compatibility

### Deprecated Methods (Old Code Still Works)
```java
// User.java
@Deprecated
public OrgUnit getOrganisationUnit() { ... }

@Deprecated
public void setOrganisationUnit(OrgUnit unit) { ... }

// OrgUnit.java
@Deprecated
public User getLeader() { ... }

@Deprecated
public void setLeader(User leader) { ... }
```

### Migration Notes
- ✅ Existing code continues to work
- ✅ New code should use Set-based methods
- ✅ No breaking changes

---

## Troubleshooting

### Org units not showing
- [ ] Role is set to ORGANISATION_TEAM_LEADER
- [ ] JavaScript is enabled
- [ ] Check browser console for errors

### Cannot save multiple org units
- [ ] User role must be ORGANISATION_TEAM_LEADER
- [ ] Org unit IDs must exist in database
- [ ] Check server logs for validation errors

### Authorization not working
- [ ] User.organisationUnits is populated
- [ ] Assessment has org unit assigned
- [ ] AuthorizationService configured correctly

---

## Performance Notes

- ✅ Eager loading prevents N+1 queries
- ✅ Set lookups are O(1)
- ✅ Typical: 3-10 org units per user
- ✅ Authorization check: ~10-50 comparisons

---

## Key Differences

| Feature | Before | After |
|---------|--------|-------|
| Org Units per User | 1 | Many |
| Selection UI | Dropdown | Checkboxes |
| DB Relationship | Single FK | Many-to-Many |
| Auth Check | 1 org tree | N org trees |
| UI Design | Basic | Modern |
| Mobile Support | Limited | Full |

---

## Contact & Support

For questions about the implementation:
1. Check `IMPLEMENTATION_SUMMARY_MULTIPLE_TEAMS.md` for details
2. Review `BEFORE_AFTER_COMPARISON.md` for examples
3. See `CHANGES_MULTIPLE_TEAM_LEADERS.md` for schema info
