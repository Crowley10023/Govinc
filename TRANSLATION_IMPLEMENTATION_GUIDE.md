# Security Controls Translation Implementation Guide

## Overview
This guide explains how to integrate the automated translation feature for security controls using AI.

## Changes Summary

### 1. Backend Translation Controller
**File**: `app/src/main/java/com/govinc/catalog/SecurityControlTranslationRestController.java` (Already Created)

**Endpoint**: `POST /api/security-control/translate`
- Accepts: `language` (de, en, fr, es, it, pt, nl, ja, zh), `name`, `detail`
- Returns: JSON with translated `name` and `detail`
- Uses: Configured AI provider via `OpenAIUtil`

### 2. Frontend Updates Required

#### A. Replace Language Selection (Checkboxes → Dropdown)

**Location**: In `app/src/main/resources/templates/security-controls.html` 
Find section with checkboxes:
```html
<div class="translation-checkbox">
    <input type="checkbox" id="translateDE" name="languages" value="de" />
    <label for="translateDE">German (Deutsch)</label>
</div>
<div class="translation-checkbox">
    <input type="checkbox" id="translateEN" name="languages" value="en" />
    <label for="translateEN">English</label>
</div>
```

Replace with:
```html
<label for="translationLanguage" style="display: block; margin-bottom: 8px;"><strong>Select Language:</strong></label>
<select id="translationLanguage" name="translationLanguage" style="width: 100%; max-width: 300px; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;">
    <option value="">-- No Translation --</option>
    <option value="de">German (Deutsch)</option>
    <option value="en">English</option>
    <option value="fr">French (Français)</option>
    <option value="es">Spanish (Español)</option>
    <option value="it">Italian (Italiano)</option>
</select>
```

#### B. Update Preview Button Handler

Find:
```javascript
// Check if translation is requested
const selectedLanguages = Array.from(document.querySelectorAll('input[name="languages"]:checked'))
    .map(cb => cb.value);

if (selectedLanguages.length > 0) {
    // Translate before parsing
    translateAndParseCsv(csvText, selectedLanguages);
} else {
    // Parse directly without translation
    parseCsvData(csvText);
}
```

Replace with:
```javascript
// Check if translation is requested
const selectedLanguage = document.getElementById('translationLanguage').value;

if (selectedLanguage) {
    // Translate before parsing
    translateAndParseCsv(csvText, selectedLanguage);
} else {
    // Parse directly without translation
    parseCsvData(csvText);
}
```

#### C. Replace translateAndParseCsv Function

Replace entire `translateAndParseCsv` function with the one in `security-controls-script.js`

**Key Changes**:
- Accepts single `language` parameter instead of array
- Adds columns: `detail_xx` and `name_xx`
- Processes translations sequentially
- Maintains progress tracking

#### D. Update parseCsvData Function

In the section where `rowData` object is created (after line with `const rowData = {`), add:

```javascript
const rowData = {
    rowNumber: i,
    name: columns[0]?.trim() || '',
    detail: columns[1]?.trim() || '',
    reference: columns[2]?.trim() || '',
    domain: columns[3]?.trim() || '',
    valid: true,
    errors: [],
    action: 'new',
    matchingRate: 'none',
    suggestedMatch: null,
    analyzed: false,
    translations: {}  // ADD THIS LINE
};

// Parse translation columns (detail_xx, name_xx)
for (let j = 4; j < columns.length; j++) {
    const headerLower = (headers[j] || '').toLowerCase();
    if (headerLower.startsWith('detail_')) {
        const lang = headerLower.substring(7);
        if (!rowData.translations[lang]) rowData.translations[lang] = {};
        rowData.translations[lang].detail = columns[j]?.trim() || '';
    } else if (headerLower.startsWith('name_')) {
        const lang = headerLower.substring(5);
        if (!rowData.translations[lang]) rowData.translations[lang] = {};
        rowData.translations[lang].name = columns[j]?.trim() || '';
    }
}

// If translations exist, use them for display
if (Object.keys(rowData.translations).length > 0) {
    const lang = Object.keys(rowData.translations)[0];
    if (rowData.translations[lang].name) {
        rowData.name = rowData.translations[lang].name;
    }
    if (rowData.translations[lang].detail) {
        rowData.detail = rowData.translations[lang].detail;
    }
    console.log('[PARSER] Using translated content (' + lang + ') for: ' + rowData.name);
}
```

#### E. Update analyzeControlsSimilarity Function

In `analyzeControlsSimilarity()`, before creating `controlDTO`, add:

```javascript
// Use translated content if available for analysis
let analysisName = control.name;
let analysisDetail = control.detail;
const translationLanguages = Object.keys(control.translations || {});
if (translationLanguages.length > 0) {
    const firstLang = translationLanguages[0];
    if (control.translations[firstLang] && control.translations[firstLang].name) {
        analysisName = control.translations[firstLang].name;
    }
    if (control.translations[firstLang] && control.translations[firstLang].detail) {
        analysisDetail = control.translations[firstLang].detail;
    }
    console.log('[ANALYSIS] Using translated content (' + firstLang + ') for: ' + analysisName);
} else {
    console.log('[ANALYSIS] Using original content for: ' + analysisName);
}

const controlDTO = {
    name: analysisName,
    detail: analysisDetail,
    reference: control.reference,
    domain: control.domain
};
```

## Workflow

1. **User uploads CSV** with standard columns: `name`, `detail`, `reference`, `domain`
2. **User selects language** from dropdown (German, English, French, Spanish, Italian)
3. **System translates** each row using AI:
   - Calls `/api/security-control/translate` endpoint
   - Creates new CSV columns: `detail_xx`, `name_xx`
   - Shows progress bar
4. **Preview table displays** translated values (not originals)
5. **AI analysis** uses translated content for similarity matching
6. **User reviews** preview with translated data
7. **User selects actions** (merge/new/skip) for each control
8. **User assigns** to catalog
9. **User imports** security controls

## Console Logging

The implementation includes debug logging:
- `[PARSER] Using translated content (de) for:` - Shows translation extraction
- `[ANALYSIS] Using translated content (de) for:` - Shows AI analysis using translated content
- Translation errors are logged with HTTP status

## Testing

### Without Translation
1. Upload CSV without selecting language
2. Preview shows original content
3. Analysis uses original content

### With Translation
1. Upload CSV and select German (Deutsch)
2. Watch progress bar as translations occur
3. Preview table shows German translations
4. AI analysis uses German text
5. Console shows `[PARSER]` and `[ANALYSIS]` messages

## Error Handling

- Translation failures fallback to original content
- Parse errors show user-friendly messages
- Network errors are logged to console
- Invalid CSV format prevents processing

## Files Modified/Created

1. ✅ `app/src/main/java/com/govinc/catalog/SecurityControlTranslationRestController.java` - NEW
2. 📝 `app/src/main/resources/templates/security-controls.html` - MODIFIED
3. ✅ `app/src/main/java/com/govinc/util/OpenAIUtil.java` - Already configured

## Language Support

- German (de) - Deutsch
- English (en)
- French (fr) - Français
- Spanish (es) - Español
- Italian (it) - Italiano
- Portuguese (pt) - Português
- Dutch (nl) - Nederlands
- Japanese (ja) - 日本語
- Chinese (zh) - 中文

Additional languages can be added to the dropdown and are supported by the backend.
