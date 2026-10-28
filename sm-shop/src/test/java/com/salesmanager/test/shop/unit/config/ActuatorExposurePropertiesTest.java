package com.salesmanager.test.shop.unit.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Guards the actuator exposure settings shipped in sm-shop application.properties.
 */
public class ActuatorExposurePropertiesTest {

	private static Properties properties;

	@BeforeClass
	public static void load() throws Exception {
		properties = new Properties();
		try (InputStream in = ActuatorExposurePropertiesTest.class.getClassLoader()
				.getResourceAsStream("application.properties")) {
			assertNotNull("application.properties not on classpath", in);
			properties.load(in);
		}
	}

	@Test
	public void onlyHealthAndInfoAreExposedOverHttp() {
		String include = properties.getProperty("management.endpoints.web.exposure.include");
		assertNotNull(include);
		Set<String> exposed = new HashSet<>();
		for (String id : include.split(",")) {
			exposed.add(id.trim());
		}
		assertEquals(new HashSet<>(Arrays.asList("health", "info")), exposed);
	}

	@Test
	public void healthDetailsAreHiddenButComponentsShown() {
		assertEquals("never", properties.getProperty("management.endpoint.health.show-details").trim());
		assertEquals("always", properties.getProperty("management.endpoint.health.show-components").trim());
	}

	@Test
	public void pingHealthIndicatorStaysEnabled() {
		assertEquals("true", properties.getProperty("management.health.ping.enabled").trim());
	}

}
