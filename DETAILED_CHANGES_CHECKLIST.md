# Detailed Changes Checklist for Security Controls Translation

## File: `app/src/main/resources/templates/security-controls.html`

### Change 1: Replace Language Selection UI (Line ~730)

**FIND THIS:**
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

**REPLACE WITH:**
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

---

### Change 2: Update Preview Button Handler (Line ~1105)

**FIND THIS:**
```javascript
    // Preview button handler
    previewBtn.addEventListener('click', function() {
        const file = csvFile.files[0];
        if (!file) return;

        const reader = new FileReader();
        reader.onload = function(e) {
            const csvText = e.target.result;
            
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
        };
        reader.readAsText(file);
    });
```

**REPLACE WITH:**
```javascript
    // Preview button handler
    previewBtn.addEventListener('click', function() {
        const file = csvFile.files[0];
        if (!file) return;

        const reader = new FileReader();
        reader.onload = function(e) {
            const csvText = e.target.result;
            
            // Check if translation is requested
            const selectedLanguage = document.getElementById('translationLanguage').value;
            
            if (selectedLanguage) {
                // Translate before parsing
                translateAndParseCsv(csvText, selectedLanguage);
            } else {
                // Parse directly without translation
                parseCsvData(csvText);
            }
        };
        reader.readAsText(file);
    });
```

---

### Change 3: Replace translateAndParseCsv Function (Line ~1245)

**FIND THIS:**
```javascript
    // Translate CSV data using AI
    function translateAndParseCsv(csvText, languages) {
        // ... entire function ...
    }
```

**REPLACE WITH:**
```javascript
    // Translate CSV data using AI with single language
    function translateAndParseCsv(csvText, language) {
        const progressDiv = document.getElementById('translationProgress');
        progressDiv.style.display = 'block';
        
        const rows = parseCSVData(csvText);
        if (rows.length < 2) {
            showParseError('CSV file must contain at least a header row and one data row.');
            progressDiv.style.display = 'none';
            return;
        }
        
        const headers = rows[0];
        const dataRows = rows.slice(1);
        const totalTranslations = dataRows.length;
        let completedTranslations = 0;
        
        // Create translated data structure
        const translatedRows = dataRows.map(row => [...row]);
        
        // Add translation headers
        headers.push('detail_' + language);
        headers.push('name_' + language);
        
        // Process each row
        const allTasks = [];
        dataRows.forEach((row, rowIdx) => {
            allTasks.push({ rowIdx, row });
        });
        
        // Process translations sequentially
        const processTranslations = () => {
            if (allTasks.length === 0) {
                progressDiv.style.display = 'none';
                
                // Now parse the translated data
                let translatedCsv = headers.join(',') + '\n';
                translatedRows.forEach(row => {
                    translatedCsv += '"' + row.map(col => (col || '').replace(/"/g, '""')).join('","') + '"\n';
                });
                
                parseCsvData(translatedCsv);
                return;
            }
            
            const task = allTasks.shift();
            
            fetch('/api/security-control/translate', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                },
                body: JSON.stringify({
                    language: language,
                    name: task.row[0] || '',
                    detail: task.row[1] || ''
                })
            })
                .then(response => response.json())
                .then(result => {
                    completedTranslations++;
                    
                    // Add translated columns in correct order: detail, then name
                    if (result.success && result.translated) {
                        translatedRows[task.rowIdx].push(result.translated.detail || task.row[1] || '');
                        translatedRows[task.rowIdx].push(result.translated.name || task.row[0] || '');
                    } else {
                        // Fallback to original if translation failed
                        translatedRows[task.rowIdx].push(task.row[1] || '');
                        translatedRows[task.rowIdx].push(task.row[0] || '');
                    }
                    
                    // Update progress
                    const progressBar = document.getElementById('translationProgressBar');
                    const progressText = document.getElementById('translationProgressText');
                    const progress = (completedTranslations / totalTranslations) * 100;
                    progressBar.style.width = progress + '%';
                    progressText.textContent = completedTranslations + '/' + totalTranslations;
                    
                    // Continue with next translation
                    setTimeout(processTranslations, 100);
                })
                .catch(error => {
                    console.error('Translation error:', error);
                    completedTranslations++;
                    
                    // Fallback to original if translation failed
                    translatedRows[task.rowIdx].push(task.row[1] || '');
                    translatedRows[task.rowIdx].push(task.row[0] || '');
                    
                    const progressBar = document.getElementById('translationProgressBar');
                    const progressText = document.getElementById('translationProgressText');
                    const progress = (completedTranslations / totalTranslations) * 100;
                    progressBar.style.width = progress + '%';
                    progressText.textContent = completedTranslations + '/' + totalTranslations;
                    
                    setTimeout(processTranslations, 100);
                });
        };
        
        processTranslations();
    }
```

---

### Change 4: Update parseCsvData - Add Translation Extraction (Line ~1380)

**FIND THIS:**
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
                    analyzed: false
                };
                
                // Validate required fields
                if (!rowData.name) {
                    rowData.valid = false;
                    rowData.errors.push('Name is required');
                }
                if (!rowData.domain) {
                    rowData.valid = false;
                    rowData.errors.push('Domain is required');
                }
```

**REPLACE WITH:**
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
                    translations: {}
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
                
                // Validate required fields
                if (!rowData.name) {
                    rowData.valid = false;
                    rowData.errors.push('Name is required');
                }
                if (!rowData.domain) {
                    rowData.valid = false;
                    rowData.errors.push('Domain is required');
                }
```

---

### Change 5: Update analyzeControlsSimilarity - Use Translated Content (Line ~1515)

**FIND THIS:**
```javascript
        // Analyze each control independently and stream results
        validControls.forEach((control, index) => {
            const controlDTO = {
                name: control.name,
                detail: control.detail,
                reference: control.reference,
                domain: control.domain
            };
```

**REPLACE WITH:**
```javascript
        // Analyze each control independently and stream results
        validControls.forEach((control, index) => {
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

---

## Summary of Changes

| Item | Location | Change Type | Impact |
|------|----------|------------|--------|
| Language Selection | Line ~730 | UI Update | Checkboxes → Dropdown |
| Preview Button | Line ~1105 | Function Update | Single language support |
| translateAndParseCsv | Line ~1245 | Complete Rewrite | Single language processing |
| rowData Creation | Line ~1380 | Enhancement | Translation extraction + display |
| Similarity Analysis | Line ~1515 | Enhancement | Use translated content |

---

## Validation Checklist

After making changes, verify:

- [ ] Dropdown appears with 5 language options
- [ ] No Translation option selectable
- [ ] Translation progress bar appears during translation
- [ ] Preview table shows **translated text** (not original)
- [ ] Browser console shows `[PARSER] Using translated content (de)`
- [ ] Browser console shows `[ANALYSIS] Using translated content (de)`
- [ ] AI analysis recommendations are based on translated text
- [ ] Catalog assignment still works
- [ ] Import process completes successfully

---

## Rollback Plan

If needed to revert, original versions are in the initial file snapshots.
Keep backups before applying these changes.
