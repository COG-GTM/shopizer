package com.salesmanager.test.business.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.salesmanager.core.model.system.IntegrationConfiguration;
import java.util.HashMap;
import java.util.Map;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.jupiter.api.Test;

class JsonSimpleSerializationTest {

	@Test
	void integrationConfigurationRoundTripsThroughJsonSimple() throws Exception {
		IntegrationConfiguration configuration = new IntegrationConfiguration();
		configuration.setModuleCode("shipping-test");
		configuration.setActive(true);
		configuration.setDefaultSelected(true);
		configuration.setEnvironment(IntegrationConfiguration.TEST_ENVIRONMENT);
		Map<String, String> keys = new HashMap<>();
		keys.put("account", "test-account");
		configuration.setIntegrationKeys(keys);

		JSONObject parsed = (JSONObject) new JSONParser().parse(configuration.toJSONString());

		assertEquals("shipping-test", parsed.get("moduleCode"));
		assertEquals(Boolean.TRUE, parsed.get("active"));
		assertEquals(Boolean.TRUE, parsed.get("defaultSelected"));
		assertEquals(IntegrationConfiguration.TEST_ENVIRONMENT, parsed.get("environment"));
		assertEquals("test-account", ((JSONObject) parsed.get("integrationKeys")).get("account"));
	}
}
