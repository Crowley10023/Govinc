# Role-Based Access Control - Architecture Overview

## System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    USER REQUEST                                 │
└─────────────────────────────────────────────────────────────────┘
                             │
                             ▼
        ┌────────────────────────────────────────┐
        │  Spring Security Filter Chain          │
        │  ├─ CSRF Protection                    │
        │  ├─ Session Management                 │
        │  └─ Authentication (Form/OAuth2)       │
        └────────────────────────────────────────┘
                             │
                ┌────────────┴────────────┐
                │                         │
                ▼                         ▼
        ┌──────────────────┐     ┌──────────────────┐
        │ Authenticated    │     │ Public Endpoints │
        │ Endpoints        │     │ (assessment-     │
        │                  │     │  direct/*)       │
        │ Requires Check   │     │                  │
        └──────────────────┘     │ No Check Needed  │
                │                │                  │
                ▼                │                  │
        ┌──────────────────────┐ │                  │
        │  Controller Method   │ │                  │
        └──────────────────────┘ │                  │
                │                │                  │
                ▼                │                  │
    ┌───────────────────────────────────┐  ▼       │
    │ AuthorizationService              │  │       │
    │ .canAccessAssessment(id)  ◄────────┤ │       │
    │ .canAccessConfig()                 │  │       │
    │ .canAccessOrganization()           │  │       │
    │ ... (30+ methods)                  │  │       │
    └───────────────────────────────────┘  │       │
                │                           │       │
        ┌───────┴──────────┐                │       │
        │                  │                │       │
        ▼                  ▼                │       │
    ┌──────────┐     ┌──────────┐          │       │
    │ Permitted│     │ Denied   │          │       │
    │ ✓        │     │ Throw    │          │       │
    │Continue  │     │Exception │          │       │
    └──────────┘     └──────────┘          │       │
        │                  │                │       │
        ▼                  ▼                │       │
    ┌──────────────┐  ┌──────────────────────────┐ │
    │ Execute      │  │ GlobalExceptionHandler   │ │
    │ Business     │  │ ├─ Catch                 │ │
    │ Logic        │  │ │ UnauthorizedException  │ │
    │              │  │ ├─ Detect API vs Page    │ │
    │ Return Data  │  │ ├─ Return 403 + Message  │ │
    └──────────────┘  └──────────────────────────┘ │
        │                  │                        │
        └────────┬─────────┘                        │
                 ▼                                  │
        ┌──────────────────────┐                   │
        │ Response (200 or 403)│◄──────────────────┘
        │ + Data/Error Message │
        └──────────────────────┘
                 │
                 ▼
        ┌──────────────────────┐
        │ Browser              │
        ├─ Parse Response      │
        ├─ Check Status Code   │
        └──────────────────────┘
                 │
        ┌────────┴────────┐
        │                 │
        ▼                 ▼
    ┌──────────┐  ┌──────────────────┐
    │ 200 OK   │  │ 403 Forbidden    │
    │ Display  │  │ authorization.js │
    │ Data     │  │ ├─ Intercept     │
    │          │  │ ├─ Parse Error   │
    │          │  │ ├─ Show Modal    │
    │          │  │ └─ User Friendly │
    └──────────┘  └──────────────────┘
```

## Request Processing Flow

### 1. Request Arrives
```
GET /assessment/123
Cookie: JSESSIONID=abc123
```

### 2. Spring Security Filter Chain
```
- Check CSRF token
- Validate session
- Extract authentication
- Load user principal
```

### 3. Route to Controller
```java
@GetMapping("/{id}")
public String getAssessmentById(@PathVariable Long id) {
```

### 4. Authorization Check
```java
if (!authorizationService.canAccessAssessment(id)) {
    throw new UnauthorizedException("...");
}
```

### 5. Response Generation
```
✓ Success: Return assessment data
✗ Failure: 403 Forbidden with message
```

## Authorization Service Architecture

```
┌─────────────────────────────────────────┐
│  AuthorizationService                   │
├─────────────────────────────────────────┤
│                                         │
│  Core Methods:                          │
│  • getCurrentUser()                     │
│  • getCurrentUserRole()                 │
│                                         │
│  Permission Checks:                     │
│  • canAccessConfig()           (ADMIN)  │
│  • canAccessSecurityFramework() (ADMIN+ISM) │
│  • canAccessOrganization()     (ADMIN+ISM) │
│  • canAccessAssessment()       (Role-based)│
│  • ... (25+ methods)                    │
│                                         │
│  Helper Methods:                        │
│  • isOrgUnitInTree()                    │
│  • isDescendantOf()                     │
│  • getAccessibleOrgUnits()              │
│                                         │
└─────────────────────────────────────────┘
         │                      │
         ▼                      ▼
    ┌──────────────┐   ┌──────────────────┐
    │ UserRepo     │   │ AssessmentRepo   │
    │ Database     │   │ Database         │
    │ Queries      │   │ Queries          │
    └──────────────┘   └──────────────────┘
         │                      │
         ▼                      ▼
    ┌────────────────────────────────────┐
    │ Spring Security Context            │
    │ Current Authentication             │
    └────────────────────────────────────┘
```

## Data Model

```
┌─────────────────────────────────────────┐
│  User Entity                            │
├─────────────────────────────────────────┤
│ - id: Long (PK)                         │
│ - name: String                          │
│ - email: String                         │
│ - role: Role (NEW)                      │
│   └─ ADMIN                              │
│   └─ INFORMATION_SECURITY_MANAGER       │
│   └─ ORGANISATION_TEAM_LEADER           │
│   └─ ASSESSMENT_DELEGATE                │
│ - organisationUnit: OrgUnit (NEW)       │
│   └─ Set for ORGANISATION_TEAM_LEADER   │
└─────────────────────────────────────────┘
           │                    │
           ▼                    ▼
    ┌──────────────┐  ┌──────────────────┐
    │ Assessment   │  │ OrgUnit          │
    │ (accessed by)│  │ (managed by OTL) │
    └──────────────┘  └──────────────────┘
```

## Authorization Decision Tree

```
User Requests Access to Assessment
                │
                ▼
        Is user ADMIN?
        ├─ YES → Grant access ✓
        └─ NO
                │
                ▼
        Is user ISM?
        ├─ YES → Grant access ✓
        └─ NO
                │
                ▼
        Is user OTL?
        ├─ YES
        │   │
        │   ▼
        │   Is assessment in user's org tree?
        │   ├─ YES → Grant access ✓
        │   └─ NO → Deny access ✗
        └─ NO
                │
                ▼
        Is user AD?
        ├─ YES
        │   │
        │   ▼
        │   Is user assigned to assessment?
        │   ├─ YES → Grant access ✓
        │   └─ NO → Deny access ✗
        └─ NO → Deny access ✗
```

## Frontend Error Handling Flow

```
Browser Makes Request
         │
         ▼
    ┌──────────────────┐
    │ Fetch/AJAX/XHR   │
    │ Call             │
    └──────────────────┘
         │
         ▼
    Response Received
         │
    ┌────┴────┐
    │          │
    ▼          ▼
Status Code  Status Code
 = 200        = 403
 (Success)    (Forbidden)
    │          │
    ▼          ▼
Display   ┌─────────────────┐
Data      │ authorization.js│
          │ Error Handler   │
          ├─────────────────┤
          │1. Intercept 403 │
          │2. Parse Message │
          │3. Create Modal  │
          │4. Show to User  │
          │5. Log Event     │
          └─────────────────┘
```

## Module Dependencies

```
┌──────────────────────────────────┐
│ Controllers                      │
│ ├─ AssessmentController          │
│ ├─ OrgUnitController             │
│ ├─ UserController               │
│ └─ ...                           │
└──────────────────────────────────┘
              ▲
              │ uses
              │
┌──────────────────────────────────┐
│ AuthorizationService             │
└──────────────────────────────────┘
              ▲
              │ uses
              │
        ┌─────┴─────┐
        │           │
        ▼           ▼
    ┌────────┐  ┌──────────┐
    │ User   │  │Assessment│
    │Repo    │  │Repo      │
    └────────┘  └──────────┘
        │           │
        └─────┬─────┘
              ▼
        Database Layer
```

## Exception Handling Architecture

```
┌─────────────────────────────────────┐
│ Controller                          │
│ if (!authService.canAccess(id)) {  │
│   throw UnauthorizedException(msg) │
│ }                                   │
└─────────────────────────────────────┘
              │
              │ throws
              ▼
┌─────────────────────────────────────┐
│ UnauthorizedException               │
│ @ResponseStatus(FORBIDDEN)          │
└─────────────────────────────────────┘
              │
              │ caught by
              ▼
┌─────────────────────────────────────┐
│ GlobalExceptionHandler              │
│ @ExceptionHandler                   │
│   (UnauthorizedException.class)     │
│                                     │
│ Decide:                             │
│  ├─ Is it API call?                │
│  │  └─ Return JSON 403             │
│  └─ Is it page request?            │
│     └─ Return HTML error page      │
└─────────────────────────────────────┘
              │
              ▼
        HTTP 403 Response
```

## Role Hierarchy

```
                    ┌──────────────┐
                    │    ADMIN     │
                    │ (Full Access)│
                    └──────────────┘
                           │
                    (Most Powerful)
                           │
                ┌──────────────────────┐
                │         ISM          │
                │  (All except Config) │
                └──────────────────────┘
                           │
                      (More Limited)
                      ╱          ╲
            ┌─────────────┐  ┌──────────────┐
            │     OTL     │  │      AD      │
            │(Org Scoped) │  │(Assignment)  │
            └─────────────┘  └──────────────┘
                      ╲          ╱
                    (Most Limited)
                           │
                ┌──────────────────────┐
                │   assessment-direct  │
                │   (No auth required) │
                └──────────────────────┘
```

## Component Interaction

```
                     ┌─────────────────────┐
                     │   User Interface    │
                     │   (Thymeleaf/HTML)  │
                     └─────────────────────┘
                              │
                ┌─────────────┴─────────────┐
                │                           │
                ▼                           ▼
        ┌──────────────┐          ┌──────────────────┐
        │ authorization│          │ authorization.js │
        │.js (Frontend)│          │ (Event Handler)  │
        └──────────────┘          └──────────────────┘
                │                           │
                └─────────────┬─────────────┘
                              │
                    ┌─────────▼─────────┐
                    │  HTTP Request(s)  │
                    └───────────────────┘
                              │
        ┌─────────────────────┴──────────────────────┐
        │                                            │
        ▼                                            ▼
┌──────────────────────┐              ┌─────────────────────┐
│ GlobalUserSession    │              │ Controllers         │
│ Advice               │              │ (with @Autowired    │
│                      │              │  AuthorizationSvc)  │
│ Adds Model Attrs:    │              │                     │
│ ├─ canAccessConfig   │              │ Check permissions   │
│ ├─ canAccess...      │              │ or throw exception  │
│ └─ ...               │              │                     │
└──────────────────────┘              └─────────────────────┘
        │                                            │
        └─────────────────┬──────────────────────────┘
                          │
                   ┌──────▼──────┐
                   │Authorization│
                   │Service       │
                   │              │
                   │ Queries:     │
                   │ └─ DB        │
                   │ └─ Spring    │
                   │   Security   │
                   │   Context    │
                   └──────────────┘
```

## Security Layers

```
Layer 1: Spring Security (Authentication)
├─ Form-based login
├─ OAuth2 providers
└─ Session validation

Layer 2: AuthorizationService (Authorization)
├─ Role checking
├─ Organization hierarchy
└─ Assessment assignment

Layer 3: Frontend (UI Protection)
├─ Navigation filtering
├─ Element hiding
└─ Error display

Layer 4: Error Handling (User-Friendly Messages)
├─ Exception catching
├─ Message translation
└─ Response formatting
```

## Request Lifecycle Sequence Diagram

```
User Request
    │
    ├─→ Spring Security Filter
    │    ├─ Check authentication
    │    ├─ Load user principal
    │    └─ Create context
    │
    ├─→ Route to Controller
    │
    ├─→ AuthorizationService.check()
    │    ├─ Get current user
    │    ├─ Get user role
    │    ├─ Get assessment/resource
    │    └─ Evaluate rules
    │
    ├─→ Decision Point
    │    ├─ Allowed → Execute business logic
    │    └─ Denied → Throw UnauthorizedException
    │
    ├─→ GlobalExceptionHandler (if exception)
    │    ├─ Detect request type
    │    ├─ Format response
    │    └─ Set HTTP status
    │
    └─→ Response
         ├─ 200 OK + Data
         ├─ 403 Forbidden + Message
         └─ Other errors
```

---

This architecture ensures:
1. ✅ Centralized authorization logic
2. ✅ Consistent permission checking
3. ✅ User-friendly error handling
4. ✅ Security at multiple layers
5. ✅ Easy to maintain and extend
