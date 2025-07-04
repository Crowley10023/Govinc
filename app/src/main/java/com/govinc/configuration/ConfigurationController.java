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
    private IamConfig iamConfig;

    @Autowired
    private ApplicationContext applicationContext;

    @GetMapping("/database")
    public String databaseConfig(Model model) {
        model.addAttribute("dbConfig", dbConfig);
        return "configuration-database";
    }

    @PostMapping("/database/check")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, Object> checkDbConnection(
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, String> params) {
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        try {
            String url = params.get("url");
            String username = params.get("username");
            String password = params.get("password");
            String driverClassName = params.get("driverClassName");
            Class.forName(driverClassName);
            try (java.sql.Connection conn = java.sql.DriverManager.getConnection(url, username, password)) {
                response.put("success", conn != null && !conn.isClosed());
            }
        } catch (Exception e) {
            response.put("success", false);
        }
        return response;
    }

    @PostMapping("/database/save")
    public String saveDatabaseConfig(@ModelAttribute DatabaseConfig dbConfigForm, Model model) {
        dbConfig.setUrl(dbConfigForm.getUrl());
        dbConfig.setUsername(dbConfigForm.getUsername());
        dbConfig.setPassword(dbConfigForm.getPassword());
        dbConfig.setDriverClassName(dbConfigForm.getDriverClassName());
        dbConfig.setDdlAuto(dbConfigForm.getDdlAuto());
        dbConfig.setShowSql(dbConfigForm.isShowSql());
        model.addAttribute("dbConfig", dbConfig);

        // Candidate paths for application.properties
        String[] candidates = {
                "app/src/main/resources/application.properties",
                "app/build/resources/main/application.properties",
                "build/resources/main/application.properties",
                "src/main/resources/application.properties",
                "build/resources/application.properties",
                "application.properties"
        };

        String targetPath = null;
        for (String candidate : candidates) {
            java.io.File f = new java.io.File(candidate);
            System.out.println("Checking for file: " + f.getAbsolutePath() + " (exists: " + f.exists() + ")");
            if (f.exists()) {
                targetPath = candidate;
                break;
            }
        }
        if (targetPath == null) {
            targetPath = candidates[0];
        }
        targetPath = "build/resources/main/application.properties ";
        System.out.println("Attempting to save DB config to: " + targetPath);

        try {
            DatabaseConfigFileUtil.saveToPropertiesFile(dbConfig, targetPath);
            model.addAttribute("message", "Configuration saved. Full dynamic reload may require restart.");
            System.out.println("Save appears successful.");
        } catch (Exception e) {
            model.addAttribute("message", "ERROR: " + e.getMessage());
            System.out.println("ERROR: Exception while saving: " + e.getMessage());
            e.printStackTrace(System.out);
        }
        return "configuration-database";
    }

    @GetMapping("/iam")
    public String iamConfigPage(Model model) {
        model.addAttribute("iamConfig", iamConfig);
        return "configuration-iam";
    }

    @PostMapping("/iam/save")
    public String saveIamConfig(@ModelAttribute IamConfig updatedConfig, Model model) {
        System.out.println("Entering /iam/save POST handler");

        // Set in-memory config
        iamConfig.setProvider(updatedConfig.getProvider());
        iamConfig.setAzureClientId(updatedConfig.getAzureClientId());
        iamConfig.setAzureClientSecret(updatedConfig.getAzureClientSecret());
        iamConfig.setAzureTenantId(updatedConfig.getAzureTenantId());
        iamConfig.setKeycloakIssuerUrl(updatedConfig.getKeycloakIssuerUrl());
        iamConfig.setKeycloakRealm(updatedConfig.getKeycloakRealm());
        iamConfig.setKeycloakClientId(updatedConfig.getKeycloakClientId());
        iamConfig.setKeycloakClientSecret(updatedConfig.getKeycloakClientSecret());

        // Show the updated config
        System.out.println("Updated IAM config: " + iamConfig);

        // Print working directory
        String workingDir = System.getProperty("user.dir");
        System.out.println("Current working directory: " + workingDir);

        // Show the intended path to application.properties

        // Candidate paths
        String[] candidates = {
                "app/src/main/resources/application.properties",
                "app/build/resources/main/application.properties",
                "build/resources/main/application.properties",
                "src/main/resources/application.properties",
                "build/resources/application.properties",
                "application.properties"
        };
        for (String candidate : candidates) {
            java.io.File f = new java.io.File(candidate);
            System.out.println("Checking for file: " + f.getAbsolutePath() + " (exists: " + f.exists() + ")");
        }

        String targetPath = "build/resources/main/application.properties ";
        System.out.println("Attempting to save IAM config to: " + targetPath);

        try {
            IamConfigFileUtil.saveToPropertiesFile(iamConfig, targetPath);
            model.addAttribute("message", "Configuration saved. Full reload may require restart.");
            System.out.println("Save appears successful.");
        } catch (Exception e) {
            java.io.File f = new java.io.File(targetPath);
            System.out.println("\n\n: " + f.getAbsolutePath());
            model.addAttribute("message", "ERROR: " + e.getMessage());
            System.out.println("ERROR: Exception while saving: " + e.getMessage());
            e.printStackTrace(System.out);
        }
        model.addAttribute("iamConfig", iamConfig);
        System.out.println("Returning view: configuration-iam");
        return "configuration-iam";
    }

    @PostMapping("/restart")
    @org.springframework.web.bind.annotation.ResponseBody
    public String restartApp() {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
            if (applicationContext instanceof org.springframework.context.ConfigurableApplicationContext ctx) {
                ctx.close();
            }
        });
        thread.setDaemon(false);
        thread.start();
        return "Restart signal sent. Please wait a few seconds...";
    }
}
