package com.salesmanager.test.shipping;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

import org.junit.Test;

import com.salesmanager.core.business.modules.integration.shipping.impl.UPSShippingQuote;
import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.country.Country;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.core.model.system.ModuleConfig;
import com.salesmanager.core.modules.integration.IntegrationException;

public class CarrierQuoteEndpointPinningTest {

	@Test
	public void rejectsUntrustedUpsHostBeforeOpeningAnHttpConnection() throws Exception {
		IntegrationModule module = moduleWithConfig("attacker.example");
		IntegrationException failure = quoteFailure(module);
		IntegrationException endpointFailure = endpointFailure(failure);
		assertTrue(endpointFailure.getMessageCode(),
				endpointFailure.getMessageCode().contains("not an allowed carrier endpoint"));
	}

	@Test
	public void rejectsMissingEnvironmentConfigBeforeOpeningAnHttpConnection() throws Exception {
		IntegrationModule module = moduleWithConfig(null);
		IntegrationException failure = quoteFailure(module);
		IntegrationException endpointFailure = endpointFailure(failure);
		assertTrue(endpointFailure.getMessageCode(),
				endpointFailure.getMessageCode().contains("No UPS module configuration"));
	}

	private static IntegrationException endpointFailure(IntegrationException failure) {
		assertTrue(failure.getCause() instanceof IntegrationException);
		return (IntegrationException) failure.getCause();
	}

	private static IntegrationException quoteFailure(IntegrationModule module) throws Exception {
		IntegrationConfiguration configuration = new IntegrationConfiguration();
		configuration.setEnvironment("PROD");
		configuration.setIntegrationOptions(Collections.singletonMap("packages", Collections.singletonList("03")));
		Map<String, String> keys = new HashMap<String, String>();
		keys.put("accessKey", "access");
		keys.put("userId", "user");
		keys.put("password", "password");
		configuration.setIntegrationKeys(keys);

		Country unitedStates = new Country();
		unitedStates.setIsoCode("US");
		Delivery delivery = new Delivery();
		delivery.setCountry(unitedStates);
		delivery.setPostalCode("10001");

		MerchantStore store = new MerchantStore();
		store.setCountry(unitedStates);

		try {
			new UPSShippingQuote().getShippingQuotes(null, Collections.emptyList(), null, delivery, null, store,
					configuration, module, null, Locale.US);
			fail("Expected an untrusted or missing endpoint to be rejected before network I/O");
		} catch (IntegrationException expected) {
			return expected;
		}
		throw new AssertionError("Expected an IntegrationException");
	}

	private static IntegrationModule moduleWithConfig(String host) {
		IntegrationModule module = new IntegrationModule();
		module.setCode("ups");
		module.getRegionsSet().add("US");
		if (host != null) {
			ModuleConfig config = new ModuleConfig();
			config.setEnv("PROD");
			config.setScheme("https");
			config.setHost(host);
			config.setPort("443");
			config.setUri("/xml/Rate");
			module.getModuleConfigs().put("PROD", config);
		}
		return module;
	}
}
