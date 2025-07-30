package com.govinc.controller;

import com.govinc.entity.OrganisationDetails;
import com.govinc.entity.OrganisationDetailsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/config/organisation-details")
public class OrganisationDetailsController {
    @Autowired
    private OrganisationDetailsRepository organisationDetailsRepository;

    @GetMapping
    public String getOrgDetails(Model model) {
        OrganisationDetails details = organisationDetailsRepository.findAll().stream().findFirst().orElse(new OrganisationDetails());
        model.addAttribute("organisationDetails", details);
        return "organisation-details";
    }

    @PostMapping
    public String saveOrgDetails(@ModelAttribute OrganisationDetails organisationDetails, Model model) {
        OrganisationDetails persisted = organisationDetailsRepository.findAll().stream().findFirst().orElse(new OrganisationDetails());
        persisted.setOrganisationName(organisationDetails.getOrganisationName());
        persisted.setToolName(organisationDetails.getToolName());
        organisationDetailsRepository.save(persisted);
        model.addAttribute("organisationDetails", persisted);
        model.addAttribute("saved", true);
        return "organisation-details";
    }
}