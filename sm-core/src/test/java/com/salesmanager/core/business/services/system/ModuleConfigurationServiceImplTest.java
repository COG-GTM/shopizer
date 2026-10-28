package com.salesmanager.core.business.services.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.Iterator;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.repositories.system.ModuleConfigurationRepository;
import com.salesmanager.core.business.services.reference.loader.IntegrationModulesLoader;
import com.salesmanager.core.model.system.IntegrationModule;

public class ModuleConfigurationServiceImplTest {

	private ModuleConfigurationRepository repository;
	private ModuleConfigurationServiceImpl service;
	private final ObjectMapper mapper = new ObjectMapper();

	@Before
	public void setUp() {
		repository = mock(ModuleConfigurationRepository.class);
		service = new ModuleConfigurationServiceImpl(repository);
		ReflectionTestUtils.setField(service, "integrationModulesLoader", new IntegrationModulesLoader());
	}

	@Test
	public void rejectsUpsAttackerHostBeforeRepositoryChanges() throws Exception {
		assertValidationFailure(moduleJson("ups", "TEST", "https", "mallory.example", "443",
				"/ups.app/xml/Rate"));
	}

	@Test
	public void rejectsUspsChangedSchemeAndPortBeforeRepositoryChanges() throws Exception {
		assertValidationFailure(moduleJson("usps", "TEST", "https", "testing.shippingapis.com", "8443",
				"/ShippingAPI.dll"));
	}

	@Test
	public void persistsUpsDefinitionCopiedFromReferenceConfiguration() throws Exception {
		service.createOrUpdateModule(referenceUpsJson());

		verify(repository).saveAndFlush(any(IntegrationModule.class));
		verify(repository, never()).delete(any(IntegrationModule.class));
	}

	@Test
	public void persistsNonCarrierWithArbitraryHost() throws Exception {
		service.createOrUpdateModule(moduleJson("custom-shipping", "TEST", "https",
				"mallory.example", "443", "/custom"));

		verify(repository).saveAndFlush(any(IntegrationModule.class));
		verify(repository, never()).delete(any(IntegrationModule.class));
	}

	@Test
	public void reportsMalformedJsonAsNonValidationServiceError() throws Exception {
		try {
			service.createOrUpdateModule("{");
			fail("Expected malformed JSON to fail");
		} catch (ServiceException expected) {
			assertNotEquals(ServiceException.EXCEPTION_VALIDATION, expected.getExceptionType());
		}
		verify(repository, never()).saveAndFlush(any(IntegrationModule.class));
		verify(repository, never()).delete(any(IntegrationModule.class));
	}

	private void assertValidationFailure(String json) throws Exception {
		try {
			service.createOrUpdateModule(json);
			fail("Expected invalid carrier endpoint to be rejected");
		} catch (ServiceException expected) {
			assertEquals(ServiceException.EXCEPTION_VALIDATION, expected.getExceptionType());
		}
		verify(repository, never()).saveAndFlush(any(IntegrationModule.class));
		verify(repository, never()).save(any(IntegrationModule.class));
		verify(repository, never()).delete(any(IntegrationModule.class));
	}

	private String moduleJson(String code, String environment, String scheme, String host,
			String port, String path) throws Exception {
		ObjectNode module = mapper.createObjectNode();
		module.put("module", "SHIPPING");
		module.put("code", code);
		module.putArray("regions").add("US");
		ArrayNode configurations = module.putArray("configuration");
		ObjectNode configuration = configurations.addObject();
		configuration.put("env", environment);
		configuration.put("scheme", scheme);
		configuration.put("host", host);
		configuration.put("port", port);
		configuration.put("uri", path);
		return mapper.writeValueAsString(module);
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
