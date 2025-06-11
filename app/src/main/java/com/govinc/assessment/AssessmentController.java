// ...existing imports...
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Table;
import com.lowagie.text.Cell;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import javax.servlet.http.HttpServletResponse;
// ...rest of imports and code...

@Controller
@RequestMapping("/assessment")
public class AssessmentController {
    // ...existing Autowired and fields...

    // ...existing code...

    @GetMapping("/{id}/report")
    public void downloadReport(@PathVariable Long id, HttpServletResponse response) throws Exception {
        Optional<Assessment> assessmentOpt = assessmentRepository.findById(id);
        if (!assessmentOpt.isPresent()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("Assessment not found");
            return;
        }
        Assessment assessment = assessmentOpt.get();
        List<SecurityControl> controls = new ArrayList<>();
        if (assessment.getSecurityCatalog() != null) {
            controls.addAll(assessment.getSecurityCatalog().getSecurityControls());
        }
        List<MaturityAnswer> maturityAnswers = new ArrayList<>();
        if (assessment.getSecurityCatalog() != null && assessment.getSecurityCatalog().getMaturityModel() != null) {
            maturityAnswers.addAll(assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers());
        }
        // fetch the answers for each control
        Map<Long, AssessmentControlAnswer> answerMap = new HashMap<>();
        Optional<AssessmentDetails> detailsOpt = assessmentDetailsService.findById(id);
        if(detailsOpt.isPresent() && detailsOpt.get().getControlAnswers()!=null) {
            for(AssessmentControlAnswer aca : detailsOpt.get().getControlAnswers()){
                if(aca.getSecurityControl()!=null) {
                    answerMap.put(aca.getSecurityControl().getId(), aca);
                }
            }
        }
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=assessment-" + assessment.getId() + ".pdf");
        Document doc = new Document(PageSize.A4);
        PdfWriter.getInstance(doc, response.getOutputStream());
        doc.open();
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 17);
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        Font textFont = FontFactory.getFont(FontFactory.HELVETICA, 12);
        doc.add(new Paragraph("Assessment Report", titleFont));
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Name: " + assessment.getName(), textFont));
        doc.add(new Paragraph("Date: " + assessment.getDate(), textFont));
        doc.add(new Paragraph("Status: " + assessment.getStatus(), textFont));
        doc.add(new Paragraph("Security Catalog: " + (assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : ""), textFont));
        if (assessment.getPredecessor()!=null) {
            doc.add(new Paragraph("Predecessor Assessment: " + assessment.getPredecessor().getName() + " (ID: " + assessment.getPredecessor().getId() + ")", textFont));
        }
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Controls & Maturity Answers", headerFont));
        doc.add(new Paragraph(" "));
        for (SecurityControl ctrl : controls) {
            doc.add(new Paragraph("Control: " + ctrl.getName(), headerFont));
            if(ctrl.getDetail()!=null && !ctrl.getDetail().isEmpty()) {
                doc.add(new Paragraph("Description: " + ctrl.getDetail(), FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 11, Color.GRAY)));
            }
            Table t = new Table(2);
            t.setWidths(new float[]{2, 7});
            t.addCell(makeHeaderCell("Maturity Answer"));
            t.addCell(makeHeaderCell("Description"));
            AssessmentControlAnswer ans = answerMap.get(ctrl.getId());
            Long selectedId = (ans != null && ans.getMaturityAnswer() != null) ? ans.getMaturityAnswer().getId() : null;
            for(MaturityAnswer ma: maturityAnswers) {
                Font cellFont = (ma.getId().equals(selectedId))
                    ? FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLUE)
                    : textFont;
                Color bgColor = (ma.getId().equals(selectedId)) ? new Color(220,235,255) : Color.WHITE;
                Cell ansCell = new Cell(new Paragraph(ma.getAnswer(), cellFont));
                ansCell.setBackgroundColor(bgColor);
                ansCell.setHorizontalAlignment(Cell.ALIGN_CENTER);
                Cell descCell = new Cell(new Paragraph(ma.getDescription()!=null? ma.getDescription() : "", cellFont));
                descCell.setBackgroundColor(bgColor);
                t.addCell(ansCell);
                t.addCell(descCell);
            }
            doc.add(t);
            doc.add(new Paragraph(" "));
        }
        doc.close();
    }

    private Cell makeHeaderCell(String text) {
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, Color.WHITE);
        Cell cell = new Cell(new Paragraph(text, bold));
        cell.setBackgroundColor(new Color(70,130,180));
        cell.setHorizontalAlignment(Cell.ALIGN_CENTER);
        return cell;
    }

    // ...rest of controller code unchanged...
}
