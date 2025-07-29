package com.govinc.controller;

import com.govinc.entity.LayoutConfiguration;
import com.govinc.entity.LayoutConfigurationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;
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
        LayoutConfiguration persisted = layoutConfigurationRepository.findAll().stream().findFirst().orElse(null);
        if (imageFile != null && !imageFile.isEmpty()) {
            if (persisted == null) {
                persisted = new LayoutConfiguration();
            }
            persisted.setImageData(imageFile.getBytes());
            persisted.setImageType(imageFile.getContentType());
        }
        if (persisted != null) {
            if (layoutConfig.getPrimaryColor() != null) persisted.setPrimaryColor(layoutConfig.getPrimaryColor());
            if (layoutConfig.getSecondaryColor() != null) persisted.setSecondaryColor(layoutConfig.getSecondaryColor());
            layoutConfigurationRepository.save(persisted);
            model.addAttribute("layoutConfig", persisted);
        } else {
            layoutConfigurationRepository.save(layoutConfig);
            model.addAttribute("layoutConfig", layoutConfig);
        }
        model.addAttribute("saved", true);
        return "layout-config";
    }

    // Endpoint to serve the image from DB
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
