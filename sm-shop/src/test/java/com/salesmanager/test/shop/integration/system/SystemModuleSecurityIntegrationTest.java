package com.salesmanager.test.shop.integration.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.services.system.ModuleConfigurationService;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.entity.EntityExists;
import com.salesmanager.shop.model.entity.UniqueEntity;
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
	private static final String BEANSTREAM = "{\"module\":\"PAYMENT\",\"code\":\"beanstream\",\"type\":\"creditcard\","
			+ "\"configuration\":[{\"env\":\"PROD\",\"scheme\":\"https\",\"host\":\"evil.example.com\","
			+ "\"port\":\"443\",\"uri\":\"/scripts/process_transaction.asp\"}]}";
	private static final String PAYPAL_SANDBOX =
			"https://www.sandbox.paypal.com/cgi-bin/webscr?cmd=_express-checkout&token=";
	private static final String PAYPAL_PRODUCTION =
			"https://www.paypal.com/cgi-bin/webscr?cmd=_express-checkout&token=";
	private static final String ADMIN_PASSWORD = "Password1";

	@Inject
	private TestRestTemplate testRestTemplate;

	@Inject
	private ModuleConfigurationService moduleConfigurationService;

	@Test
	public void missingAndInvalidCredentialsAreRejected() throws Exception {
		String paypal = paypalJson("javascript:alert(1)", PAYPAL_PRODUCTION);
		String before = beanstreamConfiguration();

		assertFalse(postWithoutCredentials(MODULE_URL, paypal).getStatusCode().is2xxSuccessful());
		assertEquals(before, beanstreamConfiguration());
		assertEquals(HttpStatus.UNAUTHORIZED,
				testRestTemplate.withBasicAuth("admin@shopizer.com", "wrong-password")
						.exchange(MODULE_URL, HttpMethod.POST, plainText(paypal), String.class).getStatusCode());
		assertEquals(before, beanstreamConfiguration());
	}

	@Test
	public void adminCannotRewriteGlobalModuleAndCanStillUseOptin() throws Exception {
		String admin = createUserIfAbsent("module-admin@test.com", "ADMIN");
		String before = beanstreamConfiguration();

		ResponseEntity<String> denied = post(admin, ADMIN_PASSWORD, BEANSTREAM);
		assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
		assertEquals(before, beanstreamConfiguration());

		ResponseEntity<String> optin = testRestTemplate.withBasicAuth(admin, ADMIN_PASSWORD)
				.exchange("/services/private/system/optin", HttpMethod.POST, plainText("{}"), String.class);
		assertEquals(HttpStatus.OK, optin.getStatusCode());
	}

	@Test
	public void adminContentCannotRewriteGlobalModuleThroughTrailingSlash() throws Exception {
		String contentAdmin = createUserIfAbsent("module-content-admin@test.com", "ADMIN_CONTENT");
		String before = beanstreamConfiguration();

		ResponseEntity<String> response = postPath(contentAdmin, ADMIN_PASSWORD, MODULE_URL + "/", BEANSTREAM);
		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
		assertEquals(before, beanstreamConfiguration());
	}

	@Test
	public void superadminRejectsUnsafePaypalWithoutChangingBeanstream() throws Exception {
		String before = beanstreamConfiguration();
		ResponseEntity<String> response = post("admin@shopizer.com", "password",
				paypalJson("javascript:alert(1)", PAYPAL_PRODUCTION));

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertEquals(before, beanstreamConfiguration());
	}

	@Test
	public void superadminRejectsUnknownModuleEndpointWithoutChangingBeanstream() throws Exception {
		String before = beanstreamConfiguration();
		String unknownModule = "{\"module\":\"SHIPPING\",\"code\":\"myship\",\"configuration\":["
				+ "{\"env\":\"PROD\",\"scheme\":\"https\",\"host\":\"evil.example.com\","
				+ "\"port\":\"443\",\"uri\":\"/\"}]}";

		assertEquals(HttpStatus.BAD_REQUEST, post("admin@shopizer.com", "password", unknownModule).getStatusCode());
		assertEquals(before, beanstreamConfiguration());
		assertNull(moduleConfigurationService.getByCode("myship"));
	}

	@Test
	public void malformedModuleJsonRetainsServiceUnavailableBehavior() throws Exception {
		String before = beanstreamConfiguration();

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
				post("admin@shopizer.com", "password", "{not json").getStatusCode());
		assertEquals(before, beanstreamConfiguration());
	}

	@Test
	public void superadminCanRedefinePaypalWithReferenceUrls() throws Exception {
		ResponseEntity<String> response = post("admin@shopizer.com", "password",
				paypalJson(PAYPAL_SANDBOX, PAYPAL_PRODUCTION));
		assertEquals(HttpStatus.OK, response.getStatusCode());

		IntegrationModule paypal = moduleConfigurationService.getByCode("paypal-express-checkout");
		assertNotNull(paypal);
		assertTrue(paypal.getConfiguration().contains(PAYPAL_SANDBOX));
		assertTrue(paypal.getConfiguration().contains(PAYPAL_PRODUCTION));
	}

	private String beanstreamConfiguration() {
		return moduleConfigurationService.getByCode("beanstream").getConfiguration();
	}

	private ResponseEntity<String> postWithoutCredentials(String path, String json) {
		return testRestTemplate.exchange(path, HttpMethod.POST, plainText(json), String.class);
	}

	private ResponseEntity<String> post(String user, String password, String json) {
		return postPath(user, password, MODULE_URL, json);
	}

	private ResponseEntity<String> postPath(String user, String password, String path, String json) {
		return testRestTemplate.withBasicAuth(user, password)
				.exchange(path, HttpMethod.POST, plainText(json), String.class);
	}

	private HttpEntity<String> plainText(String body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8));
		return new HttpEntity<String>(body, headers);
	}

	private String createUserIfAbsent(String email, String groupName) throws Exception {
		UniqueEntity unique = new UniqueEntity();
		unique.setUnique(email);
		unique.setMerchant("DEFAULT");
		ResponseEntity<EntityExists> existing = testRestTemplate.exchange("/api/v1/private/user/unique",
				HttpMethod.POST, new HttpEntity<UniqueEntity>(unique, getHeader()), EntityExists.class);
		assertEquals(HttpStatus.OK, existing.getStatusCode());
		assertNotNull(existing.getBody());
		if (existing.getBody().isExists()) {
			return email;
		}

		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(email);
		user.setFirstName("Integration");
		user.setLastName("Admin");
		user.setUserName(email);
		user.setPassword(ADMIN_PASSWORD);
		user.setRepeatPassword(ADMIN_PASSWORD);
		user.setActive(true);
		PersistableGroup group = new PersistableGroup();
		group.setName(groupName);
		user.setGroups(Arrays.asList(group));

		ResponseEntity<ReadableUser> created = testRestTemplate.exchange("/api/v1/private/user/",
				HttpMethod.POST, new HttpEntity<PersistableUser>(user, getHeader()), ReadableUser.class);
		assertEquals(HttpStatus.OK, created.getStatusCode());
		return email;
	}

	private String paypalJson(String testUrl, String productionUrl) throws Exception {
		Map<String, Object> module = new HashMap<String, Object>();
		module.put("module", "PAYMENT");
		module.put("code", "paypal-express-checkout");
		module.put("configuration", Arrays.asList(paypalConfiguration("TEST", testUrl),
				paypalConfiguration("PROD", productionUrl)));
		return new ObjectMapper().writeValueAsString(module);
	}

	private Map<String, String> paypalConfiguration(String env, String url) {
		Map<String, String> configuration = new HashMap<String, String>();
		configuration.put("env", env);
		configuration.put("scheme", "");
		configuration.put("host", "");
		configuration.put("port", "");
		configuration.put("uri", "");
		configuration.put("config1", url);
		return configuration;
	}
}
