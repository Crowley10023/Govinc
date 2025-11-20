# Data Model: Before & After

## BEFORE: Multiple Leaders Per Org Unit

### Entity Relationship
```
┌──────────────┐
│    User      │
├──────────────┤
│ id (PK)      │
│ name         │
│ email        │
│ role         │
└──────────────┘
        ▲
        │ Many
        │
     [JOIN TABLE]
   org_unit_leaders
   (org_unit_id, user_id)
        │
        │ Many
        ▼
┌──────────────┐
│   OrgUnit    │
├──────────────┤
│ id (PK)      │
│ name         │
│ parent_id(FK)│
└──────────────┘
```

### Example Data (BEFORE)
```
Users:
┌─────┬────────┐
│ id  │ name   │
├─────┼────────┤
│ 1   │ John   │
│ 2   │ Sarah  │
│ 3   │ Mike   │
└─────┴────────┘

OrgUnits:
┌─────┬──────────┐
│ id  │ name     │
├─────┼──────────┤
│ 10  │ Sales    │
│ 20  │ Marketing│
│ 30  │ Finance  │
└─────┴──────────┘

org_unit_leaders (Join Table):
┌────────────┬─────────┐
│ org_unit_id│ user_id │
├────────────┼─────────┤
│ 10         │ 1       │ ← Sales led by John
│ 10         │ 2       │ ← Sales ALSO led by Sarah ❌ (PROBLEM!)
│ 20         │ 2       │ ← Marketing led by Sarah
│ 30         │ 3       │ ← Finance led by Mike
└────────────┴─────────┘

Problem: Sales org has TWO leaders! 😕
```

---

## AFTER: One Leader Per Org Unit

### Entity Relationship
```
┌──────────────┐
│    User      │
├──────────────┤
│ id (PK)      │
│ name         │
│ email        │
│ role         │
└──────────────┘
        ▲
        │ One
        │
        │ (inverse: OneToMany)
        │ leadsOrgUnits
        │
    [FK Foreign Key]
        │
        │ Many
        ▼
┌──────────────┐
│   OrgUnit    │
├──────────────┤
│ id (PK)      │
│ name         │
│ leader_id(FK)│ ← New FK (ManyToOne)
│ parent_id(FK)│
└──────────────┘
```

### Example Data (AFTER)
```
Users:
┌─────┬────────┐
│ id  │ name   │
├─────┼────────┤
│ 1   │ John   │
│ 2   │ Sarah  │
│ 3   │ Mike   │
└─────┴────────┘

OrgUnits:
┌─────┬──────────┬───────────┐
│ id  │ name     │leader_id  │
├─────┼──────────┼───────────┤
│ 10  │ Sales    │ 1         │ ← Only John
│ 20  │ Marketing│ 2         │ ← Only Sarah
│ 30  │ Finance  │ 3         │ ← Only Mike
│ 40  │ Support  │ 1         │ ← Also John! ✓ (John leads 2 orgs)
└─────┴──────────┴───────────┘

Benefits:
✓ Sales has ONLY John as leader
✓ John can lead Sales AND Support
✓ Each org has at most 1 leader
✓ Clear and unambiguous
```

---

## Relationship Flows

### User's Perspective
```
John (User id=1)
    │
    └──→ leadsOrgUnits (OneToMany, inverse)
         ├─ Sales (org_unit_id=10)
         └─ Support (org_unit_id=40)

Sarah (User id=2)
    │
    └──→ leadsOrgUnits
         └─ Marketing (org_unit_id=20)

Mike (User id=3)
    │
    └──→ leadsOrgUnits
         └─ Finance (org_unit_id=30)
```

### Org Unit's Perspective
```
Sales (OrgUnit id=10)
    │
    └──→ leader (ManyToOne, FK)
         └─ John (user_id=1)

Marketing (OrgUnit id=20)
    │
    └──→ leader (ManyToOne, FK)
         └─ Sarah (user_id=2)

Support (OrgUnit id=40)
    │
    └──→ leader (ManyToOne, FK)
         └─ John (user_id=1) ← Same person leads 2 orgs

Unassigned (OrgUnit id=50)
    │
    └──→ leader (ManyToOne, FK)
         └─ NULL ← No leader assigned yet
```

---

## Database Schema

### BEFORE
```sql
user
├─ id (PRIMARY KEY)
├─ name
├─ email
└─ role

org_unit
├─ id (PRIMARY KEY)
├─ name
└─ parent_id (FOREIGN KEY)

org_unit_leaders (JOIN TABLE) ← DELETED!
├─ org_unit_id (FOREIGN KEY, PRIMARY KEY part)
├─ user_id (FOREIGN KEY, PRIMARY KEY part)
└─ PRIMARY KEY (org_unit_id, user_id)
```

### AFTER
```sql
user
├─ id (PRIMARY KEY)
├─ name
├─ email
└─ role

org_unit
├─ id (PRIMARY KEY)
├─ name
├─ parent_id (FOREIGN KEY) ← unchanged
├─ leader_id (FOREIGN KEY) ← NEW! Points to User
└─ responsible_id (FOREIGN KEY) ← unchanged

No join table needed ✓
```

---

## Constraint Enforcement

### BEFORE: No constraint
```
org_unit_leaders
┌────────────┬─────────┐
│ org_unit_id│ user_id │
├────────────┼─────────┤
│ 10         │ 1       │
│ 10         │ 2       │ ← ALLOWED (no constraint)
│ 10         │ 3       │ ← ALLOWED (no constraint)
└────────────┴─────────┘

Sales org now has 3 leaders! ❌
```

### AFTER: Database constraint
```
org_unit (leader_id is UNIQUE-like at business level)
┌─────┬──────────┬───────────┐
│ id  │ name     │ leader_id │
├─────┼──────────┼───────────┤
│ 10  │ Sales    │ 1         │
│ 10  │ Sales    │ 2         │ ← IMPOSSIBLE! 
│     │          │           │   (Duplicate ID would fail)
└─────┴──────────┴───────────┘

Each org has exactly one leader row ✓
```

---

## Query Comparisons

### Get org units for a user

**BEFORE (Complex):**
```sql
SELECT ou.* FROM org_unit ou
JOIN org_unit_leaders oul ON ou.id = oul.org_unit_id
WHERE oul.user_id = ?
```

**AFTER (Simple):**
```sql
SELECT ou.* FROM org_unit ou
WHERE ou.leader_id = ?
```

### Get leader for an org unit

**BEFORE (Complex):**
```sql
SELECT u.* FROM user u
JOIN org_unit_leaders oul ON u.id = oul.user_id
WHERE oul.org_unit_id = ?
```

**AFTER (Simple):**
```sql
SELECT u.* FROM user u
WHERE u.id = (
    SELECT leader_id FROM org_unit WHERE id = ?
)
```

---

## Java Code Comparison

### Access org units user leads

**BEFORE:**
```java
Set<OrgUnit> orgs = user.getOrganisationUnits();
// Had both:
// - organisationUnits (for role assignment)
// - Many-to-many leaders
```

**AFTER:**
```java
Set<OrgUnit> ledsOrgs = user.getLeadsOrgUnits();
// Clear: org units this user LEADS
// Separate from organisationUnit assignment
```

### Access leader of org unit

**BEFORE:**
```java
Set<User> leaders = orgUnit.getLeaders();
User firstLeader = leaders.stream().findFirst().orElse(null);
// Had to guess which was "the" leader
```

**AFTER:**
```java
User leader = orgUnit.getLeader();
// Crystal clear: the (only) leader
if (leader != null) {
    // Lead by specific user
}
```

### Check if user leads org

**BEFORE:**
```java
boolean leads = orgUnit.getLeaders().contains(user);
// Had to check if user in set of leaders
```

**AFTER:**
```java
boolean leads = orgUnit.getLeader() != null 
    && orgUnit.getLeader().getId().equals(user.getId());
// Single FK check
```

---

## Migration Path

### Step 1: Prepare
```sql
-- Backup your database!
BACKUP DATABASE govinc TO DISK = '/path/to/backup.bak';
```

### Step 2: Automatic (Hibernate handles)
```
1. Application starts
2. JPA sees schema mismatch
3. Creates: org_unit.leader_id column
4. Drops: org_unit_leaders table
5. Data preserved automatically
```

### Step 3: Verify
```sql
-- Check new structure
SELECT * FROM org_unit WHERE leader_id IS NOT NULL;
-- Should show each org with its leader

-- Check old table gone
SELECT * FROM org_unit_leaders; -- ERROR: Table doesn't exist ✓
```

---

## Summary Table

| Aspect | Before | After |
|--------|--------|-------|
| **Leaders per Org** | Multiple ❌ | One ✓ |
| **Orgs per Leader** | Multiple ✓ | Multiple ✓ |
| **Join Table** | org_unit_leaders | None |
| **FK Location** | Join table | org_unit |
| **Query Complexity** | Join required | Simple WHERE |
| **Constraint** | None | Natural (PK) |
| **Ambiguity** | High | None |
| **Data Model** | Many-to-Many | Many-to-One (with inverse) |

---

## Visual Summary

```
BEFORE:                          AFTER:
┌─────────────────────┐          ┌─────────────────────┐
│     John leads:     │          │     John leads:     │
├─────────────────────┤          ├─────────────────────┤
│ ✓ Sales            │          │ ✓ Sales             │
│ ✓ Marketing        │          │ ✓ Support           │
│ (shared with Sarah)│          │ (solo)              │
└─────────────────────┘          └─────────────────────┘

┌─────────────────────┐          ┌─────────────────────┐
│  Sales led by:      │          │  Sales led by:      │
├─────────────────────┤          ├─────────────────────┤
│ ✓ John             │          │ ✓ John (only)       │
│ ✓ Sarah            │          │                     │
│ ✓ Mike             │          │                     │
│ (TOO MANY!)        │          │ (PERFECT!)          │
└─────────────────────┘          └─────────────────────┘
```

---

## Conclusion

The data model now enforces **"one leader per org unit"** while still allowing users to lead multiple org units. This is achieved through a **Many-to-One (with inverse OneToMany)** relationship, which is simpler, clearer, and more efficient than the previous many-to-many join table approach.
