package com.govinc;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ComplianceViewPageController {
    @GetMapping("/compliance-view")
    public String complianceView() {
        return "compliance-view";
    }
}
