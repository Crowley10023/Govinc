package com.govinc.configuration;

import com.govinc.entity.LayoutConfiguration;
import com.govinc.entity.LayoutConfigurationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalLayoutConfigAdvice {

    @Autowired
    private LayoutConfigurationRepository layoutConfigurationRepository;

    @ModelAttribute("layoutConfig")
    public LayoutConfiguration loadLayoutConfig() {
        LayoutConfiguration resu = layoutConfigurationRepository.findAll().stream().findFirst().orElse(null);
        System.out.println("checked\n\n\n" + resu);
        System.out.println("PrimaryColor: " + resu.getPrimaryColor());
        return resu;
    }
}
