package com.govinc.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ThemeController {
    @GetMapping("/theme-css")
    public String getThemeCss() {
        return "fragments/theme-css";
    }
}
