package com.govinc.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "layout_configuration")
public class LayoutConfiguration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Not used with DB-storage mode: private String imageUrl;
    @Lob
    @Column(columnDefinition = "LONGBLOB", nullable = true)
    private byte[] imageData;

    @Column(nullable = true)
    private String imageType;

    @Column(nullable = true)
    private String primaryColor;

    @Column(nullable = true)
    private String secondaryColor;

    public LayoutConfiguration() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public byte[] getImageData() { return imageData; }
    public void setImageData(byte[] imageData) { this.imageData = imageData; }

    public String getImageType() { return imageType; }
    public void setImageType(String imageType) { this.imageType = imageType; }

    public String getPrimaryColor() { return primaryColor; }
    public void setPrimaryColor(String primaryColor) { this.primaryColor = primaryColor; }

    public String getSecondaryColor() { return secondaryColor; }
    public void setSecondaryColor(String secondaryColor) { this.secondaryColor = secondaryColor; }
}
