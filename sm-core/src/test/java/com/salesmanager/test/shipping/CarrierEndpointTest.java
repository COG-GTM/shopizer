package com.salesmanager.test.shipping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.Set;

import org.junit.Test;

import com.salesmanager.core.business.modules.integration.shipping.impl.CarrierEndpoint;
import com.salesmanager.core.model.system.ModuleConfig;
import com.salesmanager.core.modules.integration.IntegrationException;

public class CarrierEndpointTest {

	private static final Set<String> SCHEMES = Collections.singleton("https");
	private static final Set<String> HOSTS = Collections.singleton("onlinetools.ups.com");

	@Test
	public void buildsUrlForAllowedHost() throws Exception {
		assertEquals("https://onlinetools.ups.com:443/xml/Rate",
				CarrierEndpoint.buildUrl("UPS", config("https", "onlinetools.ups.com", "443", "/xml/Rate"), SCHEMES, HOSTS));
	}

	@Test
	public void rejectsUnsafeEndpoints() {
		assertRejected(null);
		assertRejected(config("https", "attacker.example", "443", "/xml/Rate"));
		assertRejected(config("https", "169.254.169.254", "443", "/latest/meta-data"));
		assertRejected(config("http", "onlinetools.ups.com", "443", "/xml/Rate"));
		assertRejected(config("https", "onlinetools.ups.com", "8080", "/xml/Rate"));
		assertRejected(config("https", "onlinetools.ups.com", "443", "@attacker.example/xml"));
		assertRejected(config("https", "onlinetools.ups.com", "443", "//attacker.example/xml"));
		assertRejected(config("https", "onlinetools.ups.com.attacker.example", "443", "/xml/Rate"));
	}

	private static void assertRejected(ModuleConfig config) {
		try {
			CarrierEndpoint.buildUrl("UPS", config, SCHEMES, HOSTS);
			fail("Expected endpoint to be rejected");
		} catch (IntegrationException expected) {
			// expected
		}
	}

	private static ModuleConfig config(String scheme, String host, String port, String uri) {
		ModuleConfig config = new ModuleConfig();
		config.setScheme(scheme);
		config.setHost(host);
		config.setPort(port);
		config.setUri(uri);
		return config;
	}

}
