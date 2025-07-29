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
        return layoutConfigurationRepository.findAll().stream().findFirst().orElse(null);
    }
}
