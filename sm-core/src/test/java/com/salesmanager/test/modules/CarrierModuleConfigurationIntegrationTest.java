package com.salesmanager.test.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.util.Iterator;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.system.ModuleConfigurationService;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.test.common.AbstractSalesManagerCoreTestCase;

public class CarrierModuleConfigurationIntegrationTest extends AbstractSalesManagerCoreTestCase {

	@Autowired
	private ModuleConfigurationService moduleConfigurationService;

	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	public void rejectsTamperedUpsEndpointAndPreservesStoredConfiguration() throws Exception {
		IntegrationModule before = moduleConfigurationService.getByCode("ups");
		assertNotNull(before);
		String originalConfiguration = before.getConfiguration();
		String tamperedJson = referenceUpsJson().replace("wwwcie.ups.com", "mallory.example");

		try {
			moduleConfigurationService.createOrUpdateModule(tamperedJson);
			fail("Expected tampered UPS endpoint to be rejected");
		} catch (ServiceException expected) {
			assertEquals(ServiceException.EXCEPTION_VALIDATION, expected.getExceptionType());
		}

		IntegrationModule after = moduleConfigurationService.getByCode("ups");
		assertNotNull(after);
		assertEquals(originalConfiguration, after.getConfiguration());
		assertTrue(after.getConfiguration().contains("wwwcie.ups.com")
				|| after.getConfiguration().contains("onlinetools.ups.com"));
		assertTrue(!after.getConfiguration().contains("mallory.example"));
	}

	@Test
	public void persistsValidUpsConfiguration() throws Exception {
		moduleConfigurationService.createOrUpdateModule(referenceUpsJson());

		IntegrationModule persisted = moduleConfigurationService.getByCode("ups");
		assertNotNull(persisted);
		assertTrue(persisted.getConfiguration().contains("wwwcie.ups.com")
				|| persisted.getConfiguration().contains("onlinetools.ups.com"));
		assertTrue(!persisted.getConfiguration().contains("mallory.example"));
	}

	private String referenceUpsJson() throws Exception {
		try (InputStream input = getClass().getClassLoader()
				.getResourceAsStream("reference/integrationmodules.json")) {
			assertNotNull(input);
			JsonNode definitions = mapper.readTree(input);
			Iterator<JsonNode> iterator = definitions.elements();
			while (iterator.hasNext()) {
				JsonNode definition = iterator.next();
				if ("ups".equals(definition.path("code").asText())) {
					return mapper.writeValueAsString(definition);
				}
			}
		}
		throw new AssertionError("UPS reference configuration was not found");
	}
}
