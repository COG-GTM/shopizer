package com.salesmanager.core.business.modules.integration.shipping.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.services.reference.loader.IntegrationModulesLoader;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.core.model.system.ModuleConfig;
import com.salesmanager.core.modules.integration.IntegrationException;

public class CarrierEndpointsTest {

	@Test
	public void resolvesFixedUpsEndpoints() throws Exception {
		assertEquals("https://onlinetools.ups.com:443/xml/Rate",
				CarrierEndpoints.resolve(CarrierEndpoints.UPS, Constants.PRODUCTION_ENVIRONMENT).toUrl());
		assertEquals("https://wwwcie.ups.com:443/ups.app/xml/Rate",
				CarrierEndpoints.resolve(CarrierEndpoints.UPS, Constants.TEST_ENVIRONMENT).toUrl());
	}

	@Test
	public void resolvesFixedUspsEndpoints() throws Exception {
		assertEquals("http://production.shippingapis.com:80/ShippingAPI.dll",
				CarrierEndpoints.resolve(CarrierEndpoints.USPS, Constants.PRODUCTION_ENVIRONMENT).toUrl());
		assertEquals("http://testing.shippingapis.com:80/ShippingAPI.dll",
				CarrierEndpoints.resolve(CarrierEndpoints.USPS, Constants.TEST_ENVIRONMENT).toUrl());
	}

	@Test(expected = IntegrationException.class)
	public void rejectsUnknownEnvironment() throws Exception {
		CarrierEndpoints.resolve(CarrierEndpoints.UPS, "STAGING");
	}

	@Test(expected = IntegrationException.class)
	public void rejectsUnknownCarrier() throws Exception {
		CarrierEndpoints.resolve("canadapost", Constants.PRODUCTION_ENVIRONMENT);
	}

	@Test
	public void shippedCarrierModulesAreValid() throws Exception {
		List<IntegrationModule> modules = new IntegrationModulesLoader()
				.loadIntegrationModules("reference/integrationmodules.json");
		int carriers = 0;
		for (IntegrationModule module : modules) {
			if (CarrierEndpoints.isCarrier(module.getCode())) {
				CarrierEndpoints.validate(module);
				carriers++;
			}
		}
		assertEquals(2, carriers);
	}

	@Test
	public void rejectsCarrierModuleWithAttackerHost() {
		assertRejected(CarrierEndpoints.UPS, config(Constants.PRODUCTION_ENVIRONMENT, "https", "mallory.example", "443", "/xml/Rate"));
		assertRejected(CarrierEndpoints.USPS, config(Constants.PRODUCTION_ENVIRONMENT, "http", "169.254.169.254", "80", "/ShippingAPI.dll"));
	}

	@Test
	public void rejectsCarrierModuleWithChangedSchemePortOrPath() {
		assertRejected(CarrierEndpoints.UPS, config(Constants.PRODUCTION_ENVIRONMENT, "http", "onlinetools.ups.com", "443", "/xml/Rate"));
		assertRejected(CarrierEndpoints.UPS, config(Constants.PRODUCTION_ENVIRONMENT, "https", "onlinetools.ups.com", "8443", "/xml/Rate"));
		assertRejected(CarrierEndpoints.UPS, config(Constants.PRODUCTION_ENVIRONMENT, "https", "onlinetools.ups.com", "443", "@mallory.example/"));
		assertRejected(CarrierEndpoints.UPS, config("STAGING", "https", "onlinetools.ups.com", "443", "/xml/Rate"));
	}

	@Test
	public void ignoresNonCarrierModules() {
		IntegrationModule module = module("paypal-express-checkout",
				config(Constants.PRODUCTION_ENVIRONMENT, "https", "anything.example", "443", "/"));
		CarrierEndpoints.validate(module);
		assertNotNull(module);
	}

	private static void assertRejected(String code, ModuleConfig config) {
		try {
			CarrierEndpoints.validate(module(code, config));
			fail("expected carrier module to be rejected: " + config.getHost());
		} catch (IllegalArgumentException expected) {
			// expected
		}
	}

	private static IntegrationModule module(String code, ModuleConfig config) {
		IntegrationModule module = new IntegrationModule();
		module.setCode(code);
		Map<String, ModuleConfig> configs = new HashMap<String, ModuleConfig>();
		configs.put(config.getEnv(), config);
		module.setModuleConfigs(configs);
		return module;
	}

	private static ModuleConfig config(String env, String scheme, String host, String port, String uri) {
		ModuleConfig config = new ModuleConfig();
		config.setEnv(env);
		config.setScheme(scheme);
		config.setHost(host);
		config.setPort(port);
		config.setUri(uri);
		return config;
	}
}
