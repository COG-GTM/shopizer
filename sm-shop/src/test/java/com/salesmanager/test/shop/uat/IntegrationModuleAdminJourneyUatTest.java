package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.entity.EntityExists;
import com.salesmanager.shop.model.entity.UniqueEntity;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class IntegrationModuleAdminJourneyUatTest extends ServicesTestSupport {

	private static final String MODULE_URL = "/services/private/system/module";
	private static final String PAYPAL_SANDBOX =
			"https://www.sandbox.paypal.com/cgi-bin/webscr?cmd=_express-checkout&token=";
	private static final String PAYPAL_PRODUCTION =
			"https://www.paypal.com/cgi-bin/webscr?cmd=_express-checkout&token=";

	@Test
	public void moduleAdministrationIsRestrictedAndEndpointValidated() throws Exception {
		ResponseEntity<String> superadmin = post("admin@shopizer.com", "password",
				paypalJson(PAYPAL_SANDBOX, PAYPAL_PRODUCTION));
		assertEquals(HttpStatus.OK, superadmin.getStatusCode());

		String adminEmail = createUserIfAbsent("module-uat-admin@test.com");
		ResponseEntity<String> storeAdmin = post(adminEmail, "Password1",
				paypalJson(PAYPAL_SANDBOX, PAYPAL_PRODUCTION));
		assertEquals(HttpStatus.FORBIDDEN, storeAdmin.getStatusCode());

		ResponseEntity<String> unsafeEndpoint = post("admin@shopizer.com", "password",
				beanstreamJson("evil.example.com"));
		assertEquals(HttpStatus.BAD_REQUEST, unsafeEndpoint.getStatusCode());
	}

	private ResponseEntity<String> post(String user, String password, String json) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8));
		return testRestTemplate.withBasicAuth(user, password).exchange(MODULE_URL, HttpMethod.POST,
				new HttpEntity<String>(json, headers), String.class);
	}

	private String createUserIfAbsent(String email) {
		UniqueEntity unique = new UniqueEntity();
		unique.setUnique(email);
		unique.setMerchant("DEFAULT");
		EntityExists exists = testRestTemplate.postForObject("/api/v1/private/user/unique",
				new HttpEntity<UniqueEntity>(unique, getHeader()), EntityExists.class);
		if (exists.isExists()) {
			return email;
		}

		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(email);
		user.setFirstName("UAT");
		user.setLastName("Admin");
		user.setUserName(email);
		user.setPassword("Password1");
		user.setRepeatPassword("Password1");
		user.setActive(true);
		PersistableGroup group = new PersistableGroup();
		group.setName("ADMIN");
		user.setGroups(Arrays.asList(group));
		ResponseEntity<ReadableUser> created = testRestTemplate.exchange("/api/v1/private/user/",
				HttpMethod.POST, new HttpEntity<PersistableUser>(user, getHeader()), ReadableUser.class);
		assertEquals(HttpStatus.OK, created.getStatusCode());
		return email;
	}

	private String beanstreamJson(String host) {
		return "{\"module\":\"PAYMENT\",\"code\":\"beanstream\",\"type\":\"creditcard\",\"configuration\":["
				+ "{\"env\":\"PROD\",\"scheme\":\"https\",\"host\":\"" + host
				+ "\",\"port\":\"443\",\"uri\":\"/scripts/process_transaction.asp\"}]}";
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
