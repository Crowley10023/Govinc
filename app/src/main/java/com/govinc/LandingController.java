package com.govinc;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class LandingController {
    @GetMapping("/")
    public String home() {
        return "landing";
    }

    @GetMapping("/create-assessment")
    public String createAssessment() {
        return "create-assessment";
    }

}
