package com.govinc.service;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses Govinc SQL backup dumps (produced by DatabaseMigrationService) and
 * extracts a single assessment as an Excel report — without touching the live
 * database.
 *
 * <p>The dump format is one INSERT per line:
 * <pre>INSERT INTO `table` VALUES (val1, val2, ...);</pre>
 * preceded by the CREATE TABLE statement (which gives us the column order).
 */
@Service
public class BackupExtractService {

    /** Tables that are needed to render an assessment Excel. */
    private static final Set<String> WANTED_TABLES = Set.of(
            "assessments",
            "assessment_assessmentdetails",
            "assessment_details",
            "assessment_control_answer",
            "assessment_snapshot_controls",
            "security_catalogs",
            "security_catalog_controls",
            "security_controls",
            "security_control_domains",
            "maturity_answers",
            "org_unit"
    );

    /** Holds parsed rows + column ordering for a single table. */
    public static class TableData {
        public final List<String> columns = new ArrayList<>();
        public final List<Map<String, Object>> rows = new ArrayList<>();
    }

    /** A parsed backup file (only the tables we need are populated). */
    public static class BackupSnapshot {
        public final Map<String, TableData> tables = new LinkedHashMap<>();

        public TableData table(String name) {
            return tables.get(name);
        }

        public List<Map<String, Object>> rows(String name) {
            TableData td = tables.get(name);
            return td != null ? td.rows : Collections.emptyList();
        }
    }

    /* =================================================================== */
    /*  Public API                                                          */
    /* =================================================================== */

    public BackupSnapshot parseBackup(Path backupFile) throws IOException {
        BackupSnapshot snap = new BackupSnapshot();
        try (BufferedReader reader = Files.newBufferedReader(backupFile, StandardCharsets.UTF_8)) {
            String line;
            String pendingTable = null;
            List<String> pendingColumns = null;
            boolean insideCreate = false;
            while ((line = reader.readLine()) != null) {
                if (insideCreate) {
                    String trimmed = line.trim();
                    // Column definition starts with `colname`
                    if (trimmed.startsWith("`")) {
                        int end = trimmed.indexOf('`', 1);
                        if (end > 1) {
                            pendingColumns.add(trimmed.substring(1, end));
                        }
                    }
                    // End of CREATE TABLE
                    if (trimmed.startsWith(")")) {
                        if (pendingTable != null && WANTED_TABLES.contains(pendingTable)) {
                            TableData td = new TableData();
                            td.columns.addAll(pendingColumns);
                            snap.tables.put(pendingTable, td);
                        }
                        insideCreate = false;
                        pendingTable = null;
                        pendingColumns = null;
                    }
                    continue;
                }

                if (line.startsWith("CREATE TABLE ")) {
                    int s = line.indexOf('`');
                    int e = s >= 0 ? line.indexOf('`', s + 1) : -1;
                    if (s >= 0 && e > s) {
                        pendingTable = line.substring(s + 1, e);
                        pendingColumns = new ArrayList<>();
                        insideCreate = true;
                    }
                    continue;
                }

                if (line.startsWith("INSERT INTO ")) {
                    int s = line.indexOf('`');
                    int e = s >= 0 ? line.indexOf('`', s + 1) : -1;
                    if (s < 0 || e <= s) continue;
                    String table = line.substring(s + 1, e);
                    if (!WANTED_TABLES.contains(table)) continue;
                    TableData td = snap.tables.get(table);
                    if (td == null) continue;
                    int parenStart = line.indexOf('(', e);
                    int parenEnd = line.lastIndexOf(')');
                    if (parenStart < 0 || parenEnd <= parenStart) continue;
                    List<Object> values = parseValueList(line, parenStart + 1, parenEnd);
                    if (values.size() != td.columns.size()) continue;
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 0; i < td.columns.size(); i++) {
                        row.put(td.columns.get(i), values.get(i));
                    }
                    td.rows.add(row);
                }
            }
        }
        return snap;
    }

    /**
     * Returns a lightweight list (id, name, status, creationDate, catalogName,
     * orgUnitName) for every assessment found in the backup.
     */
    public List<Map<String, Object>> listAssessments(BackupSnapshot snap) {
        Map<Long, String> catalogNames = indexByIdString(snap.rows("security_catalogs"), "name");
        Map<Long, String> orgUnitNames = indexByIdString(snap.rows("org_unit"), "name");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> a : snap.rows("assessments")) {
            Long id = asLong(a.get("id"));
            if (id == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("name", asString(a.get("name")));
            m.put("status", asString(a.get("status")));
            m.put("creationDate", asString(a.get("creation_date")));
            Long catId = asLong(a.get("security_catalog_id"));
            m.put("catalogName", catId != null ? catalogNames.getOrDefault(catId, "") : "");
            Long ouId = asLong(a.get("orgunit_id"));
            m.put("orgUnitName", ouId != null ? orgUnitNames.getOrDefault(ouId, "") : "");
            out.add(m);
        }
        out.sort(Comparator.comparing(
                m -> {
                    Object n = m.get("name");
                    return n != null ? n.toString().toLowerCase() : "";
                }));
        return out;
    }

    /**
     * Build an Excel report for a single assessment extracted from a backup.
     */
    public byte[] extractAssessmentExcel(BackupSnapshot snap, long assessmentId) throws IOException {
        Map<String, Object> assessment = findById(snap.rows("assessments"), assessmentId);
        if (assessment == null) {
            throw new IllegalArgumentException("Assessment " + assessmentId + " not found in backup.");
        }

        // Resolve meta lookups
        Map<Long, Map<String, Object>> catalogs = indexById(snap.rows("security_catalogs"));
        Map<Long, Map<String, Object>> orgUnits = indexById(snap.rows("org_unit"));
        Map<Long, Map<String, Object>> controls = indexById(snap.rows("security_controls"));
        Map<Long, Map<String, Object>> domains  = indexById(snap.rows("security_control_domains"));
        Map<Long, Map<String, Object>> answers  = indexById(snap.rows("maturity_answers"));
        Map<Long, Map<String, Object>> details  = indexById(snap.rows("assessment_details"));

        String assessmentName = asString(assessment.get("name"));
        String status = asString(assessment.get("status"));
        String creationDate = asString(assessment.get("creation_date"));
        Long catalogId = asLong(assessment.get("security_catalog_id"));
        Long orgUnitId = asLong(assessment.get("orgunit_id"));
        String catalogName = (catalogId != null && catalogs.containsKey(catalogId))
                ? asString(catalogs.get(catalogId).get("name")) : "-";
        String orgUnitName = (orgUnitId != null && orgUnits.containsKey(orgUnitId))
                ? asString(orgUnits.get(orgUnitId).get("name")) : "-";

        // Pick controls: snapshot for CLOSED, else catalog membership
        Set<Long> controlIds = new HashSet<>();
        boolean closed = "CLOSED".equalsIgnoreCase(status);
        if (closed) {
            for (Map<String, Object> r : snap.rows("assessment_snapshot_controls")) {
                if (assessmentId == asLongPrim(r.get("assessment_id"))) {
                    Long cid = asLong(r.get("security_control_id"));
                    if (cid != null) controlIds.add(cid);
                }
            }
        }
        if (controlIds.isEmpty() && catalogId != null) {
            for (Map<String, Object> r : snap.rows("security_catalog_controls")) {
                if (catalogId.equals(asLong(r.get("security_catalog_id")))) {
                    Long cid = asLong(r.get("security_control_id"));
                    if (cid != null) controlIds.add(cid);
                }
            }
        }

        // Collect assessment_details IDs linked to this assessment
        Set<Long> detailIds = new HashSet<>();
        for (Map<String, Object> r : snap.rows("assessment_assessmentdetails")) {
            if (assessmentId == asLongPrim(r.get("assessment_id"))) {
                Long did = asLong(r.get("assessmentdetails_id"));
                if (did != null) detailIds.add(did);
            }
        }

        // Map controlId -> answer row (from assessment_control_answer where assessmentdetails_id in detailIds)
        Map<Long, Map<String, Object>> localAnswers = new HashMap<>();
        for (Map<String, Object> r : snap.rows("assessment_control_answer")) {
            Long did = asLong(r.get("assessmentdetails_id"));
            if (did == null || !detailIds.contains(did)) continue;
            Long cid = asLong(r.get("security_control_id"));
            if (cid != null) {
                localAnswers.put(cid, r);
            }
        }

        // Sort controls by domain name then reference
        List<Map<String, Object>> sortedControls = new ArrayList<>();
        for (Long cid : controlIds) {
            Map<String, Object> c = controls.get(cid);
            if (c != null) sortedControls.add(c);
        }
        sortedControls.sort(Comparator
                .comparing((Map<String, Object> c) -> {
                    Long did = asLong(c.get("security_control_domain_id"));
                    Map<String, Object> d = did != null ? domains.get(did) : null;
                    return d != null && d.get("name") != null ? d.get("name").toString() : "";
                }, Comparator.nullsLast(String::compareTo))
                .thenComparing(c -> c.get("reference") != null ? c.get("reference").toString() : "",
                        Comparator.nullsLast(String::compareTo)));

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            CellStyle metaLabelStyle = wb.createCellStyle();
            Font metaLabelFont = wb.createFont();
            metaLabelFont.setBold(true);
            metaLabelStyle.setFont(metaLabelFont);

            CellStyle wrapStyle = wb.createCellStyle();
            wrapStyle.setWrapText(true);
            wrapStyle.setVerticalAlignment(VerticalAlignment.TOP);

            Sheet sheet = wb.createSheet("Assessment");

            int rowIdx = 0;
            String[][] metaRows = {
                    { "Assessment",    assessmentName != null ? assessmentName : "" },
                    { "Org Unit",      orgUnitName },
                    { "Catalog",       catalogName },
                    { "Status",        status != null ? status : "-" },
                    { "Creation Date", creationDate != null ? creationDate : "-" },
                    { "Export Date",   LocalDate.now().toString() },
                    { "Source",        "Extracted from backup" }
            };
            for (String[] meta : metaRows) {
                Row r = sheet.createRow(rowIdx++);
                Cell c0 = r.createCell(0);
                c0.setCellValue(meta[0]);
                c0.setCellStyle(metaLabelStyle);
                r.createCell(1).setCellValue(meta[1]);
            }
            rowIdx++; // blank separator

            String[] headers = { "Domain", "Reference", "Control Name", "Answer", "Score (%)", "Source", "Comment" };
            Row headerRow = sheet.createRow(rowIdx++);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            for (Map<String, Object> sc : sortedControls) {
                Long ctrlId = asLong(sc.get("id"));
                Long did = asLong(sc.get("security_control_domain_id"));
                Map<String, Object> dom = did != null ? domains.get(did) : null;
                String domainName = dom != null && dom.get("name") != null ? dom.get("name").toString() : "";
                String reference = sc.get("reference") != null ? sc.get("reference").toString() : "";
                String controlName = sc.get("name") != null ? sc.get("name").toString() : "";

                String answerText = "";
                Integer score = null;
                String source = "Not answered";
                String comment = "";

                Map<String, Object> aca = ctrlId != null ? localAnswers.get(ctrlId) : null;
                if (aca != null) {
                    boolean isOverride = asBool(aca.get("is_override"));
                    Long maId = asLong(aca.get("maturity_answer_id"));
                    Map<String, Object> ma = maId != null ? answers.get(maId) : null;
                    if (ma != null) {
                        answerText = ma.get("answer") != null ? ma.get("answer").toString() : "";
                        Long rating = asLong(ma.get("rating"));
                        if (rating != null) score = rating.intValue();
                    }
                    source = isOverride ? "Override" : "Direct";
                    if (aca.get("comment") != null) comment = aca.get("comment").toString();
                }

                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(domainName);
                row.createCell(1).setCellValue(reference);
                row.createCell(2).setCellValue(controlName);
                row.createCell(3).setCellValue(answerText);
                if (score != null) {
                    row.createCell(4).setCellValue(score);
                } else {
                    row.createCell(4).setCellValue("");
                }
                row.createCell(5).setCellValue(source);
                Cell commentCell = row.createCell(6);
                commentCell.setCellValue(comment);
                if (!comment.isEmpty()) commentCell.setCellStyle(wrapStyle);
            }

            sheet.setColumnWidth(0, 6000);
            sheet.setColumnWidth(1, 3500);
            sheet.setColumnWidth(2, 12000);
            sheet.setColumnWidth(3, 6000);
            sheet.setColumnWidth(4, 3000);
            sheet.setColumnWidth(5, 8000);
            sheet.setColumnWidth(6, 14000);

            int dataStartRow = metaRows.length + 2;
            sheet.createFreezePane(0, dataStartRow);

            wb.write(out);
            return out.toByteArray();
        }
    }

    /* =================================================================== */
    /*  SQL VALUE LIST PARSER                                               */
    /* =================================================================== */

    /**
     * Parses the comma-separated value list between '(' and ')' in
     * {@code INSERT INTO `t` VALUES (...)}.
     */
    static List<Object> parseValueList(String line, int start, int end) {
        List<Object> out = new ArrayList<>();
        int i = start;
        while (i < end) {
            // skip whitespace
            while (i < end && Character.isWhitespace(line.charAt(i))) i++;
            if (i >= end) break;
            char c = line.charAt(i);
            if (c == ',') { i++; continue; }
            if (c == 'N' && i + 4 <= end && "NULL".equals(line.substring(i, i + 4))) {
                out.add(null);
                i += 4;
            } else if (c == '\'') {
                StringBuilder sb = new StringBuilder();
                i++;
                while (i < end) {
                    char ch = line.charAt(i);
                    if (ch == '\'') {
                        if (i + 1 < end && line.charAt(i + 1) == '\'') {
                            sb.append('\'');
                            i += 2;
                        } else {
                            i++;
                            break;
                        }
                    } else if (ch == '\\' && i + 1 < end) {
                        char next = line.charAt(i + 1);
                        switch (next) {
                            case 'n': sb.append('\n'); break;
                            case 'r': sb.append('\r'); break;
                            case 't': sb.append('\t'); break;
                            case '0': sb.append('\0'); break;
                            case '\\': sb.append('\\'); break;
                            case '\'': sb.append('\''); break;
                            case '"': sb.append('"'); break;
                            default: sb.append(next); break;
                        }
                        i += 2;
                    } else {
                        sb.append(ch);
                        i++;
                    }
                }
                out.add(sb.toString());
            } else if (c == 'X' && i + 1 < end && line.charAt(i + 1) == '\'') {
                // hex blob X'aabb' — keep as raw string (unused by wanted tables)
                int hexEnd = line.indexOf('\'', i + 2);
                if (hexEnd < 0 || hexEnd >= end) { out.add(null); break; }
                out.add(line.substring(i, hexEnd + 1));
                i = hexEnd + 1;
            } else {
                // bare token (number, true/false, etc.) up to next , or end
                int tokStart = i;
                while (i < end && line.charAt(i) != ',' && !Character.isWhitespace(line.charAt(i))) i++;
                String tok = line.substring(tokStart, i);
                out.add(parseBareToken(tok));
            }
        }
        return out;
    }

    private static Object parseBareToken(String tok) {
        if (tok.isEmpty()) return null;
        if ("NULL".equalsIgnoreCase(tok)) return null;
        // Try long first
        try {
            return Long.parseLong(tok);
        } catch (NumberFormatException ignored) {}
        try {
            return Double.parseDouble(tok);
        } catch (NumberFormatException ignored) {}
        return tok;
    }

    /* =================================================================== */
    /*  Helpers                                                             */
    /* =================================================================== */

    private static Map<Long, Map<String, Object>> indexById(List<Map<String, Object>> rows) {
        Map<Long, Map<String, Object>> map = new HashMap<>();
        for (Map<String, Object> r : rows) {
            Long id = asLong(r.get("id"));
            if (id != null) map.put(id, r);
        }
        return map;
    }

    private static Map<Long, String> indexByIdString(List<Map<String, Object>> rows, String field) {
        Map<Long, String> map = new HashMap<>();
        for (Map<String, Object> r : rows) {
            Long id = asLong(r.get("id"));
            if (id != null) {
                Object v = r.get(field);
                map.put(id, v != null ? v.toString() : "");
            }
        }
        return map;
    }

    private static Map<String, Object> findById(List<Map<String, Object>> rows, long id) {
        for (Map<String, Object> r : rows) {
            Long rid = asLong(r.get("id"));
            if (rid != null && rid == id) return r;
        }
        return null;
    }

    private static Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Long l) return l;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static long asLongPrim(Object v) {
        Long l = asLong(v);
        return l != null ? l : Long.MIN_VALUE;
    }

    private static String asString(Object v) {
        return v == null ? null : v.toString();
    }

    private static boolean asBool(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        String s = v.toString();
        return "1".equals(s) || "true".equalsIgnoreCase(s);
    }
}
