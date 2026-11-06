package com.triobank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class IntegrationTestBaseSmokeTest extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void databaseIsPostgres18FromTestcontainers() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("select version()")) {
            result.next();
            assertThat(result.getString(1)).startsWith("PostgreSQL 18");
        }
    }

    @Test
    void mockMvcIsAvailableAndHealthIsUp() throws Exception {
        String body = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"status\":\"UP\"");
    }

}
