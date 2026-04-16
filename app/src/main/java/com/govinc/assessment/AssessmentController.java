package com.govinc.assessment;

import com.govinc.catalog.SecurityCatalog;
import com.govinc.catalog.SecurityCatalogService;
import com.govinc.catalog.SecurityControl;
import com.govinc.catalog.SecurityControlRepository;
import com.govinc.compliance.ComplianceCheck;
import com.govinc.compliance.ComplianceCheckRepository;
import com.govinc.compliance.ComplianceService;
import com.govinc.compliance.ComplianceThreshold;
import com.govinc.maturity.MaturityAnswer;
import com.govinc.maturity.MaturityAnswerRepository;
import com.govinc.organization.OrgUnit;
import com.govinc.organization.OrgUnitService;
import com.govinc.organization.OrgService;
import com.govinc.organization.OrgServiceService;
import com.govinc.organization.OrgServiceAssessment;
import com.govinc.organization.OrgServiceAssessmentControl;
import com.govinc.user.Role;
import com.govinc.user.User;
import com.govinc.user.UserRepository;
import com.govinc.catalog.SecurityControlDomain;
import com.govinc.entity.OrganisationDetailsRepository;
import com.govinc.util.OpenAIUtil;
import com.govinc.authorization.AuthorizationService;
import com.govinc.authorization.UnauthorizedException;
import com.govinc.governance.GovernanceProjectRepository;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/assessment")
public class AssessmentController {
    private static final DateTimeFormatter FRIENDLY_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");

    @Autowired
    private AssessmentRepository assessmentRepository;
    @Autowired
    private SecurityCatalogService securityCatalogService;
    @Autowired
    private AssessmentDetailsService assessmentDetailsService;
    @Autowired
    private SecurityControlRepository securityControlRepository;
    @Autowired
    private MaturityAnswerRepository maturityAnswerRepository;
    @Autowired
    private AssessmentControlAnswerRepository assessmentControlAnswerRepository;

    @Autowired
    private com.govinc.organization.OrgServiceAssessmentRepository orgServiceAssessmentRepository;

    @Autowired
    private AssessmentUrlsService assessmentUrlsService;

    // --- Inject UserRepository ---
    @Autowired
    private UserRepository userRepository;

    // --- Inject OrgUnitService ---
    @Autowired
    private OrgUnitService orgUnitService;

    // --- Inject OrgServiceService ---
    @Autowired
    private OrgServiceService orgServiceService;

    @Autowired
    private AssessmentReporter assessmentReporter;

    @Autowired
    private AssessmentReporterWord assessmentReporterWord;

    @Autowired
    private OrganisationDetailsRepository organisationDetailsRepository;

    @Autowired
    private OpenAIUtil openAIUtil;
    
    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private ComplianceCheckRepository complianceCheckRepository;

    @Autowired
    private ComplianceService complianceService;

    @Autowired
    private GovernanceProjectRepository governanceProjectRepository;

    @GetMapping("/create")
    public String showCreateAssessmentForm(Model model) {
        // Authorization check: only ADMIN and ISM can create assessments
        if (!authorizationService.canCreateAssessment()) {
            throw new UnauthorizedException("You do not have permission to create assessments.");
        }
        List<SecurityCatalog> catalogs = securityCatalogService.findAll();
        model.addAttribute("catalogs", catalogs);
        // --- Add users list to model ---
        List<User> users = userRepository.findAll();
        model.addAttribute("users", users);
        // --- Add org units to model ---
        List<OrgUnit> orgUnits = orgUnitService.getAllOrgUnits();
        model.addAttribute("orgUnits", orgUnits);
        // --- Add org services to model ---
        List<OrgService> orgServices = orgServiceService.getAllOrgServices();
        model.addAttribute("orgServices", orgServices);
        return "create-assessment";
    }

    // AJAX: get predecessor candidates for catalog + orgUnit combination
    @GetMapping("/predecessors")
    @ResponseBody
    public List<Map<String, Object>> getPredecessors(
            @RequestParam Long catalogId,
            @RequestParam Long orgUnitId) {
        if (!authorizationService.canCreateAssessment()) {
            throw new UnauthorizedException("You do not have permission.");
        }
        List<Assessment> candidates = assessmentRepository.findBySecurityCatalogIdAndOrgUnitId(catalogId, orgUnitId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Assessment a : candidates) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", a.getId());
            item.put("name", a.getName());
            item.put("status", a.getStatus().toString());
            item.put("creationDate", a.getCreationDate() != null ? a.getCreationDate().toString() : "");
            result.add(item);
        }
        return result;
    }

    // AJAX: get compliance checks for a given catalog
    @GetMapping("/compliance-checks")
    @ResponseBody
    public List<Map<String, Object>> getComplianceChecksForCatalog(@RequestParam Long catalogId) {
        if (!authorizationService.canCreateAssessment()) {
            throw new UnauthorizedException("You do not have permission.");
        }
        List<ComplianceCheck> checks = complianceCheckRepository.findBySecurityCatalogId(catalogId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ComplianceCheck cc : checks) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", cc.getId());
            item.put("name", cc.getName());
            item.put("description", cc.getDescription() != null ? cc.getDescription() : "");
            result.add(item);
        }
        return result;
    }

    // POST handler for create-assessment
    @PostMapping("/create")
    public String createAssessment(
            @RequestParam("catalogId") Long catalogId,
            @RequestParam(value = "name", required = false) String providedName,
            @RequestParam(value = "orgUnitId", required = false) Long orgUnitId,
            @RequestParam(value = "userIds", required = false) List<Long> userIds,
            @RequestParam(value = "orgServiceIds", required = false) List<Long> orgServiceIds,
            @RequestParam(value = "predecessorId", required = false) Long predecessorId,
            @RequestParam(value = "complianceCheckId", required = false) Long complianceCheckId,
            @RequestParam(value = "guideVisibleInDirect", defaultValue = "false") boolean guideVisibleInDirect) {
        if (!authorizationService.canCreateAssessment()) {
            throw new UnauthorizedException("You do not have permission to create assessments.");
        }
        SecurityCatalog catalog = securityCatalogService.findById(catalogId).orElse(null);
        if (catalog == null) {
            return "redirect:/assessment/list";
        }
        Assessment assessment = new Assessment();
        // Use provided name if given, otherwise generate one
        String assessmentName;
        if (providedName != null && !providedName.trim().isEmpty()) {
            assessmentName = providedName.trim();
        } else {
            assessmentName = "Assessment_" + java.time.LocalDate.now();
            if (orgUnitId != null) {
                OrgUnit orgUnit = orgUnitService.getOrgUnit(orgUnitId).orElse(null);
                if (orgUnit != null) {
                    assessmentName = orgUnit.getName().replaceAll("[^a-zA-Z0-9]+", "_") + "_" + java.time.LocalDate.now();
                }
            }
        }
        assessment.setName(assessmentName);
        assessment.setSecurityCatalog(catalog);
        assessment.setCreationDate(LocalDate.now());
        assessment.setGuideVisibleInDirect(guideVisibleInDirect);
        try {
            assessment.setCreatedBy(authorizationService.getCurrentUser());
        } catch (Exception e) {
            // ignore if current user cannot be determined
        }
        if (orgUnitId != null) {
            OrgUnit orgUnit = orgUnitService.getOrgUnit(orgUnitId).orElse(null);
            if (orgUnit != null) {
                assessment.setOrgUnit(orgUnit);
            }
        }
        if (userIds != null && !userIds.isEmpty()) {
            Set<User> users = userIds.stream()
                    .map(id -> userRepository.findById(id).orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            assessment.setUsers(users);
        }
        if (orgServiceIds != null && !orgServiceIds.isEmpty()) {
            Set<OrgService> orgServices = orgServiceIds.stream()
                    .map(id -> orgServiceService.getOrgService(id).orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            assessment.setOrgServices(orgServices);
        }
        // Set predecessor
        Assessment predecessor = null;
        if (predecessorId != null) {
            predecessor = assessmentRepository.findById(predecessorId).orElse(null);
            if (predecessor != null) {
                assessment.setPredecessor(predecessor);
            }
        }
        // Set compliance check
        if (complianceCheckId != null) {
            ComplianceCheck cc = complianceCheckRepository.findById(complianceCheckId).orElse(null);
            if (cc != null) {
                assessment.setComplianceCheck(cc);
            }
        }
        assessment = assessmentRepository.save(assessment);

        // Copy answers from predecessor if selected
        if (predecessor != null) {
            Optional<AssessmentDetails> predDetailsOpt = assessmentDetailsService.findById(predecessor.getId());
            if (predDetailsOpt.isPresent()) {
                AssessmentDetails predDetails = predDetailsOpt.get();
                AssessmentDetails newDetails = new AssessmentDetails();
                Set<Assessment> aSet = new HashSet<>();
                aSet.add(assessment);
                newDetails.setAssessments(aSet);
                newDetails.setDate(LocalDate.now());
                Set<AssessmentControlAnswer> copiedAnswers = new HashSet<>();
                for (AssessmentControlAnswer orig : predDetails.getControlAnswers()) {
                    AssessmentControlAnswer copy = new AssessmentControlAnswer(
                            orig.getSecurityControl(),
                            orig.getMaturityAnswer(),
                            orig.getComment());
                    copy.setIsOverride(orig.getIsOverride());
                    copy = assessmentControlAnswerRepository.save(copy);
                    copiedAnswers.add(copy);
                }
                newDetails.setControlAnswers(copiedAnswers);
                assessmentDetailsService.save(newDetails);
            }
        }

        return "redirect:/assessment/" + assessment.getId();
    }

    @GetMapping("/list")
    public String showAssessments(Model model) {
        // Authorization check: user must have permission to view assessments
        if (!authorizationService.canViewAssessmentList()) {
            throw new UnauthorizedException("You do not have permission to view assessments.");
        }
        
        // Get filtered list based on user role
        java.util.List<Assessment> assessments = assessmentRepository.findAll();
        java.util.List<Assessment> filtered = new java.util.ArrayList<>();
        
        for (Assessment assessment : assessments) {
            if (authorizationService.canAccessAssessment(assessment.getId())) {
                if (assessment.getCreationDate() == null) {
                    assessment.setCreationDate(LocalDate.now());
                    assessmentRepository.save(assessment);
                }
                filtered.add(assessment);
            }
        }
        
        model.addAttribute("assessments", filtered);
        // Ensure the template and client-side filtering always use creationDate
        model.addAttribute("isAdminOrISM", authorizationService.isAdmin() || authorizationService.isInformationSecurityManager());
        return "assessment-list";
    }

    // Add this mapping to serve assessment-step-controls.html as per your flow
    @GetMapping("/{id}/controls")
    public String assessmentStepControls(@PathVariable Long id, Model model) {
        if (!authorizationService.canAccessAssessment(id)) {
            throw new UnauthorizedException("You do not have permission to access this assessment.");
        }
        Optional<Assessment> assessmentOpt = assessmentRepository.findById(id);
        if (assessmentOpt.isEmpty()) {
            return "assessment-not-found";
        }
        Assessment assessment = assessmentOpt.get();
        model.addAttribute("assessment", assessment);
        // Sorted controls by name
        List<SecurityControl> controls = new ArrayList<>();
        if (assessment.getSecurityCatalog() != null) {
            controls.addAll(assessment.getSecurityCatalog().getSecurityControls());
            controls.sort(Comparator.comparing(SecurityControl::getName, Comparator.nullsLast(String::compareTo)));
        }
        model.addAttribute("controls", controls);
        // Sorted answers
        List<MaturityAnswer> answers = new ArrayList<>();
        if (assessment.getSecurityCatalog() != null && assessment.getSecurityCatalog().getMaturityModel() != null) {
            answers.addAll(assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers());
            answers.sort(Comparator.comparing(MaturityAnswer::getAnswer, Comparator.nullsLast(String::compareTo)));
        }
        model.addAttribute("answers", answers);

        // Add selected answers for each control
        Optional<AssessmentDetails> detailsOpt = assessmentDetailsService.findById(id);
        Map<Long, Long> controlAnswers = new HashMap<>();
        if (detailsOpt.isPresent()) {
            for (AssessmentControlAnswer aca : detailsOpt.get().getControlAnswers()) {
                if (aca.getSecurityControl() != null && aca.getMaturityAnswer() != null) {
                    controlAnswers.put(aca.getSecurityControl().getId(), aca.getMaturityAnswer().getId());
                }
            }
        }
        model.addAttribute("controlAnswers", controlAnswers);
        return "assessment-step-controls";
    }

    // POST handler for controls - saves answers and redirects to details page
    @PostMapping("/{id}/controls")
    public String handleAssessmentControls(@PathVariable Long id, @RequestParam MultiValueMap<String, String> params) {
        if (!authorizationService.canAnswerAssessment(id)) {
            throw new UnauthorizedException("You do not have permission to modify this assessment.");
        }
        // Find details or create new
        Optional<AssessmentDetails> detailsOpt = assessmentDetailsService.findById(id);
        AssessmentDetails details;
        if (!detailsOpt.isPresent()) {
            Optional<Assessment> assessmentOpt = assessmentRepository.findById(id);
            if (!assessmentOpt.isPresent())
                return "redirect:/assessment/list";
            details = new AssessmentDetails();
            Set<Assessment> assessmentSet = new HashSet<>();
            assessmentSet.add(assessmentOpt.get());
            details.setAssessments(assessmentSet);
            details.setDate(LocalDate.now());
        } else {
            details = detailsOpt.get();
        }
        Set<AssessmentControlAnswer> answers = new HashSet<>();
        // Remove all previous answers for clean update
        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("control_")) {
                try {
                    Long controlId = Long.parseLong(key.substring("control_".length()));
                    String answerIdStr = entry.getValue().get(0);
                    if (answerIdStr != null && !answerIdStr.isEmpty()) {
                        Long answerId = Long.parseLong(answerIdStr);
                        SecurityControl control = securityControlRepository.findById(controlId).orElse(null);
                        MaturityAnswer maturityAnswer = maturityAnswerRepository.findById(answerId).orElse(null);
                        if (control != null && maturityAnswer != null) {
                            AssessmentControlAnswer aca = new AssessmentControlAnswer(control, maturityAnswer);
                            aca = assessmentControlAnswerRepository.save(aca);
                            answers.add(aca);
                        }
                    }
                } catch (NumberFormatException e) {
                    // Ignore parse errors
                }
            }
        }
        details.setControlAnswers(answers);
        assessmentDetailsService.save(details);
        return "redirect:/assessment/" + id;
    }

    private static MaturityAnswer findClosestMaturityAnswer(com.govinc.maturity.MaturityModel maturityModel,
            int percent) {
        if (maturityModel == null || maturityModel.getMaturityAnswers() == null
                || maturityModel.getMaturityAnswers().isEmpty()) {
            throw new IllegalArgumentException("No maturity answers provided");
        }
        List<MaturityAnswer> answers = new ArrayList<>(maturityModel.getMaturityAnswers());
        MaturityAnswer closest = answers.get(0);
        int minDiff = Math.abs(closest.getRating() - percent);
        for (MaturityAnswer ans : answers) {
            int diff = Math.abs(ans.getRating() - percent);
            if (diff < minDiff) {
                minDiff = diff;
                closest = ans;
            }
        }
        return closest;
    }

    private static class OrgServiceInheritance {
        private final MaturityAnswer answer;
        private final int percent;
        private final String orgServiceName;
        private final String comment;

        private OrgServiceInheritance(MaturityAnswer answer, int percent, String orgServiceName, String comment) {
            this.answer = answer;
            this.percent = percent;
            this.orgServiceName = orgServiceName;
            this.comment = comment;
        }
    }

    private Map<Long, OrgServiceInheritance> collectOrgServiceInheritance(Assessment assessment) {
        Map<Long, OrgServiceInheritance> inheritanceByControl = new HashMap<>();
        if (assessment == null || assessment.getOrgServices() == null || assessment.getOrgServices().isEmpty()) {
            return inheritanceByControl;
        }
        if (assessment.getSecurityCatalog() == null || assessment.getSecurityCatalog().getMaturityModel() == null) {
            return inheritanceByControl;
        }

        List<Long> orgServiceIds = assessment.getOrgServices().stream()
                .map(OrgService::getId)
                .filter(Objects::nonNull)
                .toList();
        if (orgServiceIds.isEmpty()) {
            return inheritanceByControl;
        }

        List<OrgServiceAssessment> allAssessments = orgServiceAssessmentRepository.findByOrgServiceIdIn(orgServiceIds);
        for (OrgServiceAssessment osa : allAssessments) {
            if (osa.getControls() == null || osa.getControls().isEmpty()) {
                continue;
            }
            String orgServiceName = osa.getOrgService() != null ? osa.getOrgService().getName() : null;
            for (OrgServiceAssessmentControl osac : osa.getControls()) {
                if (!osac.isApplicable() || osac.getPercent() < 0 || osac.getSecurityControl() == null
                        || osac.getSecurityControl().getId() == null) {
                    continue;
                }
                Long ctrlId = osac.getSecurityControl().getId();
                if (inheritanceByControl.containsKey(ctrlId)) {
                    continue;
                }
                MaturityAnswer closest = findClosestMaturityAnswer(
                        assessment.getSecurityCatalog().getMaturityModel(),
                        osac.getPercent());
                if (closest == null) {
                    continue;
                }
                inheritanceByControl.put(ctrlId,
                        new OrgServiceInheritance(closest, osac.getPercent(), orgServiceName, osac.getComment()));
            }
        }
        return inheritanceByControl;
    }

    @GetMapping("/{id}")
    public String getAssessmentById(@PathVariable Long id, Model model) {
        // Authorization check
        if (!authorizationService.canAccessAssessment(id)) {
            throw new UnauthorizedException("You do not have permission to access this assessment.");
        }
        Optional<Assessment> assessmentOpt = assessmentRepository.findById(id);
        if (assessmentOpt.isPresent()) {
            Assessment assessment = assessmentOpt.get();
            model.addAttribute("assessment", assessment);

            // All security controls from the assessment's security catalog
            List<SecurityControl> controls = new ArrayList<>();
            if (assessment.getSecurityCatalog() != null) {
                controls.addAll(assessment.getSecurityCatalog().getSecurityControls());
                controls.sort(Comparator.comparing(SecurityControl::getName, Comparator.nullsLast(String::compareTo)));
            }
            model.addAttribute("controls", controls);
            // Also prepare controls sorted by 'reference' for template grouping
            List<SecurityControl> controlsByReference = new ArrayList<>(controls);
            controlsByReference.sort(Comparator.comparing(SecurityControl::getReference, Comparator.nullsLast(String::compareTo)));
            model.addAttribute("controlsByReference", controlsByReference);
            System.out.println("getAssessmentById, controls: " + controls.size());

            // Prepare maturity answers/answers by percent
            List<MaturityAnswer> maturityAnswers = new ArrayList<>();
            Map<Integer, String> percentToAnswer = new HashMap<>();
            if (assessment.getSecurityCatalog() != null && assessment.getSecurityCatalog().getMaturityModel() != null) {
                System.out.println(" maturity answers (1):  " + assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers().size());
                maturityAnswers.addAll(assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers());
                maturityAnswers
                        .sort(Comparator.comparing(MaturityAnswer::getAnswer, Comparator.nullsLast(String::compareTo)));
                for (MaturityAnswer ma : maturityAnswers) {
                    percentToAnswer.put(ma.getRating(), ma.getAnswer());
                }
            }
            model.addAttribute("maturityAnswers", maturityAnswers);

            // Retrieve existing details/answers
            Optional<AssessmentDetails> detailsOpt = assessmentDetailsService.findById(id);
            AssessmentDetails details = detailsOpt.orElse(null);
            Map<Long, AssessmentControlAnswer> localControlAnswers = new HashMap<>();
            if (details != null && details.getControlAnswers() != null) {
                for (AssessmentControlAnswer aca : details.getControlAnswers()) {
                    if (aca.getSecurityControl() != null) {
                        localControlAnswers.put(aca.getSecurityControl().getId(), aca);
                    }
                }
            }

            // Prepare output maps
            Map<Long, String> controlAnswers = new HashMap<>();
            Map<Long, Boolean> controlAnswerIsTakenOver = new HashMap<>();
            Map<Long, String> controlTakenOverOrgServiceName = new HashMap<>();
            Map<Long, Boolean> controlAnswerIsOverridden = new HashMap<>();
            List<AssessmentControlAnswer> answers = new ArrayList<>();

            Map<Long, OrgServiceInheritance> inheritedByControl = collectOrgServiceInheritance(assessment);

            // Main logic: iterate all controls, set answer with orgService inherited
            // PRECEDENCE
            boolean answersPersisted = false;
            Set<AssessmentControlAnswer> detailsAnswers = (details != null && details.getControlAnswers() != null)
                    ? new HashSet<>(details.getControlAnswers())
                    : new HashSet<>();

            // Gather comments as well - including org service comments
            Map<Long, String> controlComments = new HashMap<>();
            Map<Long, String> orgServiceControlComments = new HashMap<>();

            for (Map.Entry<Long, OrgServiceInheritance> entry : inheritedByControl.entrySet()) {
                String comment = entry.getValue().comment;
                if (comment != null && !comment.isEmpty()) {
                    orgServiceControlComments.put(entry.getKey(), comment);
                }
            }

            for (SecurityControl control : controls) {
                Long ctrlId = control.getId();
                
                // Check if there's an override for this control
                boolean hasOverride = localControlAnswers.containsKey(ctrlId) && localControlAnswers.get(ctrlId).getIsOverride();
                
                if (hasOverride) {
                    // User has overridden the org service answer
                    AssessmentControlAnswer aca = localControlAnswers.get(ctrlId);
                    if (aca.getMaturityAnswer() != null) {
                        controlAnswers.put(ctrlId, aca.getMaturityAnswer().getAnswer());
                    }
                    controlAnswerIsTakenOver.put(ctrlId, Boolean.FALSE); // Not taken over, overridden
                    controlAnswerIsOverridden.put(ctrlId, Boolean.TRUE);
                    if (aca.getComment() != null) {
                        controlComments.put(ctrlId, aca.getComment());
                    }
                    // If this control has an org service answer that was overridden, keep the org service name visible
                    if (inheritedByControl.containsKey(ctrlId)) {
                        controlTakenOverOrgServiceName.put(ctrlId, inheritedByControl.get(ctrlId).orgServiceName);
                    }
                } else if (inheritedByControl.containsKey(ctrlId)) {
                    OrgServiceInheritance inh = inheritedByControl.get(ctrlId);
                    controlAnswers.put(ctrlId, inh.answer.getAnswer());
                    controlAnswerIsTakenOver.put(ctrlId, Boolean.TRUE);
                    controlTakenOverOrgServiceName.put(ctrlId, inh.orgServiceName);
                    controlAnswerIsOverridden.put(ctrlId, Boolean.FALSE);

                    if (details != null) {
                        AssessmentControlAnswer existing = localControlAnswers.get(ctrlId);
                        if (existing != null) {
                            Long existingAnswerId = existing.getMaturityAnswer() != null
                                    ? existing.getMaturityAnswer().getId()
                                    : null;
                            Long inheritedAnswerId = inh.answer.getId();
                            if (!Objects.equals(existingAnswerId, inheritedAnswerId)) {
                                existing.setMaturityAnswer(inh.answer);
                                detailsAnswers.add(existing);
                                answersPersisted = true;
                            }
                        } else {
                            AssessmentControlAnswer aca = new AssessmentControlAnswer(control, inh.answer);
                            aca = assessmentControlAnswerRepository.save(aca);
                            detailsAnswers.add(aca);
                            answersPersisted = true;
                            localControlAnswers.put(ctrlId, aca);
                        }
                    }
                    // If inherited, try to fetch the comment from local (user) answer if exists, else use org service comment
                    if (localControlAnswers.containsKey(ctrlId)) {
                        String comment = localControlAnswers.get(ctrlId).getComment();
                        if (comment != null && !comment.isEmpty()) {
                            controlComments.put(ctrlId, comment);
                        } else if (orgServiceControlComments.containsKey(ctrlId)) {
                            controlComments.put(ctrlId, orgServiceControlComments.get(ctrlId));
                        }
                    } else if (orgServiceControlComments.containsKey(ctrlId)) {
                        controlComments.put(ctrlId, orgServiceControlComments.get(ctrlId));
                    }
                } else if (localControlAnswers.containsKey(ctrlId)) {
                    AssessmentControlAnswer aca = localControlAnswers.get(ctrlId);
                    if (aca.getMaturityAnswer() != null) {
                        controlAnswers.put(ctrlId, aca.getMaturityAnswer().getAnswer());
                    } else {
                        controlAnswers.put(ctrlId, null);
                    }
                    controlAnswerIsTakenOver.put(ctrlId, Boolean.FALSE);
                    controlAnswerIsOverridden.put(ctrlId, Boolean.FALSE);
                    // Populate comment
                    if (aca.getComment() != null) {
                        controlComments.put(ctrlId, aca.getComment());
                    }
                } else {
                    controlAnswers.put(ctrlId, null);
                    controlAnswerIsTakenOver.put(ctrlId, Boolean.FALSE);
                    controlAnswerIsOverridden.put(ctrlId, Boolean.FALSE);
                }
            }
            
            // Save auto-assigned inherited answers if any were added
            if (answersPersisted && details != null) {
                Set<AssessmentControlAnswer> mergedAnswers = new HashSet<>(details.getControlAnswers());
                for (AssessmentControlAnswer newAca : detailsAnswers) {
                    boolean found = false;
                    for (AssessmentControlAnswer existingAca : mergedAnswers) {
                        if (existingAca.getSecurityControl() != null && newAca.getSecurityControl() != null &&
                            existingAca.getSecurityControl().getId().equals(newAca.getSecurityControl().getId())) {
                            // Same control: update maturity answer if changed
                            if (existingAca.getMaturityAnswer() != null && newAca.getMaturityAnswer() != null &&
                                !existingAca.getMaturityAnswer().getId().equals(newAca.getMaturityAnswer().getId())) {
                                existingAca.setMaturityAnswer(newAca.getMaturityAnswer());
                            } else if (existingAca.getMaturityAnswer() == null && newAca.getMaturityAnswer() != null) {
                                existingAca.setMaturityAnswer(newAca.getMaturityAnswer());
                            }
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        mergedAnswers.add(newAca);
                    }
                }
                details.setControlAnswers(mergedAnswers);
                assessmentDetailsService.save(details);
            }
            
            // For backward compatibility in template, still give list of "answers" from
            // local answers only
            answers.addAll(localControlAnswers.values());

            model.addAttribute("answers", answers);
            model.addAttribute("controlAnswers", controlAnswers);
            model.addAttribute("controlComments", controlComments);
            model.addAttribute("controlAnswerIsTakenOver", controlAnswerIsTakenOver);
            model.addAttribute("controlTakenOverOrgServiceName", controlTakenOverOrgServiceName);
            model.addAttribute("controlAnswerIsOverridden", controlAnswerIsOverridden);
            model.addAttribute("orgServiceControlComments", orgServiceControlComments);

                // Summary table by answer type (catalog-scoped)
                Set<Long> catalogMaturityAnswerIds = maturityAnswers.stream()
                    .map(MaturityAnswer::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
                model.addAttribute("answerSummary",
                    assessmentDetailsService.computeAnswerSummary(details, catalogMaturityAnswerIds));

            // --- Pass securityControlDomains: all unique domains of controls in this
            // catalog ---
            List<SecurityControlDomain> securityControlDomains = controls.stream()
                    .map(SecurityControl::getSecurityControlDomain)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            model.addAttribute("securityControlDomains", securityControlDomains);
            // Also pass orgServices for details view
            model.addAttribute("orgServices", assessment.getOrgServices());
            // Pass details object to template for completedDate access
            model.addAttribute("details", details);
                model.addAttribute("creationDateDisplay", formatFriendlyDate(assessment.getCreationDate()));
                model.addAttribute("closeDateDisplay", formatFriendlyDate(assessment.getCloseDate()));
                model.addAttribute("completedDateDisplay",
                    details != null ? formatFriendlyDate(details.getCompletedDate()) : "");

            // Pass authorization info for UI restrictions
            Role currentRole = authorizationService.getCurrentUserRole();
            boolean isAdminOrISM = authorizationService.isAdmin() || authorizationService.isInformationSecurityManager();
            boolean canManageAssessors = currentRole == Role.ASSESSMENT_DELEGATE && assessment.isOpen();
            model.addAttribute("isAdminOrISM", isAdminOrISM);
            model.addAttribute("isAssessor", currentRole == Role.ASSESSOR);
            model.addAttribute("canManageAssessors", canManageAssessors);

            // Pass governance projects for task creation and all users for interviewee modal
            model.addAttribute("governanceProjects", governanceProjectRepository.findAll());
            model.addAttribute("allUsers", userRepository.findAll());

            // Compliance check score calculation
            if (assessment.getComplianceCheck() != null && details != null) {
                ComplianceCheck cc = assessment.getComplianceCheck();
                Set<Long> catalogControlIds = new java.util.LinkedHashSet<>();
                if (assessment.getSecurityCatalog() != null && assessment.getSecurityCatalog().getSecurityControls() != null) {
                    for (SecurityControl sc : assessment.getSecurityCatalog().getSecurityControls()) {
                        catalogControlIds.add(sc.getId());
                    }
                }
                List<AssessmentControlAnswer> answerList = new ArrayList<>();
                int answered = 0;
                double scoreSum = 0;
                int scoreCount = 0;
                if (details.getControlAnswers() != null) {
                    for (AssessmentControlAnswer aca : details.getControlAnswers()) {
                        if (aca.getSecurityControl() != null && catalogControlIds.contains(aca.getSecurityControl().getId())) {
                            answerList.add(aca);
                            answered++;
                            if (aca.getMaturityAnswer() != null) {
                                scoreSum += aca.getMaturityAnswer().getRating();
                                scoreCount++;
                            }
                        }
                    }
                }
                double avgScore = scoreCount > 0 ? scoreSum / scoreCount : 0.0;
                double coverage = catalogControlIds.isEmpty() ? 0.0 : (answered * 100.0 / catalogControlIds.size());
                // Evaluate thresholds
                boolean compliant = !answerList.isEmpty();
                List<Map<String, Object>> thresholdResults = new ArrayList<>();
                if (cc.getThresholds() != null) {
                    for (ComplianceThreshold t : cc.getThresholds()) {
                        boolean passed = false;
                        if (!answerList.isEmpty()) {
                            if ("ALL_ABOVE".equals(t.getType())) {
                                passed = answerList.stream()
                                        .allMatch(a2 -> a2.getMaturityAnswer() != null && a2.getMaturityAnswer().getRating() >= t.getValue());
                            } else if ("AVERAGE_ABOVE".equals(t.getType())) {
                                passed = avgScore >= t.getValue();
                            }
                        }
                        if (!passed) compliant = false;
                        Map<String, Object> td = new LinkedHashMap<>();
                        td.put("description", t.getRuleDescription());
                        td.put("type", t.getType());
                        td.put("value", t.getValue());
                        td.put("passed", passed);
                        thresholdResults.add(td);
                    }
                }
                model.addAttribute("complianceCheck", cc);
                model.addAttribute("complianceCompliant", compliant);
                model.addAttribute("complianceAvgScore", Math.round(avgScore * 10.0) / 10.0);
                model.addAttribute("complianceCoverage", Math.round(coverage * 10.0) / 10.0);
                model.addAttribute("complianceThresholdResults", thresholdResults);
            }

            return "assessment-details";
        } else {
            return "assessment-not-found";
        }
    }

    static class OrgServiceInfo {
        final MaturityAnswer answer;
        final String orgServiceName;

        public OrgServiceInfo(MaturityAnswer a, String orgServiceName) {
            this.answer = a;
            this.orgServiceName = orgServiceName;
        }

    }

    private String formatFriendlyDate(LocalDate date) {
        return date != null ? date.format(FRIENDLY_DATE_FORMATTER) : "";
    }

    // Save/update answer for a single control (AJAX POST from UI)
    @PostMapping("/{id}/answer")
    @ResponseBody
    public String saveAnswer(@PathVariable Long id, @RequestParam Long controlId, @RequestParam Long answerId,
                             @RequestParam(required = false) Boolean isOverride) {
        // Authorization check
        if (!authorizationService.canAnswerAssessment(id)) {
            return "forbidden";
        }
        Optional<AssessmentDetails> detailsOpt = assessmentDetailsService.findById(id);
        AssessmentDetails details = null;
        if (!detailsOpt.isPresent()) {
            // Try to find the assessment:
            Optional<Assessment> assessmentOpt = assessmentRepository.findById(id);
            if (!assessmentOpt.isPresent())
                return "fail";
            details = new AssessmentDetails();
            // Link this details entity to the assessment
            Set<Assessment> assessmentSet = new HashSet<>();
            assessmentSet.add(assessmentOpt.get());
            details.setAssessments(assessmentSet);
            details.setDate(LocalDate.now());
        } else {
            details = detailsOpt.get();
        }
        Set<AssessmentControlAnswer> answers = details.getControlAnswers();
        // Find or add
        AssessmentControlAnswer found = null;
        for (AssessmentControlAnswer aca : answers) {
            if (aca.getSecurityControl() != null && aca.getSecurityControl().getId().equals(controlId)) {
                found = aca;
                break;
            }
        }
        SecurityControl control = securityControlRepository.findById(controlId).orElse(null);
        MaturityAnswer maturityAnswer = maturityAnswerRepository.findById(answerId).orElse(null);
        if (control == null || maturityAnswer == null)
            return "fail";

        if (found == null) {
            found = new AssessmentControlAnswer(control, maturityAnswer);
            // If an explicit isOverride flag was provided, honor it
            if (Boolean.TRUE.equals(isOverride)) {
                found.setIsOverride(true);
            }
            found = assessmentControlAnswerRepository.save(found);
            answers.add(found);
        } else {
            found.setMaturityAnswer(maturityAnswer);
            // Only update override flag if caller provided it, otherwise keep existing
            if (isOverride != null) {
                found.setIsOverride(Boolean.TRUE.equals(isOverride));
            }
            found = assessmentControlAnswerRepository.save(found);
        }
        // Only update the modified/new answer, do NOT replace the set with only one
        // answer
        assessmentDetailsService.save(details);
        return "ok";
    }

    // Save/update comment for a single control (AJAX PUT from UI)
    @PutMapping("/{assessmentId}/control/{controlId}/comment")
    @ResponseBody
    public String saveComment(@PathVariable Long assessmentId, @PathVariable Long controlId, @RequestBody Map<String, String> body) {
        // Authorization check
        if (!authorizationService.canAnswerAssessment(assessmentId)) {
            return "forbidden";
        }
        String comment = body.get("comment");
        Optional<AssessmentDetails> detailsOpt = assessmentDetailsService.findById(assessmentId);
        AssessmentDetails details = null;
        if (!detailsOpt.isPresent()) {
            // Try to find the assessment:
            Optional<Assessment> assessmentOpt = assessmentRepository.findById(assessmentId);
            if (!assessmentOpt.isPresent())
                return "fail";
            details = new AssessmentDetails();
            Set<Assessment> assessmentSet = new HashSet<>();
            assessmentSet.add(assessmentOpt.get());
            details.setAssessments(assessmentSet);
            details.setDate(LocalDate.now());
        } else {
            details = detailsOpt.get();
        }
        Set<AssessmentControlAnswer> answers = details.getControlAnswers();
        // Find or add
        AssessmentControlAnswer found = null;
        for (AssessmentControlAnswer aca : answers) {
            if (aca.getSecurityControl() != null && aca.getSecurityControl().getId().equals(controlId)) {
                found = aca;
                break;
            }
        }
        SecurityControl control = securityControlRepository.findById(controlId).orElse(null);
        if (control == null)
            return "fail";
        if (found == null) {
            // A comment with no answer: create a dummy with null maturity answer
            found = new AssessmentControlAnswer(control, null, comment);
            found = assessmentControlAnswerRepository.save(found);
            answers.add(found);
        } else {
            found.setComment(comment);
            found = assessmentControlAnswerRepository.save(found);
        }
        assessmentDetailsService.save(details);
        return "ok";
    }

    // Finalize assessment (POST) - Only ADMIN and INFORMATION_SECURITY_MANAGER can finalize
    @PostMapping("/{id}/finalize")
    public String finalizeAssessment(@PathVariable Long id) {
        // Only ADMIN and INFORMATION_SECURITY_MANAGER can finalize
        boolean isAdmin = authorizationService.isAdmin();
        boolean isISM = authorizationService.isInformationSecurityManager();
        if (!isAdmin && !isISM) {
            throw new UnauthorizedException("You do not have permission to finalize assessments.");
        }
        
        Optional<Assessment> assessmentOpt = assessmentRepository.findById(id);
        if (assessmentOpt.isPresent()) {
            Assessment assessment = assessmentOpt.get();
            // Adhere to existing DB values: use CLOSED to indicate finalized
            assessment.setStatus(AssessmentStatus.CLOSED);
            assessment.setCloseDate(LocalDate.now());
            assessmentRepository.save(assessment);
            }

            Optional<AssessmentDetails> detailsOpt = assessmentDetailsService.findById(id);
            if (detailsOpt.isPresent()) {
            AssessmentDetails details = detailsOpt.get();
            details.setCompletedDate(LocalDate.now());
            assessmentDetailsService.save(details);
            }
        
        return "redirect:/assessment/" + id;
    }

    // Re-open assessment (POST) - Only ADMIN and INFORMATION_SECURITY_MANAGER can re-open
    @PostMapping("/{id}/reopen")
    @ResponseBody
    public ResponseEntity<Void> reopenAssessment(@PathVariable Long id) {
        boolean isAdmin = authorizationService.isAdmin();
        boolean isISM = authorizationService.isInformationSecurityManager();
        if (!isAdmin && !isISM) {
            throw new UnauthorizedException("You do not have permission to re-open assessments.");
        }

        Optional<Assessment> assessmentOpt = assessmentRepository.findById(id);
        if (assessmentOpt.isPresent()) {
            Assessment assessment = assessmentOpt.get();
            assessment.setStatus(AssessmentStatus.OPEN);
            assessment.setCloseDate(null);
            assessmentRepository.save(assessment);
        }
        return ResponseEntity.ok().build();
    }

    // Delete assessment (POST)
    @PostMapping("/{id}/delete")
    public String deleteAssessment(@PathVariable Long id) {
        // Authorization check: only ADMIN and ISM can delete
        if (!authorizationService.canDeleteAssessment(id)) {
            throw new UnauthorizedException("You do not have permission to delete assessments.");
        }
        // Remove assessment reference from all AssessmentDetails entities before
        // deleting
        Assessment assessment = assessmentRepository.findById(id).orElse(null);
        if (assessment != null) {
            List<AssessmentDetails> detailsList = assessmentDetailsService.findAll();
            for (AssessmentDetails details : detailsList) {
                if (details.getAssessments().contains(assessment)) {
                    details.getAssessments().remove(assessment);
                    assessmentDetailsService.save(details);
                }
            }
            assessmentRepository.delete(assessment);
        }
        return "redirect:/assessment/list";
    }

    @GetMapping("/{id}/word-report-progress")
    @ResponseBody
    public Map<String, Object> getWordReportProgress(@PathVariable Long id) {
        if (authorizationService.isAssessor()) {
            throw new UnauthorizedException("Assessors are not allowed to generate reports.");
        }
        ReportProgress progress = assessmentReporterWord.getProgress(id);
        Map<String, Object> response = new HashMap<>();
        response.put("percent", progress.getPercent());
        response.put("status", progress.getStatus());
        return response;
    }

    @GetMapping("/{id}/word-report")
    public ResponseEntity<byte[]> downloadWordReport(@PathVariable Long id) {
        // Authorization check
        if (!authorizationService.canAccessAssessment(id) || authorizationService.isAssessor()) {
            throw new UnauthorizedException("You do not have permission to download this assessment report.");
        }
        
        Optional<Assessment> assessmentOpt = assessmentRepository.findById(id);
        Optional<AssessmentDetails> detailsOpt = assessmentDetailsService.findById(id);
        if (assessmentOpt.isEmpty() || detailsOpt.isEmpty()) {
            
            return ResponseEntity.notFound().build();
        }
        Assessment assessment = assessmentOpt.get();
        AssessmentDetails details = detailsOpt.get();
        List<User> users = assessment.getUsers() != null ? new ArrayList<>(assessment.getUsers()) : new ArrayList<>();
        OrgUnit orgUnit = assessment.getOrgUnit();
        List<AssessmentControlAnswer> answers = (details.getControlAnswers() != null)
                ? new ArrayList<>(details.getControlAnswers())
                : new ArrayList<>();
        
        // Retrieve template path from OrganisationDetails if available
        String templatePath = null;
        try {
            com.govinc.entity.OrganisationDetails orgDetails = organisationDetailsRepository.findAll().stream().findFirst().orElse(null);
            if (orgDetails != null && orgDetails.getWordTemplatePath() != null && !orgDetails.getWordTemplatePath().isEmpty()) {
                java.io.File templateFile = new java.io.File(orgDetails.getWordTemplatePath());
                if (templateFile.exists()) {
                    templatePath = orgDetails.getWordTemplatePath();
                    System.out.println("[AssessmentController] Using template from OrganisationDetails: " + templatePath);
                } else {
                    System.out.println("[AssessmentController] Template file not found at: " + orgDetails.getWordTemplatePath());
                }
            }
        } catch (Exception e) {
            System.err.println("[AssessmentController] Error retrieving template path: " + e.getMessage());
        }
        
        try {
            byte[] wordBytes = assessmentReporterWord.createWordReport(assessment, details, users, orgUnit, answers, templatePath);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=assessment_" + id + ".docx")
                    .contentType(MediaType
                            .parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(wordBytes);
        } catch (Exception e) {
            e.printStackTrace();
            byte[] failBytes = ("Error creating Word document: " + e.getMessage()).getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(failBytes);
        }
    }

    // Download PDF using iText (via AssessmentReporter)
    @GetMapping("/{id}/report")
    public ResponseEntity<byte[]> downloadReport(@PathVariable Long id) {
        // Authorization check
        if (!authorizationService.canAccessAssessment(id) || authorizationService.isAssessor()) {
            throw new UnauthorizedException("You do not have permission to download this assessment report.");
        }
        Optional<Assessment> assessmentOpt = assessmentRepository.findById(id);
        Optional<AssessmentDetails> detailsOpt = assessmentDetailsService.findById(id);
        if (assessmentOpt.isEmpty() || detailsOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Assessment assessment = assessmentOpt.get();
        AssessmentDetails details = detailsOpt.get();
        List<User> users = assessment.getUsers() != null ? new ArrayList<>(assessment.getUsers()) : new ArrayList<>();
        OrgUnit orgUnit = assessment.getOrgUnit();
        List<AssessmentControlAnswer> answers = (details.getControlAnswers() != null)
                ? new ArrayList<>(details.getControlAnswers())
                : new ArrayList<>();
        try {
            byte[] pdfBytes = assessmentReporter.createPdfReport(assessment, details, users, orgUnit, answers);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=assessment_" + id + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfBytes);
        } catch (Exception e) {
            byte[] failBytes = ("Error creating PDF: " + e.getMessage()).getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(failBytes);
        }
    }

    // Download Excel - proper XLSX with control details, answers, and comments
    @GetMapping("/{id}/excel")
    public ResponseEntity<byte[]> downloadExcel(@PathVariable Long id) throws IOException {
        if (!authorizationService.canAccessAssessment(id) || authorizationService.isAssessor()) {
            throw new UnauthorizedException("You do not have permission to download this assessment data.");
        }
        Optional<Assessment> assessmentOpt = assessmentRepository.findById(id);
        if (!assessmentOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        Assessment assessment = assessmentOpt.get();

        // Catalog controls sorted by domain then reference
        List<SecurityControl> controls = new ArrayList<>();
        if (assessment.getSecurityCatalog() != null && assessment.getSecurityCatalog().getSecurityControls() != null) {
            controls.addAll(assessment.getSecurityCatalog().getSecurityControls());
        }
        controls.sort(Comparator
            .comparing((SecurityControl sc) -> sc.getSecurityControlDomain() != null ? sc.getSecurityControlDomain().getName() : "",
                Comparator.nullsLast(String::compareTo))
            .thenComparing(sc -> sc.getReference() != null ? sc.getReference() : "",
                Comparator.nullsLast(String::compareTo)));

        // Local answers map: controlId → AssessmentControlAnswer
        Optional<AssessmentDetails> detailsOpt = assessmentDetailsService.findById(id);
        Map<Long, AssessmentControlAnswer> localAnswers = new HashMap<>();
        if (detailsOpt.isPresent() && detailsOpt.get().getControlAnswers() != null) {
            for (AssessmentControlAnswer aca : detailsOpt.get().getControlAnswers()) {
                if (aca.getSecurityControl() != null) {
                    localAnswers.put(aca.getSecurityControl().getId(), aca);
                }
            }
        }

        // Inherited answers from org services (same logic as assessment view)
        Map<Long, OrgServiceInheritance> inherited = collectOrgServiceInheritance(assessment);

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // ---- Styles ----
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

            // ---- Meta info rows ----
            int rowIdx = 0;
            String[][] metaRows = {
                { "Assessment",   assessment.getName() != null ? assessment.getName() : "" },
                { "Org Unit",     assessment.getOrgUnit() != null ? assessment.getOrgUnit().getName() : "-" },
                { "Catalog",      assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : "-" },
                { "Status",       assessment.getStatus() != null ? assessment.getStatus().toString() : "-" },
                { "Creation Date", assessment.getCreationDate() != null ? assessment.getCreationDate().toString() : "-" },
                { "Export Date",   LocalDate.now().toString() }
            };
            for (String[] meta : metaRows) {
                Row r = sheet.createRow(rowIdx++);
                Cell c0 = r.createCell(0);
                c0.setCellValue(meta[0]);
                c0.setCellStyle(metaLabelStyle);
                r.createCell(1).setCellValue(meta[1]);
            }
            rowIdx++; // blank separator

            // ---- Header row ----
            String[] headers = { "Domain", "Reference", "Control Name", "Answer", "Score (%)", "Source", "Comment" };
            Row headerRow = sheet.createRow(rowIdx++);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // ---- Data rows ----
            for (SecurityControl sc : controls) {
                Long ctrlId = sc.getId();
                String answerText = "";
                int score = 0;
                String source = "Not answered";
                String comment = "";

                boolean hasOverride = localAnswers.containsKey(ctrlId)
                    && Boolean.TRUE.equals(localAnswers.get(ctrlId).getIsOverride());

                if (hasOverride) {
                    AssessmentControlAnswer aca = localAnswers.get(ctrlId);
                    if (aca.getMaturityAnswer() != null) {
                        answerText = aca.getMaturityAnswer().getAnswer() != null ? aca.getMaturityAnswer().getAnswer() : "";
                        score = aca.getMaturityAnswer().getRating();
                    }
                    String orgSvcName = inherited.containsKey(ctrlId) ? inherited.get(ctrlId).orgServiceName : null;
                    source = "Override" + (orgSvcName != null ? " (overriding: " + orgSvcName + ")" : "");
                    if (aca.getComment() != null) comment = aca.getComment();

                } else if (inherited.containsKey(ctrlId)) {
                    OrgServiceInheritance inh = inherited.get(ctrlId);
                    if (inh.answer != null) {
                        answerText = inh.answer.getAnswer() != null ? inh.answer.getAnswer() : "";
                        score = inh.percent;
                    }
                    source = "Inherited from: " + (inh.orgServiceName != null ? inh.orgServiceName : "Org Service");
                    // Prefer local comment, fall back to org service comment
                    AssessmentControlAnswer local = localAnswers.get(ctrlId);
                    if (local != null && local.getComment() != null && !local.getComment().isEmpty()) {
                        comment = local.getComment();
                    } else if (inh.comment != null) {
                        comment = inh.comment;
                    }

                } else if (localAnswers.containsKey(ctrlId)) {
                    AssessmentControlAnswer aca = localAnswers.get(ctrlId);
                    if (aca.getMaturityAnswer() != null) {
                        answerText = aca.getMaturityAnswer().getAnswer() != null ? aca.getMaturityAnswer().getAnswer() : "";
                        score = aca.getMaturityAnswer().getRating();
                    }
                    source = "Direct";
                    if (aca.getComment() != null) comment = aca.getComment();
                }

                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(sc.getSecurityControlDomain() != null ? sc.getSecurityControlDomain().getName() : "");
                row.createCell(1).setCellValue(sc.getReference() != null ? sc.getReference() : "");
                row.createCell(2).setCellValue(sc.getName() != null ? sc.getName() : "");
                row.createCell(3).setCellValue(answerText);
                if (!answerText.isEmpty()) {
                    row.createCell(4).setCellValue(score);
                } else {
                    row.createCell(4).setCellValue("");
                }
                row.createCell(5).setCellValue(source);
                Cell commentCell = row.createCell(6);
                commentCell.setCellValue(comment);
                if (!comment.isEmpty()) commentCell.setCellStyle(wrapStyle);
            }

            // Column widths
            sheet.setColumnWidth(0, 6000);   // Domain
            sheet.setColumnWidth(1, 3500);   // Reference
            sheet.setColumnWidth(2, 12000);  // Control Name
            sheet.setColumnWidth(3, 6000);   // Answer
            sheet.setColumnWidth(4, 3000);   // Score
            sheet.setColumnWidth(5, 8000);   // Source
            sheet.setColumnWidth(6, 14000);  // Comment

            // Freeze panes above data rows
            int dataStartRow = metaRows.length + 2; // meta + blank separator + header
            sheet.createFreezePane(0, dataStartRow);

            wb.write(out);
            byte[] excelBytes = out.toByteArray();
            String filename = "assessment_" + id + ".xlsx";
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
        }
    }

    // --- Create direct URL for assessment ---
    @PostMapping("/{id}/create-url")
    @ResponseBody
    public Map<String, String> createUrl(@PathVariable Long id) {
        if (!authorizationService.canAccessAssessmentUrls()) {
            throw new UnauthorizedException("You do not have permission to create assessment URLs.");
        }
        AssessmentUrls url = assessmentUrlsService.createOrReplaceUrl(id);
        String fullUrl = "/assessment-direct/" + url.getUrl();
        Assessment updated = assessmentRepository.findById(id).orElse(null);
        String expiry = "";
        if (updated != null && updated.getUrlExpirationDate() != null) {
            expiry = updated.getUrlExpirationDate().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy"));
        }
        Map<String, String> result = new java.util.HashMap<>();
        result.put("directUrl", fullUrl);
        result.put("expirationDate", expiry);
        return result;
    }

    // --- Set OrgUnit for Assessment ---
    @PostMapping("/{id}/set-orgunit")
    public String setOrgUnitForAssessment(@PathVariable Long id,
            @RequestParam(value = "orgUnitId", required = false) Long orgUnitId) {
        if (!authorizationService.isInformationSecurityManager()) {
            throw new UnauthorizedException("You do not have permission to set an organization unit for this assessment.");
        }
        Optional<Assessment> assessmentOpt = assessmentRepository.findById(id);
        if (assessmentOpt.isPresent() && orgUnitId != null) {
            OrgUnit orgUnit = orgUnitService.getOrgUnit(orgUnitId).orElse(null);
            if (orgUnit != null) {
                Assessment assessment = assessmentOpt.get();
                assessment.setOrgUnit(orgUnit);
                assessmentRepository.save(assessment);
            }
        }
        return "redirect:/assessment/" + id;
    }

    // --- Assign Users to Assessment via API ---
    @PutMapping("/{id}/users")
    @ResponseBody
    public List<User> updateAssessmentUsers(@PathVariable Long id, @RequestBody List<Long> userIds) {
        if (!authorizationService.canModifyAssessment(id)) {
            throw new UnauthorizedException("You do not have permission to update assessment users.");
        }
        Optional<Assessment> opt = assessmentRepository.findById(id);
        if (opt.isEmpty())
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND);
        Assessment assessment = opt.get();
        Set<User> users = userIds.stream()
                .map(uid -> userRepository.findById(uid).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        assessment.setUsers(users);
        assessment = assessmentRepository.save(assessment);
        return new ArrayList<>(users);
    }

    // --- Assign Interviewees to Assessment via API ---
    @PutMapping("/{id}/interviewees")
    @ResponseBody
    public List<Map<String, Object>> updateAssessmentInterviewees(@PathVariable Long id, @RequestBody List<Long> userIds) {
        if (!authorizationService.canModifyAssessment(id)) {
            throw new UnauthorizedException("You do not have permission to update assessment interviewees.");
        }
        Optional<Assessment> opt = assessmentRepository.findById(id);
        if (opt.isEmpty())
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND);
        Assessment assessment = opt.get();
        Set<User> interviewees = userIds.stream()
                .map(uid -> userRepository.findById(uid).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        assessment.setInterviewees(interviewees);
        assessmentRepository.save(assessment);
        return interviewees.stream()
                .map(u -> { Map<String, Object> m = new java.util.LinkedHashMap<>(); m.put("id", u.getId()); m.put("name", u.getName()); return m; })
                .collect(Collectors.toList());
    }

    // Delegate workflow: fetch only assessor users for assignment modal
    @GetMapping("/{id}/assessors")
    @ResponseBody
    public List<User> getAssessorsForAssessment(@PathVariable Long id) {
        if (!authorizationService.canAccessAssessment(id)) {
            throw new UnauthorizedException("You do not have permission to access this assessment.");
        }

        Role role = authorizationService.getCurrentUserRole();
        boolean allowed = role == Role.ASSESSMENT_DELEGATE
                || role == Role.ADMIN
                || role == Role.INFORMATION_SECURITY_MANAGER;
        if (!allowed) {
            throw new UnauthorizedException("You do not have permission to view assessors.");
        }

        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ASSESSOR)
                .collect(Collectors.toList());
    }

    // Delegate workflow: add assessor users to an assessment without replacing existing users
    @PutMapping("/{id}/assessors")
    @ResponseBody
    public List<User> addAssessmentAssessors(@PathVariable Long id, @RequestBody List<Long> assessorUserIds) {
        if (!authorizationService.canAccessAssessment(id)) {
            throw new UnauthorizedException("You do not have permission to access this assessment.");
        }

        Role role = authorizationService.getCurrentUserRole();
        boolean delegateOrAdmin = role == Role.ASSESSMENT_DELEGATE
                || role == Role.ADMIN
                || role == Role.INFORMATION_SECURITY_MANAGER;
        if (!delegateOrAdmin) {
            throw new UnauthorizedException("You do not have permission to add assessors to this assessment.");
        }

        Optional<Assessment> opt = assessmentRepository.findById(id);
        if (opt.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND);
        }

        Assessment assessment = opt.get();
        Set<User> updatedUsers = assessment.getUsers() == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(assessment.getUsers());

        for (Long userId : assessorUserIds) {
            User assessor = userRepository.findById(userId).orElse(null);
            if (assessor == null || assessor.getRole() != Role.ASSESSOR) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Only users with the Assessor role can be added.");
            }
            updatedUsers.add(assessor);
        }

        assessment.setUsers(updatedUsers);
        assessment = assessmentRepository.save(assessment);
        return new ArrayList<>(assessment.getUsers());
    }

    // Save answer with override flag for org service answers
    @PostMapping("/{id}/answer-override")
    @ResponseBody
    public String saveAnswerWithOverride(@PathVariable Long id, @RequestParam Long controlId, @RequestParam Long answerId) {
        // Authorization check
        if (!authorizationService.canAnswerAssessment(id)) {
            return "forbidden";
        }
        Optional<AssessmentDetails> detailsOpt = assessmentDetailsService.findById(id);
        AssessmentDetails details = null;
        if (!detailsOpt.isPresent()) {
            // Try to find the assessment:
            Optional<Assessment> assessmentOpt = assessmentRepository.findById(id);
            if (!assessmentOpt.isPresent())
                return "fail";
            details = new AssessmentDetails();
            // Link this details entity to the assessment
            Set<Assessment> assessmentSet = new HashSet<>();
            assessmentSet.add(assessmentOpt.get());
            details.setAssessments(assessmentSet);
            details.setDate(LocalDate.now());
        } else {
            details = detailsOpt.get();
        }
        Set<AssessmentControlAnswer> answers = details.getControlAnswers();
        // Find or add
        AssessmentControlAnswer found = null;
        for (AssessmentControlAnswer aca : answers) {
            if (aca.getSecurityControl() != null && aca.getSecurityControl().getId().equals(controlId)) {
                found = aca;
                break;
            }
        }
        SecurityControl control = securityControlRepository.findById(controlId).orElse(null);
        MaturityAnswer maturityAnswer = maturityAnswerRepository.findById(answerId).orElse(null);
        if (control == null || maturityAnswer == null)
            return "fail";

        if (found == null) {
            found = new AssessmentControlAnswer(control, maturityAnswer);
            found.setIsOverride(true);
            found = assessmentControlAnswerRepository.save(found);
            answers.add(found);
        } else {
            found.setMaturityAnswer(maturityAnswer);
            found.setIsOverride(true);
            found = assessmentControlAnswerRepository.save(found);
        }
        assessmentDetailsService.save(details);
        return "ok";
    }

    // Remove override and revert to org service answer
    @PostMapping("/{id}/control/{controlId}/remove-override")
    @ResponseBody
    public String removeOverride(@PathVariable Long id, @PathVariable Long controlId) {
        // Authorization check
        if (!authorizationService.canAnswerAssessment(id)) {
            return "forbidden";
        }
        Optional<AssessmentDetails> detailsOpt = assessmentDetailsService.findById(id);
        if (!detailsOpt.isPresent()) {
            return "fail";
        }
        AssessmentDetails details = detailsOpt.get();
        Set<AssessmentControlAnswer> answers = details.getControlAnswers();
        // Find and remove the answer that is marked as override
        AssessmentControlAnswer toRemove = null;
        for (AssessmentControlAnswer aca : answers) {
            if (aca.getSecurityControl() != null && aca.getSecurityControl().getId().equals(controlId) && aca.getIsOverride()) {
                toRemove = aca;
                break;
            }
        }
        if (toRemove != null) {
            answers.remove(toRemove);
            assessmentControlAnswerRepository.delete(toRemove);
            assessmentDetailsService.save(details);
            return "ok";
        }
        return "fail";
    }

    // Get control state for UI update without page reload
    @GetMapping("/{id}/control/{controlId}/state")
    @ResponseBody
    public Map<String, Object> getControlState(@PathVariable Long id, @PathVariable Long controlId) {
        // Authorization check
        if (!authorizationService.canAccessAssessment(id)) {
            return Map.of("error", "forbidden");
        }
        
        Optional<Assessment> assessmentOpt = assessmentRepository.findById(id);
        if (assessmentOpt.isEmpty()) {
            return Map.of("error", "not_found");
        }
        
        Assessment assessment = assessmentOpt.get();
        Map<String, Object> state = new HashMap<>();
        
        // Find org service answer for this control with a single pre-aggregated pass
        OrgServiceInheritance inherited = collectOrgServiceInheritance(assessment).get(controlId);
        
        state.put("controlId", controlId);
        if (inherited != null && inherited.orgServiceName != null) {
            state.put("orgServiceName", inherited.orgServiceName);
        }
        if (inherited != null && inherited.answer != null && inherited.answer.getId() != null) {
            state.put("orgServiceAnswerId", inherited.answer.getId());
        }
        if (inherited != null && inherited.comment != null) {
            state.put("orgServiceComment", inherited.comment);
        }
        
        return state;
    }

    // Generate AI management summary for an assessment
    @PostMapping("/{id}/management-summary")
    @ResponseBody
    public Map<String, Object> generateManagementSummary(@PathVariable Long id) {
        if (!authorizationService.canModifyAssessment(id)) {
            return Map.of("error", "forbidden");
        }
        Optional<Assessment> assessmentOpt = assessmentRepository.findById(id);
        if (assessmentOpt.isEmpty()) {
            return Map.of("error", "not_found");
        }
        Assessment assessment = assessmentOpt.get();
        Optional<AssessmentDetails> detailsOpt = assessmentDetailsService.findById(id);
        AssessmentDetails details = detailsOpt.orElse(null);

        // Build a comprehensive prompt for the AI
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a security expert. Generate a concise management summary for the following security assessment.\n\n");
        prompt.append("Assessment Name: ").append(assessment.getName()).append("\n");
        if (assessment.getOrgUnit() != null) {
            prompt.append("Organizational Unit: ").append(assessment.getOrgUnit().getName()).append("\n");
        }
        if (assessment.getSecurityCatalog() != null) {
            prompt.append("Security Catalog: ").append(assessment.getSecurityCatalog().getName()).append("\n");
        }
        prompt.append("Status: ").append(assessment.getStatus()).append("\n");
        prompt.append("Creation Date: ").append(assessment.getCreationDate()).append("\n");

        if (details != null && details.getControlAnswers() != null && !details.getControlAnswers().isEmpty()) {
            prompt.append("\nControl Answers Summary:\n");
            Map<String, Integer> answerCounts = new LinkedHashMap<>();
            int totalAnswered = 0;
            int totalControls = 0;
            double scoreSum = 0;
            int scoreCount = 0;
            for (AssessmentControlAnswer aca : details.getControlAnswers()) {
                totalControls++;
                if (aca.getMaturityAnswer() != null) {
                    totalAnswered++;
                    String ans = aca.getMaturityAnswer().getAnswer();
                    answerCounts.merge(ans, 1, Integer::sum);
                    scoreSum += aca.getMaturityAnswer().getRating();
                    scoreCount++;
                    prompt.append("- ").append(aca.getSecurityControl() != null ? aca.getSecurityControl().getName() : "Unknown")
                          .append(": ").append(ans);
                    if (aca.getComment() != null && !aca.getComment().isBlank()) {
                        prompt.append(" (Comment: ").append(aca.getComment()).append(")");
                    }
                    prompt.append("\n");
                }
            }
            prompt.append("\nTotal controls: ").append(totalControls)
                  .append(", Answered: ").append(totalAnswered).append("\n");
            if (scoreCount > 0) {
                prompt.append("Average Maturity Score: ").append(String.format("%.1f", scoreSum / scoreCount)).append("\n");
            }
            prompt.append("Answer distribution: ").append(answerCounts).append("\n");
        }

        if (assessment.getComplianceCheck() != null) {
            prompt.append("\nCompliance Check: ").append(assessment.getComplianceCheck().getName()).append("\n");
        }

        prompt.append("\nPlease provide a 3-5 paragraph management summary covering: overall security posture, key findings, risks, and recommendations. Use plain text without markdown headers.");

        try {
            String summary = openAIUtil.askAI(prompt.toString(), false);
            assessment.setManagementSummary(summary);
            assessmentRepository.save(assessment);
            return Map.of("summary", summary);
        } catch (Exception e) {
            return Map.of("error", "AI generation failed: " + e.getMessage());
        }
    }

    // Toggle guide visibility in assessment-direct view
    @PostMapping("/{id}/guide-visible")
    @ResponseBody
    public Map<String, Object> setGuideVisible(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Optional<Assessment> assessmentOpt = assessmentRepository.findById(id);
        if (assessmentOpt.isEmpty()) {
            return Map.of("error", "not_found");
        }
        Assessment assessment = assessmentOpt.get();
        boolean visible = Boolean.TRUE.equals(body.get("guideVisibleInDirect"));
        assessment.setGuideVisibleInDirect(visible);
        assessmentRepository.save(assessment);
        return Map.of("guideVisibleInDirect", assessment.isGuideVisibleInDirect());
    }
}
