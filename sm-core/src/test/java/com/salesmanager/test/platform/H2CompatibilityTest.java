package com.salesmanager.test.platform;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.sql.*;
import java.util.Properties;
import java.io.InputStream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

public class H2CompatibilityTest {
    @Test
    public void legacyPageStoreSupportsSql() throws Exception {
        assertTrue(org.h2.engine.Constants.VERSION.startsWith("1.4.200"), org.h2.engine.Constants.VERSION);
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:compatibility;MV_STORE=FALSE", "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE sample (id INT PRIMARY KEY, name VARCHAR(40))");
            assertEquals(1, statement.executeUpdate("INSERT INTO sample VALUES (1, 'legacy')"));
            try (ResultSet rows = statement.executeQuery("SELECT name FROM sample WHERE id=1")) {
                assertTrue(rows.next());
                assertEquals("legacy", rows.getString(1));
                assertFalse(rows.next());
            }
        }
    }

    @Test
    public void readsCopyOfSeedDatabase() throws Exception {
        Path shop = Paths.get("../sm-shop");
        Path seed = shop.resolve("SALESMANAGER.h2.db");
        Assumptions.assumeTrue(Files.isRegularFile(seed), "Seed database not present");
        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(shop.resolve("src/main/resources/profiles/docker/database.properties"))) {
            properties.load(stream);
        }
        Path directory = Files.createTempDirectory("shopizer-h2-");
        Path copy = directory.resolve("SALESMANAGER.h2.db");
        try {
            Files.copy(seed, copy);
            String url = properties.getProperty("db.jdbcUrl").replace("./SALESMANAGER",
                    directory.resolve("SALESMANAGER").toAbsolutePath().toString());
            try (Connection connection = DriverManager.getConnection(url + ";ACCESS_MODE_DATA=r",
                    properties.getProperty("db.user"), properties.getProperty("db.password"));
                 Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM SALESMANAGER.MERCHANT_STORE")) {
                assertTrue(rows.next());
                assertTrue(rows.getLong(1) > 0);
            }
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.list(directory)) {
                for (Path path : (Iterable<Path>) paths::iterator) {
                    Files.deleteIfExists(path);
                }
            }
            Files.deleteIfExists(directory);
        }
    }
}
