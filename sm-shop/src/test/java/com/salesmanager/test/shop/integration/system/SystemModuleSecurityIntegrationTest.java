package com.salesmanager.test.shop.integration.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.services.system.ModuleConfigurationService;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class SystemModuleSecurityIntegrationTest extends ServicesTestSupport {

	private static final String MODULE_URL = "/services/private/system/module";
	private static final String SUPERADMIN_USER = "admin@shopizer.com";
	private static final String SUPERADMIN_PASSWORD = "password";

	@Inject
	private ModuleConfigurationService moduleConfigurationService;

	@Inject
	private TestRestTemplate testRestTemplate;

	@Test
	public void restrictsSystemModuleOperationsToSuperadmins() throws Exception {
		String validUps = upsJson(false);
		ResponseEntity<String> validResponse = postModule(MODULE_URL, validUps,
				basicHeaders(SUPERADMIN_USER, SUPERADMIN_PASSWORD));
		assertEquals(validResponse.toString(), HttpStatus.OK, validResponse.getStatusCode());
		assertTrue(moduleConfiguration().contains("onlinetools.ups.com"));

		String storedConfiguration = moduleConfiguration();
		ResponseEntity<String> invalidResponse = postModule(MODULE_URL, upsJson(true),
				basicHeaders(SUPERADMIN_USER, SUPERADMIN_PASSWORD));
		assertEquals(invalidResponse.toString(), HttpStatus.SERVICE_UNAVAILABLE, invalidResponse.getStatusCode());
		assertEquals(storedConfiguration, moduleConfiguration());
		assertFalse(moduleConfiguration().contains("attacker.example"));

		assertAuthenticationDenied(
				postModule(MODULE_URL, validUps, basicHeaders(SUPERADMIN_USER, "wrong-password")).getStatusCode());
		assertAuthenticationDenied(postModule(MODULE_URL, validUps, moduleHeaders()).getStatusCode());

		String adminUserName = "admin-only-" + System.nanoTime() + "@example.com";
		createAdminUser(adminUserName);
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<AuthenticationRequest>(new AuthenticationRequest(adminUserName, "Password1")),
				AuthenticationResponse.class);
		assertEquals(login.toString(), HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());

		ResponseEntity<String> deniedModule = postModule(MODULE_URL, validUps,
				basicHeaders(adminUserName, "Password1"));
		assertEquals(deniedModule.toString(), HttpStatus.FORBIDDEN, deniedModule.getStatusCode());
		ResponseEntity<String> deniedTrailingSlash = postModule(MODULE_URL + "/", validUps,
				basicHeaders(adminUserName, "Password1"));
		assertEquals(deniedTrailingSlash.toString(), HttpStatus.FORBIDDEN, deniedTrailingSlash.getStatusCode());
		ResponseEntity<String> deniedOptin = testRestTemplate.exchange("/services/private/system/optin",
				HttpMethod.POST, new HttpEntity<String>("{}", basicHeaders(adminUserName, "Password1")),
				String.class);
		assertEquals(deniedOptin.toString(), HttpStatus.FORBIDDEN, deniedOptin.getStatusCode());
		assertEquals(storedConfiguration, moduleConfiguration());

		HttpHeaders adminJwtHeaders = new HttpHeaders();
		adminJwtHeaders.setBearerAuth(login.getBody().getToken());
		adminJwtHeaders.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> adminApiResponse = testRestTemplate.exchange("/api/v1/private/users/1",
				HttpMethod.GET, new HttpEntity<String>(adminJwtHeaders), String.class);
		assertEquals(adminApiResponse.toString(), HttpStatus.OK, adminApiResponse.getStatusCode());
	}

	private void createAdminUser(String userName) {
		PersistableUser user = new PersistableUser();
		user.setEmailAddress(userName);
		user.setUserName(userName);
		user.setDefaultLanguage("en");
		user.setFirstName("Admin");
		user.setLastName("Only");
		user.setActive(true);
		user.setPassword("Password1");
		user.setRepeatPassword("Password1");
		PersistableGroup group = new PersistableGroup();
		group.setName("ADMIN");
		user.getGroups().add(group);

		ResponseEntity<ReadableUser> response = testRestTemplate.exchange("/api/v1/private/user/",
				HttpMethod.POST, new HttpEntity<PersistableUser>(user, getHeader()), ReadableUser.class);
		assertEquals(response.toString(), HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
	}

	private ResponseEntity<String> postModule(String path, String body, HttpHeaders headers) {
		return testRestTemplate.exchange(path, HttpMethod.POST, new HttpEntity<String>(body, headers), String.class);
	}

	private HttpHeaders moduleHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType("text/plain;charset=UTF-8"));
		return headers;
	}

	private HttpHeaders basicHeaders(String userName, String password) {
		HttpHeaders headers = moduleHeaders();
		headers.setBasicAuth(userName, password, StandardCharsets.UTF_8);
		return headers;
	}

	private void assertAuthenticationDenied(HttpStatus status) {
		assertTrue("Expected authentication to be denied but received " + status,
				status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FOUND);
	}

	private String moduleConfiguration() {
		IntegrationModule module = moduleConfigurationService.getByCode("ups");
		return module == null ? "" : String.valueOf(module.getConfiguration());
	}

	private String upsJson(boolean attackerHost) throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		try (InputStream input = getClass().getClassLoader()
				.getResourceAsStream("reference/integrationmodules.json")) {
			JsonNode modules = mapper.readTree(input);
			for (JsonNode module : modules) {
				if (!"ups".equals(module.path("code").asText())) {
					continue;
				}
				JsonNode result = module.deepCopy();
				if (attackerHost) {
					for (JsonNode config : result.path("configuration")) {
						if ("PROD".equals(config.path("env").asText())) {
							((com.fasterxml.jackson.databind.node.ObjectNode) config)
									.put("host", "attacker.example");
						}
					}
				}
				return mapper.writeValueAsString(result);
			}
		}
		throw new IllegalStateException("Reference UPS module not found");
	}
}
