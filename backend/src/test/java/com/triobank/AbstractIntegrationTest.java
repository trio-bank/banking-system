package com.triobank;

import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base class for backend integration tests (docs/09-testing.md §1).
 * Starts the full application against a real PostgreSQL 18 from Testcontainers
 * and gives subclasses MockMvc, JDBC access, a movable clock and test data helpers.
 * All subclasses share one cached Spring context, so the container is started only once per test run.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    /** Moves time for lockout and token tests. Reset after every test. */
    @Autowired
    protected MutableClock clock;

    protected JdbcTemplate jdbc;

    protected TestDataFactory data;

    @Autowired
    protected void setDataSource(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void createDataFactory() {
        this.data = new TestDataFactory(mockMvc);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    /**
     * Runs a "select count(*)" style query. Named countRows (not count) so that test classes
     * can declare their own private count(...) helpers without clashing with this one.
     */
    protected long countRows(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

}
