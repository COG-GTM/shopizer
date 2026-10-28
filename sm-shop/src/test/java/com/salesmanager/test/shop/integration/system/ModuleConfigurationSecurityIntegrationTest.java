package com.salesmanager.test.shop.integration.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.Iterator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmanager.core.business.services.system.ModuleConfigurationService;
import com.salesmanager.core.business.utils.ajax.AjaxResponse;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ModuleConfigurationSecurityIntegrationTest extends ServicesTestSupport {

	private static final String ADMIN_PASSWORD = "Password1";
	private static final String ADMIN_EMAIL = "pr188.admin." + System.currentTimeMillis() + "@example.com";
	private static boolean adminCreated;

	@Autowired
	private ModuleConfigurationService moduleConfigurationService;

	private final ObjectMapper mapper = new ObjectMapper();

	@Before
	public void createRegularAdmin() {
		if (adminCreated) {
			return;
		}

		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(ADMIN_EMAIL);
		user.setFirstName("PR188");
		user.setLastName("Admin");
		user.setUserName(ADMIN_EMAIL);
		user.setPassword(ADMIN_PASSWORD);
		user.setRepeatPassword(ADMIN_PASSWORD);
		user.setActive(true);

		PersistableGroup group = new PersistableGroup();
		group.setName("ADMIN");
		user.getGroups().add(group);

		ResponseEntity<ReadableUser> response = testRestTemplate.postForEntity(
				"/api/v1/private/user/", new HttpEntity<PersistableUser>(user, getHeader()),
				ReadableUser.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		adminCreated = true;
	}

	@Test
	public void rejectsAnonymousAndInvalidSuperadminCredentials() throws Exception {
		String originalUps = storedConfiguration("ups");
		HttpEntity<String> request = moduleRequest(referenceUpsJson());
		ResponseEntity<String> anonymous = testRestTemplate.postForEntity(
				"/services/private/system/module", request, String.class);
		ResponseEntity<String> wrongCredentials = testRestTemplate.withBasicAuth(
				"admin@shopizer.com", "wrong-password").postForEntity(
						"/services/private/system/module", request, String.class);

		assertTrue(anonymous.getStatusCode() == HttpStatus.FOUND
				|| anonymous.getStatusCode() == HttpStatus.UNAUTHORIZED);
		assertFalse(anonymous.getStatusCode().is2xxSuccessful());
		if (anonymous.getStatusCode() == HttpStatus.FOUND) {
			assertNotNull(anonymous.getHeaders().getLocation());
			assertFalse(anonymous.getHeaders().getLocation().toString()
					.contains("/services/private/system/module"));
		}
		assertEquals(HttpStatus.UNAUTHORIZED, wrongCredentials.getStatusCode());
		assertEquals(originalUps, storedConfiguration("ups"));
	}

	@Test
	public void deniesRegularAdminForModuleRoutesWithoutChangingDatabase() throws Exception {
		String originalConfiguration = storedConfiguration("ups");
		org.springframework.boot.test.web.client.TestRestTemplate regularAdmin =
				testRestTemplate.withBasicAuth(ADMIN_EMAIL, ADMIN_PASSWORD);
		ResponseEntity<String> validConfiguration = regularAdmin.postForEntity(
				"/services/private/system/module", moduleRequest(referenceUpsJson()), String.class);
		ResponseEntity<String> tamperedConfiguration = regularAdmin.postForEntity(
				"/services/private/system/module", moduleRequest(
						referenceUpsJson().replace("wwwcie.ups.com", "mallory.example")), String.class);
		ResponseEntity<String> trailingSlash = regularAdmin.postForEntity(
				"/services/private/system/module/", moduleRequest(referenceUpsJson()), String.class);

		assertTrue(validConfiguration.getStatusCode() == HttpStatus.UNAUTHORIZED
				|| validConfiguration.getStatusCode() == HttpStatus.FORBIDDEN);
		assertTrue(tamperedConfiguration.getStatusCode() == HttpStatus.UNAUTHORIZED
				|| tamperedConfiguration.getStatusCode() == HttpStatus.FORBIDDEN);
		assertTrue(trailingSlash.getStatusCode() == HttpStatus.UNAUTHORIZED
				|| trailingSlash.getStatusCode() == HttpStatus.FORBIDDEN);
		assertNotEquals(HttpStatus.OK, trailingSlash.getStatusCode());
		assertEquals(originalConfiguration, storedConfiguration("ups"));
		assertTrue(!storedConfiguration("ups").contains("mallory.example"));
	}

	@Test
	public void superadminRejectsTamperingAndPersistsValidModules() throws Exception {
		String originalUps = storedConfiguration("ups");
		String originalUsps = storedConfiguration("usps");
		org.springframework.boot.test.web.client.TestRestTemplate superadmin =
				testRestTemplate.withBasicAuth("admin@shopizer.com", "password");

		ResponseEntity<String> tamperedUps = superadmin.postForEntity(
				"/services/private/system/module", moduleRequest(
						referenceUpsJson().replace("wwwcie.ups.com", "mallory.example")), String.class);
		assertEquals("Unexpected response body: " + tamperedUps.getBody(),
				HttpStatus.BAD_REQUEST, tamperedUps.getStatusCode());
		assertEquals(originalUps, storedConfiguration("ups"));

		String tamperedUspsJson = referenceUspsJson().replace(
				"testing.shippingapis.com", "169.254.169.254");
		ResponseEntity<String> tamperedUsps = superadmin.postForEntity(
				"/services/private/system/module", moduleRequest(tamperedUspsJson), String.class);
		assertEquals("Unexpected response body: " + tamperedUsps.getBody(),
				HttpStatus.BAD_REQUEST, tamperedUsps.getStatusCode());
		assertEquals(originalUsps, storedConfiguration("usps"));

		ResponseEntity<AjaxResponse> validUps = superadmin.postForEntity(
				"/services/private/system/module", moduleRequest(referenceUpsJson()), AjaxResponse.class);
		assertEquals(HttpStatus.OK, validUps.getStatusCode());
		assertNotNull(validUps.getBody());
		assertEquals(200, validUps.getBody().getStatus());
		assertNotNull(moduleConfigurationService.getByCode("ups"));
		assertTrue(moduleConfigurationService.getByCode("ups").getConfiguration().contains("wwwcie.ups.com"));

		String nonCarrierCode = "pr188-custom-" + System.currentTimeMillis();
		ResponseEntity<AjaxResponse> nonCarrier = superadmin.postForEntity(
				"/services/private/system/module",
				moduleRequest(nonCarrierJson(nonCarrierCode)), AjaxResponse.class);
		assertEquals(HttpStatus.OK, nonCarrier.getStatusCode());
		assertNotNull(nonCarrier.getBody());
		assertEquals(200, nonCarrier.getBody().getStatus());
		IntegrationModule persistedNonCarrier = moduleConfigurationService.getByCode(nonCarrierCode);
		assertNotNull(persistedNonCarrier);
		assertTrue(persistedNonCarrier.getConfiguration().contains("mallory.example"));
	}

	@Test
	public void regularAdminCanAccessOtherPrivateServices() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.TEXT_PLAIN);
		HttpEntity<String> request = new HttpEntity<String>("missing-optin", headers);
		ResponseEntity<String> response = testRestTemplate.withBasicAuth(ADMIN_EMAIL, ADMIN_PASSWORD)
				.exchange("/services/private/system/optin/missing-optin", HttpMethod.DELETE,
						request, String.class);

		assertNotEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
		assertNotEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
	}

	private HttpEntity<String> moduleRequest(String json) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType("text/plain;charset=UTF-8"));
		return new HttpEntity<String>(json, headers);
	}

	private String storedConfiguration(String code) {
		IntegrationModule module = moduleConfigurationService.getByCode(code);
		assertNotNull(module);
		return module.getConfiguration();
	}

	private String referenceUpsJson() throws Exception {
		return referenceModuleJson("ups");
	}

	private String referenceUspsJson() throws Exception {
		return referenceModuleJson("usps");
	}

	private String referenceModuleJson(String code) throws Exception {
		try (InputStream input = getClass().getClassLoader()
				.getResourceAsStream("reference/integrationmodules.json")) {
			assertNotNull(input);
			JsonNode definitions = mapper.readTree(input);
			Iterator<JsonNode> iterator = definitions.elements();
			while (iterator.hasNext()) {
				JsonNode definition = iterator.next();
				if (code.equals(definition.path("code").asText())) {
					return mapper.writeValueAsString(definition);
				}
			}
		}
		throw new AssertionError("Reference configuration was not found for " + code);
	}

	private String nonCarrierJson(String code) throws Exception {
		ObjectNode module = mapper.createObjectNode();
		module.put("module", "SHIPPING");
		module.put("code", code);
		module.putArray("regions").add("US");
		ObjectNode configuration = module.putArray("configuration").addObject();
		configuration.put("env", "TEST");
		configuration.put("scheme", "https");
		configuration.put("host", "mallory.example");
		configuration.put("port", "443");
		configuration.put("uri", "/custom");
		return mapper.writeValueAsString(module);
	}

}
