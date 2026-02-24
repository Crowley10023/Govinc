package com.govinc.configuration;

import com.govinc.entity.LayoutConfiguration;
import com.govinc.entity.LayoutConfigurationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalLayoutConfigAdvice {

    @Autowired
    private LayoutConfigurationRepository layoutConfigurationRepository;

    @ModelAttribute("layoutConfig")
    public LayoutConfiguration loadLayoutConfig() {
        LayoutConfiguration resu = layoutConfigurationRepository.findAll().stream().findFirst().orElse(new LayoutConfiguration());
        
        // Critical fix: Ensure all required properties have default values to prevent Thymeleaf binding errors
        if (resu != null) {
            // Force initialization of defaults if they're null
            if (resu.getPrimaryColor() == null) resu.setPrimaryColor("#007bff");
            if (resu.getPrimaryColorDark() == null) resu.setPrimaryColorDark("#0056b3");
            if (resu.getAccentColor() == null) resu.setAccentColor("#28a745");
            if (resu.getBackgroundColor() == null) resu.setBackgroundColor("#ffffff");
            if (resu.getBorderColor() == null) resu.setBorderColor("#dee2e6");
            if (resu.getNavViolet() == null) resu.setNavViolet("#6f42c1");
            if (resu.getTextMain() == null) resu.setTextMain("#212529");
            if (resu.getSecondaryColor() == null) resu.setSecondaryColor("#6c757d");
            if (resu.getShineGlare() == null) resu.setShineGlare("rgba(255,255,255,0.60)");
            if (resu.getShineHighlight() == null) resu.setShineHighlight("rgba(255,255,255,0.33)");
            if (resu.getFontFamily() == null) resu.setFontFamily("Arial, sans-serif");
            if (resu.getFontSizeNav() == null) resu.setFontSizeNav("14px");
            if (resu.getFontSizeHeadline() == null) resu.setFontSizeHeadline("24px");
            
            // Initialize all theme colors with defaults
            if (resu.getSuccessGreen() == null) resu.setSuccessGreen("#28a745");
            if (resu.getErrorRed() == null) resu.setErrorRed("#dc3545");
            if (resu.getModalBeige1() == null) resu.setModalBeige1("#f8f5f0");
            if (resu.getModalBeige2() == null) resu.setModalBeige2("#f5f2ed");
            if (resu.getModalBeige3() == null) resu.setModalBeige3("#f2efea");
            if (resu.getModalBeige4() == null) resu.setModalBeige4("#efece7");
            if (resu.getLabelGold() == null) resu.setLabelGold("#ffd700");
            if (resu.getGray777() == null) resu.setGray777("#777777");
            if (resu.getGray888() == null) resu.setGray888("#888888");
            if (resu.getTableBg1() == null) resu.setTableBg1("#f8f9fa");
            if (resu.getTableBg2() == null) resu.setTableBg2("#f1f3f4");
            if (resu.getTableBg3() == null) resu.setTableBg3("#e9ecef");
            if (resu.getTableBg4() == null) resu.setTableBg4("#dee2e6");
            if (resu.getTableBg5() == null) resu.setTableBg5("#ced4da");
            if (resu.getHeaderGradHighlight() == null) resu.setHeaderGradHighlight("#007bff");
            if (resu.getYellowHighlight() == null) resu.setYellowHighlight("#ffeb3b");
            if (resu.getTableHover1() == null) resu.setTableHover1("#f5f5f5");
            if (resu.getTableHover2() == null) resu.setTableHover2("#e8e8e8");
            if (resu.getAlertBg1() == null) resu.setAlertBg1("#d4edda");
            if (resu.getAlertBg2() == null) resu.setAlertBg2("#c3e6cb");
            if (resu.getAlertColor() == null) resu.setAlertColor("#155724");
            if (resu.getTakenOverBg() == null) resu.setTakenOverBg("#fff3cd");
            if (resu.getDropdownBgHover() == null) resu.setDropdownBgHover("#e2e6ea");
            if (resu.getSecondaryNavBg() == null) resu.setSecondaryNavBg("#f8f9fa");
            if (resu.getSecondaryNavBorder() == null) resu.setSecondaryNavBorder("#dee2e6");
            if (resu.getLogoBorder() == null) resu.setLogoBorder("#adb5bd");
            if (resu.getDropdownHoverBlue() == null) resu.setDropdownHoverBlue("#0d6efd");
            if (resu.getMainNavBorder() == null) resu.setMainNavBorder("#495057");
            if (resu.getFaintBlue1() == null) resu.setFaintBlue1("#e3f2fd");
            if (resu.getOrgNameColor() == null) resu.setOrgNameColor("#212529");
            if (resu.getOrgNameFontSize() == null) resu.setOrgNameFontSize("16px");
            if (resu.getToolNameColor() == null) resu.setToolNameColor("#6c757d");
            if (resu.getToolNameFontSize() == null) resu.setToolNameFontSize("14px");
            
        }
        return resu;
    }
}