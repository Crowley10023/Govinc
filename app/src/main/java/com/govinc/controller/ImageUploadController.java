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
@RequestMapping("/config/image-upload")
public class ImageUploadController {
    
    @Autowired
    private LayoutConfigurationRepository layoutConfigurationRepository;

    @GetMapping
    public String showImageUploadPage(Model model) {
        LayoutConfiguration layoutConfig = layoutConfigurationRepository.findAll().stream()
                .findFirst().orElse(new LayoutConfiguration());
        model.addAttribute("layoutConfig", layoutConfig);
        model.addAttribute("hasImage", layoutConfig.getImageData() != null);
        return "image-upload";
    }

    @PostMapping
    public String uploadImage(
            @RequestParam("imageFile") MultipartFile imageFile,
            Model model) throws IOException {
        
        System.out.println("=== DEBUG: Image upload called ===");
        System.out.println("File name: " + (imageFile != null ? imageFile.getOriginalFilename() : "null"));
        System.out.println("File size: " + (imageFile != null ? imageFile.getSize() : 0));
        
        if (imageFile == null || imageFile.isEmpty()) {
            model.addAttribute("error", "Please select an image file to upload.");
            return showImageUploadPage(model);
        }

        // Validate file type
        String contentType = imageFile.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            model.addAttribute("error", "Please select a valid image file (PNG, JPG, GIF, etc.).");
            return showImageUploadPage(model);
        }

        // Validate file size (e.g., max 5MB)
        if (imageFile.getSize() > 5 * 1024 * 1024) {
            model.addAttribute("error", "Image file size must be less than 5MB.");
            return showImageUploadPage(model);
        }

        try {
            // Get all configs (cleanup duplicates!)
            java.util.List<LayoutConfiguration> configs = layoutConfigurationRepository.findAll();
            LayoutConfiguration layoutConfig;
            if (configs.isEmpty()) {
                layoutConfig = new LayoutConfiguration();
            } else {
                layoutConfig = configs.get(0);
                // Remove extras if any
                if (configs.size() > 1) {
                    for (int i = 1; i < configs.size(); i++) {
                        layoutConfigurationRepository.delete(configs.get(i));
                    }
                }
            }
            // Update image data
            layoutConfig.setImageData(imageFile.getBytes());
            layoutConfig.setImageType(contentType);
            // Save to database (only one config remains)
            layoutConfigurationRepository.save(layoutConfig);
            System.out.println("Image uploaded successfully: " + imageFile.getOriginalFilename());
            // Refresh the model
            model.addAttribute("layoutConfig", layoutConfig);
            model.addAttribute("hasImage", true);
            model.addAttribute("success", "Image uploaded successfully!");
            
        } catch (Exception e) {
            System.err.println("Error uploading image: " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("error", "Failed to upload image. Please try again.");
        }
        
        return "image-upload";
    }

    @PostMapping("/delete")
    public String deleteImage(Model model) {
        try {
            LayoutConfiguration layoutConfig = layoutConfigurationRepository.findAll().stream()
                    .findFirst().orElse(new LayoutConfiguration());
            
            layoutConfig.setImageData(null);
            layoutConfig.setImageType(null);
            
            layoutConfigurationRepository.save(layoutConfig);
            
            model.addAttribute("layoutConfig", layoutConfig);
            model.addAttribute("hasImage", false);
            model.addAttribute("success", "Image deleted successfully!");
            
        } catch (Exception e) {
            System.err.println("Error deleting image: " + e.getMessage());
            model.addAttribute("error", "Failed to delete image. Please try again.");
        }
        
        return "image-upload";
    }

    @GetMapping("/preview")
    @ResponseBody
    public ResponseEntity<byte[]> getImagePreview() {
        LayoutConfiguration config = layoutConfigurationRepository.findAll().stream()
                .findFirst().orElse(null);
        
        if (config != null && config.getImageData() != null && config.getImageType() != null) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(config.getImageType()))
                    .body(config.getImageData());
        }
        
        return ResponseEntity.notFound().build();
    }
}