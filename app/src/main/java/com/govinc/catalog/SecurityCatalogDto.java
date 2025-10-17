package com.govinc.catalog;

public class SecurityCatalogDto {
    private Long id;
    private String name;
    private String description;
    private String revision;
    
    public SecurityCatalogDto() {}
    
    public SecurityCatalogDto(Long id, String name, String description, String revision) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.revision = revision;
    }
    
    public static SecurityCatalogDto fromEntity(SecurityCatalog catalog) {
        return new SecurityCatalogDto(
            catalog.getId(),
            catalog.getName(),
            catalog.getDescription(),
            catalog.getRevision()
        );
    }
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getRevision() {
        return revision;
    }
    
    public void setRevision(String revision) {
        this.revision = revision;
    }
}