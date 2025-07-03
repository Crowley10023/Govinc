package com.govinc.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.context.ApplicationContext;

@Controller
@RequestMapping("/configuration")
public class ConfigurationController {
    @Autowired
    private DatabaseConfig dbConfig;

    @Autowired
    private ApplicationContext applicationContext;

    @GetMapping("/database")
    public String databaseConfig(Model model) {
        model.addAttribute("dbConfig", dbConfig);
        return "configuration-database";
    }

    @PostMapping("/database/save")
    public String saveDatabaseConfig(@ModelAttribute DatabaseConfig dbConfigForm, Model model) {
        dbConfig.setUrl(dbConfigForm.getUrl());
        dbConfig.setUsername(dbConfigForm.getUsername());
        dbConfig.setPassword(dbConfigForm.getPassword());
        dbConfig.setDriverClassName(dbConfigForm.getDriverClassName());
        dbConfig.setDdlAuto(dbConfigForm.getDdlAuto());
        dbConfig.setShowSql(dbConfigForm.isShowSql());
        // Dynamic reloading is hacky and not fully supported. Here, just say config saved.
        model.addAttribute("dbConfig", dbConfig);
        model.addAttribute("message", "Configuration saved. Full dynamic reload may require restart.");
        return "configuration-database";
    }

    @GetMapping("/iam")
    public String iamConfig(Model model) {
        return "configuration-iam";
    }
}
