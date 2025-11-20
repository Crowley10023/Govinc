# Before & After Comparison

## User Form UI

### BEFORE
```
Simple form with basic styling:
- Name input
- Email input  
- Role dropdown (no description)
- Single org unit dropdown (hidden for non-leaders)
- Basic submit/cancel buttons
- No visual hierarchy
- Desktop-only layout
```

### AFTER
```
Modern card-based form with sections:
┌─────────────────────────────────┐
│ HEADER WITH SUBTITLE            │
├─────────────────────────────────┤
│ BASIC INFORMATION               │
│  ├─ Name (with placeholder)     │
│  └─ Email (with placeholder)    │
├─────────────────────────────────┤
│ PERMISSIONS & ROLE              │
│  ├─ Role Dropdown               │
│  ├─ Role Description Box        │
│  └─ Organization Units (NEW!)   │
│     ├─ ☑ Sales                  │
│     ├─ ☑ Marketing              │
│     ├─ ☐ Finance                │
│     └─ Help Text                │
├─────────────────────────────────┤
│ [Cancel]             [Submit]   │
└─────────────────────────────────┘
```

**Visual Improvements:**
- ✨ Card design with subtle shadow
- 📐 Clear section separation
- 🎨 Color-coded (blues & oranges)
- 📱 Fully responsive
- ♿ Better accessibility
- ⌨️ Focus states visible
- 💬 Inline help text

---

## Data Model

### BEFORE: Single Leader Pattern
```java
// User.java
@ManyToOne(fetch = FetchType.EAGER)
private OrgUnit organisationUnit;

// OrgUnit.java
@ManyToOne
private User leader;
```

**Database:**
```
user table:
  id | name | email | organisation_unit_id

org_unit table:
  id | name | parent_id | leader_id
```

**Limitation:**
```
User "John" → leads 1 org unit maximum
└─ Sales
```

### AFTER: Multiple Leaders Pattern
```java
// User.java
@ManyToMany(fetch = FetchType.EAGER)
@JoinTable(name = "user_organisation_units", ...)
private Set<OrgUnit> organisationUnits = new HashSet<>();

// OrgUnit.java
@ManyToMany(fetch = FetchType.EAGER)
@JoinTable(name = "org_unit_leaders", ...)
private Set<User> leaders = new HashSet<>();
```

**Database:**
```
user table:
  id | name | email

org_unit table:
  id | name | parent_id

user_organisation_units (NEW):
  user_id | org_unit_id  ← Many-to-many mapping

org_unit_leaders (NEW):
  org_unit_id | user_id  ← Many-to-many mapping
```

**Capability:**
```
User "John" → leads multiple org units
├─ Sales
├─ Marketing
└─ Operations
```

---

## Controller - Form Parameter Handling

### BEFORE
```java
@PostMapping
public String createUser(
    @ModelAttribute User user, 
    @RequestParam(value = "organisationUnitId", required = false) Long orgUnitId) {
    
    if (orgUnitId != null) {
        Optional<OrgUnit> orgUnit = orgUnitService.getOrgUnit(orgUnitId);
        if (orgUnit.isPresent()) {
            user.setOrganisationUnit(orgUnit.get());  // ← Single assignment
        }
    }
    userRepository.save(user);
    return "redirect:/users";
}
```

**Form Input:**
```html
<select name="organisationUnitId">  <!-- Single dropdown -->
  <option value="1">Sales</option>
  <option value="2">Marketing</option>
  <option value="3">Finance</option>
</select>
```

**Form Submission:**
```
POST /users
organisationUnitId=1
```

### AFTER
```java
@PostMapping
public String createUser(
    @ModelAttribute User user, 
    @RequestParam(value = "organisationUnitIds", required = false) Long[] orgUnitIds) {
    
    if (orgUnitIds != null && orgUnitIds.length > 0) {
        if (Role.ORGANISATION_TEAM_LEADER == user.getRole()) {
            Set<OrgUnit> orgUnits = new HashSet<>();
            for (Long orgUnitId : orgUnitIds) {
                if (orgUnitId != null && orgUnitId > 0) {
                    Optional<OrgUnit> orgUnit = orgUnitService.getOrgUnit(orgUnitId);
                    orgUnit.ifPresent(orgUnits::add);
                }
            }
            user.setOrganisationUnits(orgUnits);  // ← Multiple assignment
        }
    }
    userRepository.save(user);
    return "redirect:/users";
}
```

**Form Input:**
```html
<!-- Multiple checkboxes -->
<input type="checkbox" name="organisationUnitIds" value="1"> Sales
<input type="checkbox" name="organisationUnitIds" value="2"> Marketing
<input type="checkbox" name="organisationUnitIds" value="3"> Finance
```

**Form Submission:**
```
POST /users
organisationUnitIds=1&organisationUnitIds=2&organisationUnitIds=3
```

---

## Authorization - Assessment Access

### BEFORE: Single Org Unit
```java
public boolean canAccessAssessment(Long assessmentId) {
    User user = getCurrentUser();
    Role role = user.getRole();
    
    if (role == Role.ORGANISATION_TEAM_LEADER) {
        OrgUnit userOrg = user.getOrganisationUnit();  // ← Single org
        
        OrgUnit assessmentOrg = assessment.getOrgUnit();
        
        // Check if assessment is in user's org or children
        return isOrgUnitInTree(assessmentOrg, userOrg);
    }
}
```

**Example:**
```
User "John" leads: Sales
Assessment org: Sales → ✓ Access granted
Assessment org: Marketing → ✗ Access denied

User can only see assessments from 1 org tree
```

### AFTER: Multiple Org Units
```java
public boolean canAccessAssessment(Long assessmentId) {
    User user = getCurrentUser();
    Role role = user.getRole();
    
    if (role == Role.ORGANISATION_TEAM_LEADER) {
        Set<OrgUnit> userOrgs = user.getOrganisationUnits();  // ← Multiple orgs
        
        OrgUnit assessmentOrg = assessment.getOrgUnit();
        
        // Check if assessment is in ANY of user's orgs or their children
        for (OrgUnit userOrg : userOrgs) {
            if (isOrgUnitInTree(assessmentOrg, userOrg)) {
                return true;  // ✓ Found in at least one tree
            }
        }
        return false;
    }
}
```

**Example:**
```
User "John" leads: Sales, Marketing, Operations
Assessment org: Sales → ✓ Access granted (in Sales tree)
Assessment org: Sales > Northeast → ✓ Access granted (child of Sales)
Assessment org: Marketing → ✓ Access granted (in Marketing tree)
Assessment org: Finance → ✗ Access denied (not in any tree)

User can see assessments from 3 org trees
```

---

## Real-World Scenario

### Scenario: Company Restructuring

**Day 1: Before Changes**
```
Sales Department
├─ John (Team Leader for Sales)
└─ 5 assessments

Marketing Department  
├─ Sarah (Team Leader for Marketing)
└─ 3 assessments

Operations merged into Marketing
└─ 2 assessments now under Marketing
```

**Day 2: After Changes & Restructuring**
```
Marketing Department (merged Operations)
├─ Sarah (Team Leader for Marketing) - OLD
├─ John (Team Leader for Sales & Operations) - NEW!
└─ 10 assessments total (3 old + 2 merged + 5 from John)

Result:
- John now leads Sales + Operations (2 teams) → can access 7 assessments
- Sarah leads Marketing (1 team) → can access 3 assessments
```

### Form Interaction

**Before:** Would need to create separate roles or reassign users

**After:** 
```
1. Click Edit on John
2. Check: ☑ Sales, ☑ Operations
3. Uncheck: Marketing (if was selected)
4. Click Update
5. Done! John leads 2 teams instantly
```

---

## Form Submission Examples

### Example 1: Create Single-Team Leader
```
POST /users
- name=John%20Smith
- email=john@company.com
- role=ORGANISATION_TEAM_LEADER
- organisationUnitIds=1

Result: John leads Sales (org unit 1)
```

### Example 2: Create Multi-Team Leader
```
POST /users
- name=Jane%20Doe
- email=jane@company.com
- role=ORGANISATION_TEAM_LEADER
- organisationUnitIds=1&organisationUnitIds=2&organisationUnitIds=3

Result: Jane leads Sales, Marketing, Finance (org units 1, 2, 3)
```

### Example 3: Edit to Add Team
```
POST /users/update/5
- name=John%20Smith
- email=john@company.com
- role=ORGANISATION_TEAM_LEADER
- organisationUnitIds=1&organisationUnitIds=4

Result: John now leads Sales & Operations (was just Sales)
```

### Example 4: Change Role (Clears Org Units)
```
POST /users/update/5
- name=John%20Smith
- email=john@company.com
- role=ASSESSMENT_DELEGATE
- (no organisationUnitIds)

Result: John is Assessment Delegate, org units cleared
```

---

## Database State Comparison

### BEFORE: User with Org Unit
```sql
SELECT * FROM user WHERE id = 5;
id | name | email | organisation_unit_id | role
5  | John | john@ | 1                    | ORGANISATION_TEAM_LEADER

User is tied to single org unit (1 = Sales)
```

### AFTER: User with Multiple Org Units
```sql
SELECT * FROM user WHERE id = 5;
id | name | email | role
5  | John | john@ | ORGANISATION_TEAM_LEADER

SELECT * FROM user_organisation_units WHERE user_id = 5;
user_id | org_unit_id
5       | 1           ← Sales
5       | 2           ← Marketing
5       | 4           ← Operations

User is connected to 3 org units through join table
```

---

## Performance Impact

### Before
```
Query: Get user org units
Time: 1 join operation
Result: 1 org unit (max)
```

### After
```
Query: Get user org units
Time: 1 join operation (same)
Result: N org units (no practical limit)

Authorization Check: O(n * m)
- n = number of user org units (3-10 typical)
- m = depth of org tree (3-5 typical)
- Total: ~10-50 comparisons (negligible)
```

**Negligible performance difference for typical usage.**

---

## Summary Table

| Aspect | Before | After |
|--------|--------|-------|
| **UI Design** | Basic | Modern/Responsive |
| **Org Units Per User** | 1 | Many |
| **Selection Method** | Dropdown | Checkboxes |
| **Data Model** | Single FK | Many-to-Many |
| **Join Tables** | None | 2 new tables |
| **Authorization** | Single tree check | Multiple tree checks |
| **Use Case Support** | Limited | Full flexibility |
| **Complexity** | Simple | Moderate |
| **User Experience** | Functional | Polished |
