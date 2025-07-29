package com.govinc.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "layout_configuration")
public class LayoutConfiguration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Lob
    @Column(nullable = true)
    private byte[] imageData;

    @Column(nullable = true)
    private String imageType;

    @Column(nullable = true)
    private String primaryColor;

    @Column(nullable = true)
    private String primaryColorDark;

    @Column(nullable = true)
    private String accentColor;

    @Column(nullable = true)
    private String backgroundColor;

    @Column(nullable = true)
    private String borderColor;

    @Column(nullable = true)
    private String navViolet;

    @Column(nullable = true)
    private String textMain;

    @Column(nullable = true)
    private String shineGlare;

    @Column(nullable = true)
    private String shineHighlight;

    @Column(nullable = true)
    private String secondaryColor;

    @Column(nullable = true)
    private String fontFamily;

    @Column(nullable = true)
    private String fontSizeNav;

    @Column(nullable = true)
    private String fontSizeHeadline;

    public LayoutConfiguration() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public byte[] getImageData() { return imageData; }
    public void setImageData(byte[] imageData) { this.imageData = imageData; }

    public String getImageType() { return imageType; }
    public void setImageType(String imageType) { this.imageType = imageType; }

    public String getPrimaryColor() { return primaryColor; }
    public void setPrimaryColor(String primaryColor) { this.primaryColor = primaryColor; }

    public String getPrimaryColorDark() { return primaryColorDark; }
    public void setPrimaryColorDark(String primaryColorDark) { this.primaryColorDark = primaryColorDark; }

    public String getAccentColor() { return accentColor; }
    public void setAccentColor(String accentColor) { this.accentColor = accentColor; }

    public String getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(String backgroundColor) { this.backgroundColor = backgroundColor; }

    public String getBorderColor() { return borderColor; }
    public void setBorderColor(String borderColor) { this.borderColor = borderColor; }

    public String getNavViolet() { return navViolet; }
    public void setNavViolet(String navViolet) { this.navViolet = navViolet; }

    public String getTextMain() { return textMain; }
    public void setTextMain(String textMain) { this.textMain = textMain; }

    public String getShineGlare() { return shineGlare; }
    public void setShineGlare(String shineGlare) { this.shineGlare = shineGlare; }

    public String getShineHighlight() { return shineHighlight; }
    public void setShineHighlight(String shineHighlight) { this.shineHighlight = shineHighlight; }

    public String getSecondaryColor() { return secondaryColor; }
    public void setSecondaryColor(String secondaryColor) { this.secondaryColor = secondaryColor; }

    public String getFontFamily() { return fontFamily; }
    public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }

    public String getFontSizeNav() { return fontSizeNav; }
    public void setFontSizeNav(String fontSizeNav) { this.fontSizeNav = fontSizeNav; }

    public String getFontSizeHeadline() { return fontSizeHeadline; }
    public void setFontSizeHeadline(String fontSizeHeadline) { this.fontSizeHeadline = fontSizeHeadline; }
}

