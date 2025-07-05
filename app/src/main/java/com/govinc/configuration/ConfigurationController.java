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
            String msg = e.getMessage();
            if (msg == null && e.getCause() != null) msg = e.getCause().getMessage();
            if (msg == null) msg = e.toString();
            response.put("error", msg);
        }
        return response;
    }

    @PostMapping("/database/save")
    public String saveDatabaseConfig(@ModelAttribute DatabaseConfig dbConfigForm, Model model) {
        try {
            dbConfig.setUrl(dbConfigForm.getUrl());
            dbConfig.setUsername(dbConfigForm.getUsername());
            dbConfig.setPassword(dbConfigForm.getPassword());
            dbConfig.setDriverClassName(dbConfigForm.getDriverClassName());
            dbConfig.setDdlAuto(dbConfigForm.getDdlAuto());
            dbConfig.setShowSql(dbConfigForm.isShowSql());
            model.addAttribute("dbConfig", dbConfig);
            model.addAttribute("message", "Database configuration was saved successfully! Note: A full reload of the application may be required for all changes to take effect.");
        } catch (Exception e) {
            model.addAttribute("dbConfig", dbConfigForm);
            String errorMsg = (e.getMessage() != null) ? e.getMessage() : "Unknown error occurred while saving database configuration.";
            model.addAttribute("message", "Error saving database configuration: " + errorMsg);
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
        try {
            iamConfig.setProvider(updatedConfig.getProvider());
            iamConfig.setAzureClientId(updatedConfig.getAzureClientId());
            iamConfig.setAzureClientSecret(updatedConfig.getAzureClientSecret());
            iamConfig.setAzureTenantId(updatedConfig.getAzureTenantId());
            iamConfig.setKeycloakIssuerUrl(updatedConfig.getKeycloakIssuerUrl());
            iamConfig.setKeycloakRealm(updatedConfig.getKeycloakRealm());
            iamConfig.setKeycloakClientId(updatedConfig.getKeycloakClientId());
            iamConfig.setKeycloakClientSecret(updatedConfig.getKeycloakClientSecret());

            // Save to config/iam.properties (in working directory)
            String configDir = "app/config";
            java.io.File dir = new java.io.File(configDir);
            if (!dir.exists()) dir.mkdirs();
            String targetPath = configDir + java.io.File.separator + "iam.properties";
            
            System.out.println("Attempting to save IAM config to: " + targetPath);

            IamConfigFileUtil.saveToPropertiesFile(iamConfig, targetPath);
            model.addAttribute("message", "IAM configuration was saved successfully! Note: A full application reload may be needed for changes to fully apply.");
            System.out.println("Save appears successful.");
        } catch (Exception e) {
            String errorMsg = (e.getMessage() != null) ? e.getMessage() : "Unknown error occurred while saving IAM configuration.";
            model.addAttribute("message", "Error saving IAM configuration: " + errorMsg);
            System.out.println("ERROR: Exception while saving: " + errorMsg);
            e.printStackTrace(System.out);
        }
        model.addAttribute("iamConfig", iamConfig);
        System.out.println("Returning view: configuration-iam");
        return "configuration-iam";
    }

    // --- New: Restart endpoint ---
    @PostMapping("/database/restart")
    @org.springframework.web.bind.annotation.ResponseBody
    public String restartWithDatabaseConfig(@ModelAttribute DatabaseConfig dbConfigForm) {
        dbConfig.setUrl(dbConfigForm.getUrl());
        dbConfig.setUsername(dbConfigForm.getUsername());
        dbConfig.setPassword(dbConfigForm.getPassword());
        dbConfig.setDriverClassName(dbConfigForm.getDriverClassName());
        dbConfig.setDdlAuto(dbConfigForm.getDdlAuto());
        dbConfig.setShowSql(dbConfigForm.isShowSql());
        // Issue a restart; note: actually causes JVM to exit and relaunch
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(1500); // let response flush
            } catch (InterruptedException ignored) {}
            org.springframework.boot.SpringApplication.exit(applicationContext, () -> 0);
        });
        thread.setDaemon(false);
        thread.start();
        return "Restarting application to apply new database configuration... Please wait and reload the page.";
    }
}
