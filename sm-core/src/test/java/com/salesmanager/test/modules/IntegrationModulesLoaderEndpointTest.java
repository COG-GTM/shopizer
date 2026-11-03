package com.salesmanager.test.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.reference.loader.IntegrationModulesLoader;
import com.salesmanager.core.model.system.IntegrationModule;

public class IntegrationModulesLoaderEndpointTest {

	private final IntegrationModulesLoader loader = new IntegrationModulesLoader();

	@Test
	public void referenceModulesLoad() throws Exception {
		List<IntegrationModule> modules = loader.loadIntegrationModules("reference/integrationmodules.json");
		assertTrue(modules.size() > 0);
	}

	@Test
	public void knownCarrierHostIsAccepted() throws Exception {
		IntegrationModule module = loader.loadModule(module("ups", "https", "onlinetools.ups.com", "443", "/xml/Rate"));
		assertEquals("onlinetools.ups.com", module.getModuleConfigs().get("PROD").getHost());
	}

	@Test
	public void blankHostIsAccepted() throws Exception {
		loader.loadModule(module("paypal-express-checkout", "", "", "", ""));
	}

	@Test
	public void attackerHostIsRejected() throws Exception {
		assertRejected(module("ups", "https", "attacker.example", "443", "/xml/Rate"));
		assertRejected(module("beanstream", "https", "127.0.0.1", "443", "/scripts/process_transaction.asp"));
	}

	@Test
	public void schemeDowngradeIsRejected() throws Exception {
		assertRejected(module("ups", "http", "onlinetools.ups.com", "80", "/xml/Rate"));
	}

	@Test
	public void hostFromAnotherModuleIsRejected() throws Exception {
		assertRejected(module("beanstream", "https", "onlinetools.ups.com", "443", "/xml/Rate"));
	}

	@Test
	public void unknownModuleWithHostIsRejected() throws Exception {
		assertRejected(module("custom-gateway", "https", "onlinetools.ups.com", "443", "/"));
	}

	@Test
	public void invalidUriIsRejected() throws Exception {
		assertRejected(module("ups", "https", "onlinetools.ups.com", "443", "@attacker.example/x"));
		assertRejected(module("ups", "https", "onlinetools.ups.com", "443", "//attacker.example/x"));
	}

	private void assertRejected(Map<String, Object> json) throws Exception {
		try {
			loader.loadModule(json);
			fail("Expected module endpoint to be rejected");
		} catch (ServiceException expected) {
			// expected
		}
	}

	private static Map<String, Object> module(String code, String scheme, String host, String port, String uri) {
		Map<String, Object> config = new HashMap<String, Object>();
		config.put("env", "PROD");
		config.put("scheme", scheme);
		config.put("host", host);
		config.put("port", port);
		config.put("uri", uri);
		List<Object> configs = new ArrayList<Object>();
		configs.add(config);

		Map<String, Object> json = new HashMap<String, Object>();
		json.put("module", "SHIPPING");
		json.put("code", code);
		json.put("regions", Collections.singletonList("*"));
		json.put("configuration", configs);
		return json;
	}

}
