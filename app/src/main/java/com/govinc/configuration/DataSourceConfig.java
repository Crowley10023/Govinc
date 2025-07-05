package com.govinc.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;

@Configuration
public class DataSourceConfig {
    @Autowired
    private DatabaseConfig dbConfig;

    @Primary
    @Bean
    @ConditionalOnMissingBean
    public DataSource dataSource() {
        // Attempt to connect with user properties
        if (isConnectable(dbConfig)) {
            return buildUserDataSource(dbConfig);
        }
        // If user config is bad, fallback H2
        return buildInMemoryH2();
    }

    private boolean isConnectable(DatabaseConfig config) {
        try {
            if (!StringUtils.hasLength(config.getUrl()) || !StringUtils.hasLength(config.getDriverClassName())) {
                return false;
            }
            Class.forName(config.getDriverClassName());
            try (Connection conn = DriverManager.getConnection(
                    config.getUrl(), config.getUsername(), config.getPassword())) {
                return conn != null && !conn.isClosed();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private DataSource buildUserDataSource(DatabaseConfig config) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName(config.getDriverClassName());
        ds.setUrl(config.getUrl());
        ds.setUsername(config.getUsername());
        ds.setPassword(config.getPassword());
        return ds;
    }

    private DataSource buildInMemoryH2() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        ds.setUsername("sa");
        ds.setPassword("");
        return ds;
    }
}
