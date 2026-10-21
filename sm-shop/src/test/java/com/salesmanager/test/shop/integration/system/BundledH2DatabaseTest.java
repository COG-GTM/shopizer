package com.salesmanager.test.shop.integration.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.h2.Driver;
import org.h2.engine.Constants;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class BundledH2DatabaseTest {

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void bundledDriverIsH2Version14200() {
		Driver driver = new Driver();

		assertEquals(1, driver.getMajorVersion());
		assertEquals(4, driver.getMinorVersion());
		assertEquals(200, Constants.BUILD_ID);
	}

	@Test
	public void bundledDriverSupportsInMemoryDatabase() throws Exception {
		String url = "jdbc:h2:mem:pin534;mv_store=false;INIT=CREATE SCHEMA IF NOT EXISTS SALESMANAGER";

		try (Connection connection = DriverManager.getConnection(url, "test", "password");
				Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery("SELECT 1")) {
			assertTrue(result.next());
			assertEquals(1, result.getInt(1));
		}
	}

	@Test
	public void committedSeedDatabaseContainsDefaultStoreAndProducts() throws Exception {
		File seedDatabase = new File("SALESMANAGER.h2.db");
		Assume.assumeTrue(seedDatabase.isFile());

		File databaseDirectory = temporaryFolder.newFolder("seed-database");
		Path copiedDatabase = databaseDirectory.toPath().resolve(seedDatabase.getName());
		Files.copy(seedDatabase.toPath(), copiedDatabase);
		String url = "jdbc:h2:file:" + databaseDirectory.toPath().resolve("SALESMANAGER").toAbsolutePath()
				+ ";mv_store=false;IFEXISTS=TRUE";

		try (Connection connection = DriverManager.getConnection(url, "test", "password");
				Statement statement = connection.createStatement()) {
			try (ResultSet products = statement.executeQuery("SELECT COUNT(*) FROM SALESMANAGER.PRODUCT")) {
				assertTrue(products.next());
				assertEquals(4, products.getInt(1));
			}

			try (ResultSet store = statement.executeQuery(
					"SELECT STORE_CODE FROM SALESMANAGER.MERCHANT_STORE WHERE STORE_CODE = 'DEFAULT'")) {
				assertTrue(store.next());
				assertEquals("DEFAULT", store.getString(1));
			}
		}
	}
}
