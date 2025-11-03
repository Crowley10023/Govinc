// Complete updated script section for security-controls.html
// This replaces the entire <script> section

<script>
    // Global variables
    let csvData = [];
    let availableCatalogs = [];
    let analysisResults = {};
    let analysisInProgress = {};
    
    // Get modal elements
    const modal = document.getElementById('csvImportModal');
    const importBtn = document.getElementById('importBtn');
    const closeBtn = document.getElementsByClassName('close')[0];
    const csvFile = document.getElementById('csvFile');
    const previewBtn = document.getElementById('previewBtn');
    const uploadForm = document.getElementById('csvUploadForm');

    // Show modal when import button is clicked
    importBtn.onclick = function() {
        modal.style.display = 'block';
        loadAvailableCatalogs();
    }

    // Close modal when X is clicked
    closeBtn.onclick = function() {
        modal.style.display = 'none';
        resetForm();
    }

    // Close modal when clicking outside of it
    window.onclick = function(event) {
        if (event.target === modal) {
            modal.style.display = 'none';
            resetForm();
        }
    }

    // Close modal on Escape key
    document.addEventListener('keydown', function(event) {
        if (event.key === 'Escape' && modal.style.display === 'block') {
            modal.style.display = 'none';
            resetForm();
        }
    });

    // File selection handler
    csvFile.addEventListener('change', function(event) {
        const file = event.target.files[0];
        previewBtn.disabled = !file;
        
        if (file) {
            const fileName = file.name.toLowerCase();
            if (!fileName.endsWith('.csv')) {
                alert('Please select a CSV file.');
                event.target.value = '';
                previewBtn.disabled = true;
                return;
            }
            
            // Check file size (max 10MB)
            const maxSize = 10 * 1024 * 1024;
            if (file.size > maxSize) {
                alert('File size must be less than 10MB.');
                event.target.value = '';
                previewBtn.disabled = true;
                return;
            }
        }
    });

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

    // RFC4180 compliant CSV parser - handles multi-line quoted fields
    function parseCSVData(csvText) {
        const rows = [];
        let currentRow = [];
        let currentField = '';
        let inQuotes = false;
        let i = 0;
        
        while (i < csvText.length) {
            const char = csvText[i];
            const nextChar = i + 1 < csvText.length ? csvText[i + 1] : '';
            
            if (char === '"') {
                if (inQuotes && nextChar === '"') {
                    currentField += '"';
                    i += 2;
                    continue;
                } else {
                    inQuotes = !inQuotes;
                    i++;
                    continue;
                }
            }
            
            if (char === ',' && !inQuotes) {
                currentRow.push(currentField);
                currentField = '';
                i++;
                continue;
            }
            
            if ((char === '\n' || char === '\r') && !inQuotes) {
                if (currentField || currentRow.length > 0) {
                    currentRow.push(currentField);
                    if (currentRow.some(f => f.trim())) {
                        rows.push(currentRow);
                    }
                    currentRow = [];
                    currentField = '';
                }
                if (char === '\r' && nextChar === '\n') {
                    i += 2;
                } else {
                    i++;
                }
                continue;
            }
            
            currentField += char;
            i++;
        }
        
        if (currentField || currentRow.length > 0) {
            currentRow.push(currentField);
            if (currentRow.some(f => f.trim())) {
                rows.push(currentRow);
            }
        }
        
        return rows;
    }

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

    // CSV parsing function with proper error handling
    function parseCsvData(csvText) {
        try {
            const allRows = parseCSVData(csvText);
            
            if (allRows.length < 2) {
                showParseError('CSV file must contain at least a header row and one data row.');
                return;
            }

            const headers = allRows[0];
            const expectedHeaders = ['name', 'detail', 'reference', 'domain'];
            
            const headersValid = expectedHeaders.every((header, index) => 
                headers[index] && headers[index].toLowerCase().trim() === header
            );
            
            if (!headersValid) {
                showParseError('CSV headers must start with: name,detail,reference,domain (in order). Found: ' + headers.slice(0, 4).map(h => '"' + h + '"').join(', '));
                return;
            }

            // Parse data rows
            csvData = [];
            for (let i = 1; i < allRows.length; i++) {
                const columns = allRows[i];
                if (columns.length === 0 || (columns.length === 1 && columns[0].trim() === '')) {
                    continue;
                }
                
                if (columns.length < 4) {
                    console.warn(`Row ${i}: has only ${columns.length} columns (need 4), skipping`);
                    continue;
                }
                
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
                
                csvData.push(rowData);
            }
            
            if (csvData.length === 0) {
                showParseError('No valid data rows found in CSV file.');
                return;
            }

            displayPreview();
            analyzeControlsSimilarity();
            showCatalogOptions();
        } catch (error) {
            showParseError('Error parsing CSV: ' + error.message);
            console.error('CSV Parse Error:', error);
        }
    }
    
    // Show parse error with visual feedback
    function showParseError(message) {
        const previewSection = document.getElementById('previewSection');
        const previewStats = document.getElementById('previewStats');
        const previewError = document.getElementById('previewError');
        const previewTableBody = document.getElementById('previewTableBody');
        const previewEmpty = document.getElementById('previewEmpty');
        
        previewSection.style.display = 'block';
        previewStats.innerHTML = '<span style="color: #dc3545;">❌ Parse Error</span>';
        previewError.style.display = 'block';
        previewError.innerHTML = `
            <strong>❌ CSV Parse Error:</strong><br>
            ${message}<br><br>
            <small>Please ensure your CSV file:</small>
            <ul style="margin: 8px 0; padding-left: 20px;">
                <li>Has headers: name,detail,reference,domain</li>
                <li>Uses double quotes for fields with commas or newlines</li>
                <li>Escapes quotes inside quoted fields by doubling them ("")</li>
                <li>Uses standard line breaks (LF or CRLF)</li>
                <li>Is saved as UTF-8 encoding</li>
            </ul>
        `;
        
        previewTableBody.innerHTML = '';
        previewEmpty.style.display = 'block';
        
        document.getElementById('catalogSection').style.display = 'none';
        document.getElementById('importSection').style.display = 'none';
    }

    // Analyze controls for similarity using AI
    function analyzeControlsSimilarity() {
        const progressDiv = document.getElementById('analysisProgress');
        const validControls = csvData.filter(row => row.valid);
        
        if (validControls.length === 0) {
            return;
        }
        
        progressDiv.style.display = 'block';
        analysisResults = {};
        analysisInProgress = {};
        let completedCount = 0;
        const totalCount = validControls.length;
        
        updatePreviewWithAnalysis();
        
        // Analyze each control independently
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
            
            analysisInProgress[control.rowNumber] = true;
            updatePreviewWithAnalysis();
            
            fetch('/api/security-control/import/analyze-single', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                },
                body: JSON.stringify(controlDTO)
            })
                .then(response => {
                    if (!response.ok) {
                        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
                    }
                    return response.json();
                })
                .then(result => {
                    analysisResults[control.rowNumber] = result;
                    completedCount++;
                    analysisInProgress[control.rowNumber] = false;
                    
                    const progressBar = document.getElementById('progressBar');
                    const progressText = document.getElementById('progressText');
                    const progress = (completedCount / totalCount) * 100;
                    progressBar.style.width = progress + '%';
                    progressText.textContent = completedCount + '/' + totalCount;
                    
                    updatePreviewWithAnalysis();
                    
                    if (completedCount === totalCount) {
                        setTimeout(() => {
                            progressDiv.style.display = 'none';
                        }, 500);
                    }
                })
                .catch(error => {
                    console.error('Error analyzing control:', control.name, error);
                    completedCount++;
                    analysisInProgress[control.rowNumber] = false;
                    
                    const progressBar = document.getElementById('progressBar');
                    const progressText = document.getElementById('progressText');
                    const progress = (completedCount / totalCount) * 100;
                    progressBar.style.width = progress + '%';
                    progressText.textContent = completedCount + '/' + totalCount;
                    
                    updatePreviewWithAnalysis();
                    
                    if (completedCount === totalCount) {
                        setTimeout(() => {
                            progressDiv.style.display = 'none';
                            console.warn('Analysis completed with some errors');
                        }, 500);
                    }
                });
        });
    }

    // Update preview table with analysis results
    function updatePreviewWithAnalysis() {
        const previewTableBody = document.getElementById('previewTableBody');
        previewTableBody.innerHTML = '';
        
        csvData.forEach(row => {
            const tr = document.createElement('tr');
            
            if (!row.valid) {
                tr.className = 'error-row';
            } else {
                if (analysisResults[row.rowNumber]) {
                    const analysis = analysisResults[row.rowNumber];
                    row.matchingRate = analysis.matchingRate;
                    row.action = analysis.recommendedAction;
                    if (analysis.suggestedMergeControl) {
                        row.suggestedMatch = analysis.suggestedMergeControl;
                    }
                    row.analyzed = true;
                }
                
                if (analysisInProgress[row.rowNumber]) {
                    tr.className = 'row-analyzing';
                } else {
                    if (row.matchingRate === 'high') {
                        tr.className = 'match-high';
                    } else if (row.matchingRate === 'medium') {
                        tr.className = 'match-medium';
                    } else if (row.matchingRate === 'low') {
                        tr.className = 'match-low';
                    } else {
                        tr.className = 'match-none';
                    }
                }
            }
            
            let statusHtml;
            if (!row.valid) {
                statusHtml = '<span style="color: #dc3545;">✗ ' + row.errors.join(', ') + '</span>';
            } else if (analysisInProgress[row.rowNumber]) {
                statusHtml = '<span style="color: #007bff;">⏳ Analyzing...</span>';
            } else {
                statusHtml = '<span style="color: #28a745;">✓ Valid</span>';
            }
            
            let matchBadge = row.analyzed ? '' : '<span style="color: #999; font-size: 11px;">Pending...</span>';
            if (row.matchingRate === 'high') {
                matchBadge = '<span class="match-badge-high">High</span>';
            } else if (row.matchingRate === 'medium') {
                matchBadge = '<span class="match-badge-medium">Medium</span>';
            } else if (row.matchingRate === 'low') {
                matchBadge = '<span class="match-badge-low">Low</span>';
            } else if (row.analyzed) {
                matchBadge = '<span class="match-badge-none">None</span>';
            }
            
            let actionSelect = '<select class="action-select action-selector" data-row="' + row.rowNumber + '"' + (row.valid ? '' : ' disabled') + '>';
            if (row.matchingRate !== 'none' && row.suggestedMatch) {
                actionSelect += '<option value="merge:' + row.suggestedMatch.existingControlId + '" selected>Merge</option>';
                actionSelect += '<option value="new">Create New</option>';
                actionSelect += '<option value="skip">Skip</option>';
            } else {
                actionSelect += '<option value="new" selected>Create New</option>';
                actionSelect += '<option value="skip">Skip</option>';
            }
            actionSelect += '</select>';
            
            const suggestionInfo = row.suggestedMatch ? 
                ' (' + row.suggestedMatch.existingControlName.substring(0, 30) + ')' : '';
            
            tr.innerHTML = `
                <td>${row.rowNumber}</td>
                <td title="${row.name}">${row.name.substring(0, 30)}${row.name.length > 30 ? '...' : ''}</td>
                <td title="${row.detail.replace(/\n/g, ' ')}">${row.detail.substring(0, 35).replace(/\n/g, ' ')}${row.detail.length > 35 ? '...' : ''}</td>
                <td title="${row.reference}">${row.reference}</td>
                <td title="${row.domain}">${row.domain}</td>
                <td>${matchBadge}${suggestionInfo ? '<br><small>' + suggestionInfo + '</small>' : ''}</td>
                <td>${actionSelect}</td>
                <td>${statusHtml}</td>
            `;
            
            previewTableBody.appendChild(tr);
        });
        
        document.querySelectorAll('.action-selector').forEach(select => {
            select.addEventListener('change', function() {
                const rowNumber = parseInt(this.dataset.row);
                const row = csvData.find(r => r.rowNumber === rowNumber);
                if (row) {
                    row.action = this.value;
                }
            });
        });
        
        document.getElementById('previewEmpty').style.display = 'none';
    }

    // Display preview
    function displayPreview() {
        const previewSection = document.getElementById('previewSection');
        const previewStats = document.getElementById('previewStats');
        const previewWarnings = document.getElementById('previewWarnings');
        const previewError = document.getElementById('previewError');
        
        previewSection.style.display = 'block';
        previewError.style.display = 'none';
        
        const validRows = csvData.filter(row => row.valid).length;
        const invalidRows = csvData.length - validRows;
        const uniqueDomains = [...new Set(csvData.map(row => row.domain))].filter(d => d).length;
        
        previewStats.innerHTML = `
            <strong>📊 Import Summary:</strong><br>
            Total rows: ${csvData.length} | 
            Valid rows: <span style="color: #28a745;">${validRows}</span> | 
            Invalid rows: <span style="color: #dc3545;">${invalidRows}</span> | 
            Unique domains: ${uniqueDomains}
        `;
        
        if (invalidRows > 0) {
            previewWarnings.style.display = 'block';
            previewWarnings.innerHTML = `
                <strong>⚠️ Warning:</strong> ${invalidRows} row(s) have validation errors and will be skipped during import.
                Please review the data below and fix any issues in your CSV file if needed.
            `;
        } else {
            previewWarnings.style.display = 'none';
        }
    }

    function showCatalogOptions() {
        document.getElementById('catalogSection').style.display = 'block';
    }

    function loadAvailableCatalogs() {
        console.log('Loading catalogs from /api/security-catalogs');
        fetch('/api/security-catalogs', {
            method: 'GET',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            }
        })
            .then(response => {
                if (!response.ok) {
                    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
                }
                return response.json();
            })
            .then(catalogs => {
                console.log('Received catalogs:', catalogs);
                availableCatalogs = catalogs || [];
                populateCatalogSelect();
                
                const existingCatalogRadio = document.getElementById('existingCatalog');
                const existingCatalogStatus = document.getElementById('existingCatalogStatus');
                
                if (availableCatalogs.length === 0) {
                    existingCatalogRadio.disabled = true;
                    existingCatalogStatus.textContent = 'No catalogs available';
                } else {
                    existingCatalogRadio.disabled = false;
                    existingCatalogStatus.textContent = 'Link imported controls to an existing security catalog';
                }
            })
            .catch(error => {
                console.error('Error loading catalogs:', error);
                document.getElementById('existingCatalog').disabled = true;
                document.getElementById('existingCatalogStatus').textContent = `Error loading catalogs: ${error.message}`;
            });
    }

    function populateCatalogSelect() {
        const select = document.getElementById('catalogSelect');
        select.innerHTML = '<option value="">-- Select a Security Catalog --</option>';
        
        availableCatalogs.forEach(catalog => {
            const option = document.createElement('option');
            option.value = catalog.id;
            option.textContent = `${catalog.name}${catalog.revision ? ' (' + catalog.revision + ')' : ''}`;
            select.appendChild(option);
        });
    }

    document.getElementsByName('catalogOption').forEach(radio => {
        radio.addEventListener('change', function() {
            const value = this.value;
            
            document.getElementById('existingCatalogFields').style.display = 
                value === 'existing' ? 'block' : 'none';
            document.getElementById('newCatalogFields').style.display = 
                value === 'new' ? 'block' : 'none';
                
            if (csvData.length > 0) {
                updateImportSection();
            }
        });
    });

    function updateImportSection() {
        const importSection = document.getElementById('importSection');
        const importSummary = document.getElementById('importSummary');
        const catalogOption = document.querySelector('input[name="catalogOption"]:checked').value;
        
        let validCount = csvData.filter(row => row.valid && row.action !== 'skip').length;
        let mergeCount = csvData.filter(row => row.valid && row.action.startsWith('merge:')).length;
        let newCount = validCount - mergeCount;
        
        let summaryText = `<p><strong>📋 Import Plan:</strong><br>`;
        summaryText += `Total: ${validCount} controls | `;
        summaryText += `<span style="color: #007bff;">Merge: ${mergeCount}</span> | `;
        summaryText += `<span style="color: #28a745;">New: ${newCount}</span></p>`;
        
        if (catalogOption === 'existing') {
            const selectedCatalog = document.getElementById('catalogSelect');
            if (selectedCatalog.value) {
                const catalogName = selectedCatalog.options[selectedCatalog.selectedIndex].text;
                summaryText += `<p>Controls will be added to existing catalog: <strong>${catalogName}</strong></p>`;
            } else {
                summaryText += `<p style="color: #dc3545;">Please select a catalog from the dropdown above.</p>`;
            }
        } else if (catalogOption === 'new') {
            const catalogName = document.getElementById('catalogName').value;
            if (catalogName) {
                summaryText += `<p>New catalog "<strong>${catalogName}</strong>" will be created and linked to all imported controls.</p>`;
            } else {
                summaryText += `<p style="color: #dc3545;">Please enter a name for the new catalog above.</p>`;
            }
        } else {
            summaryText += `<p>Controls will be imported without catalog assignment.</p>`;
        }
        
        importSummary.innerHTML = summaryText;
        importSection.style.display = 'block';
    }

    document.getElementById('catalogSelect').addEventListener('change', updateImportSection);
    document.getElementById('catalogName').addEventListener('input', updateImportSection);

    uploadForm.addEventListener('submit', function(event) {
        const catalogOption = document.querySelector('input[name="catalogOption"]:checked').value;
        
        if (catalogOption === 'existing') {
            const selectedCatalog = document.getElementById('catalogSelect').value;
            if (!selectedCatalog) {
                event.preventDefault();
                alert('Please select an existing catalog.');
                return;
            }
            document.getElementById('existingCatalogIdInput').value = selectedCatalog;
        } else if (catalogOption === 'new') {
            const catalogName = document.getElementById('catalogName').value.trim();
            if (!catalogName) {
                event.preventDefault();
                alert('Please enter a name for the new catalog.');
                return;
            }
            document.getElementById('catalogNameInput').value = catalogName;
            document.getElementById('catalogDescriptionInput').value = document.getElementById('catalogDescription').value;
            document.getElementById('catalogRevisionInput').value = document.getElementById('catalogRevision').value;
        }
        
        document.getElementById('catalogOptionInput').value = catalogOption;
        
        const mergeActions = {};
        csvData.forEach(row => {
            if (row.valid && row.action !== 'skip') {
                mergeActions[row.name] = row.action;
            }
        });
        document.getElementById('mergeActionsInput').value = JSON.stringify(mergeActions);
        
        const fileInput = document.createElement('input');
        fileInput.type = 'file';
        fileInput.name = 'file';
        fileInput.style.display = 'none';
        
        const dt = new DataTransfer();
        dt.items.add(csvFile.files[0]);
        fileInput.files = dt.files;
        
        uploadForm.appendChild(fileInput);
        
        const submitBtn = uploadForm.querySelector('button[type="submit"]');
        submitBtn.disabled = true;
        submitBtn.textContent = 'Importing...';
    });

    function resetForm() {
        csvData = [];
        analysisResults = {};
        analysisInProgress = {};
        csvFile.value = '';
        previewBtn.disabled = true;
        
        document.getElementById('previewSection').style.display = 'none';
        document.getElementById('catalogSection').style.display = 'none';
        document.getElementById('importSection').style.display = 'none';
        
        document.getElementById('previewError').style.display = 'none';
        document.getElementById('previewEmpty').style.display = 'none';
        
        document.getElementById('noCatalog').checked = true;
        document.getElementById('existingCatalogFields').style.display = 'none';
        document.getElementById('newCatalogFields').style.display = 'none';
        
        document.getElementById('catalogSelect').value = '';
        document.getElementById('catalogName').value = '';
        document.getElementById('catalogDescription').value = '';
        document.getElementById('catalogRevision').value = '';
    }
</script>
