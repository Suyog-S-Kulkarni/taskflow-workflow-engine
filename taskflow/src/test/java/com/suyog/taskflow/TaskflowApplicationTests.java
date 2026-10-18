package com.suyog.taskflow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TaskflowApplicationTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void contextLoads() {
		assertNotNull(jdbcTemplate);
	}

	@Test
	void usesTestDatabaseAndAppliesInitialMigration() {
		String database = jdbcTemplate.queryForObject(
				"SELECT DATABASE()",
				String.class
		);

		assertEquals("flowforge_test", database);

		Integer successfulMigrations = jdbcTemplate.queryForObject(
				"""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '1'
                  AND success = 1
                """,
				Integer.class
		);

		assertEquals(1, successfulMigrations.intValue());

		Integer definitionTables = jdbcTemplate.queryForObject(
				"""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = 'workflow_definition'
                """,
				Integer.class
		);

		assertEquals(1, definitionTables.intValue());
	}
}