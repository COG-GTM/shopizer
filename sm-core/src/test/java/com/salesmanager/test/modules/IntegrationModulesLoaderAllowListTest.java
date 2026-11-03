package com.salesmanager.test.modules;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.reference.loader.IntegrationModulesLoader;
import com.salesmanager.core.model.system.IntegrationModule;

public class IntegrationModulesLoaderAllowListTest {

	private final IntegrationModulesLoader loader = new IntegrationModulesLoader();

	@Test
	public void everyBundledModuleReloadsFromItsRawJsonMap() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		try (InputStream input = getClass().getClassLoader()
				.getResourceAsStream("reference/integrationmodules.json")) {
			@SuppressWarnings("unchecked")
			Map<String, Object>[] definitions = mapper.readValue(input, Map[].class);
			for (Map<String, Object> definition : definitions) {
				IntegrationModule module = loader.loadModule(definition);
				assertTrue(module.getCode(), module.getCode() != null);
			}
		}
	}

	@Test
	public void acceptsCaseInsensitivePairsAndHostsDeclaredInAnotherEnvironment() throws Exception {
		loader.loadModule(module("ups", "HTTPS", "OnlineTools.UPS.com", "443", "/xml/Rate", "PROD"));
		loader.loadModule(module("ups", "https", "wwwcie.ups.com", "443", "/ups.app/xml/Rate", "PROD"));
	}

	@Test
	public void allowsNotRequiredAndModulesWithoutEndpointConfiguration() throws Exception {
		loader.loadModule(module("braintree", "https", "NOTREQUIRED", "NOTREQUIRED", "NOTREQUIRED", "PROD"));
		Map<String, Object> definition = new HashMap<String, Object>();
		definition.put("module", "SHIPPING");
		definition.put("code", "local-module");
		loader.loadModule(definition);
	}

	@Test
	public void validatesPortAndAllowsBlankPort() throws Exception {
		assertRejected(module("ups", "https", "onlinetools.ups.com", "443a", "/xml/Rate", "PROD"), "ups");
		loader.loadModule(module("ups", "https", "onlinetools.ups.com", "", "/xml/Rate", "PROD"));
	}

	@Test
	public void scopesPaymentHostsToTheirOwnModule() throws Exception {
		loader.loadModule(module("beanstream", "https", "www.beanstream.com", "443",
				"/scripts/process_transaction.asp", "PROD"));
		loader.loadModule(module("stripe", "https", "www.stripe.com", "443", "/", "PROD"));
		assertRejected(module("beanstream", "https", "www.stripe.com", "443", "/", "PROD"), "beanstream");
	}

	@Test
	public void rejectsUspsHttpsProductionHostBecauseOnlyHttpIsDeclared() throws Exception {
		assertRejected(module("usps", "https", "production.shippingapis.com", "443", "/ShippingAPI.dll", "PROD"),
				"usps");
	}

	private void assertRejected(Map<String, Object> definition, String code) throws Exception {
		try {
			loader.loadModule(definition);
			fail("Expected module endpoint to be rejected");
		} catch (ServiceException expected) {
			assertTrue(expected.getMessageCode(), expected.getMessageCode().contains(code));
		}
	}

	private static Map<String, Object> module(String code, String scheme, String host, String port, String uri,
			String environment) {
		Map<String, Object> config = new HashMap<String, Object>();
		config.put("env", environment);
		config.put("scheme", scheme);
		config.put("host", host);
		config.put("port", port);
		config.put("uri", uri);
		List<Object> configs = new ArrayList<Object>();
		configs.add(config);

		Map<String, Object> definition = new HashMap<String, Object>();
		definition.put("module", "PAYMENT");
		definition.put("code", code);
		definition.put("configuration", configs);
		return definition;
	}
}
