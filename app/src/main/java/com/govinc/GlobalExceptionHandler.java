package com.govinc;

import com.govinc.entity.LayoutConfiguration;
import com.govinc.entity.LayoutConfigurationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import java.io.PrintWriter;
import java.io.StringWriter;

@ControllerAdvice
public class GlobalExceptionHandler {

    private final Environment env;
    private final LayoutConfigurationRepository layoutConfigurationRepository;

    public GlobalExceptionHandler(Environment env, LayoutConfigurationRepository layoutConfigurationRepository) {
        this.env = env;
        this.layoutConfigurationRepository = layoutConfigurationRepository;
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleException(HttpServletRequest request, Exception ex) {
        ex.printStackTrace();
        boolean showDetails = false;
        // You can configure this with a specific property or use Spring's built-in
        // profile
        for (String profile : env.getActiveProfiles()) {
            if (profile.equalsIgnoreCase("dev") || profile.equalsIgnoreCase("development")) {
                showDetails = true;
                break;
            }
        }

        ModelAndView mav = new ModelAndView();
        mav.setViewName("error");
        mav.addObject("message", ex.getMessage());
        mav.addObject("showDetails", showDetails);

        // Add layoutConfig to avoid Thymeleaf binding errors in error page
        LayoutConfiguration layoutConfig = layoutConfigurationRepository.findAll().stream()
                .findFirst().orElse(new LayoutConfiguration());
        
        // Initialize with default values if null
        if (layoutConfig != null) {
            if (layoutConfig.getPrimaryColor() == null) layoutConfig.setPrimaryColor("#2274A5");
            if (layoutConfig.getPrimaryColorDark() == null) layoutConfig.setPrimaryColorDark("#164666");
            if (layoutConfig.getAccentColor() == null) layoutConfig.setAccentColor("#FF9505");
            if (layoutConfig.getBackgroundColor() == null) layoutConfig.setBackgroundColor("#F5F9FC");
            if (layoutConfig.getBorderColor() == null) layoutConfig.setBorderColor("#dae1e7");
            if (layoutConfig.getNavViolet() == null) layoutConfig.setNavViolet("#ebe8fc");
            if (layoutConfig.getTextMain() == null) layoutConfig.setTextMain("#222E3A");
            if (layoutConfig.getShineGlare() == null) layoutConfig.setShineGlare("rgba(255,255,255,0.60)");
            if (layoutConfig.getShineHighlight() == null) layoutConfig.setShineHighlight("rgba(255,255,255,0.33)");
            if (layoutConfig.getSecondaryColor() == null) layoutConfig.setSecondaryColor("#9596AE");
            if (layoutConfig.getFontFamily() == null) layoutConfig.setFontFamily("Segoe UI, Arial, sans-serif");
            if (layoutConfig.getFontSizeNav() == null) layoutConfig.setFontSizeNav("1em");
            if (layoutConfig.getFontSizeHeadline() == null) layoutConfig.setFontSizeHeadline("1.5em");
            if (layoutConfig.getOrgNameColor() == null) layoutConfig.setOrgNameColor("#2274A5");
            if (layoutConfig.getOrgNameFontSize() == null) layoutConfig.setOrgNameFontSize("1.18em");
            if (layoutConfig.getToolNameColor() == null) layoutConfig.setToolNameColor("#164666");
            if (layoutConfig.getToolNameFontSize() == null) layoutConfig.setToolNameFontSize("1em");
        }
        mav.addObject("layoutConfig", layoutConfig);

        if (showDetails) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            mav.addObject("details", sw.toString());
        }
        return mav;

    }
}
