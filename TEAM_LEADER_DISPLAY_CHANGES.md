# Team Leader Display Changes

## Overview
Modified the org-tree-view.html to display team leaders for each organization unit if assigned. Updated backend data provisioning to ensure leader information is properly fetched and available to the frontend.

## Changes Made

### 1. Frontend Changes: org-tree-view.html
**Location**: `app/src/main/resources/templates/org-tree-view.html`

#### Changes:
- Added new CSS class `.org-label-leader` for styling leader information (italic, blue, smaller font)
- Modified `drawNode()` function to include leader name display
  - Builds array of text lines from org unit name and leader information
  - Handles long org unit names by splitting into multiple lines
  - Appends leader name as additional line if present: `"Lead: [Leader Name]"`
  - Applies distinctive styling to leader line for visual differentiation
  
- Updated `drawOrgTreeFull()` function with identical leader display logic
  - Ensures consistent display across both rendering modes (with siblings or full tree)

#### How it works:
- Each organization unit node now displays:
  1. Org unit name (split across lines if longer than 16 chars)
  2. Team leader name (if assigned), displayed as: `"Lead: [Name]"` in italic blue text

### 2. Backend Changes: OrgUnitRepository.java
**Location**: `app/src/main/java/com/govinc/organization/OrgUnitRepository.java`

#### Changes:
- Updated `findByIdWithChildren()` query to eagerly fetch the `leader` relationship
- **Original**: `SELECT u FROM OrgUnit u LEFT JOIN FETCH u.children WHERE u.id = :id`
- **Updated**: `SELECT u FROM OrgUnit u LEFT JOIN FETCH u.children LEFT JOIN FETCH u.leader WHERE u.id = :id`

#### Impact:
- Ensures the leader User object is loaded in a single database query (N+1 query prevention)
- Serializes leader information to JSON when returning OrgUnit tree via REST API
- No additional API calls needed for leader data

### 3. Backend Authorization (Already Implemented)
**Status**: ✅ Already implemented correctly

Authorization checks in place:
- **View org units**: `canViewOrgUnits()` - Requires ADMIN or ISM role
- **Edit org units**: `canAccessOrganization()` - Requires ADMIN or ISM role  
- **Team leader access**: Organisation Team Leaders can view their org units and children via assessments

No changes needed - authorization is properly enforced through:
- `AuthorizationService.canAccessOrganization()` for org unit management
- `AuthorizationService.canAccessAssessment()` for team leaders to access assessments in their org tree

### 4. Data Model (Already Supports)
**Status**: ✅ Already implemented correctly

The OrgUnit entity already has:
```java
@ManyToOne
private User leader;
```

The User entity has the inverse relationship:
```java
@OneToMany(mappedBy = "leader", fetch = FetchType.EAGER)
private Set<OrgUnit> leadsOrgUnits = new HashSet<>();
```

## Testing Recommendations

1. **Display Test**: 
   - Create an org unit with a team leader assigned
   - View the org tree - verify leader name appears on the node
   
2. **Authorization Test**:
   - Log in as ADMIN/ISM - should see org unit tree view
   - Log in as ORGANISATION_TEAM_LEADER - should only see their org units
   - Log in as ASSESSMENT_DELEGATE - should not have access to org tree view

3. **Data Test**:
   - Check browser console network tab
   - Verify only one `/orgunits/tree/{id}/fulltree` call is made
   - Inspect JSON response - should include leader object: `{"id": ..., "name": ..., "leader": {"id": ..., "name": ...}}`

4. **Visual Test**:
   - Multi-line org names with leaders should display correctly
   - Leader text should be styled differently (italic, blue, smaller)
   - Pan and zoom should work correctly with additional text lines

## Files Modified
1. `app/src/main/resources/templates/org-tree-view.html` - Frontend display
2. `app/src/main/java/com/govinc/organization/OrgUnitRepository.java` - Data provisioning

## Files Not Modified (Already Correct)
- `OrgUnit.java` - Has leader field ✅
- `AuthorizationService.java` - Has proper authorization checks ✅
- `OrgUnitController.java` - Has authorization guards ✅
- `User.java` - Has leadsOrgUnits relationship ✅
