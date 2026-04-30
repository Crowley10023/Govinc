package com.govinc.assessment;

import com.govinc.authorization.AuthorizationService;
import com.govinc.authorization.UnauthorizedException;
import com.govinc.catalog.SecurityCatalog;
import com.govinc.catalog.SecurityCatalogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/assessment-stereotype")
public class AssessmentStereotypeController {

    @Autowired
    private AssessmentStereotypeRepository stereotypeRepository;

    @Autowired
    private SecurityCatalogService securityCatalogService;

    @Autowired
    private AuthorizationService authorizationService;

    private void checkAccess() {
        if (!authorizationService.isAdmin() && !authorizationService.isInformationSecurityManager()) {
            throw new UnauthorizedException("You do not have permission to manage stereotypes.");
        }
    }

    @GetMapping("/list")
    public String list(Model model, @RequestParam(required = false) Long catalogId) {
        checkAccess();
        List<AssessmentStereotype> stereotypes = catalogId != null
                ? stereotypeRepository.findBySecurityCatalogId(catalogId)
                : stereotypeRepository.findAll();
        model.addAttribute("stereotypes", stereotypes);
        model.addAttribute("catalogs", securityCatalogService.findAll());
        model.addAttribute("selectedCatalogId", catalogId);
        return "assessment-stereotypes";
    }

    @GetMapping("/create")
    public String showCreate(Model model) {
        checkAccess();
        model.addAttribute("stereotype", new AssessmentStereotype());
        model.addAttribute("catalogs", securityCatalogService.findAll());
        model.addAttribute("editMode", false);
        return "assessment-stereotype-edit";
    }

    @GetMapping("/edit")
    public String showEdit(@RequestParam Long id, Model model) {
        checkAccess();
        Optional<AssessmentStereotype> opt = stereotypeRepository.findById(id);
        if (opt.isEmpty()) return "redirect:/assessment-stereotype/list";
        model.addAttribute("stereotype", opt.get());
        model.addAttribute("catalogs", securityCatalogService.findAll());
        model.addAttribute("editMode", true);
        return "assessment-stereotype-edit";
    }

    @PostMapping("/save")
    public String save(
            @RequestParam(required = false) Long id,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Long catalogId) {
        checkAccess();
        AssessmentStereotype stereotype = id != null
                ? stereotypeRepository.findById(id).orElse(new AssessmentStereotype())
                : new AssessmentStereotype();
        stereotype.setName(name.trim());
        stereotype.setDescription(description != null ? description.trim() : null);
        if (catalogId != null) {
            SecurityCatalog catalog = securityCatalogService.findById(catalogId).orElse(null);
            stereotype.setSecurityCatalog(catalog);
        } else {
            stereotype.setSecurityCatalog(null);
        }
        stereotypeRepository.save(stereotype);
        return "redirect:/assessment-stereotype/list";
    }

    @PostMapping("/delete")
    @ResponseBody
    public String delete(@RequestParam Long id) {
        checkAccess();
        stereotypeRepository.deleteById(id);
        return "ok";
    }

    /** AJAX: get stereotypes for a given catalog (used by assessment-details popup) */
    @GetMapping("/by-catalog")
    @ResponseBody
    public List<AssessmentStereotype> byCatalog(@RequestParam Long catalogId) {
        return stereotypeRepository.findBySecurityCatalogId(catalogId);
    }
}
