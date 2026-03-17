package com.govinc.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "layout_configuration")
public class LayoutConfiguration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Logo image data, maximum 5MB. Enforced at upload and (if DB supports) at schema.
     */
    @Lob
    @Column(nullable = true, length = 5242880)
    private byte[] imageData;

    @Column(nullable = true, columnDefinition = "VARCHAR(50) DEFAULT 'image/png'")
    private String imageType;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#007bff'")
    private String primaryColor;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#0056b3'")
    private String primaryColorDark;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#28a745'")
    private String accentColor;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#ffffff'")
    private String backgroundColor;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#dee2e6'")
    private String borderColor;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#6f42c1'")
    private String navViolet;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#212529'")
    private String textMain;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#f8f9fa'")
    private String shineGlare;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#e9ecef'")
    private String shineHighlight;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#6c757d'")
    private String secondaryColor;

    // THEME COLORS ADDED FOR FULL REBRANDING
    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#28a745'")
    private String successGreen;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#dc3545'")
    private String errorRed;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#f8f5f0'")
    private String modalBeige1;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#f5f2ed'")
    private String modalBeige2;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#f2efea'")
    private String modalBeige3;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#efece7'")
    private String modalBeige4;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#ffd700'")
    private String labelGold;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#777777'")
    private String gray777;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#888888'")
    private String gray888;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#f8f9fa'")
    private String tableBg1;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#f1f3f4'")
    private String tableBg2;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#e9ecef'")
    private String tableBg3;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#dee2e6'")
    private String tableBg4;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#ced4da'")
    private String tableBg5;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#007bff'")
    private String headerGradHighlight;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#ffeb3b'")
    private String yellowHighlight;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#f5f5f5'")
    private String tableHover1;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#e8e8e8'")
    private String tableHover2;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#d4edda'")
    private String alertBg1;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#c3e6cb'")
    private String alertBg2;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#155724'")
    private String alertColor;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#fff3cd'")
    private String takenOverBg;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#e2e6ea'")
    private String dropdownBgHover;

    // Standard spacing and typography (NEW)
    @Column(nullable = true, columnDefinition = "VARCHAR(10) DEFAULT '0.95em'")
    private String fontSizeBody;

    @Column(nullable = true, columnDefinition = "VARCHAR(10) DEFAULT '0.85em'")
    private String fontSizeBtn;

    @Column(nullable = true, columnDefinition = "VARCHAR(10) DEFAULT '1rem'")
    private String spacingBase;

    @Column(nullable = true, columnDefinition = "VARCHAR(100) DEFAULT 'Arial, sans-serif'")
    private String fontFamily;

    @Column(nullable = true, columnDefinition = "VARCHAR(10) DEFAULT '14px'")
    private String fontSizeNav;

    @Column(nullable = true, columnDefinition = "VARCHAR(10) DEFAULT '24px'")
    private String fontSizeHeadline;

    // ADDED: Organisation/Tool details styles for navigation bar
    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#212529'")
    private String orgNameColor;

    @Column(nullable = true, columnDefinition = "VARCHAR(10) DEFAULT '16px'")
    private String orgNameFontSize;

    @Column(nullable = true, columnDefinition = "VARCHAR(7) DEFAULT '#6c757d'")
    private String toolNameColor;

    @Column(nullable = true, columnDefinition = "VARCHAR(10) DEFAULT '14px'")
    private String toolNameFontSize;

    public LayoutConfiguration() {
        // Initialize with default values if not set by columnDefinition
        initializeDefaults();
    }

    private void initializeDefaults() {
        if (this.imageType == null) this.imageType = "image/png";
        if (this.primaryColor == null) this.primaryColor = "#007bff";
        if (this.primaryColorDark == null) this.primaryColorDark = "#0056b3";
        if (this.accentColor == null) this.accentColor = "#28a745";
        if (this.backgroundColor == null) this.backgroundColor = "#ffffff";
        if (this.borderColor == null) this.borderColor = "#dee2e6";
        if (this.navViolet == null) this.navViolet = "#6f42c1";
        if (this.textMain == null) this.textMain = "#212529";
        if (this.shineGlare == null) this.shineGlare = "#f8f9fa";
        if (this.shineHighlight == null) this.shineHighlight = "#e9ecef";
        if (this.secondaryColor == null) this.secondaryColor = "#6c757d";
        if (this.successGreen == null) this.successGreen = "#28a745";
        if (this.errorRed == null) this.errorRed = "#dc3545";
        if (this.modalBeige1 == null) this.modalBeige1 = "#f8f5f0";
        if (this.modalBeige2 == null) this.modalBeige2 = "#f5f2ed";
        if (this.modalBeige3 == null) this.modalBeige3 = "#f2efea";
        if (this.modalBeige4 == null) this.modalBeige4 = "#efece7";
        if (this.labelGold == null) this.labelGold = "#ffd700";
        if (this.gray777 == null) this.gray777 = "#777777";
        if (this.gray888 == null) this.gray888 = "#888888";
        if (this.tableBg1 == null) this.tableBg1 = "#f8f9fa";
        if (this.tableBg2 == null) this.tableBg2 = "#f1f3f4";
        if (this.tableBg3 == null) this.tableBg3 = "#e9ecef";
        if (this.tableBg4 == null) this.tableBg4 = "#dee2e6";
        if (this.tableBg5 == null) this.tableBg5 = "#ced4da";
        if (this.headerGradHighlight == null) this.headerGradHighlight = "#007bff";
        if (this.yellowHighlight == null) this.yellowHighlight = "#ffeb3b";
        if (this.tableHover1 == null) this.tableHover1 = "#f5f5f5";
        if (this.tableHover2 == null) this.tableHover2 = "#e8e8e8";
        if (this.alertBg1 == null) this.alertBg1 = "#d4edda";
        if (this.alertBg2 == null) this.alertBg2 = "#c3e6cb";
        if (this.alertColor == null) this.alertColor = "#155724";
        if (this.takenOverBg == null) this.takenOverBg = "#fff3cd";
        if (this.dropdownBgHover == null) this.dropdownBgHover = "#e2e6ea";
        if (this.fontSizeBody == null) this.fontSizeBody = "0.95em";
        if (this.fontSizeBtn == null) this.fontSizeBtn = "0.85em";
        if (this.spacingBase == null) this.spacingBase = "1rem";
        if (this.fontFamily == null) this.fontFamily = "Arial, sans-serif";
        if (this.fontSizeNav == null) this.fontSizeNav = "14px";
        if (this.fontSizeHeadline == null) this.fontSizeHeadline = "24px";
        if (this.orgNameColor == null) this.orgNameColor = "#212529";
        if (this.orgNameFontSize == null) this.orgNameFontSize = "16px";
        if (this.toolNameColor == null) this.toolNameColor = "#6c757d";
        if (this.toolNameFontSize == null) this.toolNameFontSize = "14px";
    }

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

    // Getters and setters for additional theme fields
    public String getSuccessGreen() { return successGreen; }
    public void setSuccessGreen(String v) { this.successGreen = v; }

    public String getErrorRed() { return errorRed; }
    public void setErrorRed(String v) { this.errorRed = v; }

    public String getModalBeige1() { return modalBeige1; }
    public void setModalBeige1(String v) { this.modalBeige1 = v; }

    public String getModalBeige2() { return modalBeige2; }
    public void setModalBeige2(String v) { this.modalBeige2 = v; }

    public String getModalBeige3() { return modalBeige3; }
    public void setModalBeige3(String v) { this.modalBeige3 = v; }

    public String getModalBeige4() { return modalBeige4; }
    public void setModalBeige4(String v) { this.modalBeige4 = v; }

    public String getLabelGold() { return labelGold; }
    public void setLabelGold(String v) { this.labelGold = v; }

    public String getGray777() { return gray777; }
    public void setGray777(String v) { this.gray777 = v; }

    public String getGray888() { return gray888; }
    public void setGray888(String v) { this.gray888 = v; }

    public String getTableBg1() { return tableBg1; }
    public void setTableBg1(String v) { this.tableBg1 = v; }

    public String getTableBg2() { return tableBg2; }
    public void setTableBg2(String v) { this.tableBg2 = v; }

    public String getTableBg3() { return tableBg3; }
    public void setTableBg3(String v) { this.tableBg3 = v; }

    public String getTableBg4() { return tableBg4; }
    public void setTableBg4(String v) { this.tableBg4 = v; }

    public String getTableBg5() { return tableBg5; }
    public void setTableBg5(String v) { this.tableBg5 = v; }

    public String getHeaderGradHighlight() { return headerGradHighlight; }
    public void setHeaderGradHighlight(String v) { this.headerGradHighlight = v; }

    public String getYellowHighlight() { return yellowHighlight; }
    public void setYellowHighlight(String v) { this.yellowHighlight = v; }

    public String getTableHover1() { return tableHover1; }
    public void setTableHover1(String v) { this.tableHover1 = v; }

    public String getTableHover2() { return tableHover2; }
    public void setTableHover2(String v) { this.tableHover2 = v; }

    public String getAlertBg1() { return alertBg1; }
    public void setAlertBg1(String v) { this.alertBg1 = v; }

    public String getAlertBg2() { return alertBg2; }
    public void setAlertBg2(String v) { this.alertBg2 = v; }

    public String getAlertColor() { return alertColor; }
    public void setAlertColor(String v) { this.alertColor = v; }

    public String getTakenOverBg() { return takenOverBg; }
    public void setTakenOverBg(String v) { this.takenOverBg = v; }

    public String getDropdownBgHover() { return dropdownBgHover; }
    public void setDropdownBgHover(String v) { this.dropdownBgHover = v; }

    public String getFontSizeBody() { return fontSizeBody; }
    public void setFontSizeBody(String v) { this.fontSizeBody = v; }

    public String getFontSizeBtn() { return fontSizeBtn; }
    public void setFontSizeBtn(String v) { this.fontSizeBtn = v; }

    public String getSpacingBase() { return spacingBase; }
    public void setSpacingBase(String v) { this.spacingBase = v; }

    public String getFontFamily() { return fontFamily; }
    public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }

    public String getFontSizeNav() { return fontSizeNav; }
    public void setFontSizeNav(String fontSizeNav) { this.fontSizeNav = fontSizeNav; }

    public String getFontSizeHeadline() { return fontSizeHeadline; }
    public void setFontSizeHeadline(String fontSizeHeadline) { this.fontSizeHeadline = fontSizeHeadline; }

    public String getOrgNameColor() { return orgNameColor; }
    public void setOrgNameColor(String orgNameColor) { this.orgNameColor = orgNameColor; }

    public String getOrgNameFontSize() { return orgNameFontSize; }
    public void setOrgNameFontSize(String orgNameFontSize) { this.orgNameFontSize = orgNameFontSize; }

    public String getToolNameColor() { return toolNameColor; }
    public void setToolNameColor(String toolNameColor) { this.toolNameColor = toolNameColor; }

    public String getToolNameFontSize() { return toolNameFontSize; }
    public void setToolNameFontSize(String toolNameFontSize) { this.toolNameFontSize = toolNameFontSize; }
}