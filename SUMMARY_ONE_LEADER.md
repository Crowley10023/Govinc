# Summary: One Team Leader Per Organization Unit

## What Changed?

### ✅ Core Change
**From:** Many users can lead the same org unit  
**To:** Only ONE user can lead each org unit

### 📊 Relationship Model

**Before:**
```
OrgUnit ←→ User (Many-to-Many)
├─ Org can have multiple leaders
└─ User can lead multiple orgs

Join Table: org_unit_leaders
```

**After:**
```
OrgUnit ──→ User (Many-to-One)
├─ Each org has at most 1 leader
└─ User can lead multiple orgs (inverse: OneToMany)

No join table needed
```

## Files Changed (5 total)

### Backend (3 files)
1. **OrgUnit.java**
   - Changed: `Set<User> leaders` → `User leader` (single)
   - No more join table

2. **User.java**
   - Added: `Set<OrgUnit> leadsOrgUnits` (inverse relationship)
   - User can lead multiple orgs

3. **UserController.java**
   - Now accepts single `leadOrgUnitId` (not array)
   - Validates org unit doesn't already have leader
   - Cleans up on user deletion/role change

4. **AuthorizationService.java**
   - Checks `user.getLeadsOrgUnits()` for access
   - Still supports users leading multiple orgs

5. **UserRepository.java**
   - Updated queries to fetch `leadsOrgUnits`

### Frontend (2 files)
1. **user_form.html**
   - Dropdown (not checkboxes)
   - Shows available org units only
   - Disables already-assigned orgs
   - Warning display

2. **orgunit-edit.html**
   - Single dropdown for leader selection
   - Disables users already leading another org
   - Warning display

## Key Features

✅ **One Leader Per Org Unit**
- Each org unit can have at most 1 team leader
- Prevents conflicts and ambiguity

✅ **Multiple Org Unit Leadership**
- A user CAN lead multiple org units
- Example: Sarah leads Sales and Marketing

✅ **Smart UI Validation**
- Disables unavailable options
- Shows warnings for conflicts
- Prevents invalid selections

✅ **Strong Backend Validation**
- Enforces constraints server-side
- Removes leadership on role change
- Cleanup on user deletion

## Usage

### Create Team Leader
```
1. Add User
2. Name: John Smith
3. Role: Organisation Team Leader
4. Lead Organization Unit: Sales (from dropdown)
5. Create

→ John leads Sales org only
```

### Edit Team Leader
```
1. Edit John
2. Change: Lead Organization Unit: Marketing
3. Update

→ John now leads Marketing (Sales has no leader)
```

### Assign Org Unit Leader
```
1. Edit Sales Org Unit
2. Team Leader: John (from dropdown)
3. Save

→ John is now leader of Sales
```

## Database

### Added
- `leader_id` column in `org_unit` table

### Removed
- `org_unit_leaders` join table (no longer needed)

### Auto-Migration
Hibernate creates/removes tables automatically on startup.

## Validation

### Frontend
- Dropdowns show only available options
- Disabled options for conflicts
- Real-time warnings

### Backend
- Validates leader assignment
- Enforces one-per-org constraint
- Prevents conflicts

## Testing

**Create/Edit Users:**
- [ ] Create Team Leader with org
- [ ] Edit to change org
- [ ] Try to assign taken org (disabled)

**Edit Org Units:**
- [ ] Assign user as leader
- [ ] Change leader
- [ ] Try to assign already-leading user

**Authorization:**
- [ ] Team Leader accesses own org ✓
- [ ] Team Leader cannot access other orgs ✗
- [ ] Multiple orgs all work ✓

**UI:**
- [ ] Org field shows/hides correctly
- [ ] Dropdowns work properly
- [ ] Warnings display

## Benefits

✨ **Clear:** No ambiguity about who leads what  
🔒 **Safe:** Backend validation prevents conflicts  
⚡ **Fast:** No join table, simpler queries  
👥 **Flexible:** Users can lead multiple orgs  
🎯 **User-Friendly:** UI prevents errors  

---

## All Changes Ready

Files proposed and ready for acceptance:
- ✅ OrgUnit.java
- ✅ User.java
- ✅ UserController.java
- ✅ AuthorizationService.java
- ✅ UserRepository.java
- ✅ user_form.html
- ✅ orgunit-edit.html
- ✅ ONE_LEADER_PER_ORG_UNIT.md (documentation)
