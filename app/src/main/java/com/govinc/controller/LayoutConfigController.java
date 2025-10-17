package com.govinc.controller;

import com.govinc.entity.LayoutConfiguration;
import com.govinc.entity.LayoutConfigurationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@Controller
@RequestMapping("/config/layout")
public class LayoutConfigController {
    @Autowired
    private LayoutConfigurationRepository layoutConfigurationRepository;

    @GetMapping
    public String getLayoutConfig(Model model) {
        LayoutConfiguration layoutConfig = layoutConfigurationRepository.findAll().stream().findFirst().orElse(new LayoutConfiguration());
        model.addAttribute("layoutConfig", layoutConfig);
        return "layout-config";
    }

    @PostMapping
    public String saveLayoutConfig(
            @ModelAttribute LayoutConfiguration layoutConfig,
            Model model) throws IOException {
        
        System.out.println("=== DEBUG: saveLayoutConfig called ===");
        System.out.println("Incoming layoutConfig ID: " + layoutConfig.getId());
        System.out.println("Incoming primaryColor: " + layoutConfig.getPrimaryColor());
        System.out.println("Incoming primaryColorDark: " + layoutConfig.getPrimaryColorDark());
        
        LayoutConfiguration persisted = layoutConfigurationRepository.findAll().stream().findFirst().orElse(new LayoutConfiguration());

        // Update all color/theme fields from the form
        if (layoutConfig.getPrimaryColor() != null && !layoutConfig.getPrimaryColor().trim().isEmpty()) {
            persisted.setPrimaryColor(layoutConfig.getPrimaryColor());
        }
        if (layoutConfig.getPrimaryColorDark() != null && !layoutConfig.getPrimaryColorDark().trim().isEmpty()) {
            persisted.setPrimaryColorDark(layoutConfig.getPrimaryColorDark());
        }
        if (layoutConfig.getAccentColor() != null && !layoutConfig.getAccentColor().trim().isEmpty()) {
            persisted.setAccentColor(layoutConfig.getAccentColor());
        }
        if (layoutConfig.getBackgroundColor() != null && !layoutConfig.getBackgroundColor().trim().isEmpty()) {
            persisted.setBackgroundColor(layoutConfig.getBackgroundColor());
        }
        if (layoutConfig.getBorderColor() != null && !layoutConfig.getBorderColor().trim().isEmpty()) {
            persisted.setBorderColor(layoutConfig.getBorderColor());
        }
        if (layoutConfig.getNavViolet() != null && !layoutConfig.getNavViolet().trim().isEmpty()) {
            persisted.setNavViolet(layoutConfig.getNavViolet());
        }
        if (layoutConfig.getTextMain() != null && !layoutConfig.getTextMain().trim().isEmpty()) {
            persisted.setTextMain(layoutConfig.getTextMain());
        }
        if (layoutConfig.getShineGlare() != null && !layoutConfig.getShineGlare().trim().isEmpty()) {
            persisted.setShineGlare(layoutConfig.getShineGlare());
        }
        if (layoutConfig.getShineHighlight() != null && !layoutConfig.getShineHighlight().trim().isEmpty()) {
            persisted.setShineHighlight(layoutConfig.getShineHighlight());
        }
        if (layoutConfig.getSecondaryColor() != null && !layoutConfig.getSecondaryColor().trim().isEmpty()) {
            persisted.setSecondaryColor(layoutConfig.getSecondaryColor());
        }
        if (layoutConfig.getFontFamily() != null && !layoutConfig.getFontFamily().trim().isEmpty()) {
            persisted.setFontFamily(layoutConfig.getFontFamily());
        }
        if (layoutConfig.getFontSizeNav() != null && !layoutConfig.getFontSizeNav().trim().isEmpty()) {
            persisted.setFontSizeNav(layoutConfig.getFontSizeNav());
        }
        if (layoutConfig.getFontSizeHeadline() != null && !layoutConfig.getFontSizeHeadline().trim().isEmpty()) {
            persisted.setFontSizeHeadline(layoutConfig.getFontSizeHeadline());
        }

        // Organisation/Tool styling fields
        if (layoutConfig.getOrgNameColor() != null && !layoutConfig.getOrgNameColor().trim().isEmpty()) {
            persisted.setOrgNameColor(layoutConfig.getOrgNameColor());
        }
        if (layoutConfig.getOrgNameFontSize() != null && !layoutConfig.getOrgNameFontSize().trim().isEmpty()) {
            persisted.setOrgNameFontSize(layoutConfig.getOrgNameFontSize());
        }
        if (layoutConfig.getToolNameColor() != null && !layoutConfig.getToolNameColor().trim().isEmpty()) {
            persisted.setToolNameColor(layoutConfig.getToolNameColor());
        }
        if (layoutConfig.getToolNameFontSize() != null && !layoutConfig.getToolNameFontSize().trim().isEmpty()) {
            persisted.setToolNameFontSize(layoutConfig.getToolNameFontSize());
        }

        // All theme color fields
        if (layoutConfig.getSuccessGreen() != null && !layoutConfig.getSuccessGreen().trim().isEmpty()) {
            persisted.setSuccessGreen(layoutConfig.getSuccessGreen());
        }
        if (layoutConfig.getErrorRed() != null && !layoutConfig.getErrorRed().trim().isEmpty()) {
            persisted.setErrorRed(layoutConfig.getErrorRed());
        }
        if (layoutConfig.getModalBeige1() != null && !layoutConfig.getModalBeige1().trim().isEmpty()) {
            persisted.setModalBeige1(layoutConfig.getModalBeige1());
        }
        if (layoutConfig.getModalBeige2() != null && !layoutConfig.getModalBeige2().trim().isEmpty()) {
            persisted.setModalBeige2(layoutConfig.getModalBeige2());
        }
        if (layoutConfig.getModalBeige3() != null && !layoutConfig.getModalBeige3().trim().isEmpty()) {
            persisted.setModalBeige3(layoutConfig.getModalBeige3());
        }
        if (layoutConfig.getModalBeige4() != null && !layoutConfig.getModalBeige4().trim().isEmpty()) {
            persisted.setModalBeige4(layoutConfig.getModalBeige4());
        }
        if (layoutConfig.getLabelGold() != null && !layoutConfig.getLabelGold().trim().isEmpty()) {
            persisted.setLabelGold(layoutConfig.getLabelGold());
        }
        if (layoutConfig.getGray777() != null && !layoutConfig.getGray777().trim().isEmpty()) {
            persisted.setGray777(layoutConfig.getGray777());
        }
        if (layoutConfig.getGray888() != null && !layoutConfig.getGray888().trim().isEmpty()) {
            persisted.setGray888(layoutConfig.getGray888());
        }
        if (layoutConfig.getTableBg1() != null && !layoutConfig.getTableBg1().trim().isEmpty()) {
            persisted.setTableBg1(layoutConfig.getTableBg1());
        }
        if (layoutConfig.getTableBg2() != null && !layoutConfig.getTableBg2().trim().isEmpty()) {
            persisted.setTableBg2(layoutConfig.getTableBg2());
        }
        if (layoutConfig.getTableBg3() != null && !layoutConfig.getTableBg3().trim().isEmpty()) {
            persisted.setTableBg3(layoutConfig.getTableBg3());
        }
        if (layoutConfig.getTableBg4() != null && !layoutConfig.getTableBg4().trim().isEmpty()) {
            persisted.setTableBg4(layoutConfig.getTableBg4());
        }
        if (layoutConfig.getTableBg5() != null && !layoutConfig.getTableBg5().trim().isEmpty()) {
            persisted.setTableBg5(layoutConfig.getTableBg5());
        }
        if (layoutConfig.getHeaderGradHighlight() != null && !layoutConfig.getHeaderGradHighlight().trim().isEmpty()) {
            persisted.setHeaderGradHighlight(layoutConfig.getHeaderGradHighlight());
        }
        if (layoutConfig.getYellowHighlight() != null && !layoutConfig.getYellowHighlight().trim().isEmpty()) {
            persisted.setYellowHighlight(layoutConfig.getYellowHighlight());
        }
        if (layoutConfig.getTableHover1() != null && !layoutConfig.getTableHover1().trim().isEmpty()) {
            persisted.setTableHover1(layoutConfig.getTableHover1());
        }
        if (layoutConfig.getTableHover2() != null && !layoutConfig.getTableHover2().trim().isEmpty()) {
            persisted.setTableHover2(layoutConfig.getTableHover2());
        }
        if (layoutConfig.getAlertBg1() != null && !layoutConfig.getAlertBg1().trim().isEmpty()) {
            persisted.setAlertBg1(layoutConfig.getAlertBg1());
        }
        if (layoutConfig.getAlertBg2() != null && !layoutConfig.getAlertBg2().trim().isEmpty()) {
            persisted.setAlertBg2(layoutConfig.getAlertBg2());
        }
        if (layoutConfig.getAlertColor() != null && !layoutConfig.getAlertColor().trim().isEmpty()) {
            persisted.setAlertColor(layoutConfig.getAlertColor());
        }
        if (layoutConfig.getTakenOverBg() != null && !layoutConfig.getTakenOverBg().trim().isEmpty()) {
            persisted.setTakenOverBg(layoutConfig.getTakenOverBg());
        }
        if (layoutConfig.getDropdownBgHover() != null && !layoutConfig.getDropdownBgHover().trim().isEmpty()) {
            persisted.setDropdownBgHover(layoutConfig.getDropdownBgHover());
        }
        if (layoutConfig.getSecondaryNavBg() != null && !layoutConfig.getSecondaryNavBg().trim().isEmpty()) {
            persisted.setSecondaryNavBg(layoutConfig.getSecondaryNavBg());
        }
        if (layoutConfig.getSecondaryNavBorder() != null && !layoutConfig.getSecondaryNavBorder().trim().isEmpty()) {
            persisted.setSecondaryNavBorder(layoutConfig.getSecondaryNavBorder());
        }
        if (layoutConfig.getLogoBorder() != null && !layoutConfig.getLogoBorder().trim().isEmpty()) {
            persisted.setLogoBorder(layoutConfig.getLogoBorder());
        }
        if (layoutConfig.getDropdownHoverBlue() != null && !layoutConfig.getDropdownHoverBlue().trim().isEmpty()) {
            persisted.setDropdownHoverBlue(layoutConfig.getDropdownHoverBlue());
        }
        if (layoutConfig.getMainNavBorder() != null && !layoutConfig.getMainNavBorder().trim().isEmpty()) {
            persisted.setMainNavBorder(layoutConfig.getMainNavBorder());
        }
        if (layoutConfig.getFaintBlue1() != null && !layoutConfig.getFaintBlue1().trim().isEmpty()) {
            persisted.setFaintBlue1(layoutConfig.getFaintBlue1());
        }

        try {
            layoutConfigurationRepository.save(persisted);
            System.out.println("Successfully saved layoutConfig with primaryColor: " + persisted.getPrimaryColor());
        } catch (Exception e) {
            System.err.println("Error saving layoutConfig: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
        
        // Ensure the model has a properly initialized layoutConfig
        LayoutConfiguration finalConfig = layoutConfigurationRepository.findAll().stream().findFirst().orElse(new LayoutConfiguration());
        model.addAttribute("layoutConfig", finalConfig);
        model.addAttribute("saved", true);
        return "layout-config";
    }


}