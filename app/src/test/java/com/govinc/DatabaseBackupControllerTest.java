package com.govinc;

import com.govinc.controller.DatabaseConfigController;
import com.govinc.service.BackupExtractService;
import com.govinc.service.DatabaseBackupJobService;
import com.govinc.service.DatabaseMigrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DatabaseBackupControllerTest {
    private final DatabaseBackupJobService jobService = mock(DatabaseBackupJobService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        DatabaseConfigController controller = new DatabaseConfigController(mock(DatabaseMigrationService.class),
                mock(BackupExtractService.class), jobService);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void startReturnsAcceptedAndReportsExistingJob() throws Exception {
        when(jobService.startBackup()).thenReturn(true, false);

        mvc.perform(post("/config/database/backup"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.started").value(true));
        mvc.perform(post("/config/database/backup"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.started").value(false));
    }

    @Test
    void statusReturnsCompletedBackup() throws Exception {
        when(jobService.getStatus()).thenReturn(new DatabaseBackupJobService.BackupStatus(
                "SUCCEEDED", Map.of("fileName", "backup.sql"), null));

        mvc.perform(get("/config/database/backup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SUCCEEDED"))
                .andExpect(jsonPath("$.backup.fileName").value("backup.sql"));
    }
}