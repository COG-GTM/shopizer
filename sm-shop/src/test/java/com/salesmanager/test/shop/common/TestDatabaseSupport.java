package com.salesmanager.test.shop.common;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

public final class TestDatabaseSupport {

	private TestDatabaseSupport() {
	}

	public static void assertDatabaseUrl(DataSource dataSource, String databaseName) throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			assertTrue(connection.getMetaData().getURL().contains(databaseName));
		}
	}
}
