# Multiple Team Leaders per Organization Unit - Implementation Summary

## Overview
Modified the system to support users being leaders of **multiple organization units**. Previously, a user could only lead one org unit; now a Team Leader can be responsible for managing multiple teams/org units.

## Database Schema Changes

### User Entity Changes
- **Before**: `@ManyToOne organisationUnit` - single org unit per user
- **After**: `@ManyToMany organisationUnits` - multiple org units per user
- New join table: `user_organisation_units`

```sql
CREATE TABLE user_organisation_units (
    user_id BIGINT,
    org_unit_id BIGINT,
    PRIMARY KEY (user_id, org_unit_id)
);
```

### OrgUnit Entity Changes
- **Before**: `@ManyToOne leader` - single leader per org unit
- **After**: `@ManyToMany leaders` - multiple leaders per org unit
- New join table: `org_unit_leaders`

```sql
CREATE TABLE org_unit_leaders (
    org_unit_id BIGINT,
    user_id BIGINT,
    PRIMARY KEY (org_unit_id, user_id)
);
```

## Code Changes

### 1. User.java
- Changed from `Set<OrgUnit> organisationUnits` (many-to-many relationship)
- Added helper methods:
  - `addOrganisationUnit(OrgUnit)` - add a single org unit
  - `removeOrganisationUnit(OrgUnit)` - remove a single org unit
- Maintained backward compatibility with deprecated getters/setters for single org unit

### 2. OrgUnit.java
- Changed `leader` field to `Set<User> leaders` (many-to-many relationship)
- Added helper methods:
  - `addLeader(User)` - add a single leader
  - `removeLeader(User)` - remove a single leader
- Maintained backward compatibility with deprecated getters/setters

### 3. UserController.java
- Updated `createUser()` method to accept array of org unit IDs (`organisationUnitIds[]`)
- Updated `updateUser()` method to accept array of org unit IDs
- Both methods now:
  - Accept multiple org unit selections
  - Only apply org units if user role is `ORGANISATION_TEAM_LEADER`
  - Clear org units for non-Team-Leader roles

### 4. AuthorizationService.java
- Updated `canAccessAssessment()` to check all org units for Team Leaders
  - Team Leader can access assessment if it's in ANY of their assigned org units or children
- Updated `getAccessibleOrgUnits()` to return all org units and children for Team Leaders
  - Returns combined accessible tree for all assigned org units

### 5. user_form.html (Template)
- Updated org unit selection to use **checkboxes** instead of a single dropdown
- Multiple selections now possible:
  - Shows all available org units
  - User can check multiple boxes
  - Pre-selects currently assigned org units when editing
- Form sends array parameter: `organisationUnitIds[]`
- Improved styling with checkbox container

## Frontend Features

### Form Layout
- **Org Unit Selection Container**: Displays as checkboxes in a grid
- **Conditional Display**: Only shown when "Organisation Team Leader" role is selected
- **Dynamic Updates**: JavaScript updates UI when role changes
- **Pre-selection**: Existing org units are checked when editing a user

### Form Submission
- Multiple org units are submitted as: `organisationUnitIds=1&organisationUnitIds=2&organisationUnitIds=3`
- Backend receives as `Long[] orgUnitIds` array

## Backward Compatibility

Both entities maintain deprecated single-unit getters/setters for backward compatibility:
- `getOrganisationUnit()` / `setOrganisationUnit()` in User
- `getLeader()` / `setLeader()` in OrgUnit

These return/set the first item in the collection for existing code compatibility.

## Authorization Rules

### Team Leader Access with Multiple Org Units
A Team Leader can now access assessments if the assessment's org unit is:
1. Equal to any of their assigned org units, OR
2. A child/descendant of any of their assigned org units

Example:
- User "John" is team leader for: [Sales, Marketing]
- Assessment in "Sales" org unit → John can access ✓
- Assessment in "Sales > Northeast" org unit → John can access ✓
- Assessment in "Marketing" org unit → John can access ✓
- Assessment in "Finance" org unit → John cannot access ✗

## Migration Steps

If migrating from single-leader to multi-leader system:

1. **Database Migration**: Create join tables
2. **Redeploy**: Updated JAR with new entity relationships
3. **Existing Data**: Automatically migrated via JPA
   - Single `leader` → stored in new `org_unit_leaders` table
   - Single `organisationUnit` → stored in new `user_organisation_units` table
4. **No Data Loss**: All existing assignments preserved

## Testing Checklist

- [ ] Create Team Leader with multiple org units
- [ ] Edit Team Leader to add/remove org units
- [ ] Team Leader can access assessments in all assigned org units
- [ ] Team Leader cannot access assessments in unassigned org units
- [ ] Team Leader can access child org unit assessments
- [ ] Remove Team Leader role clears org unit assignments
- [ ] Add non-Team-Leader role doesn't save org units
- [ ] Display in user list shows all assigned org units
