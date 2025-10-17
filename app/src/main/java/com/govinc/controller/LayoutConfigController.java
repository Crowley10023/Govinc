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
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            Model model) throws IOException {
        LayoutConfiguration persisted = layoutConfigurationRepository.findAll().stream().findFirst().orElse(new LayoutConfiguration());

        // If image uploaded, always replace
        if (imageFile != null && !imageFile.isEmpty()) {
            persisted.setImageData(imageFile.getBytes());
            persisted.setImageType(imageFile.getContentType());
        }

        // For all color/theme fields: overwrite with user value if present, else keep old
        persisted.setPrimaryColor(layoutConfig.getPrimaryColor());
        persisted.setPrimaryColorDark(layoutConfig.getPrimaryColorDark());
        persisted.setAccentColor(layoutConfig.getAccentColor());
        persisted.setBackgroundColor(layoutConfig.getBackgroundColor());
        persisted.setBorderColor(layoutConfig.getBorderColor());
        persisted.setNavViolet(layoutConfig.getNavViolet());
        persisted.setTextMain(layoutConfig.getTextMain());
        persisted.setShineGlare(layoutConfig.getShineGlare());
        persisted.setShineHighlight(layoutConfig.getShineHighlight());
        persisted.setSecondaryColor(layoutConfig.getSecondaryColor());
        persisted.setFontFamily(layoutConfig.getFontFamily());
        persisted.setFontSizeNav(layoutConfig.getFontSizeNav());
        persisted.setFontSizeHeadline(layoutConfig.getFontSizeHeadline());

        // New fields:
        persisted.setOrgNameColor(layoutConfig.getOrgNameColor());
        persisted.setOrgNameFontSize(layoutConfig.getOrgNameFontSize());
        persisted.setToolNameColor(layoutConfig.getToolNameColor());
        persisted.setToolNameFontSize(layoutConfig.getToolNameFontSize());

        // All theme color fields:
        persisted.setSuccessGreen(layoutConfig.getSuccessGreen());
        persisted.setErrorRed(layoutConfig.getErrorRed());
        persisted.setModalBeige1(layoutConfig.getModalBeige1());
        persisted.setModalBeige2(layoutConfig.getModalBeige2());
        persisted.setModalBeige3(layoutConfig.getModalBeige3());
        persisted.setModalBeige4(layoutConfig.getModalBeige4());
        persisted.setLabelGold(layoutConfig.getLabelGold());
        persisted.setGray777(layoutConfig.getGray777());
        persisted.setGray888(layoutConfig.getGray888());
        persisted.setTableBg1(layoutConfig.getTableBg1());
        persisted.setTableBg2(layoutConfig.getTableBg2());
        persisted.setTableBg3(layoutConfig.getTableBg3());
        persisted.setTableBg4(layoutConfig.getTableBg4());
        persisted.setTableBg5(layoutConfig.getTableBg5());
        persisted.setHeaderGradHighlight(layoutConfig.getHeaderGradHighlight());
        persisted.setYellowHighlight(layoutConfig.getYellowHighlight());
        persisted.setTableHover1(layoutConfig.getTableHover1());
        persisted.setTableHover2(layoutConfig.getTableHover2());
        persisted.setAlertBg1(layoutConfig.getAlertBg1());
        persisted.setAlertBg2(layoutConfig.getAlertBg2());
        persisted.setAlertColor(layoutConfig.getAlertColor());
        persisted.setTakenOverBg(layoutConfig.getTakenOverBg());
        persisted.setDropdownBgHover(layoutConfig.getDropdownBgHover());
        persisted.setSecondaryNavBg(layoutConfig.getSecondaryNavBg());
        persisted.setSecondaryNavBorder(layoutConfig.getSecondaryNavBorder());
        persisted.setLogoBorder(layoutConfig.getLogoBorder());
        persisted.setDropdownHoverBlue(layoutConfig.getDropdownHoverBlue());
        persisted.setMainNavBorder(layoutConfig.getMainNavBorder());
        persisted.setFaintBlue1(layoutConfig.getFaintBlue1());

        layoutConfigurationRepository.save(persisted);
        model.addAttribute("layoutConfig", persisted);
        model.addAttribute("saved", true);
        return "layout-config";
    }

    @GetMapping("/image")
    @ResponseBody
    public ResponseEntity<byte[]> getNavBarImage() {
        LayoutConfiguration config = layoutConfigurationRepository.findAll().stream().findFirst().orElse(null);
        if (config != null && config.getImageData() != null && config.getImageType() != null) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(config.getImageType()))
                    .body(config.getImageData());
        }
        return ResponseEntity.notFound().build();
    }
}
