package com.govinc.organization;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/orgunits")
public class OrgUnitController {
    @Autowired
    private OrgUnitService orgUnitService;

    @Autowired
    private Environment env;

    // View for HTML rendering (Thymeleaf)
    @GetMapping({ "/list", "/list.html" })
    public String listOrgUnitsView(Model model) {
        List<OrgUnit> orgUnits = orgUnitService.getAllOrgUnits();
        model.addAttribute("orgUnits", orgUnits);
        return "orgunit-list";
    }

    // New method to enable the create org unit button (HTML form)
    @GetMapping("/create")
    public String createOrgUnitForm(Model model) {
        OrgUnit orgUnit = new OrgUnit();
        List<OrgUnit> allOrgUnits = orgUnitService.getAllOrgUnits();
        model.addAttribute("orgUnit", orgUnit);
        model.addAttribute("allOrgUnits", allOrgUnits);
        return "orgunit-edit";
    }

    // Edit Org Unit form (by id)
    @GetMapping("/edit/{id}")
    public String editOrgUnitForm(@PathVariable Long id, Model model) {
        Optional<OrgUnit> orgUnitOpt = orgUnitService.getOrgUnit(id);
        if (orgUnitOpt.isEmpty()) {
            return "redirect:/orgunits/list";
        }
        OrgUnit orgUnit = orgUnitOpt.get();
        List<OrgUnit> allOrgUnits = orgUnitService.getAllOrgUnits().stream()
                .filter(ou -> !ou.getId().equals(orgUnit.getId())) // Don't include self as possible parent/child
                .collect(Collectors.toList());
        model.addAttribute("orgUnit", orgUnit);
        model.addAttribute("allOrgUnits", allOrgUnits);
        return "orgunit-edit";
    }

    // Added method: Save or update OrgUnit via HTML form (Thymeleaf)
    @PostMapping("/save")
    public String saveOrgUnit(@ModelAttribute OrgUnit orgUnit,
            @RequestParam(value = "parentId", required = false) Long parentId,
            @RequestParam(value = "childrenIds", required = false) List<Long> childrenIds) {
        // Handle Parent
        if (parentId != null) {
            orgUnit.setParent(orgUnitService.getOrgUnit(parentId).orElse(null));
        } else {
            orgUnit.setParent(null);
        }
        // Handle Children
        if (childrenIds != null) {
            List<OrgUnit> allOrgUnits = orgUnitService.getAllOrgUnits();
            orgUnit.setChildren(allOrgUnits.stream().filter(ou -> childrenIds.contains(ou.getId()))
                    .collect(java.util.stream.Collectors.toSet()));
        } else {
            orgUnit.setChildren(null);
        }
        orgUnitService.addOrgUnit(orgUnit);
        return "redirect:/orgunits/list";
    }

    // REST API to get all org units (returning JSON)
    @ResponseBody
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<OrgUnit> getAllOrgUnits() {
        return orgUnitService.getAllOrgUnits();
    }

    // REST API for single org unit (returning JSON)
    @ResponseBody
    @GetMapping(value = "/{id:\\d+}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Optional<OrgUnit> getOrgUnit(@PathVariable Long id) {
        return orgUnitService.getOrgUnit(id);
    }

    // REST API for children of an org unit (returning JSON)
    @ResponseBody
    @GetMapping(value = "/children/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<OrgUnit> getChildrenOfOrgUnit(@PathVariable Long id) {
        List<OrgUnit> children = orgUnitService.getChildrenOfOrgUnit(id);
        System.out.println("Children for OrgUnit ID " + id + ":");
        for (OrgUnit child : children) {
            System.out.println(child);
        }
        return children;
    }

    // REST API to add org unit (returning JSON)
    @ResponseBody
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public OrgUnit addOrgUnit(@RequestBody OrgUnit orgUnit) {
        return orgUnitService.addOrgUnit(orgUnit);
    }

    // REST API to delete org unit (returning nothing)
    @ResponseBody
    @DeleteMapping(value = "/{id:\\d+}", produces = MediaType.APPLICATION_JSON_VALUE)
    public void deleteOrgUnit(@PathVariable Long id) {
        orgUnitService.deleteOrgUnit(id);
    }

    @ResponseBody
    @GetMapping(value = "/tree/{id}/fulltree", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getOrgUnitFullTree(@PathVariable Long id) {
        Optional<OrgUnit> orgUnitOpt = orgUnitService.getOrgUnitWithChildrenRecursive(id);
        if (orgUnitOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
    
        // Print the full tree to sysout
        OrgUnit root = orgUnitOpt.get();
        printOrgUnitTree(root, 0);
    
        return ResponseEntity.ok(root);
    }
    
    // Helper method to print the OrgUnit tree recursively
    private void printOrgUnitTree(OrgUnit orgUnit, int level) {
        // Indentation for readability
        String indent = "  ".repeat(level);
        System.out.println(indent + "OrgUnit ID: " + orgUnit.getId() + ", Name: " + orgUnit.getName());
        if (orgUnit.getChildren() != null) {
            for (OrgUnit child : orgUnit.getChildren()) {
                printOrgUnitTree(child, level + 1);
            }
        }
    }

    // Handle tree view rendering for selected org unit as root and all its children
    @GetMapping("/tree-view/{id}")
    public String orgTreeView(@PathVariable Long id, Model model) {
        Optional<OrgUnit> orgUnitOpt = orgUnitService.getOrgUnitWithChildrenRecursive(id);
        OrgUnit orgUnit = orgUnitOpt.orElse(null);
        // checkthat
        printOrgTree(orgUnit);

        model.addAttribute("orgUnit", orgUnit);
        return "org-tree-view";
    }

    public void printOrgTree(OrgUnit unit) {
        if (unit == null) return;
        System.out.println("Unit: " + unit.getName());
        if (unit.getChildren() != null) {
            for (OrgUnit child : unit.getChildren()) {
                printOrgTree(child);
            }
        }
    }

    // Exception Handler for this controller
    @ExceptionHandler(Exception.class)
    public Object handleException(Exception ex, HttpServletRequest request) {
        if (ex == null) {
            System.out.println("that's weird");
            return null;
        }
        ex.printStackTrace();
        boolean showDetails = false;
        for (String profile : env.getActiveProfiles()) {
            if (profile.equalsIgnoreCase("dev") || profile.equalsIgnoreCase("development")) {
                showDetails = true;
                break;
            }
        }
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            String details = null;
            if (showDetails) {
                StringWriter sw = new StringWriter();
                ex.printStackTrace(new PrintWriter(sw));
                details = sw.toString();
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ErrorResponseDetailed("error", ex.getMessage(), showDetails, details));
        } else {
            ModelAndView mav = new ModelAndView();
            mav.setViewName("error");
            mav.addObject("message", ex.getMessage());
            mav.addObject("showDetails", showDetails);
            if (showDetails) {
                StringWriter sw = new StringWriter();
                ex.printStackTrace(new PrintWriter(sw));
                mav.addObject("details", sw.toString());
            }
            return mav;
        }
    }

    // ErrorResponse inner class
    static class ErrorResponse {
        public String status;
        public String message;
        public ErrorResponse(String status, String message) {
            this.status = status;
            this.message = message;
        }
        public String getStatus() { return status; }
        public String getMessage() { return message; }
    }
    // ErrorResponseDetailed inner class for detailed error info
    static class ErrorResponseDetailed {
        public String status;
        public String message;
        public boolean showDetails;
        public String details;
        public ErrorResponseDetailed(String status, String message, boolean showDetails, String details) {
            this.status = status;
            this.message = message;
            this.showDetails = showDetails;
            this.details = details;
        }
        public String getStatus() { return status; }
        public String getMessage() { return message; }
        public boolean isShowDetails() { return showDetails; }
        public String getDetails() { return details; }
    }
}
