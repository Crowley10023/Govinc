package com.govinc.controller;

import com.govinc.entity.OrganisationDetails;
import com.govinc.entity.OrganisationDetailsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.ui.Model;

@ControllerAdvice
public class GlobalOrganisationDetailsAdvice {
    @Autowired
    private OrganisationDetailsRepository organisationDetailsRepository;

    @ModelAttribute
    public void addOrganisationDetails(Model model) {
        OrganisationDetails details = organisationDetailsRepository.findAll().stream().findFirst().orElse(null);
        model.addAttribute("organisationDetails", details);
    }
}
