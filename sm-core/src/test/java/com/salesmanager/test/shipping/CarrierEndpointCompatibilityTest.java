package com.salesmanager.test.shipping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import com.salesmanager.core.business.modules.integration.shipping.impl.CarrierEndpoint;
import com.salesmanager.core.business.modules.integration.shipping.impl.UPSShippingQuote;
import com.salesmanager.core.business.modules.integration.shipping.impl.USPSShippingQuote;
import com.salesmanager.core.business.services.reference.loader.IntegrationModulesLoader;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.core.model.system.ModuleConfig;
import com.salesmanager.core.modules.integration.IntegrationException;

public class CarrierEndpointCompatibilityTest {

	@Test
	public void referenceCarrierUrlsMatchLegacyUrls() throws Exception {
		List<IntegrationModule> modules = new IntegrationModulesLoader()
				.loadIntegrationModules("reference/integrationmodules.json");
		for (IntegrationModule module : modules) {
			if (!"ups".equals(module.getCode()) && !"usps".equals(module.getCode())) {
				continue;
			}
			Set<String> schemes = allowed(module.getCode(), "ALLOWED_SCHEMES");
			Set<String> hosts = allowed(module.getCode(), "ALLOWED_HOSTS");
			for (ModuleConfig config : module.getModuleConfigs().values()) {
				String legacyUrl = config.getScheme() + "://" + config.getHost() + ":" + config.getPort()
						+ config.getUri();
				assertEquals(module.getCode() + " " + config.getEnv(), legacyUrl,
						CarrierEndpoint.buildUrl(module.getCode(), config, schemes, hosts));
			}
		}
	}

	@Test
	public void normalizesSchemeAndHostAndOmitsBlankPort() throws Exception {
		ModuleConfig mixedCase = config(" HTTPS ", " OnlineTools.UPS.com ", "443", "/xml/Rate");
		assertEquals("https://onlinetools.ups.com:443/xml/Rate",
				CarrierEndpoint.buildUrl("UPS", mixedCase, upsSchemes(), upsHosts()));

		ModuleConfig blankPort = config("https", "onlinetools.ups.com", "", "/xml/Rate");
		assertEquals("https://onlinetools.ups.com/xml/Rate",
				CarrierEndpoint.buildUrl("UPS", blankPort, upsSchemes(), upsHosts()));
	}

	@Test
	public void encodedXmlQueryKeepsThePinnedHost() throws Exception {
		String endpoint = CarrierEndpoint.buildUrl("UPS",
				config("https", "onlinetools.ups.com", "443", "/xml/Rate"), upsSchemes(), upsHosts());
		String query = URLEncoder.encode("<x a=\"1\"/>", StandardCharsets.UTF_8.name());
		URI request = new URI(endpoint + "?API=RateV3&XML=" + query);
		assertEquals("onlinetools.ups.com", request.getHost());
	}

	@Test
	public void rejectsHostAndPathVariantsThatCouldChangeTheAuthority() throws Exception {
		assertRejected(config("https", "user@onlinetools.ups.com", "443", "/xml/Rate"), upsSchemes(), upsHosts());
		assertRejected(config("https", "onlinetools.ups.com:8443", "443", "/xml/Rate"), upsSchemes(), upsHosts());
		assertRejected(config("https", "127.0.0.1", "443", "/xml/Rate"), upsSchemes(), upsHosts());
		assertRejected(config(null, "onlinetools.ups.com", "443", "/xml/Rate"), upsSchemes(), upsHosts());
		assertRejected(config("https", "onlinetools.ups.com", "443", ""), upsSchemes(), upsHosts());
		assertRejected(config("https", "onlinetools.ups.com", "0443", "/xml/Rate"), upsSchemes(), upsHosts());
		assertRejected(config("https", "onlinetools.ups.com", "8443", "/xml/Rate"), upsSchemes(), upsHosts());
		assertRejected(config("http", "onlinetools.ups.com", "443", "/xml/Rate"), upsSchemes(), upsHosts());

		ModuleConfig uspsConfig = config("http", "production.shippingapis.com", "80", "/ShippingAPI.dll");
		assertRejected(uspsConfig, upsSchemes(), upsHosts());

		ModuleConfig encodedAuthorityPath = config("https", "onlinetools.ups.com", "443",
				"/%2F%2Fattacker.example/x");
		try {
			URI result = new URI(CarrierEndpoint.buildUrl("UPS", encodedAuthorityPath, upsSchemes(), upsHosts()));
			assertEquals("onlinetools.ups.com", result.getHost());
		} catch (IntegrationException expected) {
			assertTrue(expected.getMessageCode().contains("path") || expected.getMessageCode().contains("endpoint"));
		}
	}

	@SuppressWarnings("unchecked")
	private static Set<String> allowed(String code, String name) throws Exception {
		Class<?> carrier = "ups".equals(code) ? UPSShippingQuote.class : USPSShippingQuote.class;
		Field field = carrier.getDeclaredField(name);
		field.setAccessible(true);
		return (Set<String>) field.get(null);
	}

	private static Set<String> upsSchemes() throws Exception {
		return allowed("ups", "ALLOWED_SCHEMES");
	}

	private static Set<String> upsHosts() throws Exception {
		return allowed("ups", "ALLOWED_HOSTS");
	}

	private static void assertRejected(ModuleConfig config, Set<String> schemes, Set<String> hosts) throws Exception {
		try {
			CarrierEndpoint.buildUrl("UPS", config, schemes, hosts);
			fail("Expected an unsafe carrier endpoint to be rejected");
		} catch (IntegrationException expected) {
			assertTrue(expected.getMessageCode() != null);
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
