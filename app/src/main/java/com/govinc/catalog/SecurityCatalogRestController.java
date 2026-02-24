package com.govinc.catalog;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/security-catalogs")
public class SecurityCatalogRestController {

    @Autowired
    private SecurityCatalogService service;

    @Autowired
    private com.govinc.authorization.AuthorizationService authorizationService;
    
    @GetMapping
    public ResponseEntity<List<SecurityCatalogDto>> getAllCatalogs() {
        try {
            System.out.println("REST API endpoint called: /api/security-catalogs");

            // Only authenticated users should access catalogs (some catalogs may be public in future)
            if (authorizationService == null || !authorizationService.canAccessSecurityFramework()) {
                return ResponseEntity.status(403).build();
            }

            List<SecurityCatalog> catalogs = service.findAll();
            System.out.println("Found " + catalogs.size() + " catalogs");

            List<SecurityCatalogDto> catalogDtos = catalogs.stream()
                .map(SecurityCatalogDto::fromEntity)
                .collect(Collectors.toList());

            System.out.println("Returning DTO list with " + catalogDtos.size() + " items");
            return ResponseEntity.ok(catalogDtos);
        } catch (Exception e) {
            System.err.println("Error in getAllCatalogs: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("Security Catalog REST API is working!");
    }
}