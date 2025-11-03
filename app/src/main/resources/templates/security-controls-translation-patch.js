// This patch should be integrated into security-controls.html script section
// It enhances the analysis to use translated content

// In the parseCsvData function, after creating rowData object, add:
/*
    // Parse translation columns (detail_de, name_de, detail_en, name_en, etc.)
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
*/

// In the analyzeControlsSimilarity function, within validControls.forEach, BEFORE creating controlDTO:
/*
    // Use translated content if available for analysis (prefer first language)
    let analysisName = control.name;
    let analysisDetail = control.detail;
    const translationLanguages = control.translations ? Object.keys(control.translations) : [];
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
*/

// CHANGE GLOBAL VARIABLE section from:
// let csvData = [];
// TO:
// let csvData = [];
// let translationsMap = {};
