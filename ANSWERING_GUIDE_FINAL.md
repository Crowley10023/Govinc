# Answering Guide - Final Implementation (Percentage-Based Matching)

## Overview
The Answering Guide now works with a **percentage-based approach**:
1. AI analyzes yes/no answers and returns a **percentage (0-100)**
2. System matches that percentage to the **closest MaturityAnswer by rating**
3. Results always match the actual maturity levels in the database

## How It Works

### Step 1: Question Generation (Cached)
```
User clicks "💡 Guide"
    ↓
Check cache for this controlId
    ├─ Cache Hit: Return cached questions (~50ms)
    │             Increment usage_count
    │
    └─ Cache Miss: Generate via AI (~3 sec)
                   Store in database
                   Return questions
```

### Step 2: Answer Analysis (Percentage-Based)
```
User answers 3-5 yes/no questions
    ↓
Submit answers to backend
    ↓
AnsweringGuideService.proposeAnswerFromGuide():
    1. Count Yes answers
    2. Calculate yes percentage (e.g., 4/5 = 80%)
    3. Call AI: "Analyze this and return a percentage 0-100"
    4. AI returns: "75" (percentage)
    5. Find closest MaturityAnswer by rating (e.g., rating=75 → "Managed")
    6. Return that MaturityAnswer
    
Result: Answer guaranteed to match database maturity levels
```

### Step 3: Database Matching
```
AI Percentage (e.g., 75)
    ↓
Query: Find MaturityAnswer closest to rating 75
    ├─ Not Implemented (rating: 0)    → diff = 75
    ├─ Informal (rating: 25)          → diff = 50
    ├─ Repeatable (rating: 50)        → diff = 25
    ├─ Managed (rating: 75)           → diff = 0 ✓ MATCH!
    └─ Optimized (rating: 100)        → diff = 25
    
Result: "Managed" selected and displayed
```

## Key Changes from Previous Version

### ✅ Simplified Caching
- **Before**: Cached questions + answer mappings
- **Now**: Cache only questions (simpler, faster)
- Mapping happens in real-time from database

### ✅ Percentage-Based AI
- **Before**: AI tried to return "Managed" or "Repeatable"
- **Now**: AI returns percentage "0-100" (much simpler)
- System matches to database answers (guaranteed accuracy)

### ✅ Database-Driven Matching
- **Before**: Hard-coded mapping rules in service
- **Now**: Query MaturityAnswer table with actual ratings
- Always matches what's in the database

## File Changes

### 1. AnsweringGuideService.java (Reworked)
**Key Changes:**
- `getAnsweringGuide()` - Check cache first, simplified
- `generateAnsweringGuide()` - Generate and cache questions only
- `proposeAnswerFromGuide()` - New logic:
  ```java
  1. Count yes/no answers
  2. Call AI with simple prompt: "Return percentage 0-100"
  3. Parse percentage from AI response
  4. Call findClosestMaturityAnswer(catalogId, percentage)
  5. Return the matching MaturityAnswer
  ```
- `findClosestMaturityAnswer()` - Find closest rating match

### 2. AnsweringGuideCache.java (Simplified)
**Changes:**
- Removed `answer_mapping` column (no longer needed)
- Removed `createAnswerMappingJSON()` method
- Only stores: controlId, questions, timestamps, usage_count
- Constructor simplified

### 3. V003__create_answering_guide_cache.sql (Simplified)
**Changes:**
- Removed `answer_mapping` column
- Cleaner, simpler table structure
- Same indexes for performance

### 4. AnsweringGuideController.java (Updated)
**Changes:**
- Receives `securityCatalogId` from frontend
- Passes it to service methods
- Better error handling for both IDs

### 5. assessment-details.html (Updated)
**Changes:**
- Added `securityCatalogId` to state object
- Passes catalog ID to `openAnsweringGuideModal()`
- Sends catalog ID in both AJAX calls
- Exposes `window.securityCatalogId` globally

## Example Workflow

**Control**: "Access Control Management"

**Questions Generated**:
```
1. "Are access policies documented?"
2. "Is access reviewed before granted?"
3. "Are rights revoked when not needed?"
4. "Is periodic review done?"
5. "Are violations logged?"
```

**User Answers**: Yes, Yes, No, Yes, Yes
**Calculation**: 4/5 = 80% Yes

**AI Call**:
```
Prompt: "Analyze these 5 yes/no answers. Return percentage 0-100."
Response: "78"
```

**Database Lookup**:
```
MaturityAnswers in database:
- Not Implemented (rating: 0)
- Informal (rating: 25)
- Repeatable (rating: 50)
- Managed (rating: 75)
- Optimized (rating: 100)

Closest to 78 is "Managed" (rating: 75, diff: 3)
```

**Result**: "Managed" proposed and displayed

## Performance

| Operation | Time | Notes |
|-----------|------|-------|
| First question generation | ~3 sec | AI call, then cached |
| Cached questions retrieval | ~50ms | Database lookup |
| Answer analysis | ~2-3 sec | AI analyzes % |
| Database matching | <1ms | Simple SQL query |
| Total first time | ~5 sec | - |
| Total cached | ~2-3 sec | Much faster! |

## Database Schema

```sql
answering_guide_cache:
├─ id (Primary Key)
├─ control_id (Unique, indexed) ← Fast lookup
├─ control_name
├─ control_detail
├─ questions (JSON array of 3-5 questions)
├─ created_at (Not updatable)
├─ updated_at (Auto-updated on increment)
└─ usage_count (Analytics)
```

## Advantages of This Approach

✅ **Accuracy**: Always matches database maturity levels  
✅ **Simplicity**: AI just returns percentage, no text parsing  
✅ **Flexibility**: Works with any maturity model in database  
✅ **Performance**: Cached questions, fast DB matching  
✅ **Maintainability**: No hard-coded mappings  
✅ **Scalability**: Works with different rating scales  

## API Endpoints

### POST /assessment/generate-answering-guide-questions
**Request**:
```json
{
  "controlId": 123,
  "controlName": "Access Control",
  "controlDetail": "...",
  "securityCatalogId": 1
}
```

**Response**:
```json
{
  "success": true,
  "questions": ["Is...?", "Does...?", ...],
  "cached": false
}
```

### POST /assessment/generate-answer-from-guide
**Request**:
```json
{
  "controlId": 123,
  "securityCatalogId": 1,
  "questions": ["Is...?", "Does...?", ...],
  "answers": ["Yes", "No", "Yes", "Yes", "Yes"]
}
```

**Response**:
```json
{
  "success": true,
  "proposedAnswer": "Managed",
  "proposedAnswerId": 4,
  "aiPercentage": 78,
  "yesPercentage": 80,
  "yesCount": 4,
  "totalCount": 5,
  "matchedRating": 75
}
```

## Testing Checklist

- [ ] First question generation creates cache
- [ ] Second use retrieves from cache
- [ ] Cache usage_count increments
- [ ] 0% Yes (0/5 No) → "Not Implemented"
- [ ] 20% Yes (1/5) → "Informal"
- [ ] 60% Yes (3/5) → "Repeatable"
- [ ] 80% Yes (4/5) → "Managed"
- [ ] 100% Yes (5/5) → "Optimized" or "Managed"
- [ ] "Take Over" saves answer to dropdown
- [ ] "Discard" keeps questions for retry
- [ ] Works with different maturity models
- [ ] Cache survives server restart

## Troubleshooting

### Issue: Wrong maturity level proposed
**Solution**: Check MaturityAnswer ratings in database
```sql
SELECT id, answer, rating FROM maturity_answers ORDER BY rating;
```

### Issue: Cache not working
**Solution**: Check cache table exists and has data
```sql
SELECT COUNT(*) FROM answering_guide_cache;
SELECT * FROM answering_guide_cache WHERE control_id = 123;
```

### Issue: AI response not parsed
**Solution**: AI must return only a number. If it returns text, filter it.
- Current: `Integer.parseInt(aiResponse.replaceAll("[^0-9]", ""))`
- Extracts all digits and parses

### Issue: "No maturity answers found"
**Solution**: Check maturity answers loaded for catalog
```sql
SELECT * FROM maturity_answers WHERE id IN (SELECT ma_id FROM mm_maturity_answers WHERE mm_id = 1);
```

## Migration Notes

1. Run `V003__create_answering_guide_cache.sql`
2. Creates table with UNIQUE constraint on control_id
3. Prevents duplicate caches for same control
4. Indexes optimized for lookup

## Future Enhancements

- Cache invalidation API for admins
- Analytics dashboard (most-used controls)
- Cache refresh scheduling
- Bulk question generation
- Question rating/feedback system
