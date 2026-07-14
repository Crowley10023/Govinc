package com.govinc;

import com.govinc.repository.DatabaseConfigRepository;
import com.govinc.service.DatabaseMigrationService;
import com.govinc.user.User;
import com.govinc.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DatabaseStartupIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseConfigRepository databaseConfigRepository;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void freshInstallBootstrapsQuotedUserTableAndCurrentSchemaVersion() {
        Integer userCount = jdbcTemplate.queryForObject("select count(*) from \"user\"", Integer.class);

        assertThat(userCount).isNotNull();
        assertThat(databaseConfigRepository.findByVersionKey("schema_version"))
                .isPresent()
                .get()
                .extracting(config -> config.getCurrentVersion())
                .isEqualTo(DatabaseMigrationService.CURRENT_SCHEMA_VERSION);
    }

    @Test
    void builtInAdminUserHasAdminAuthorityInTestProfile() {
        UserDetails admin = userDetailsService.loadUserByUsername("admin");

        assertThat(admin.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMIN");
    }

    @Test
    void userRepositoryCanFetchAdminByEmailOnH2() {
        User adminUser = new User("admin", "", "admin@example.com");
        adminUser.setRole(com.govinc.user.Role.ADMIN);
        userRepository.save(adminUser);

        User admin = userRepository.findByEmail("admin@example.com").orElseThrow();

        assertThat(admin.getEmail()).isEqualTo("admin@example.com");
        assertThat(admin.getRole().name()).isEqualTo("ADMIN");
    }
}