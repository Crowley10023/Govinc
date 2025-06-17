package com.govinc.assessment;

import com.govinc.catalog.SecurityControl;
import com.govinc.catalog.SecurityControlDomain;
import com.govinc.maturity.MaturityAnswer;
import com.govinc.user.User;
import com.govinc.organization.OrgUnit;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.draw.LineSeparator;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AssessmentReporter {
    public byte[] createPdfReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document();
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            doc.open();

            // Add metadata
            doc.addTitle("Assessment Report");
            doc.addAuthor("GovInc Assessment System");
            doc.addSubject("Security Assessment Report");
            doc.addCreationDate();

            // Fonts and styles
            Font titleFont = new Font(Font.FontFamily.HELVETICA, 22, Font.BOLD);
            Font headerFont = new Font(Font.FontFamily.HELVETICA, 16, Font.BOLD);
            Font subHeaderFont = new Font(Font.FontFamily.HELVETICA, 13, Font.BOLDITALIC);
            Font regularFont = new Font(Font.FontFamily.HELVETICA, 12);
            Font boldFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);
            Font tableHeaderFont = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD, new BaseColor(255, 255, 255));
            Font tableCellFont = new Font(Font.FontFamily.HELVETICA, 10);

            // Title/cover section
            Paragraph title = new Paragraph("Assessment Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);
            doc.add(Chunk.NEWLINE);
            Paragraph meta = new Paragraph();
            meta.setAlignment(Element.ALIGN_CENTER);
            meta.add(new Chunk("Generated on: ", boldFont));
            meta.add(new Chunk(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), regularFont));
            doc.add(meta);
            doc.add(Chunk.NEWLINE);
            doc.add(new LineSeparator(1, 100, BaseColor.LIGHT_GRAY, Element.ALIGN_CENTER, 1));
            doc.add(Chunk.NEWLINE);

            // --- Chapter 1: General Info ---
            Paragraph chapter1 = new Paragraph("1. General Information", headerFont);
            doc.add(chapter1);
            Paragraph p1;
            p1 = new Paragraph();
            p1.add(new Chunk("Assessment Name: ", boldFont));
            p1.add(new Chunk(assessment.getName(), regularFont));
            doc.add(p1);
            p1 = new Paragraph();
            p1.add(new Chunk("Assessment ID: ", boldFont));
            p1.add(new Chunk(String.valueOf(assessment.getId()), regularFont));
            doc.add(p1);
            p1 = new Paragraph();
            p1.add(new Chunk("Date: ", boldFont));
            p1.add(new Chunk(assessment.getDate() != null ? assessment.getDate().toString() : "-", regularFont));
            doc.add(p1);
            p1 = new Paragraph();
            p1.add(new Chunk("Catalog: ", boldFont));
            p1.add(new Chunk((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"), regularFont));
            doc.add(p1);
            p1 = new Paragraph();
            p1.add(new Chunk("Completed On: ", boldFont));
            p1.add(new Chunk(details.getDate() != null ? details.getDate().toString() : "-", regularFont));
            doc.add(p1);
            doc.add(Chunk.NEWLINE);
            doc.add(new LineSeparator(0.5f, 100, BaseColor.LIGHT_GRAY, Element.ALIGN_CENTER, -4));
            doc.add(Chunk.NEWLINE);

            // --- Chapter 2: Users & Organization ---
            Paragraph chapter2 = new Paragraph("2. Users and Organization", headerFont);
            doc.add(chapter2);
            if (orgUnit != null) {
                p1 = new Paragraph();
                p1.add(new Chunk("Org Unit: ", boldFont));
                p1.add(new Chunk(orgUnit.getName(), regularFont));
                doc.add(p1);
            } else {
                doc.add(new Paragraph("Org Unit: -", boldFont));
            }
            if (!users.isEmpty()) {
                doc.add(new Paragraph("Users Participating:", boldFont));
                com.itextpdf.text.List userList = new com.itextpdf.text.List(com.itextpdf.text.List.UNORDERED);
                for (User u : users) {
                    userList.add(new com.itextpdf.text.ListItem(u.getName() + " <" + u.getEmail() + ">", regularFont));
                }
                doc.add(userList);
            } else {
                doc.add(new Paragraph("Users: -", regularFont));
            }
            doc.add(Chunk.NEWLINE);
            doc.add(new LineSeparator(0.5f, 100, BaseColor.LIGHT_GRAY, Element.ALIGN_CENTER, 0));
            doc.add(Chunk.NEWLINE);

            // Chapter 3 – Assessment Answers Table
            Paragraph chapter3 = new Paragraph("3. Control Answers", headerFont);
            doc.add(chapter3);
            doc.add(Chunk.NEWLINE);
            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[] { 2f, 2.5f, 3f, 3f, 1.5f });
            BaseColor thBg = new BaseColor(67, 100, 163);
            String[] ths = { "Domain", "Security Control", "Description", "Reference", "Answer" };
            for (String h : ths) {
                PdfPCell cell = new PdfPCell(new Phrase(h, tableHeaderFont));
                cell.setBackgroundColor(thBg);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setPadding(5);
                table.addCell(cell);
            }
            for (AssessmentControlAnswer aca : answers) {
                SecurityControl ctrl = aca.getSecurityControl();
                MaturityAnswer ans = aca.getMaturityAnswer();
                String domainName = ctrl != null && ctrl.getSecurityControlDomain() != null ? ctrl.getSecurityControlDomain().getName() : "-";
                String controlName = ctrl != null ? ctrl.getName() : "-";
                String controlDesc = ctrl != null ? (ctrl.getDetail() != null ? ctrl.getDetail() : "-") : "-";
                String question = ctrl != null ? (ctrl.getReference() != null ? ctrl.getReference() : "-") : "-";
                String answerText = ans != null ? ans.getAnswer() : "-";
                PdfPCell c1 = new PdfPCell(new Phrase(domainName, tableCellFont));
                PdfPCell c2 = new PdfPCell(new Phrase(controlName, tableCellFont));
                PdfPCell c3 = new PdfPCell(new Phrase(controlDesc, tableCellFont));
                PdfPCell c4 = new PdfPCell(new Phrase(question, tableCellFont));
                PdfPCell c5 = new PdfPCell(new Phrase(answerText, boldFont));
                c1.setPadding(4);
                c2.setPadding(4);
                c3.setPadding(4);
                c4.setPadding(4);
                c5.setPadding(4);
                c1.setVerticalAlignment(Element.ALIGN_TOP);
                c2.setVerticalAlignment(Element.ALIGN_TOP);
                c3.setVerticalAlignment(Element.ALIGN_TOP);
                c4.setVerticalAlignment(Element.ALIGN_TOP);
                c5.setVerticalAlignment(Element.ALIGN_TOP);
                table.addCell(c1);
                table.addCell(c2);
                table.addCell(c3);
                table.addCell(c4);
                table.addCell(c5);
            }
            doc.add(table);
            doc.add(Chunk.NEWLINE);
            doc.add(new LineSeparator(0.5f, 100, BaseColor.LIGHT_GRAY, Element.ALIGN_CENTER, 0));
            doc.add(Chunk.NEWLINE);

            // --- Chapter 4 – Summary ---
            Paragraph chapter4 = new Paragraph("4. Assessment Summary", headerFont);
            doc.add(chapter4);
            doc.add(Chunk.NEWLINE);
            Paragraph pSum = new Paragraph();
            pSum.add(new Chunk("Total Controls Answered: ", boldFont));
            pSum.add(new Chunk(String.valueOf(answers.size()), regularFont));
            doc.add(pSum);
            Map<String, Long> answerStats = answers.stream()
                .filter(aca -> aca.getMaturityAnswer() != null)
                .collect(java.util.stream.Collectors.groupingBy(aca -> aca.getMaturityAnswer().getAnswer(), java.util.stream.Collectors.counting()));
            if (!answerStats.isEmpty()) {
                Paragraph breakdown = new Paragraph("Breakdown by Answer Type: ", boldFont);
                breakdown.setSpacingBefore(10);
                doc.add(breakdown);
                for (Map.Entry<String, Long> entry : answerStats.entrySet()) {
                    doc.add(new Paragraph("  • " + entry.getKey() + ": " + entry.getValue(), regularFont));
                }
            }

            // Footer on every page: generated by + date
            String footerTxt = "Generated by GovInc Assessment System on: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            Phrase footerPhrase = new Phrase(footerTxt, new Font(Font.FontFamily.HELVETICA, 9, Font.ITALIC, BaseColor.GRAY));
            int totalPages = writer.getPageNumber();
            for (int i = 1; i <= totalPages; i++) {
                PdfContentByte cb = writer.getDirectContentUnder();
                Rectangle rect = doc.getPageSize();
                ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, footerPhrase, rect.getRight(30), rect.getBottom(22), 0);
            }

            doc.close();
            return baos.toByteArray();
        }
    }
}
