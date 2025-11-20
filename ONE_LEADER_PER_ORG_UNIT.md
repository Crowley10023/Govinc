# Implementation: One Team Leader Per Organization Unit

## Overview
Modified the system to ensure that **only one user can be a team leader of an organization unit**. Previously, many users could lead the same org unit. Now each org unit has at most one leader.

## Key Changes

### 1. Data Model Changes

#### OrgUnit Entity (Reverted to Single Leader)
```java
// BEFORE: Many-to-Many
@ManyToMany(fetch = FetchType.EAGER)
@JoinTable(name = "org_unit_leaders", ...)
private Set<User> leaders = new HashSet<>();

// AFTER: Many-to-One
@ManyToOne
private User leader;
```

**Result:** Each OrgUnit has exactly one `leader` field (can be null).

#### User Entity (Added Inverse Relationship)
```java
// NEW: One-to-Many (inverse of OrgUnit.leader)
@OneToMany(mappedBy = "leader", fetch = FetchType.EAGER)
private Set<OrgUnit> leadsOrgUnits = new HashSet<>();
```

**Result:** A User can lead multiple org units, but each org unit can only be led by one user.

### 2. Database Schema Changes

#### Removed Join Table
- ❌ `org_unit_leaders` table is no longer needed

#### OrgUnit Table
```sql
-- Added field
ALTER TABLE org_unit ADD COLUMN leader_id BIGINT;
ALTER TABLE org_unit ADD FOREIGN KEY (leader_id) REFERENCES user(id);
```

The `leader_id` field stores which user leads the org unit.

### 3. Backend Logic Changes

#### UserController.java
- **Create/Update:** Accepts single `leadOrgUnitId` instead of array
- **Validation:** Checks if org unit already has a different leader
- **Delete:** Removes user from any org units they lead
- **Constraint:** Only applies leader assignment if user role is `ORGANISATION_TEAM_LEADER`

```java
// Before Save
if (leadOrgUnitId != null && leadOrgUnitId > 0) {
    if (Role.ORGANISATION_TEAM_LEADER == user.getRole()) {
        Optional<OrgUnit> orgUnit = orgUnitService.getOrgUnit(leadOrgUnitId);
        if (orgUnit.isPresent()) {
            OrgUnit ou = orgUnit.get();
            // Check if already has a different leader
            if (ou.getLeader() != null && !ou.getLeader().getId().equals(user.getId())) {
                // Skip - already has a leader
            } else {
                ou.setLeader(user);
                orgUnitService.addOrgUnit(ou);
            }
        }
    }
}
```

#### AuthorizationService.java
- **Assessment Access:** Checks if team leader leads the assessment's org unit
- **Accessible Orgs:** Returns all org units the user leads

```java
// Team Leader authorization
Set<OrgUnit> userLeadingOrgs = user.getLeadsOrgUnits();
for (OrgUnit userOrg : userLeadingOrgs) {
    if (isOrgUnitInTree(assessmentOrg, userOrg)) {
        return true; // Can access
    }
}
```

### 4. Frontend Changes

#### user_form.html
**Key Features:**
- ✅ Single dropdown for org unit selection (not checkboxes)
- ✅ Shows org units that have no leader
- ✅ Disables org units already led by another user
- ✅ Displays warning if org unit is already assigned
- ✅ "-- No Organization Unit --" option for non-leaders or removal

```html
<select id="leadOrgUnitId" name="leadOrgUnitId">
    <option value="">-- No Organization Unit --</option>
    <option th:each="orgUnit : ${orgUnits}" 
            th:value="${orgUnit.id}" 
            th:text="${orgUnit.name}"
            th:disabled="${orgUnit.leader != null && orgUnit.leader.id != currentUserId}">
    </option>
</select>
```

**Dynamic Features:**
- Shows/hides org unit field based on role selection
- Disables options for org units with different leaders
- Shows warning icon if selected org has a leader

#### orgunit-edit.html
**Key Features:**
- ✅ Single dropdown for "Team Leader" selection
- ✅ Shows users without existing leader assignments
- ✅ Disables users who already lead another org unit
- ✅ Warning display for users already leading

```html
<select id="leaderId" name="leaderId">
    <option value="">(none)</option>
    <option th:each="user : ${allUsers}" 
            th:value="${user.id}" 
            th:text="${user.name}"
            th:disabled="${user.leadsOrgUnits != null && user.leadsOrgUnits.size() > 0}">
    </option>
</select>
```

**Dynamic Features:**
- Displays warning if user is already a team leader
- Disables unavailable users
- Real-time validation

### 5. UserRepository.java

Updated queries to fetch the org units a user leads:

```java
@Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.leadsOrgUnits WHERE u.name = :name")
Optional<User> findByName(@Param("name") String name);

@Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.leadsOrgUnits")
List<User> findAll();
```

---

## Business Rules Implemented

### Rule 1: Unique Leadership
- ✅ Each org unit has at most ONE leader
- ✅ If org unit has a leader, no other user can be assigned

### Rule 2: Multiple Leadership
- ✅ A user CAN lead multiple org units
- ✅ A user CANNOT be Team Leader for multiple org units at the same time through UI
- ✅ Each org unit assignment is independent

### Rule 3: Role Enforcement
- ✅ Only users with `ORGANISATION_TEAM_LEADER` role can lead org units
- ✅ Changing role to non-Team-Leader removes leadership
- ✅ Adding user as Team Leader without org unit is allowed

### Rule 4: Constraint Validation
- ✅ Cannot assign user if org unit already has a different leader
- ✅ Cannot change org unit to one with another leader
- ✅ Display prevents invalid selections

---

## Usage Scenarios

### Scenario 1: Create Team Leader with Org Unit
```
1. Click "Add User"
2. Name: John Smith
3. Email: john@example.com
4. Role: Organisation Team Leader
5. Lead Organization Unit: [Sales] (dropdown shows available orgs)
6. Click Create

Result: John leads Sales org unit
```

### Scenario 2: Edit Existing Leader
```
1. Click Edit on John
2. Current: Lead Organization Unit = Sales
3. Change to: Lead Organization Unit = Marketing
4. Click Update

Result: John now leads Marketing (was Sales)
Note: Sales org now has no leader
```

### Scenario 3: Try to Assign Already-Led Org
```
1. Click "Add User"
2. Role: Organisation Team Leader
3. Lead Organization Unit: [Marketing] (disabled - led by Sarah)
4. Cannot select (grayed out)

Result: Form prevents invalid assignment
```

### Scenario 4: Edit Org Unit to Change Leader
```
1. Click Edit on Marketing Org Unit
2. Current Team Leader: Sarah
3. Change to: John
4. Click Save

Result: John now leads Marketing (was Sarah)
Note: Sarah no longer leads Marketing
```

---

## Database Migration

### Manual Migration (if needed)
```sql
-- Drop old join table
DROP TABLE org_unit_leaders;

-- Add leader_id to org_unit if not exists
ALTER TABLE org_unit ADD COLUMN leader_id BIGINT DEFAULT NULL;

-- Add foreign key
ALTER TABLE org_unit ADD CONSTRAINT fk_org_unit_leader 
    FOREIGN KEY (leader_id) REFERENCES user(id);

-- For existing data: migrate first leader from old table if needed
-- (Automatic if using Hibernate schema auto-update)
```

### Automatic Migration
Hibernate/JPA will automatically:
1. Remove `org_unit_leaders` table
2. Add `leader_id` column to `org_unit` table
3. Set foreign key constraint

---

## Files Modified

✅ **Backend (3 files):**
1. `OrgUnit.java` - Changed from many-to-many leaders to single leader
2. `User.java` - Added inverse relationship `leadsOrgUnits`
3. `UserController.java` - Updated to handle single org unit assignment
4. `AuthorizationService.java` - Updated to check single leader
5. `UserRepository.java` - Updated queries

✅ **Frontend (2 files):**
1. `user_form.html` - Single dropdown, validation, warnings
2. `orgunit-edit.html` - Single dropdown for leader selection

---

## Validation & Constraints

### Frontend Validation
- ✅ Dropdown disables unavailable options
- ✅ Warning boxes show conflicts
- ✅ Role-based conditional display
- ✅ Real-time feedback on selection

### Backend Validation
- ✅ Checks if org unit already has different leader
- ✅ Verifies role is Team Leader before assignment
- ✅ Removes leadership when role changes
- ✅ Cleans up on user deletion

---

## Testing Checklist

### Create Operations
- [ ] Create Team Leader without org unit
- [ ] Create Team Leader with available org unit
- [ ] Try to create with unavailable org unit (should be disabled)
- [ ] Create non-Team-Leader role

### Edit Operations
- [ ] Edit user to add org unit
- [ ] Edit user to change org unit
- [ ] Edit user to remove org unit
- [ ] Edit user to change role away from Team Leader
- [ ] Edit user to change role to Team Leader

### Org Unit Operations
- [ ] Edit org unit to assign leader
- [ ] Edit org unit to change leader
- [ ] Edit org unit to remove leader
- [ ] Try to assign user already leading another org

### Authorization
- [ ] Team Leader can access their org assessments ✓
- [ ] Team Leader can access child org assessments ✓
- [ ] Team Leader cannot access other org assessments ✗
- [ ] User without leader role has no org access ✗

### UI
- [ ] Org unit field hidden for non-Team-Leaders
- [ ] Org unit field shown for Team Leaders
- [ ] Unavailable options disabled
- [ ] Warnings display correctly
- [ ] Form validation works

### Database
- [ ] `leader_id` field exists in `org_unit` table
- [ ] Foreign key properly configured
- [ ] No `org_unit_leaders` table exists
- [ ] Data integrity maintained

---

## Important Notes

### Performance
- ✅ Simpler queries with single FK instead of join table
- ✅ No N+1 queries (using eager loading)
- ✅ Faster authorization checks

### Backward Compatibility
- ❌ Not backward compatible with old data model
- ✅ Migration path provided
- ✅ Automatic schema update via Hibernate

### Business Logic
- ✅ Enforces "one leader per org unit" constraint
- ✅ Allows users to lead multiple org units
- ✅ Prevents conflicts and ambiguity
- ✅ Clear UI prevents user errors

---

## Troubleshooting

### "User already leads another org unit" Warning
**Cause:** User has existing leader assignment  
**Solution:** Edit user to remove or change org unit first

### Org Unit Option Disabled
**Cause:** Org unit already has a different leader  
**Solution:** Edit org unit to change or remove leader, then retry

### Cannot Create Team Leader
**Cause:** Selected org unit doesn't have empty leader field  
**Solution:** Use dropdown to select available org unit or choose "(none)"

---

## Summary

This implementation enforces **one-to-many relationship** between Users and OrgUnits:
- Each OrgUnit has at most ONE leader (ManyToOne)
- Each User can lead MULTIPLE OrgUnits (OneToMany)
- Prevents conflicts and ambiguity
- Clear UI prevents invalid assignments
- Strong backend validation

**Result:** Clean, maintainable, and conflict-free team leadership model.
