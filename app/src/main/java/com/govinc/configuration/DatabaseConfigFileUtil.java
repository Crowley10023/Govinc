package com.govinc.configuration;

import java.io.*;
import java.util.Properties;

public class DatabaseConfigFileUtil {
    public static void saveToPropertiesFile(DatabaseConfig dbConfig, String filename) throws IOException {
        FileInputStream in = new FileInputStream(filename);
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

        FileOutputStream out = new FileOutputStream(filename);
        props.store(out, null);
        out.close();
    }
}
