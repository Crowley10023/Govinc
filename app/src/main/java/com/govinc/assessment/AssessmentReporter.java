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
                }
                doc.add(svcTable);
                doc.add(Chunk.NEWLINE);
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
                    if(scoresByControl.containsKey(ctrl.getId())) { sc += scoresByControl.get(ctrl.getId()); n++; }
                }
                double perc = n>0 ? (sc/(double)n) : 0.0;
                overviewTable.addCell(new Phrase(domain, tableCellFont));
                overviewTable.addCell(new Phrase(String.format("%.1f", perc), boldFont));
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
                    }
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

                    if (assessment.getOrgServices() != null) {
    for (com.govinc.organization.OrgService orgService : assessment.getOrgServices()) {
        com.govinc.organization.OrgServiceAssessment osa = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
        if (osa != null && osa.getControls() != null) {
            for (com.govinc.organization.OrgServiceAssessmentControl osac : osa.getControls()) {
                if (osac.getSecurityControl() != null && osac.getSecurityControl().getId().equals(ctrl.getId())) {
                    Integer osPercent = osac.getPercent();
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

                    // If no org service provided an answer, use assessment answer if available
                    if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                        MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                        if (ma != null) {
                            System.out.println("Using assessment answer for control " + ctrl.getName() + ": " + ma.getAnswer());
                            answ = ma.getAnswer();
                            src = "Assessment";
                        }
                    }
                    // Default: check main assessment answers if no service answer found
                    if (!foundServiceAnswer && answerMap.containsKey(ctrl.getId())) {
                        MaturityAnswer ma = answerMap.get(ctrl.getId()).getMaturityAnswer();
                        if(ma != null) answ = ma.getAnswer();
                        src = "Assessment";
                    }
                    t.addCell(new PdfPCell(new Phrase(tt, tableCellFont)));
                    t.addCell(new PdfPCell(new Phrase(desc, tableCellFont)));
                    t.addCell(new PdfPCell(new Phrase(ref, tableCellFont)));
                    t.addCell(new PdfPCell(new Phrase(answ, boldFont)));
                    t.addCell(new PdfPCell(new Phrase(src, tableCellFont)));
                }
                doc.add(t);
                doc.add(Chunk.NEWLINE);
                domainNum++;

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
        }
    }
}
