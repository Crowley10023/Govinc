sessionId: f57ac576-075f-41bd-a2db-cf950c2656b3
date: '2026-02-24T20:33:43.807Z'
label: AssessmentReporter Style Mapping Fix
---
Summary for AI agents — context, decisions, changes, current state, and next tasks

Context
- Repo root: workspace with Java Spring app.
- Key files touched:
  - app/src/main/java/com/govinc/assessment/AssessmentReporterWord.java (primary file updated repeatedly; latest version applied)
  - app/src/main/java/com/govinc/util/OpenAIUtil.java (existing; used for style-matching micro-prompts)
- Goal: generate Word (docx) reports from an assessment, using templates and AI-generated JSON. Ensure generated content uses template styles, supports paragraphs and tables, supports inline visual tuning (color / bold), removes old template body content before insertion, and updates TOC/fields.

High-level decisions implemented
1. Template-first workflow
   - Load Word template (WordprocessingMLPackage).
   - Analyze template to extract:
     - available paragraph styles (ids and display names),
     - style definitions (StyleDefinitionsPart / Style objects),
     - template structure (existing paragraphs),
     - placeholder paragraphs containing string "{{REPORT_CONTENT}}".
   - Use that analysis to instruct AI which styles are available.

2. Style mapping & matching
   - Local resolution: attempt to map requested style (display name or id) to a template style id via TemplateAnalysis (resolveStyle).
   - If local resolution fails, call OpenAIUtil.askAI with a concise micro-prompt to select the closest available style. Method: resolveStylePreferAI(String requested, TemplateAnalysis ta).
   - Caching of these micro-prompts was discussed but not yet implemented.

3. Accept AI-provided paragraphs without headings
   - buildPrompt instructs the AI how to return sections with or without headings.
   - buildElementsFromAiResponse accepts section objects without heading and creates paragraphs accordingly.

4. Table support
   - AI may signal a table by contentStyle = "Table" and providing a "table" object.
   - createTableFromJson builds real docx4j Tbl from JSON input. createSummaryTable builds a fallback summary table from controls & answers.
   - Table cells can be strings or objects with fields: { "text": "...", "color": "#RRGGBB", "bold": true }.

5. Inline styling (visual tuning)
   - AI may add inline XML-like markup in paragraph content: <style color="#RRGGBB" bold="true">text</style>.
   - parseStyledRuns(...) extracts these spans into RunData (text, color, bold).
   - createParagraphWithStyle merges the template style's run properties (RPr) with inline attributes and creates runs accordingly.
   - Table cell creation (createTableCell) honors cell-level color and bold, using run-level color/bold (text color). Background shading (TcPr/Shd) was considered but fallback currently sets run text color.

6. Removing pre-existing template content
   - Before inserting generated content the logic clears body content and retains only section properties (SectPr) where possible. Generated content is then appended.

7. Table style detection
   - TemplateAnalysis.findPreferredTableColor() heuristically scans style names and style definitions for keywords (blue, light, table, grid) and for shd.fill values; returns a hex color (e.g., "#D9EAF7") if found. That color is applied to table cell text (best-effort).

8. TOC / fields update
   - Initial attempt to programmatically update fields via docx4j SettingsPart and FieldUpdater caused compile errors in this environment (missing types/methods).
   - That code was removed. Current behavior: log that the document should refresh fields on open; rely on Word to update TOC/fields when opened.
   - A programmatic FieldUpdater attempt was left out to avoid environment-specific compile problems.

What was changed (key locations / methods)
- File: app/src/main/java/com/govinc/assessment/AssessmentReporterWord.java
  - High-level flow (createWordReport): load template, analyze, build prompt, ask AI, parse JSON, build elements, remove original content, insert elements, attempt to apply preferred table color, save docx.
  - TemplateAnalyzer (inner class)
    - extractParagraphStyles uses StyleDefinitionsPart (type-safe) and stores:
      - Map<String,String> styleNameToId
      - Map<String,String> styleIdToName
      - Map<String,Style> styleIdToStyle
      - StyleDefinitionsPart styleDefinitionsPart
    - findPlaceholderParagraphs, extractTemplateStructure remain present.
  - TemplateAnalysis (inner class)
    - New method: findPreferredTableColor() — heuristics + inspect style shading via style.getPPr().getShd() and reflective attempt for tblPr shading.
  - resolveStylePreferAI(String requested, TemplateAnalysis ta)
    - Local resolution first, then micro-prompt to OpenAIUtil.askAI to select best matching style from list (returns single token).
  - buildPrompt: instructs AI to return:
    - title/titleStyle
    - sections[] elements; sections may omit heading
    - contentStyle "Table" + table object OR paragraphs with inline markup
    - inline markup convention: <style color="#RRGGBB" bold="true">text</style>
    - table cells may be objects with text/color/bold
  - buildElementsFromAiResponse: interprets sections and builds docx elements, resolves styles using resolveStylePreferAI.
  - parseStyledRuns & RunData: parse inline <style ...> tags.
  - createParagraphWithStyle: merges template run-level RPr and applies inline color/bold to runs.
  - createTableCell/createTableFromJson/createSummaryTable: support for colored/bold cell text.
  - Insertion logic updated to clear existing paragraphs (keep SectPr) and then add generated content.
  - Removed programmatic SettingsPart/FieldUpdater code (compile errors). Replaced with log and rely on Word to refresh fields on open.

- File: app/src/main/java/com/govinc/util/OpenAIUtil.java
  - Existing provider routing used by resolveStylePreferAI (askAI). It caches full prompts (existing behavior); no micro-prompt caching implemented.

Compile issues encountered and fixes applied
- Initial compile error: call to templateAnalysis.findPreferredTableColor() was missing — implemented findPreferredTableColor in TemplateAnalysis.
- SettingsPart and FieldUpdater code produced compile errors because SettingsPart type and MainDocumentPart.getSettingsPart() usage did not exist in the docx4j version used. That block was removed and replaced with logging and reliance on Word to update fields on open.

Pending / open tasks and recommendations (actionable)
1. Caching micro-prompts
   - Add caching for the small style-matching micro-prompts used in resolveStylePreferAI to avoid repeated AI calls per document/section. Location: use existing AIPromptCache in OpenAIUtil or introduce a local cache keyed by (requestedName, availableStylesHash).

2. Table cell background shading (preferred)
   - Current implementation sets run text color for table cells. Preferred UX is to set cell background shading (TcPr/Shd). Implement create/set TcPr and Shd for cells using docx4j (ensure correct JAXB objects). File: AssessmentReporterWord.createTableCell / apply shading in insertion post-processing.
   - Verify docx4j API for TcPr/Shd types and usage on version used in project.

3. Programmatic TOC update (optional)
   - If docx4j version supports org.docx4j.model.fields.FieldUpdater and SettingsPart, re-introduce programmatic update of fields (update(true)). Otherwise, leave as log and instruct Word users to open document and accept prompt to update fields.
   - To re-enable safely, guard with reflection & fallback and/or a configuration flag.

4. Thorough testing with real templates
   - Test cases:
     - Templates where style display names differ from IDs (e.g., "Heading 1" vs "heading1" vs localized names).
     - Templates that already contain content (existing headers/footers/placeholder).
     - Table insertion with AI-provided table objects and inline styling (cells as objects).
     - AI responses with inline markup <style ...> and nested/overlapping tags (note: nested tags not supported yet).
     - Verify TOC refresh in MS Word after opening.
   - Location: create unit/integration tests around AssessmentReporterWord.createWordReport.

5. Localization & fuzzy matching
   - Improve style matching heuristics:
     - Normalize localized display names (strip diacritics) before matching.
     - Optionally use fuzzy string matching locally before calling AI micro-prompt.
   - Implement caching of mapping results.

6. Logging / debug dump
   - Add an option to dump template style definitions and the style XML for debugging (e.g., to logs or to a file in uploads/). Useful to debug issues where style copying does not visually match.

7. Minor improvements / hardening
   - Preserve specific template content when desired rather than clearing all body text (configurable behavior).
   - Ensure insertion preserves header/footer parts.
   - Support more inline attributes: italic, underline, font name/size.
   - Add unit tests for parseStyledRuns and createTableFromJson.

State of the task / changeset
- The main changes for AssessmentReporterWord.java have been applied in-place in the workspace (file updated).
- Previously failing compile points were fixed:
  - findPreferredTableColor implemented.
  - SettingsPart/FieldUpdater removed to avoid docx4j version mismatch.
- The code now:
  - Parses template styles (StyleDefinitionsPart).
  - Uses AI (OpenAIUtil.askAI) to select closest style when local resolution fails.
  - Supports paragraphs without headings, tables from AI JSON, inline styling for color/bold, table cell color/bold (text color).
  - Clears prior template body paragraphs (keeps SectPr) before inserting new content.
  - Attempts to apply a light-blue table color if a table-related style is detected (via findPreferredTableColor). Current application sets run text color for cells.

Important file references you can continue from
- Main implementation / edits: app/src/main/java/com/govinc/assessment/AssessmentReporterWord.java
- AI routing & caching: app/src/main/java/com/govinc/util/OpenAIUtil.java
- Suggested test and debug helpers: consider adding to app/src/test or creating a debug endpoint under app/src/main/java/com/govinc/controller/.

Next immediate actions I can perform for you (pick one or more)
- Implement cell background shading (TcPr/Shd) for table cells instead of text color (requires docx4j APIs).
- Add caching for resolveStylePreferAI micro-prompts (use AIPromptCache or a lightweight in-memory cache).
- Re-introduce programmatic update of fields with safe reflection and feature flag.
- Add debug dumping of style definitions to logs or a file at uploads/.
- Add unit tests for the template analysis, run parsing, and table creation.

If you want me to continue, tell me which immediate action(s) to implement next.