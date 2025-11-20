# Fix: UnsatisfiedDependencyException Error

## Problem
```
org.springframework.beans.factory.UnsatisfiedDependencyException: 
Error creating bean with name 'assessmentController': 
Unsatisfied dependency expressed through field 'userRepository': 
Error creating bean with name 'userRepository': 
Could not create query for public abstract java.util.List 
com.govinc.user.UserRepository.findAll()
```

## Root Cause
The `UserRepository` interface was using outdated JPA queries that referenced the old field name `organisationUnit` (singular), but the `User` entity was updated to use `organisationUnits` (plural) for the many-to-many relationship.

### Before (Broken)
```java
@Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.organisationUnit WHERE u.name = :name")
Optional<User> findByName(@Param("name") String name);

@Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.organisationUnit")
List<User> findAll();
```

The JPA/Hibernate query validator couldn't find a field named `organisationUnit` in the User entity, causing the bean creation to fail.

### After (Fixed)
```java
@Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.organisationUnits WHERE u.name = :name")
Optional<User> findByName(@Param("name") String name);

@Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.organisationUnits")
List<User> findAll();
```

Now the queries correctly reference the many-to-many relationship field `organisationUnits`.

## File Changed
📄 **`app/src/main/java/com/govinc/user/UserRepository.java`**

### Changes
- Line 10: `u.organisationUnit` → `u.organisationUnits`
- Line 13: `u.organisationUnit` → `u.organisationUnits`

## What Was Fixed
1. ✅ Query now references correct entity field name
2. ✅ Many-to-many relationship properly mapped
3. ✅ LEFT JOIN FETCH works with Set instead of single entity
4. ✅ DISTINCT keyword removes duplicates from many-to-many join

## Testing the Fix

### Before Build
```bash
# This would fail with UnsatisfiedDependencyException
mvn clean package
```

### After Fix
```bash
# This should succeed
mvn clean package

# Then run the application
mvn spring-boot:run
```

### Verify It Works
1. Application starts without errors
2. Can navigate to users page
3. Can create a new user
4. Can edit existing user
5. Can select multiple org units for Team Leaders

## Why This Happened
When the User entity was updated from a single-unit relationship to a many-to-many relationship:
- The entity field name changed: `organisationUnit` → `organisationUnits`
- The database schema changed (added join table)
- But the repository queries weren't updated

## Related Files (No Changes Needed)
The following files were already correctly using the new field:
- ✅ `User.java` - Already uses `organisationUnits` (Set)
- ✅ `UserController.java` - Already uses `organisationUnits` (Set)
- ✅ `AuthorizationService.java` - Already uses `organisationUnits` (Set)

## Summary
This was a **simple field name mismatch** between:
- Entity model (`organisationUnits` - many-to-many Set)
- Repository queries (`organisationUnit` - old single entity)

The fix updates the repository queries to use the correct field name, allowing Spring Data JPA to properly initialize the bean and resolve the dependency injection error.
