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

    // THEME COLORS ADDED FOR FULL REBRANDING
    private String successGreen;
    private String errorRed;
    private String modalBeige1;
    private String modalBeige2;
    private String modalBeige3;
    private String modalBeige4;
    private String labelGold;
    private String gray777;
    private String gray888;
    private String tableBg1;
    private String tableBg2;
    private String tableBg3;
    private String tableBg4;
    private String tableBg5;
    private String headerGradHighlight;
    private String yellowHighlight;
    private String tableHover1;
    private String tableHover2;
    private String alertBg1;
    private String alertBg2;
    private String alertColor;
    private String takenOverBg;
    private String dropdownBgHover;
    private String secondaryNavBg;
    private String secondaryNavBorder;
    private String logoBorder;
    private String dropdownHoverBlue;
    private String mainNavBorder;
    private String faintBlue1;

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

    public String getSecondaryNavBg() { return secondaryNavBg; }
    public void setSecondaryNavBg(String v) { this.secondaryNavBg = v; }

    public String getSecondaryNavBorder() { return secondaryNavBorder; }
    public void setSecondaryNavBorder(String v) { this.secondaryNavBorder = v; }

    public String getLogoBorder() { return logoBorder; }
    public void setLogoBorder(String v) { this.logoBorder = v; }

    public String getDropdownHoverBlue() { return dropdownHoverBlue; }
    public void setDropdownHoverBlue(String v) { this.dropdownHoverBlue = v; }

    public String getMainNavBorder() { return mainNavBorder; }
    public void setMainNavBorder(String v) { this.mainNavBorder = v; }

    public String getFaintBlue1() { return faintBlue1; }
    public void setFaintBlue1(String v) { this.faintBlue1 = v; }

    public String getFontFamily() { return fontFamily; }
    public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }

    public String getFontSizeNav() { return fontSizeNav; }
    public void setFontSizeNav(String fontSizeNav) { this.fontSizeNav = fontSizeNav; }

    public String getFontSizeHeadline() { return fontSizeHeadline; }
    public void setFontSizeHeadline(String fontSizeHeadline) { this.fontSizeHeadline = fontSizeHeadline; }
}

