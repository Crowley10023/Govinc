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

    private com.govinc.organization.OrgServiceAssessmentService orgServiceAssessmentService;

    public AssessmentReporter(com.govinc.organization.OrgServiceAssessmentService orgServiceAssessmentService) {
        this.orgServiceAssessmentService = orgServiceAssessmentService;
        // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    // Requires Apache POI on classpath.
    }

    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // Title
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        doc.createParagraph();
        
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary");
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain");

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1).setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1).setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTable.getRow(3).getCell(0).setText("Catalog:");
        infoTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // --- 2. Users & Organization ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTable.getRow(0).getCell(0).setText("Org Unit:");
        userTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        int idx=1;
        for(User u : users) {
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;
        }

        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("3. Assessment Summary");
        // Org Service assignments
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices().size()+1,2);
            svcTable.getRow(0).getCell(0).setText("Org Service");
            svcTable.getRow(0).getCell(1).setText("Description");
            int row=1;
            for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                svcTable.getRow(row).getCell(0).setText(orgService.getName());
                svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.getDescription():"-");
                row++;
            }
        }

        // --- 4. Domain Overview Table ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("4. Domain Overview Table");
        java.util.List<com.govinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.stream.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        domainTable.getRow(0).getCell(0).setText("Security Control Domain");
        domainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc=0,n=0;
            for(SecurityControl ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }
            }
            double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTable.getRow(drow).getCell(0).setText(domain);
            domainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;
        }

        // --- 5. Controls by Domain ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;
        for(String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermodel.XWPFParagraph dp = doc.createParagraph();
            dp.setSpacingBefore(220);
            dp.createRun().setText("5."+domainNum+" "+domain);

            // Controls Table: Title, Description, Reference, Answer, Source
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow(0).getCell(3).setText("Answer");
            t.getRow(0).getCell(4).setText("Answer Source");
            int cRow=1;
            for(SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
        return baos.toByteArray();
    }


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

            // -- TITLE PAGE --
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
            doc.newPage();

            // -- CONTENTS PAGE --
            Paragraph tocTitle = new Paragraph("Contents", headerFont);
            tocTitle.setAlignment(Element.ALIGN_CENTER);
            doc.add(tocTitle);
            doc.add(Chunk.NEWLINE);
            // Contents list (simple)
            com.itextpdf.text.List tocList = new com.itextpdf.text.List(com.itextpdf.text.List.ORDERED);
            tocList.add(new com.itextpdf.text.ListItem("General Information", regularFont));
            tocList.add(new com.itextpdf.text.ListItem("Users and Organization", regularFont));
            tocList.add(new com.itextpdf.text.ListItem("Assessment Summary", regularFont));
            tocList.add(new com.itextpdf.text.ListItem("Domain Overview Table", regularFont));
            tocList.add(new com.itextpdf.text.ListItem("Controls by Domain", regularFont));
            doc.add(tocList);
            doc.newPage();

            // -- MAIN REPORT STARTS ON PAGE 3 (SUMMARY FIRST, THEN OTHER CHAPTERS) --

            // --- 1. General Info ---
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

            // --- 2. Users & Organization ---
            doc.newPage();
            Paragraph chapter2 = new Paragraph("2. Users and Organization", headerFont);
            doc.add(chapter2);
            if (orgUnit != null) {
                p1 = new Paragraph();
                p1.add(new Chunk("Org Unit: ", boldFont));
                p1.add(new Chunk(orgUnit.getName(), regularFont));
                doc.add(p1);
            } else {
                doc.add(new Paragraph("Org Unit: -", boldFont));
                // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    
            if (!users.isEmpty()) {
                doc.add(new Paragraph("Users Participating:", boldFont));
                com.itextpdf.text.List userList = new com.itextpdf.text.List(com.itextpdf.text.List.UNORDERED);
                for (User u : users) {
                    userList.add(new com.itextpdf.text.ListItem(u.getName() + " <" + u.getEmail() + ">", regularFont));
                    // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    // Requires Apache POI on classpath.
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // Title
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        doc.createParagraph();
        
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary");
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain");

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1).setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1).setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTable.getRow(3).getCell(0).setText("Catalog:");
        infoTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // --- 2. Users & Organization ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTable.getRow(0).getCell(0).setText("Org Unit:");
        userTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        int idx=1;
        for(User u : users) {
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;
        }

        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("3. Assessment Summary");
        // Org Service assignments
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices().size()+1,2);
            svcTable.getRow(0).getCell(0).setText("Org Service");
            svcTable.getRow(0).getCell(1).setText("Description");
            int row=1;
            for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                svcTable.getRow(row).getCell(0).setText(orgService.getName());
                svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.getDescription():"-");
                row++;
            }
        }

        // --- 4. Domain Overview Table ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("4. Domain Overview Table");
        java.util.List<com.govinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.stream.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        domainTable.getRow(0).getCell(0).setText("Security Control Domain");
        domainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc=0,n=0;
            for(SecurityControl ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }
            }
            double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTable.getRow(drow).getCell(0).setText(domain);
            domainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;
        }

        // --- 5. Controls by Domain ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;
        for(String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermodel.XWPFParagraph dp = doc.createParagraph();
            dp.setSpacingBefore(220);
            dp.createRun().setText("5."+domainNum+" "+domain);

            // Controls Table: Title, Description, Reference, Answer, Source
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow(0).getCell(3).setText("Answer");
            t.getRow(0).getCell(4).setText("Answer Source");
            int cRow=1;
            for(SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
        return baos.toByteArray();
    }
}
                doc.add(userList);
            } else {
                doc.add(new Paragraph("Users: -", regularFont));
                // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    // Requires Apache POI on classpath.
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // Title
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        doc.createParagraph();
        
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary");
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain");

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1).setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1).setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTable.getRow(3).getCell(0).setText("Catalog:");
        infoTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // --- 2. Users & Organization ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTable.getRow(0).getCell(0).setText("Org Unit:");
        userTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        int idx=1;
        for(User u : users) {
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;
        }

        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("3. Assessment Summary");
        // Org Service assignments
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices().size()+1,2);
            svcTable.getRow(0).getCell(0).setText("Org Service");
            svcTable.getRow(0).getCell(1).setText("Description");
            int row=1;
            for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                svcTable.getRow(row).getCell(0).setText(orgService.getName());
                svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.getDescription():"-");
                row++;
            }
        }

        // --- 4. Domain Overview Table ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("4. Domain Overview Table");
        java.util.List<com.govinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.stream.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        domainTable.getRow(0).getCell(0).setText("Security Control Domain");
        domainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc=0,n=0;
            for(SecurityControl ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }
            }
            double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTable.getRow(drow).getCell(0).setText(domain);
            domainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;
        }

        // --- 5. Controls by Domain ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;
        for(String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermodel.XWPFParagraph dp = doc.createParagraph();
            dp.setSpacingBefore(220);
            dp.createRun().setText("5."+domainNum+" "+domain);

            // Controls Table: Title, Description, Reference, Answer, Source
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow(0).getCell(3).setText("Answer");
            t.getRow(0).getCell(4).setText("Answer Source");
            int cRow=1;
            for(SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
        return baos.toByteArray();
    }
}
            doc.add(Chunk.NEWLINE);
            doc.add(new LineSeparator(0.5f, 100, BaseColor.LIGHT_GRAY, Element.ALIGN_CENTER, 0));
            doc.add(Chunk.NEWLINE);

            // --- 3. Summary Table ---
            doc.newPage();
            Paragraph summarySection = new Paragraph("3. Assessment Summary", headerFont);
            summarySection.setSpacingBefore(10);
            doc.add(summarySection);
            doc.add(Chunk.NEWLINE);

            // Table: Org Services assigned (chapter 3a)
            if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
                Paragraph orgSvcHead = new Paragraph("3.1 Assigned Org Services", subHeaderFont);
                orgSvcHead.setSpacingBefore(5); doc.add(orgSvcHead); doc.add(Chunk.NEWLINE);
                PdfPTable svcTable = new PdfPTable(2);
                svcTable.setWidthPercentage(60);
                svcTable.setSpacingBefore(5);
                svcTable.setWidths(new float[]{2.5f, 4f});
                PdfPCell hn1 = new PdfPCell(new Phrase("Org Service", tableHeaderFont));
                PdfPCell hn2 = new PdfPCell(new Phrase("Description", tableHeaderFont));
                hn1.setBackgroundColor(new BaseColor(67,100,163)); hn1.setPadding(5);
                hn2.setBackgroundColor(new BaseColor(67,100,163)); hn2.setPadding(5);
                svcTable.addCell(hn1); svcTable.addCell(hn2);
                for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                    svcTable.addCell(new Phrase(orgService.getName(), tableCellFont));
                    svcTable.addCell(new Phrase(orgService.getDescription()!=null?orgService.getDescription():"-", tableCellFont));
                    // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    // Requires Apache POI on classpath.
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // Title
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        doc.createParagraph();
        
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary");
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain");

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1).setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1).setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTable.getRow(3).getCell(0).setText("Catalog:");
        infoTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // --- 2. Users & Organization ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTable.getRow(0).getCell(0).setText("Org Unit:");
        userTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        int idx=1;
        for(User u : users) {
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;
        }

        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("3. Assessment Summary");
        // Org Service assignments
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices().size()+1,2);
            svcTable.getRow(0).getCell(0).setText("Org Service");
            svcTable.getRow(0).getCell(1).setText("Description");
            int row=1;
            for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                svcTable.getRow(row).getCell(0).setText(orgService.getName());
                svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.getDescription():"-");
                row++;
            }
        }

        // --- 4. Domain Overview Table ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("4. Domain Overview Table");
        java.util.List<com.govinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.stream.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        domainTable.getRow(0).getCell(0).setText("Security Control Domain");
        domainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc=0,n=0;
            for(SecurityControl ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }
            }
            double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTable.getRow(drow).getCell(0).setText(domain);
            domainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;
        }

        // --- 5. Controls by Domain ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;
        for(String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermodel.XWPFParagraph dp = doc.createParagraph();
            dp.setSpacingBefore(220);
            dp.createRun().setText("5."+domainNum+" "+domain);

            // Controls Table: Title, Description, Reference, Answer, Source
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow(0).getCell(3).setText("Answer");
            t.getRow(0).getCell(4).setText("Answer Source");
            int cRow=1;
            for(SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
        return baos.toByteArray();
    }
}
                doc.add(svcTable);
                doc.add(Chunk.NEWLINE);
                // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    // Requires Apache POI on classpath.
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // Title
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        doc.createParagraph();
        
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary");
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain");

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1).setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1).setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTable.getRow(3).getCell(0).setText("Catalog:");
        infoTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // --- 2. Users & Organization ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTable.getRow(0).getCell(0).setText("Org Unit:");
        userTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        int idx=1;
        for(User u : users) {
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;
        }

        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("3. Assessment Summary");
        // Org Service assignments
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices().size()+1,2);
            svcTable.getRow(0).getCell(0).setText("Org Service");
            svcTable.getRow(0).getCell(1).setText("Description");
            int row=1;
            for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                svcTable.getRow(row).getCell(0).setText(orgService.getName());
                svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.getDescription():"-");
                row++;
            }
        }

        // --- 4. Domain Overview Table ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("4. Domain Overview Table");
        java.util.List<com.govinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.stream.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        domainTable.getRow(0).getCell(0).setText("Security Control Domain");
        domainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc=0,n=0;
            for(SecurityControl ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }
            }
            double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTable.getRow(drow).getCell(0).setText(domain);
            domainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;
        }

        // --- 5. Controls by Domain ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;
        for(String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermodel.XWPFParagraph dp = doc.createParagraph();
            dp.setSpacingBefore(220);
            dp.createRun().setText("5."+domainNum+" "+domain);

            // Controls Table: Title, Description, Reference, Answer, Source
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow(0).getCell(3).setText("Answer");
            t.getRow(0).getCell(4).setText("Answer Source");
            int cRow=1;
            for(SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
        return baos.toByteArray();
    }
}

            // Gather all control answers, including per-service
            java.util.List<SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
            java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
            java.util.List<OrgUnit> allUnits = new java.util.ArrayList<>();
            if(orgUnit!=null) allUnits.add(orgUnit);

            // Try to get org service controls and scores
            java.util.List<com.govinc.organization.OrgServiceAssessmentControl> orgServiceAnswers = new java.util.ArrayList<>();
            java.util.Map<Long, String> orgServiceUsed = new java.util.HashMap<>();
            if(assessment.getOrgServices()!=null) {
                for(com.govinc.organization.OrgService svc : assessment.getOrgServices()) {
                    if(svc==null) continue;
                    // Try to get possible OrgServiceAssessment and the controls
                    // This part may need adapting based on your actual business logic for retrieving associated assessments.
                    // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    // Requires Apache POI on classpath.
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // Title
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        doc.createParagraph();
        
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary");
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain");

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1).setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1).setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTable.getRow(3).getCell(0).setText("Catalog:");
        infoTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // --- 2. Users & Organization ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTable.getRow(0).getCell(0).setText("Org Unit:");
        userTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        int idx=1;
        for(User u : users) {
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;
        }

        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("3. Assessment Summary");
        // Org Service assignments
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices().size()+1,2);
            svcTable.getRow(0).getCell(0).setText("Org Service");
            svcTable.getRow(0).getCell(1).setText("Description");
            int row=1;
            for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                svcTable.getRow(row).getCell(0).setText(orgService.getName());
                svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.getDescription():"-");
                row++;
            }
        }

        

        // --- 4. Domain Overview Table ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("4. Domain Overview Table");
        java.util.List<com.govinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.stream.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        domainTable.getRow(0).getCell(0).setText("Security Control Domain");
        domainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc=0,n=0;
            for(SecurityControl ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }
            }
            double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTable.getRow(drow).getCell(0).setText(domain);
            domainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;
        }

        // --- 5. Controls by Domain ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;
        for(String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermodel.XWPFParagraph dp = doc.createParagraph();
            dp.setSpacingBefore(220);
            dp.createRun().setText("5."+domainNum+" "+domain);

            // Controls Table: Title, Description, Reference, Answer, Source
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow(0).getCell(3).setText("Answer");
            t.getRow(0).getCell(4).setText("Answer Source");
            int cRow=1;
            for(SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
        return baos.toByteArray();
    }
}
                // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    // Requires Apache POI on classpath.
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // Title
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        doc.createParagraph();
        
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary");
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain");

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1).setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1).setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTable.getRow(3).getCell(0).setText("Catalog:");
        infoTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // --- 2. Users & Organization ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTable.getRow(0).getCell(0).setText("Org Unit:");
        userTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        int idx=1;
        for(User u : users) {
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;
        }

        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("3. Assessment Summary");
        // Org Service assignments
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices().size()+1,2);
            svcTable.getRow(0).getCell(0).setText("Org Service");
            svcTable.getRow(0).getCell(1).setText("Description");
            int row=1;
            for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                svcTable.getRow(row).getCell(0).setText(orgService.getName());
                svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.getDescription():"-");
                row++;
            }
        }

        // --- 4. Domain Overview Table ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("4. Domain Overview Table");
        java.util.List<com.govinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.stream.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        domainTable.getRow(0).getCell(0).setText("Security Control Domain");
        domainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc=0,n=0;
            for(SecurityControl ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }
            }
            double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTable.getRow(drow).getCell(0).setText(domain);
            domainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;
        }

        // --- 5. Controls by Domain ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;
        for(String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermodel.XWPFParagraph dp = doc.createParagraph();
            dp.setSpacingBefore(220);
            dp.createRun().setText("5."+domainNum+" "+domain);

            // Controls Table: Title, Description, Reference, Answer, Source
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow(0).getCell(3).setText("Answer");
            t.getRow(0).getCell(4).setText("Answer Source");
            int cRow=1;
            for(SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
        return baos.toByteArray();
    }
}

            int totalScore = 0;
            int numAnswered = 0;
            java.util.Map<Long,Integer> scoresByControl = new java.util.HashMap<>();
            java.util.Map<Long,String> labelByControl = new java.util.HashMap<>(); // For org service answers

            for(SecurityControl ctrl : allControls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    AssessmentControlAnswer aca = answerMap.get(ctrl.getId());
                    int score = aca.getScore();
                    scoresByControl.put(ctrl.getId(), score);
                    totalScore += score;
                    numAnswered++;
                    labelByControl.put(ctrl.getId(), "");
                } else {
                    // Could check orgservice assessments here + fill values if present
                    // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    // Requires Apache POI on classpath.
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // Title
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        doc.createParagraph();
        
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary");
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain");

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1).setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1).setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTable.getRow(3).getCell(0).setText("Catalog:");
        infoTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // --- 2. Users & Organization ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTable.getRow(0).getCell(0).setText("Org Unit:");
        userTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        int idx=1;
        for(User u : users) {
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;
        }

        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("3. Assessment Summary");
        // Org Service assignments
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices().size()+1,2);
            svcTable.getRow(0).getCell(0).setText("Org Service");
            svcTable.getRow(0).getCell(1).setText("Description");
            int row=1;
            for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                svcTable.getRow(row).getCell(0).setText(orgService.getName());
                svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.getDescription():"-");
                row++;
            }
        }

        // --- 4. Domain Overview Table ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("4. Domain Overview Table");
        java.util.List<com.govinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.stream.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        domainTable.getRow(0).getCell(0).setText("Security Control Domain");
        domainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc=0,n=0;
            for(SecurityControl ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }
            }
            double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTable.getRow(drow).getCell(0).setText(domain);
            domainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;
        }

        // --- 5. Controls by Domain ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;
        for(String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermodel.XWPFParagraph dp = doc.createParagraph();
            dp.setSpacingBefore(220);
            dp.createRun().setText("5."+domainNum+" "+domain);

            // Controls Table: Title, Description, Reference, Answer, Source
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow(0).getCell(3).setText("Answer");
            t.getRow(0).getCell(4).setText("Answer Source");
            int cRow=1;
            for(SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
        return baos.toByteArray();
    }
}
                // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    // Requires Apache POI on classpath.
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // Title
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        doc.createParagraph();
        
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary");
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain");

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1).setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1).setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTable.getRow(3).getCell(0).setText("Catalog:");
        infoTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // --- 2. Users & Organization ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTable.getRow(0).getCell(0).setText("Org Unit:");
        userTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        int idx=1;
        for(User u : users) {
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;
        }

        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("3. Assessment Summary");
        // Org Service assignments
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices().size()+1,2);
            svcTable.getRow(0).getCell(0).setText("Org Service");
            svcTable.getRow(0).getCell(1).setText("Description");
            int row=1;
            for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                svcTable.getRow(row).getCell(0).setText(orgService.getName());
                svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.getDescription():"-");
                row++;
            }
        }

        // --- 4. Domain Overview Table ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("4. Domain Overview Table");
        java.util.List<com.govinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.stream.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        domainTable.getRow(0).getCell(0).setText("Security Control Domain");
        domainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc=0,n=0;
            for(SecurityControl ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }
            }
            double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTable.getRow(drow).getCell(0).setText(domain);
            domainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;
        }

        // --- 5. Controls by Domain ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;
        for(String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermodel.XWPFParagraph dp = doc.createParagraph();
            dp.setSpacingBefore(220);
            dp.createRun().setText("5."+domainNum+" "+domain);

            // Controls Table: Title, Description, Reference, Answer, Source
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow(0).getCell(3).setText("Answer");
            t.getRow(0).getCell(4).setText("Answer Source");
            int cRow=1;
            for(SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
        return baos.toByteArray();
    }
}
            double avgScore = numAnswered > 0 ? (totalScore/(double)numAnswered) : 0.0;

            PdfPTable summaryTable = new PdfPTable(3);
            summaryTable.setWidthPercentage(70);
            summaryTable.setSpacingBefore(10);
            summaryTable.setWidths(new float[] {2f,2f,3f});
            PdfPCell h1 = new PdfPCell(new Phrase("# Security Controls", tableHeaderFont));
            PdfPCell h2 = new PdfPCell(new Phrase("Average Score (%)", tableHeaderFont));
            PdfPCell h3 = new PdfPCell(new Phrase("Org Unit", tableHeaderFont));
            h1.setBackgroundColor(new BaseColor(67,100,163));
            h2.setBackgroundColor(new BaseColor(67,100,163));
            h3.setBackgroundColor(new BaseColor(67,100,163));
            h1.setPadding(5); h2.setPadding(5); h3.setPadding(5);
            summaryTable.addCell(h1);
            summaryTable.addCell(h2);
            summaryTable.addCell(h3);
            summaryTable.addCell(new Phrase(String.valueOf(allControls.size()), tableCellFont));
            summaryTable.addCell(new Phrase(String.format("%.1f", avgScore), tableCellFont));
            summaryTable.addCell(new Phrase(orgUnit != null ? orgUnit.getName() : "-", tableCellFont));
            doc.add(summaryTable);

            doc.add(Chunk.NEWLINE);

            // --- 4. Control Domain Overview Table ---
            doc.newPage();
            Paragraph ovwSection = new Paragraph("4. Domain Overview Table", headerFont);
            ovwSection.setSpacingBefore(15);
            doc.add(ovwSection);
            doc.add(Chunk.NEWLINE);
            Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream()
                .collect(Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
            PdfPTable overviewTable = new PdfPTable(2);
            overviewTable.setWidthPercentage(60);
            overviewTable.setSpacingBefore(7);
            overviewTable.setWidths(new float[]{2.5f,1.5f});
            PdfPCell dh = new PdfPCell(new Phrase("Security Control Domain", tableHeaderFont));
            PdfPCell sh = new PdfPCell(new Phrase("Score (%)", tableHeaderFont));
            dh.setBackgroundColor(new BaseColor(67,100,163));
            sh.setBackgroundColor(new BaseColor(67,100,163));
            dh.setPadding(5); sh.setPadding(5);
            overviewTable.addCell(dh);
            overviewTable.addCell(sh);
            for(String domain : controlsPerDomain.keySet()) {
                java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
                int sc = 0, n = 0;
                for(SecurityControl ctrl : domainCtrls) {
                    if(scoresByControl.containsKey(ctrl.getId())) { sc += scoresByControl.get(ctrl.getId()); n++;     // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    // Requires Apache POI on classpath.
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // Title
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        doc.createParagraph();
        
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary");
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain");

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1).setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1).setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTable.getRow(3).getCell(0).setText("Catalog:");
        infoTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // --- 2. Users & Organization ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTable.getRow(0).getCell(0).setText("Org Unit:");
        userTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        int idx=1;
        for(User u : users) {
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;
        }

        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("3. Assessment Summary");
        // Org Service assignments
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices().size()+1,2);
            svcTable.getRow(0).getCell(0).setText("Org Service");
            svcTable.getRow(0).getCell(1).setText("Description");
            int row=1;
            for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                svcTable.getRow(row).getCell(0).setText(orgService.getName());
                svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.getDescription():"-");
                row++;
            }
        }

        // --- 4. Domain Overview Table ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("4. Domain Overview Table");
        java.util.List<com.govinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.stream.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        domainTable.getRow(0).getCell(0).setText("Security Control Domain");
        domainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc=0,n=0;
            for(SecurityControl ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }
            }
            double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTable.getRow(drow).getCell(0).setText(domain);
            domainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;
        }

        // --- 5. Controls by Domain ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;
        for(String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermodel.XWPFParagraph dp = doc.createParagraph();
            dp.setSpacingBefore(220);
            dp.createRun().setText("5."+domainNum+" "+domain);

            // Controls Table: Title, Description, Reference, Answer, Source
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow(0).getCell(3).setText("Answer");
            t.getRow(0).getCell(4).setText("Answer Source");
            int cRow=1;
            for(SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
        return baos.toByteArray();
    }
}
                    // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    // Requires Apache POI on classpath.
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // Title
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        doc.createParagraph();
        
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary");
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain");

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1).setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1).setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTable.getRow(3).getCell(0).setText("Catalog:");
        infoTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // --- 2. Users & Organization ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTable.getRow(0).getCell(0).setText("Org Unit:");
        userTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        int idx=1;
        for(User u : users) {
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;
        }

        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("3. Assessment Summary");
        // Org Service assignments
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices().size()+1,2);
            svcTable.getRow(0).getCell(0).setText("Org Service");
            svcTable.getRow(0).getCell(1).setText("Description");
            int row=1;
            for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                svcTable.getRow(row).getCell(0).setText(orgService.getName());
                svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.getDescription():"-");
                row++;
            }
        }

        // --- 4. Domain Overview Table ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("4. Domain Overview Table");
        java.util.List<com.govinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.stream.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        domainTable.getRow(0).getCell(0).setText("Security Control Domain");
        domainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc=0,n=0;
            for(SecurityControl ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }
            }
            double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTable.getRow(drow).getCell(0).setText(domain);
            domainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;
        }

        // --- 5. Controls by Domain ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;
        for(String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermodel.XWPFParagraph dp = doc.createParagraph();
            dp.setSpacingBefore(220);
            dp.createRun().setText("5."+domainNum+" "+domain);

            // Controls Table: Title, Description, Reference, Answer, Source
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow(0).getCell(3).setText("Answer");
            t.getRow(0).getCell(4).setText("Answer Source");
            int cRow=1;
            for(SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
        return baos.toByteArray();
    }
}
                double perc = n>0 ? (sc/(double)n) : 0.0;
                overviewTable.addCell(new Phrase(domain, tableCellFont));
                overviewTable.addCell(new Phrase(String.format("%.1f", perc), boldFont));
                // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    // Requires Apache POI on classpath.
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // Title
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        doc.createParagraph();
        
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary");
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain");

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1).setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1).setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTable.getRow(3).getCell(0).setText("Catalog:");
        infoTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // --- 2. Users & Organization ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTable.getRow(0).getCell(0).setText("Org Unit:");
        userTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        int idx=1;
        for(User u : users) {
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;
        }

        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("3. Assessment Summary");
        // Org Service assignments
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices().size()+1,2);
            svcTable.getRow(0).getCell(0).setText("Org Service");
            svcTable.getRow(0).getCell(1).setText("Description");
            int row=1;
            for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                svcTable.getRow(row).getCell(0).setText(orgService.getName());
                svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.getDescription():"-");
                row++;
            }
        }

        // --- 4. Domain Overview Table ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("4. Domain Overview Table");
        java.util.List<com.govinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.stream.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        domainTable.getRow(0).getCell(0).setText("Security Control Domain");
        domainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc=0,n=0;
            for(SecurityControl ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }
            }
            double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTable.getRow(drow).getCell(0).setText(domain);
            domainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;
        }

        // --- 5. Controls by Domain ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;
        for(String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermodel.XWPFParagraph dp = doc.createParagraph();
            dp.setSpacingBefore(220);
            dp.createRun().setText("5."+domainNum+" "+domain);

            // Controls Table: Title, Description, Reference, Answer, Source
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow(0).getCell(3).setText("Answer");
            t.getRow(0).getCell(4).setText("Answer Source");
            int cRow=1;
            for(SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
        return baos.toByteArray();
    }
}
            doc.add(overviewTable);
            doc.add(Chunk.NEWLINE);
            doc.add(new LineSeparator(0.8f, 100, BaseColor.LIGHT_GRAY, Element.ALIGN_CENTER, 0));
            doc.add(Chunk.NEWLINE);

            // --- 5. Controls by Domain (detailed) ---
            doc.newPage();
            Paragraph chapter5 = new Paragraph("5. Controls by Domain", headerFont);
            chapter5.setSpacingBefore(15);
            doc.add(chapter5);
            doc.add(Chunk.NEWLINE);

            // Prepare map for org service answers, to detect source
            java.util.Map<Long, String> orgServiceControlSources = new java.util.HashMap<>();
            if(assessment.getOrgServices()!=null) {
                for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                    if(orgService==null) continue;
                    // Find linked OrgServiceAssessment if available
                    if(orgService.getOrgUnits()!=null) {
                        // Placeholder: in reality, you'd fetch the corresponding OrgServiceAssessment for this orgService/assessment
                        // and iterate its controls to determine which controls have a percent/answer;
                        // Here we simply lookup by model as your DB logic allows.
                        // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    // Requires Apache POI on classpath.
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // Title
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        doc.createParagraph();
        
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary");
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain");

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1).setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1).setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTable.getRow(3).getCell(0).setText("Catalog:");
        infoTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // --- 2. Users & Organization ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTable.getRow(0).getCell(0).setText("Org Unit:");
        userTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        int idx=1;
        for(User u : users) {
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;
        }

        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("3. Assessment Summary");
        // Org Service assignments
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices().size()+1,2);
            svcTable.getRow(0).getCell(0).setText("Org Service");
            svcTable.getRow(0).getCell(1).setText("Description");
            int row=1;
            for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                svcTable.getRow(row).getCell(0).setText(orgService.getName());
                svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.getDescription():"-");
                row++;
            }
        }

        // --- 4. Domain Overview Table ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("4. Domain Overview Table");
        java.util.List<com.govinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.stream.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        domainTable.getRow(0).getCell(0).setText("Security Control Domain");
        domainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc=0,n=0;
            for(SecurityControl ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }
            }
            double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTable.getRow(drow).getCell(0).setText(domain);
            domainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;
        }

        // --- 5. Controls by Domain ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;
        for(String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermodel.XWPFParagraph dp = doc.createParagraph();
            dp.setSpacingBefore(220);
            dp.createRun().setText("5."+domainNum+" "+domain);

            // Controls Table: Title, Description, Reference, Answer, Source
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow(0).getCell(3).setText("Answer");
            t.getRow(0).getCell(4).setText("Answer Source");
            int cRow=1;
            for(SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
        return baos.toByteArray();
    }
}
                    // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    // Requires Apache POI on classpath.
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // Title
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        doc.createParagraph();
        
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary");
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain");

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1).setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1).setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTable.getRow(3).getCell(0).setText("Catalog:");
        infoTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // --- 2. Users & Organization ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTable.getRow(0).getCell(0).setText("Org Unit:");
        userTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        int idx=1;
        for(User u : users) {
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;
        }

        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("3. Assessment Summary");
        // Org Service assignments
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices().size()+1,2);
            svcTable.getRow(0).getCell(0).setText("Org Service");
            svcTable.getRow(0).getCell(1).setText("Description");
            int row=1;
            for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                svcTable.getRow(row).getCell(0).setText(orgService.getName());
                svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.getDescription():"-");
                row++;
            }
        }

        // --- 4. Domain Overview Table ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("4. Domain Overview Table");
        java.util.List<com.govinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.stream.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        domainTable.getRow(0).getCell(0).setText("Security Control Domain");
        domainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc=0,n=0;
            for(SecurityControl ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }
            }
            double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTable.getRow(drow).getCell(0).setText(domain);
            domainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;
        }

        // --- 5. Controls by Domain ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;
        for(String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermodel.XWPFParagraph dp = doc.createParagraph();
            dp.setSpacingBefore(220);
            dp.createRun().setText("5."+domainNum+" "+domain);

            // Controls Table: Title, Description, Reference, Answer, Source
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow(0).getCell(3).setText("Answer");
            t.getRow(0).getCell(4).setText("Answer Source");
            int cRow=1;
            for(SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
        return baos.toByteArray();
    }
}
                // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    // Requires Apache POI on classpath.
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // Title
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        doc.createParagraph();
        
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary");
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain");

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1).setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1).setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTable.getRow(3).getCell(0).setText("Catalog:");
        infoTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // --- 2. Users & Organization ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTable.getRow(0).getCell(0).setText("Org Unit:");
        userTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        int idx=1;
        for(User u : users) {
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;
        }

        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("3. Assessment Summary");
        // Org Service assignments
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices().size()+1,2);
            svcTable.getRow(0).getCell(0).setText("Org Service");
            svcTable.getRow(0).getCell(1).setText("Description");
            int row=1;
            for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                svcTable.getRow(row).getCell(0).setText(orgService.getName());
                svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.getDescription():"-");
                row++;
            }
        }

        // --- 4. Domain Overview Table ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("4. Domain Overview Table");
        java.util.List<com.govinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.stream.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        domainTable.getRow(0).getCell(0).setText("Security Control Domain");
        domainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc=0,n=0;
            for(SecurityControl ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }
            }
            double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTable.getRow(drow).getCell(0).setText(domain);
            domainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;
        }

        // --- 5. Controls by Domain ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;
        for(String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermodel.XWPFParagraph dp = doc.createParagraph();
            dp.setSpacingBefore(220);
            dp.createRun().setText("5."+domainNum+" "+domain);

            // Controls Table: Title, Description, Reference, Answer, Source
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow(0).getCell(3).setText("Answer");
            t.getRow(0).getCell(4).setText("Answer Source");
            int cRow=1;
            for(SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
        return baos.toByteArray();
    }
}

            java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
            java.util.Collections.sort(domainOrder);
            int domainNum = 1;
            for(String domain : domainOrder) {
                java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);

                Paragraph domainP = new Paragraph("5." + domainNum + " " + domain, subHeaderFont);
                domainP.setSpacingBefore(13); domainP.setSpacingAfter(2);
                doc.add(domainP);
                // Add domain description if needed

                PdfPTable t = new PdfPTable(5);
                t.setWidthPercentage(100);
                t.setSpacingBefore(6);
                t.setWidths(new float[]{2.5f,4f,3.5f,2.1f,2.4f});
                PdfPCell[] tHeads = new PdfPCell[] {
                    new PdfPCell(new Phrase("Title", tableHeaderFont)),
                    new PdfPCell(new Phrase("Description", tableHeaderFont)),
                    new PdfPCell(new Phrase("Reference", tableHeaderFont)),
                    new PdfPCell(new Phrase("Answer", tableHeaderFont)),
                    new PdfPCell(new Phrase("Answer Source", tableHeaderFont)),
                };
                for(PdfPCell cell : tHeads) {
                    cell.setBackgroundColor(new BaseColor(67,100,163));
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cell.setPadding(5);
                    t.addCell(cell);
                    // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    // Requires Apache POI on classpath.
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // Title
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        doc.createParagraph();
        
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary");
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain");

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1).setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1).setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTable.getRow(3).getCell(0).setText("Catalog:");
        infoTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // --- 2. Users & Organization ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTable.getRow(0).getCell(0).setText("Org Unit:");
        userTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        int idx=1;
        for(User u : users) {
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;
        }

        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("3. Assessment Summary");
        // Org Service assignments
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices().size()+1,2);
            svcTable.getRow(0).getCell(0).setText("Org Service");
            svcTable.getRow(0).getCell(1).setText("Description");
            int row=1;
            for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                svcTable.getRow(row).getCell(0).setText(orgService.getName());
                svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.getDescription():"-");
                row++;
            }
        }

        // --- 4. Domain Overview Table ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("4. Domain Overview Table");
        java.util.List<com.govinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.stream.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        domainTable.getRow(0).getCell(0).setText("Security Control Domain");
        domainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc=0,n=0;
            for(SecurityControl ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }
            }
            double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTable.getRow(drow).getCell(0).setText(domain);
            domainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;
        }

        // --- 5. Controls by Domain ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;
        for(String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermodel.XWPFParagraph dp = doc.createParagraph();
            dp.setSpacingBefore(220);
            dp.createRun().setText("5."+domainNum+" "+domain);

            // Controls Table: Title, Description, Reference, Answer, Source
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow(0).getCell(3).setText("Answer");
            t.getRow(0).getCell(4).setText("Answer Source");
            int cRow=1;
            for(SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
        return baos.toByteArray();
    }
}
                for(SecurityControl ctrl : ctrlList) {
                    String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                    String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                    String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                    String answ = "-";
                    String src = "-";
                    boolean foundServiceAnswer = false;
                    // If org service answer exists, use the service name as source and maturity level closest to percent
                    // --- Start check for possible Org Service-sourced answer for this control ---
                    answ = "-";
                    src = "-";
                    foundServiceAnswer = false;

                    // Only use an org service answer if this control is actually covered (mapped) by this org service AND is applicable
if (assessment.getOrgServices() != null) {
    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
        if (osa != null && osa.getControls() != null) {
            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                    Integer osPercent = osac.getPercent();
                    // percent is always set (not Integer) in this model, but check logic preserved if model changes
                    if (osPercent != null) {
                        // Find the closest maturity answer
                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet =
                            assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream()
                            .min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent)))
                            .orElse(null);
                        if (closest != null) {
                            answ = closest.getAnswer();
                        } else {
                            answ = String.valueOf(osPercent) + "%";
                            // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    // Requires Apache POI on classpath.
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // Title
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        doc.createParagraph();
        
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary");
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain");

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1).setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1).setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTable.getRow(3).getCell(0).setText("Catalog:");
        infoTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // --- 2. Users & Organization ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTable.getRow(0).getCell(0).setText("Org Unit:");
        userTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        int idx=1;
        for(User u : users) {
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;
        }

        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("3. Assessment Summary");
        // Org Service assignments
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices().size()+1,2);
            svcTable.getRow(0).getCell(0).setText("Org Service");
            svcTable.getRow(0).getCell(1).setText("Description");
            int row=1;
            for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                svcTable.getRow(row).getCell(0).setText(orgService.getName());
                svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.getDescription():"-");
                row++;
            }
        }

        // --- 4. Domain Overview Table ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("4. Domain Overview Table");
        java.util.List<com.govinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.stream.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        domainTable.getRow(0).getCell(0).setText("Security Control Domain");
        domainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc=0,n=0;
            for(SecurityControl ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }
            }
            double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTable.getRow(drow).getCell(0).setText(domain);
            domainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;
        }

        // --- 5. Controls by Domain ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;
        for(String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermodel.XWPFParagraph dp = doc.createParagraph();
            dp.setSpacingBefore(220);
            dp.createRun().setText("5."+domainNum+" "+domain);

            // Controls Table: Title, Description, Reference, Answer, Source
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow(0).getCell(3).setText("Answer");
            t.getRow(0).getCell(4).setText("Answer Source");
            int cRow=1;
            for(SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
        return baos.toByteArray();
    }
}
                        src = orgService.getName();
                        foundServiceAnswer = true;
                        break;
                        // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    // Requires Apache POI on classpath.
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // Title
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        doc.createParagraph();
        
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary");
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain");

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1).setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1).setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTable.getRow(3).getCell(0).setText("Catalog:");
        infoTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // --- 2. Users & Organization ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTable.getRow(0).getCell(0).setText("Org Unit:");
        userTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        int idx=1;
        for(User u : users) {
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;
        }

        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("3. Assessment Summary");
        // Org Service assignments
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices().size()+1,2);
            svcTable.getRow(0).getCell(0).setText("Org Service");
            svcTable.getRow(0).getCell(1).setText("Description");
            int row=1;
            for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                svcTable.getRow(row).getCell(0).setText(orgService.getName());
                svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.getDescription():"-");
                row++;
            }
        }

        // --- 4. Domain Overview Table ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("4. Domain Overview Table");
        java.util.List<com.govinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.stream.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        domainTable.getRow(0).getCell(0).setText("Security Control Domain");
        domainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc=0,n=0;
            for(SecurityControl ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }
            }
            double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTable.getRow(drow).getCell(0).setText(domain);
            domainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;
        }

        // --- 5. Controls by Domain ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;
        for(String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermodel.XWPFParagraph dp = doc.createParagraph();
            dp.setSpacingBefore(220);
            dp.createRun().setText("5."+domainNum+" "+domain);

            // Controls Table: Title, Description, Reference, Answer, Source
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow(0).getCell(3).setText("Answer");
            t.getRow(0).getCell(4).setText("Answer Source");
            int cRow=1;
            for(SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
        return baos.toByteArray();
    }
}
                    // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    // Requires Apache POI on classpath.
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // Title
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        doc.createParagraph();
        
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary");
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain");

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1).setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1).setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTable.getRow(3).getCell(0).setText("Catalog:");
        infoTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // --- 2. Users & Organization ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTable.getRow(0).getCell(0).setText("Org Unit:");
        userTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        int idx=1;
        for(User u : users) {
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;
        }

        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("3. Assessment Summary");
        // Org Service assignments
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices().size()+1,2);
            svcTable.getRow(0).getCell(0).setText("Org Service");
            svcTable.getRow(0).getCell(1).setText("Description");
            int row=1;
            for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                svcTable.getRow(row).getCell(0).setText(orgService.getName());
                svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.getDescription():"-");
                row++;
            }
        }

        // --- 4. Domain Overview Table ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("4. Domain Overview Table");
        java.util.List<com.govinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.stream.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        domainTable.getRow(0).getCell(0).setText("Security Control Domain");
        domainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc=0,n=0;
            for(SecurityControl ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }
            }
            double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTable.getRow(drow).getCell(0).setText(domain);
            domainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;
        }

        // --- 5. Controls by Domain ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;
        for(String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermodel.XWPFParagraph dp = doc.createParagraph();
            dp.setSpacingBefore(220);
            dp.createRun().setText("5."+domainNum+" "+domain);

            // Controls Table: Title, Description, Reference, Answer, Source
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow(0).getCell(3).setText("Answer");
            t.getRow(0).getCell(4).setText("Answer Source");
            int cRow=1;
            for(SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
        return baos.toByteArray();
    }
}
                // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    // Requires Apache POI on classpath.
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // Title
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        doc.createParagraph();
        
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary");
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain");

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1).setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1).setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTable.getRow(3).getCell(0).setText("Catalog:");
        infoTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // --- 2. Users & Organization ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTable.getRow(0).getCell(0).setText("Org Unit:");
        userTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        int idx=1;
        for(User u : users) {
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;
        }

        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("3. Assessment Summary");
        // Org Service assignments
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices().size()+1,2);
            svcTable.getRow(0).getCell(0).setText("Org Service");
            svcTable.getRow(0).getCell(1).setText("Description");
            int row=1;
            for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                svcTable.getRow(row).getCell(0).setText(orgService.getName());
                svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.getDescription():"-");
                row++;
            }
        }

        // --- 4. Domain Overview Table ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("4. Domain Overview Table");
        java.util.List<com.govinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.stream.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        domainTable.getRow(0).getCell(0).setText("Security Control Domain");
        domainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc=0,n=0;
            for(SecurityControl ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }
            }
            double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTable.getRow(drow).getCell(0).setText(domain);
            domainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;
        }

        // --- 5. Controls by Domain ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;
        for(String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermodel.XWPFParagraph dp = doc.createParagraph();
            dp.setSpacingBefore(220);
            dp.createRun().setText("5."+domainNum+" "+domain);

            // Controls Table: Title, Description, Reference, Answer, Source
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow(0).getCell(3).setText("Answer");
            t.getRow(0).getCell(4).setText("Answer Source");
            int cRow=1;
            for(SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
        return baos.toByteArray();
    }
}
            // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    // Requires Apache POI on classpath.
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // Title
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        doc.createParagraph();
        
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary");
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain");

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1).setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1).setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTable.getRow(3).getCell(0).setText("Catalog:");
        infoTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // --- 2. Users & Organization ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTable.getRow(0).getCell(0).setText("Org Unit:");
        userTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        int idx=1;
        for(User u : users) {
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;
        }

        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("3. Assessment Summary");
        // Org Service assignments
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices().size()+1,2);
            svcTable.getRow(0).getCell(0).setText("Org Service");
            svcTable.getRow(0).getCell(1).setText("Description");
            int row=1;
            for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                svcTable.getRow(row).getCell(0).setText(orgService.getName());
                svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.getDescription():"-");
                row++;
            }
        }

        // --- 4. Domain Overview Table ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("4. Domain Overview Table");
        java.util.List<com.govinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.stream.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        domainTable.getRow(0).getCell(0).setText("Security Control Domain");
        domainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc=0,n=0;
            for(SecurityControl ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }
            }
            double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTable.getRow(drow).getCell(0).setText(domain);
            domainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;
        }

        // --- 5. Controls by Domain ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;
        for(String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermodel.XWPFParagraph dp = doc.createParagraph();
            dp.setSpacingBefore(220);
            dp.createRun().setText("5."+domainNum+" "+domain);

            // Controls Table: Title, Description, Reference, Answer, Source
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow(0).getCell(3).setText("Answer");
            t.getRow(0).getCell(4).setText("Answer Source");
            int cRow=1;
            for(SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
        return baos.toByteArray();
    }
}
        if (foundServiceAnswer) break;
        // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    // Requires Apache POI on classpath.
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // Title
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        doc.createParagraph();
        
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary");
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain");

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1).setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1).setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTable.getRow(3).getCell(0).setText("Catalog:");
        infoTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // --- 2. Users & Organization ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTable.getRow(0).getCell(0).setText("Org Unit:");
        userTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        int idx=1;
        for(User u : users) {
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;
        }

        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("3. Assessment Summary");
        // Org Service assignments
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices().size()+1,2);
            svcTable.getRow(0).getCell(0).setText("Org Service");
            svcTable.getRow(0).getCell(1).setText("Description");
            int row=1;
            for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                svcTable.getRow(row).getCell(0).setText(orgService.getName());
                svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.getDescription():"-");
                row++;
            }
        }

        // --- 4. Domain Overview Table ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("4. Domain Overview Table");
        java.util.List<com.govinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.stream.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        domainTable.getRow(0).getCell(0).setText("Security Control Domain");
        domainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc=0,n=0;
            for(SecurityControl ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }
            }
            double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTable.getRow(drow).getCell(0).setText(domain);
            domainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;
        }

        // --- 5. Controls by Domain ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;
        for(String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermodel.XWPFParagraph dp = doc.createParagraph();
            dp.setSpacingBefore(220);
            dp.createRun().setText("5."+domainNum+" "+domain);

            // Controls Table: Title, Description, Reference, Answer, Source
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow(0).getCell(3).setText("Answer");
            t.getRow(0).getCell(4).setText("Answer Source");
            int cRow=1;
            for(SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
        return baos.toByteArray();
    }
}
    // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    // Requires Apache POI on classpath.
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // Title
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        doc.createParagraph();
        
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary");
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain");

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1).setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1).setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTable.getRow(3).getCell(0).setText("Catalog:");
        infoTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // --- 2. Users & Organization ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTable.getRow(0).getCell(0).setText("Org Unit:");
        userTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        int idx=1;
        for(User u : users) {
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;
        }

        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("3. Assessment Summary");
        // Org Service assignments
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices().size()+1,2);
            svcTable.getRow(0).getCell(0).setText("Org Service");
            svcTable.getRow(0).getCell(1).setText("Description");
            int row=1;
            for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                svcTable.getRow(row).getCell(0).setText(orgService.getName());
                svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.getDescription():"-");
                row++;
            }
        }

        // --- 4. Domain Overview Table ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("4. Domain Overview Table");
        java.util.List<com.govinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.stream.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        domainTable.getRow(0).getCell(0).setText("Security Control Domain");
        domainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc=0,n=0;
            for(SecurityControl ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }
            }
            double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTable.getRow(drow).getCell(0).setText(domain);
            domainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;
        }

        // --- 5. Controls by Domain ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;
        for(String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermodel.XWPFParagraph dp = doc.createParagraph();
            dp.setSpacingBefore(220);
            dp.createRun().setText("5."+domainNum+" "+domain);

            // Controls Table: Title, Description, Reference, Answer, Source
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow(0).getCell(3).setText("Answer");
            t.getRow(0).getCell(4).setText("Answer Source");
            int cRow=1;
            for(SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
        return baos.toByteArray();
    }
}


                    // If no org service provided an answer, use assessment answer if available
                    if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                        MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                        if (ma != null) {
                            System.out.println("Using assessment answer for control " + ctrl.getName() + ": " + ma.getAnswer());
                            answ = ma.getAnswer();
                            src = "Assessment";
                            // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    // Requires Apache POI on classpath.
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // Title
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        doc.createParagraph();
        
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary");
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain");

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1).setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1).setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTable.getRow(3).getCell(0).setText("Catalog:");
        infoTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // --- 2. Users & Organization ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTable.getRow(0).getCell(0).setText("Org Unit:");
        userTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        int idx=1;
        for(User u : users) {
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;
        }

        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("3. Assessment Summary");
        // Org Service assignments
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices().size()+1,2);
            svcTable.getRow(0).getCell(0).setText("Org Service");
            svcTable.getRow(0).getCell(1).setText("Description");
            int row=1;
            for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                svcTable.getRow(row).getCell(0).setText(orgService.getName());
                svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.getDescription():"-");
                row++;
            }
        }

        // --- 4. Domain Overview Table ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("4. Domain Overview Table");
        java.util.List<com.govinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.stream.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        domainTable.getRow(0).getCell(0).setText("Security Control Domain");
        domainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc=0,n=0;
            for(SecurityControl ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }
            }
            double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTable.getRow(drow).getCell(0).setText(domain);
            domainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;
        }

        // --- 5. Controls by Domain ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;
        for(String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermodel.XWPFParagraph dp = doc.createParagraph();
            dp.setSpacingBefore(220);
            dp.createRun().setText("5."+domainNum+" "+domain);

            // Controls Table: Title, Description, Reference, Answer, Source
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow(0).getCell(3).setText("Answer");
            t.getRow(0).getCell(4).setText("Answer Source");
            int cRow=1;
            for(SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
        return baos.toByteArray();
    }
}
                        // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    // Requires Apache POI on classpath.
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // Title
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        doc.createParagraph();
        
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary");
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain");

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1).setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1).setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTable.getRow(3).getCell(0).setText("Catalog:");
        infoTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // --- 2. Users & Organization ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTable.getRow(0).getCell(0).setText("Org Unit:");
        userTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        int idx=1;
        for(User u : users) {
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;
        }

        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("3. Assessment Summary");
        // Org Service assignments
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices().size()+1,2);
            svcTable.getRow(0).getCell(0).setText("Org Service");
            svcTable.getRow(0).getCell(1).setText("Description");
            int row=1;
            for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                svcTable.getRow(row).getCell(0).setText(orgService.getName());
                svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.getDescription():"-");
                row++;
            }
        }

        // --- 4. Domain Overview Table ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("4. Domain Overview Table");
        java.util.List<com.govinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.stream.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        domainTable.getRow(0).getCell(0).setText("Security Control Domain");
        domainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc=0,n=0;
            for(SecurityControl ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }
            }
            double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTable.getRow(drow).getCell(0).setText(domain);
            domainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;
        }

        // --- 5. Controls by Domain ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;
        for(String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermodel.XWPFParagraph dp = doc.createParagraph();
            dp.setSpacingBefore(220);
            dp.createRun().setText("5."+domainNum+" "+domain);

            // Controls Table: Title, Description, Reference, Answer, Source
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow(0).getCell(3).setText("Answer");
            t.getRow(0).getCell(4).setText("Answer Source");
            int cRow=1;
            for(SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
        return baos.toByteArray();
    }
}
                    // Default: check main assessment answers if no service answer found
                    if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                        MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                        if(ma != null) answ = ma.getAnswer();
                        src = "Assessment";
                        // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    // Requires Apache POI on classpath.
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // Title
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        doc.createParagraph();
        
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary");
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain");

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1).setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1).setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTable.getRow(3).getCell(0).setText("Catalog:");
        infoTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // --- 2. Users & Organization ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTable.getRow(0).getCell(0).setText("Org Unit:");
        userTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        int idx=1;
        for(User u : users) {
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;
        }

        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("3. Assessment Summary");
        // Org Service assignments
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices().size()+1,2);
            svcTable.getRow(0).getCell(0).setText("Org Service");
            svcTable.getRow(0).getCell(1).setText("Description");
            int row=1;
            for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                svcTable.getRow(row).getCell(0).setText(orgService.getName());
                svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.getDescription():"-");
                row++;
            }
        }

        // --- 4. Domain Overview Table ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("4. Domain Overview Table");
        java.util.List<com.govinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.stream.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        domainTable.getRow(0).getCell(0).setText("Security Control Domain");
        domainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc=0,n=0;
            for(SecurityControl ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }
            }
            double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTable.getRow(drow).getCell(0).setText(domain);
            domainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;
        }

        // --- 5. Controls by Domain ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;
        for(String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermodel.XWPFParagraph dp = doc.createParagraph();
            dp.setSpacingBefore(220);
            dp.createRun().setText("5."+domainNum+" "+domain);

            // Controls Table: Title, Description, Reference, Answer, Source
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow(0).getCell(3).setText("Answer");
            t.getRow(0).getCell(4).setText("Answer Source");
            int cRow=1;
            for(SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
        return baos.toByteArray();
    }
}
                    t.addCell(new PdfPCell(new Phrase(tt, tableCellFont)));
                    t.addCell(new PdfPCell(new Phrase(desc, tableCellFont)));
                    t.addCell(new PdfPCell(new Phrase(ref, tableCellFont)));
                    t.addCell(new PdfPCell(new Phrase(answ, boldFont)));
                    t.addCell(new PdfPCell(new Phrase(src, tableCellFont)));
                    // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    // Requires Apache POI on classpath.
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // Title
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        doc.createParagraph();
        
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary");
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain");

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1).setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1).setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTable.getRow(3).getCell(0).setText("Catalog:");
        infoTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // --- 2. Users & Organization ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTable.getRow(0).getCell(0).setText("Org Unit:");
        userTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        int idx=1;
        for(User u : users) {
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;
        }

        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("3. Assessment Summary");
        // Org Service assignments
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices().size()+1,2);
            svcTable.getRow(0).getCell(0).setText("Org Service");
            svcTable.getRow(0).getCell(1).setText("Description");
            int row=1;
            for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                svcTable.getRow(row).getCell(0).setText(orgService.getName());
                svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.getDescription():"-");
                row++;
            }
        }

        // --- 4. Domain Overview Table ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("4. Domain Overview Table");
        java.util.List<com.govinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.stream.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        domainTable.getRow(0).getCell(0).setText("Security Control Domain");
        domainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc=0,n=0;
            for(SecurityControl ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }
            }
            double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTable.getRow(drow).getCell(0).setText(domain);
            domainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;
        }

        // --- 5. Controls by Domain ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;
        for(String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermodel.XWPFParagraph dp = doc.createParagraph();
            dp.setSpacingBefore(220);
            dp.createRun().setText("5."+domainNum+" "+domain);

            // Controls Table: Title, Description, Reference, Answer, Source
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow(0).getCell(3).setText("Answer");
            t.getRow(0).getCell(4).setText("Answer Source");
            int cRow=1;
            for(SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
        return baos.toByteArray();
    }
}
                doc.add(t);
                doc.add(Chunk.NEWLINE);
                domainNum++;

                // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    // Requires Apache POI on classpath.
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // Title
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        doc.createParagraph();
        
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary");
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain");

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1).setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1).setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTable.getRow(3).getCell(0).setText("Catalog:");
        infoTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // --- 2. Users & Organization ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTable.getRow(0).getCell(0).setText("Org Unit:");
        userTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        int idx=1;
        for(User u : users) {
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;
        }

        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("3. Assessment Summary");
        // Org Service assignments
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices().size()+1,2);
            svcTable.getRow(0).getCell(0).setText("Org Service");
            svcTable.getRow(0).getCell(1).setText("Description");
            int row=1;
            for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                svcTable.getRow(row).getCell(0).setText(orgService.getName());
                svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.getDescription():"-");
                row++;
            }
        }

        // --- 4. Domain Overview Table ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("4. Domain Overview Table");
        java.util.List<com.govinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.stream.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        domainTable.getRow(0).getCell(0).setText("Security Control Domain");
        domainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc=0,n=0;
            for(SecurityControl ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }
            }
            double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTable.getRow(drow).getCell(0).setText(domain);
            domainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;
        }

        // --- 5. Controls by Domain ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;
        for(String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermodel.XWPFParagraph dp = doc.createParagraph();
            dp.setSpacingBefore(220);
            dp.createRun().setText("5."+domainNum+" "+domain);

            // Controls Table: Title, Description, Reference, Answer, Source
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow(0).getCell(3).setText("Answer");
            t.getRow(0).getCell(4).setText("Answer Source");
            int cRow=1;
            for(SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
        return baos.toByteArray();
    }
}
            doc.add(Chunk.NEWLINE);

            // Footer: generated by + date (only last page)
            String footerTxt = "Generated by GovInc Assessment System on: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            Phrase footerPhrase = new Phrase(footerTxt, new Font(Font.FontFamily.HELVETICA, 9, Font.ITALIC, BaseColor.GRAY));
            PdfContentByte cb = writer.getDirectContentUnder();
            Rectangle rect = doc.getPageSize();
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, footerPhrase, rect.getRight(30), rect.getBottom(22), 0);

            doc.close();
            return baos.toByteArray();
            // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    // Requires Apache POI on classpath.
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // Title
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        doc.createParagraph();
        
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary");
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain");

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1).setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1).setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTable.getRow(3).getCell(0).setText("Catalog:");
        infoTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // --- 2. Users & Organization ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTable.getRow(0).getCell(0).setText("Org Unit:");
        userTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        int idx=1;
        for(User u : users) {
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;
        }

        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("3. Assessment Summary");
        // Org Service assignments
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices().size()+1,2);
            svcTable.getRow(0).getCell(0).setText("Org Service");
            svcTable.getRow(0).getCell(1).setText("Description");
            int row=1;
            for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                svcTable.getRow(row).getCell(0).setText(orgService.getName());
                svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.getDescription():"-");
                row++;
            }
        }

        // --- 4. Domain Overview Table ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("4. Domain Overview Table");
        java.util.List<com.govinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.stream.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        domainTable.getRow(0).getCell(0).setText("Security Control Domain");
        domainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc=0,n=0;
            for(SecurityControl ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }
            }
            double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTable.getRow(drow).getCell(0).setText(domain);
            domainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;
        }

        // --- 5. Controls by Domain ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;
        for(String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermodel.XWPFParagraph dp = doc.createParagraph();
            dp.setSpacingBefore(220);
            dp.createRun().setText("5."+domainNum+" "+domain);

            // Controls Table: Title, Description, Reference, Answer, Source
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow(0).getCell(3).setText("Answer");
            t.getRow(0).getCell(4).setText("Answer Source");
            int cRow=1;
            for(SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
        return baos.toByteArray();
    }
}
        // Generate a Microsoft Word (.docx) version of the assessment report (structure mirrors createPdfReport).
    // Requires Apache POI on classpath.
    public byte[] createWordReport(Assessment assessment, AssessmentDetails details, java.util.List<User> users, OrgUnit orgUnit, java.util.List<AssessmentControlAnswer> answers) throws Exception {
        org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        
        // Title
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = title.createRun();
        run.setText("Assessment Report");
        run.setBold(true);
        run.setFontSize(22);
        doc.createParagraph();
        
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary");
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain");

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1).setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1).setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTable.getRow(3).getCell(0).setText("Catalog:");
        infoTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // --- 2. Users & Organization ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTable.getRow(0).getCell(0).setText("Org Unit:");
        userTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        int idx=1;
        for(User u : users) {
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;
        }

        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("3. Assessment Summary");
        // Org Service assignments
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices().size()+1,2);
            svcTable.getRow(0).getCell(0).setText("Org Service");
            svcTable.getRow(0).getCell(1).setText("Description");
            int row=1;
            for(com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                svcTable.getRow(row).getCell(0).setText(orgService.getName());
                svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.getDescription():"-");
                row++;
            }
        }

        // --- 4. Domain Overview Table ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("4. Domain Overview Table");
        java.util.List<com.govinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog().getSecurityControls();
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.stream.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<String,java.util.List<SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        domainTable.getRow(0).getCell(0).setText("Security Control Domain");
        domainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String domain : controlsPerDomain.keySet()) {
            java.util.List<SecurityControl> domainCtrls = controlsPerDomain.get(domain);
            int sc=0,n=0;
            for(SecurityControl ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }
            }
            double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTable.getRow(drow).getCell(0).setText(domain);
            domainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;
        }

        // --- 5. Controls by Domain ---
        p = doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        java.util.Collections.sort(domainOrder);
        int domainNum = 1;
        for(String domain : domainOrder) {
            java.util.List<SecurityControl> ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermodel.XWPFParagraph dp = doc.createParagraph();
            dp.setSpacingBefore(220);
            dp.createRun().setText("5."+domainNum+" "+domain);

            // Controls Table: Title, Description, Reference, Answer, Source
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow(0).getCell(3).setText("Answer");
            t.getRow(0).getCell(4).setText("Answer Source");
            int cRow=1;
            for(SecurityControl ctrl : ctrlList) {
                String tt = ctrl.getName()!=null ? ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                String answ = "-";
                String src = "-";
                boolean foundServiceAnswer = false;

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null) {
                            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                    if (osPercent != null) {
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
 

    
    // 
    
    // 
            
    }
    // 

        
    // Generate a Microsoft Word (.docx) version of the assessment report (struct

        equires Apache POI on classpath.
    public byte[] createWordReport(Assessment assessment, AssessmentDetails detai

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        

        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org

        run.setText("Assessment Report");
        run.setBold(true);
                

        doc.createParagraph();
        
                
        // Meta Info
        org.apache.poi.xwpf.usermodel.XWPFParagraph meta = doc.createParagraph();
        meta.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignme
                nt.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun metaRun = meta.createRun();
        metaRun.setText("Generated on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        metaRun.setBold(true);
        doc.createParagraph();

        // -- CONTENTS --
        org.apache.poi.xwpf.usermodel.XWPFParagraph tocTitle = doc.createParagraph();
        tocTitle.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        tocTitle.createRun().setText("Contents");
        org.apache.poi.xwpf.usermodel.XWPFParagraph contained = doc.createParagraph();
        contained.createRun().setText("1. General Information");
        contained = doc.createParagraph();
        contained.createRun().setText("2. Users and Organization");
        contained = doc.createParagraph();
        contained.createRun().setText("3. Assessment Summary"); 
        contained = doc.createParagraph();
        contained.createRun().setText("4. Domain Overview Table");
        contained = doc.createParagraph();
        contained.createRun().setText("5. Controls by Domain"); 

        // --- 1. General Info ---
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(350); 
                
        p.createRun().setText("1. General Information");
        org.apache.poi.xwpf.usermodel.XWPFTable infoTable = doc.createTable(5,2);
        infoTable.getRow(0).getCell(0).setText("Assessment Name:");
        infoTable.getRow(0).getCell(1)
                .setText(assessment.getName());
        infoTable.getRow(1).getCell(0).setText("Assessment ID:");
        infoTable.getRow(1).getCell(1).setText(String.valueOf(assessment.getId()));
        infoTable.getRow(2).getCell(0).setText("Date:");
        infoTable.getRow(2).getCell(1)
                .setText(assessment.getDate() != null ? assessment.getDate().toString() : "-");
        infoTab l e.getRow(3).getCell(0).setText("Catalog:");
        inf oTable.getRow(3).getCell(1).setText((assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-"));
        infoTable.getRow(4).getCell(0).setText("Completed On:");
        infoTable.getRow(4).getCell(1).setText(details.getDate() != null ? details.getDate().toString() : "-");

        // ---  2 . Users & Organization ---
        p =  doc.createParagraph();
        p.setSpacingBefore(350);
        p.createRun().setText("2. Users and Organization");
        org.apache.poi.xwpf.usermodel.XWPFTable userTable = doc.createTable(1 + users.size(), 2);
        userTab l e.getRow(0).getCell(0).setText("Org Unit:");
        use rTable.getRow(0).getCell(1).setText(orgUnit != null ? orgUnit.getName() : "-");
        in t idx=1;  
        for(User u : users) {  
                    
            userTable.getRow(idx).getCell(0).setText("User:");
            userTable.getRow(idx).getCell(1).setText(u.getName() + " <" + u.getEmail() + ">");
            idx++;  
        }    
  
                    
                              
        // --- 3. Assessment Summary ---
        p = doc.createParagraph();
        p.setSpacin g Before(350);
        p. crea teRun().setText("3. Asse ss ment Summary");
        // Org Service assignments  
                    
                              
        if(assessment.getOrgServices()!=null && !assessment.getOrgServices().isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTable svcTable = doc.createTable(assessment.getOrgServices
                ().size()+1,2);
            svcTabl e .getRow(0).getCell(0).setText("Org Service");
                
            svc Table.getRow( 0).getCell(1).setText("Description");
                
                          
                                
            int row=1;   
            for(com.govinc.organization.OrgServ
                        ice orgService : assessment.getOrgSe rv ices ( )) {  
                 s vcTable.getRow(row).getCell(0).setText(orgService.getName());
                 svcTable.getRow(row).getCell(1).setText(orgService.getDescription()!=null?orgService.get
                Description():"-");
                ro w ++ ;  
                 
            }  
                  
                          
                                
        }   
     
        // --- 4. Domain Overview Table ---
        p = doc. c reateParagraph();
        p.s etSpacingBefore(350);
                
        p.createRu n () . s etText("4. Domain Overview Table");
                 
        java.util. List<com.go vinc.catalog.SecurityControl> allControls = assessment.getSecurityCatalog
                ().get Se curityControls();
                          
                                
        java.util.Map<Long, AssessmentControlAnswer> answerMap = answers.stream().collect(java.util.st r ea m.Collectors.toMap(a -> a.getSecurityControl().getId(), a -> a));
        java.util.Map<Strin g ,java.u t il.List< SecurityControl>> controlsPerDomain = allControls.stream().collect(java.util.stream.Collectors.groupingBy(ctrl -> ctrl.getSecurityControlDomain()!=null ? ctrl.getSecurityControlDomain().getName() : "Unknown"));
        org.apache.poi.xwpf.usermodel.XWPFTable domainTable = doc.createTable(controlsPerDomain.size()+1,2);
        dom ainTa b le.getRow(0).getCell(0).setText("Security Control Domain");
        dom ainTable.getRow(0).getCell(1).setText("Score (%)");
        int drow=1;
        for(String   do m a in : controlsPerDomain.keySet()) {
            jav a.util.List<SecurityCon t rol> doma i nCt r ls = controlsPerDomain.get(domain);
            int sc =0,n=0;
            for(Securi ty Control ctrl : domainCtrls) {
                if(answerMap.containsKey(ctrl.getId())) {   
                    sc+=answerMap.get(ctrl.getId()).getScore();
                    n++;
                }     
            }
             double perc = n>0 ? (sc/(double)n) : 0.0;
            domainTa b le.getRow(drow).getCell(0).setText(domain);
            dom ainTable.getRow(drow).getCell(1).setText(String.format("%.1f", perc));
            drow++;  
        }        
  
        // --- 5. Controls by Domain ---
        p = doc.createParagraph();   
        p.setSpacingBefore(350);
        p.createRun().setText("5. Controls by Domain");

                // 
        java.util.List<String> domainOrder = new java.util.ArrayList<>(controlsPerDomain.keySet());
        jav a.util.Collections.sort(domainOrder);
        int domainNu m  = 1;
                                
        for(Str ing domain : domainOrder) {
            java.util.List<SecurityControl >  ctrlList = controlsPerDomain.get(domain);
            org.apache.poi.xwpf.usermod e l.XW PF Par a gra p h dp = 
                                        oc.createParagraph();
                                        
            dp.setSpacingBefore(220);  
            dp.createRun().setText("5."+domainNum+" "+domain);
   
                                                
            // Controls Table: Title, Description, Reference, Answer, Source
                                                
                                                        
                                                
            org.apache.poi.xwpf.usermodel.XWPFTable t = doc.createTable(ctrlList.size()+1,5);
            t.getRow(0).getCell(0).setText("Title");
                // 
            t.getRow(0).getCell(1).setText("Description");
            t.getRow(0).getCell(2).setText("Reference");
            t.getRow ( 0).getCell(3).setText("Answer");
                                
            t.g etRow(0).getCell(4).setText("Answer Source");
            int cRow=1;  
            for(SecurityControl ctrl : ctrlLis t)  {
                                        
                                        
                String tt = ctrl.getName()!=nul
                              ?  ctrl.getName() : "-";
                String desc = ctrl.getDetail()!=null ? ctrl.getDetail() : "-";
                String ref = ctrl.getReference()!=null ? ctrl.getReference() : "-";
                                                
                String answ = "-";
                                   
                                     
                                                        
                                                
                String src = "-";
                boolean foundServiceAnswer = false;
                // 

                // Copy org service answer/applicability logic from PDF (only show if covered/applicable)
                if (assessment.getOrgServices() != null) {
                                
                    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
                        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                        if (osa != null && osa.getControls() != null)
                                        {
                                        
                            for (com.govinc.org
                            nization.OrgServiceAssessmentControl osac : osa.getControls()) {
                
                                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId()) && osac.isApplicable()) {
                                    Integer osPercent = osac.getPercent();
                                                
                                    if (osPercent != null) {
                                   
                                     
                                                        
                                                
                                        java.util.Set<com.govinc.maturity.MaturityAnswer> maturityAnswersSet = assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers();
                                        com.govinc.maturity.MaturityAnswer closest = maturityAnswersSet.stream().min(java.util.Comparator.comparingInt(ma -> Math.abs(ma.getScore() - osPercent))).orElse(null);
                                        if (closest != null) {
                                            answ = closest.getAnswer();
                                        } else {
                                            answ = String.valueOf(osPercent) + "%";
                                        }
                                        src = orgService.getName();
                                        foundServiceAnswer = true;
                                        break;
                                    }
                                }
                            }
                            
                
                        }
                        if (foundServiceAnswer) break;
                    }
                }
                if (!f oundServiceA
                        swer && answerMap.containsKey(ctrl.getId())) {
                    MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                    if(ma != null) answ = ma.getAnswer();
                    src = "Assessment";
                }

                t.getRow(cRow).getCell(0).setText(tt);
                t.getRow(cRow).getCell(1).setText(desc);
                t.getRow(cRow).getCell(2).setText(ref);
                t.getRow(cRow).getCell(3).setText(answ);
                t.getRow(cRow).getCell(4).setText(src);
                cRow++;
            }
            domainNum++;
        }
        // Footer (meta)
        org.apache.poi.xwpf.usermodel.XWPFParagraph foot = doc.createParagraph();
                
        foot.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        foot.createRun().setText("Generated by GovInc Assessment System on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        doc.write(baos);
        baos.close();
        doc.close();
        return baos.toByteArray();
    }
}
