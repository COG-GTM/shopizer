package com.salesmanager.core.business.modules.integration.snowflake;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.snowflake.client.jdbc.SnowflakeBasicDataSource;

/**
 * Builds the Snowflake DataSource used by the outbound order export from the
 * snowflake.* properties. The datasource is only created when
 * snowflake.export.enabled is true, nothing connects to Snowflake otherwise.
 *
 */
@Configuration
@ConditionalOnProperty(name = "snowflake.export.enabled", havingValue = "true")
public class SnowflakeConfiguration {

	private static final Logger LOGGER = LoggerFactory.getLogger(SnowflakeConfiguration.class);

	@Value("${snowflake.url:}")
	private String url;

	@Value("${snowflake.user:}")
	private String user;

	@Value("${snowflake.password:}")
	private String password;

	@Value("${snowflake.warehouse:}")
	private String warehouse;

	@Value("${snowflake.db:}")
	private String db;

	@Value("${snowflake.schema:}")
	private String schema;

	@Value("${snowflake.role:}")
	private String role;

	@Bean(name = "snowflakeDataSource")
	public DataSource snowflakeDataSource() {

		SnowflakeBasicDataSource dataSource = new SnowflakeBasicDataSource();
		dataSource.setUrl(url);
		dataSource.setUser(user);
		dataSource.setPassword(password);
		dataSource.setWarehouse(warehouse);
		dataSource.setDatabaseName(db);
		dataSource.setSchema(schema);
		if (role != null && !role.isEmpty()) {
			dataSource.setRole(role);
		}

		LOGGER.info("Snowflake export configured on database [{}] schema [{}] warehouse [{}]", db, schema, warehouse);

		return dataSource;
	}

}
