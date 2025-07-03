package com.govinc.configuration;

import java.io.*;
import java.util.Properties;

public class DatabaseConfigFileUtil {
    public static void saveToPropertiesFile(DatabaseConfig dbConfig, String filename) throws IOException {
        java.io.File f = new java.io.File(filename);
        System.out.println("\n\n f......" + f.getAbsolutePath());

        FileInputStream in = new FileInputStream(f);
        Properties props = new Properties();
        props.load(in);
        in.close();

        // Update database properties
        props.setProperty("spring.datasource.url", dbConfig.getUrl());
        props.setProperty("spring.datasource.username", dbConfig.getUsername());
        props.setProperty("spring.datasource.password", dbConfig.getPassword());
        props.setProperty("spring.datasource.driver-class-name", dbConfig.getDriverClassName());
        props.setProperty("spring.jpa.hibernate.ddl-auto", dbConfig.getDdlAuto());
        props.setProperty("spring.jpa.show-sql", String.valueOf(dbConfig.isShowSql()));

        FileOutputStream out = new FileOutputStream(f);
        props.store(out, null);
        out.close();
    }
}
