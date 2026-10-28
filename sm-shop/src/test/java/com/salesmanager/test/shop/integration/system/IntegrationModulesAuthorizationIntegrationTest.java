package com.salesmanager.test.shop.integration.system;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.payments.PaymentService;
import com.salesmanager.core.business.services.shipping.ShippingService;
import com.salesmanager.core.business.services.user.GroupService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.model.user.Group;
import com.salesmanager.core.model.user.GroupType;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.test.shop.common.ServicesTestSupport;

public class IntegrationModulesAuthorizationIntegrationTest extends ServicesTestSupport {

	private static final String ROOT = "/api/v1/private/modules/";
	private static final String MASK = "********";
	private static final String PASSWORD = "Password1";
	@Autowired private MerchantStoreService merchantStoreService;
	@Autowired private ShippingService shippingService;
	@Autowired private PaymentService paymentService;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private GroupService groupService;
	private MerchantStore store;
	private HttpHeaders admin;

	@BeforeEach
	void configureModules() throws Exception {
		store = merchantStoreService.getByCode("DEFAULT");
		admin = getHeader();
		for (String name : Arrays.asList("SHIPPING", "ADMIN_RETAIL")) {
			if (groupService.findByName(name) == null) {
				Group group = new Group(name);
				group.setGroupType(GroupType.ADMIN);
				groupService.save(group);
			}
		}
		for (String type : Arrays.asList("shipping", "payment")) {
			IntegrationConfiguration config = new IntegrationConfiguration();
			config.setModuleCode(code(type));
			config.setEnvironment("TEST");
			config.setActive(true);
			config.setDefaultSelected(true);
			config.setIntegrationKeys(keys(type));
			config.setIntegrationOptions(Collections.singletonMap("testOption", Arrays.asList("one", "two")));
			if (type.equals("shipping")) {
				shippingService.saveShippingQuoteModuleConfiguration(config, store);
			} else {
				paymentService.savePaymentModuleConfiguration(config, store);
			}
		}
	}

	private String code(String type) {
		return type.equals("shipping") ? "storePickUp" : "stripe";
	}

	private Map<String, String> keys(String type) {
		Map<String, String> keys = new LinkedHashMap<>();
		keys.put("secretKey", "original-secret-167");
		keys.put("publishableKey", "original-publishable-167");
		if (type.equals("shipping")) {
			keys.put("price", "12.50");
			keys.put("note", "Collect at desk");
			keys.put("address", "Main Street");
		}
		return keys;
	}

	private IntegrationModuleConfiguration payload(String type) {
		IntegrationModuleConfiguration config = new IntegrationModuleConfiguration();
		config.setCode(code(type));
		config.setActive(true);
		config.setDefaultSelected(true);
		config.setIntegrationKeys(keys(type));
		config.setIntegrationOptions(Collections.singletonMap("testOption", Arrays.asList("one", "two")));
		return config;
	}

	private IntegrationConfiguration stored(String type) throws Exception {
		return type.equals("shipping") ? shippingService.getShippingConfiguration(code(type), store)
				: paymentService.getPaymentConfiguration(code(type), store);
	}

	private ResponseEntity<String> request(String type, String suffix, HttpMethod method, HttpHeaders headers,
			IntegrationModuleConfiguration body) {
		return testRestTemplate.exchange(ROOT + type + suffix, method, new HttpEntity<>(body, headers), String.class);
	}

	private HttpHeaders user(String group) {
		String email = "pr167-" + UUID.randomUUID() + "@example.com";
		PersistableUser user = new PersistableUser();
		user.setUserName(email);
		user.setEmailAddress(email);
		user.setFirstName("Integration");
		user.setLastName("Test");
		user.setDefaultLanguage("en");
		user.setStore("DEFAULT");
		user.setActive(true);
		user.setPassword(PASSWORD);
		user.setRepeatPassword(PASSWORD);
		user.setGroups(Collections.singletonList(new PersistableGroup(group)));
		ResponseEntity<String> created = testRestTemplate.postForEntity("/api/v1/private/user/",
				new HttpEntity<>(user, admin), String.class);
		assertEquals(200, created.getStatusCodeValue(), created.getBody());
		return getHeader(email, PASSWORD);
	}

	private void assertEndpoints(HttpHeaders headers, boolean shippingAllowed, boolean paymentAllowed,
			int deniedStatus) throws Exception {
		for (String type : Arrays.asList("shipping", "payment")) {
			int expected = (type.equals("shipping") ? shippingAllowed : paymentAllowed) ? 200 : deniedStatus;
			for (String suffix : Arrays.asList("", "/" + code(type))) {
				assertEquals(expected, request(type, suffix, HttpMethod.GET, headers, null).getStatusCodeValue(),
						"GET " + type + suffix);
			}
			String before = objectMapper.writeValueAsString(stored(type));
			IntegrationModuleConfiguration change = payload(type);
			change.getIntegrationKeys().put("secretKey", "unauthorized-replacement-167");
			change.setActive(false);
			assertEquals(expected, request(type, "", HttpMethod.POST, headers, change).getStatusCodeValue(),
					"POST " + type);
			if (expected != 200) {
				assertEquals(before, objectMapper.writeValueAsString(stored(type)), "Denied POST mutated " + type);
			}
		}
	}

	@Test void superadminCanAccessAllSixEndpoints() throws Exception { assertEndpoints(admin, true, true, 403); }
	@Test void orderAdminIsDenied() throws Exception { assertEndpoints(user("ADMIN_ORDER"), false, false, 401); }
	@Test void catalogueAdminIsDenied() throws Exception { assertEndpoints(user("ADMIN_CATALOGUE"), false, false, 401); }
	@Test void shippingUserCannotAccessPayment() throws Exception { assertEndpoints(user("SHIPPING"), true, false, 401); }
	@Test void retailAdminCanAccessAllSixEndpoints() throws Exception { assertEndpoints(user("ADMIN_RETAIL"), true, true, 403); }

	@Test
	void anonymousIsDenied() throws Exception {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		assertEndpoints(headers, false, false, 401);
	}

	@Test void shippingMaskingRoundTrip() throws Exception { roundTrip("shipping"); }
	@Test void paymentMaskingRoundTrip() throws Exception { roundTrip("payment"); }

	private void roundTrip(String type) throws Exception {
		IntegrationModuleConfiguration config = payload(type);
		assertEquals(200, request(type, "", HttpMethod.POST, admin, config).getStatusCodeValue());
		ResponseEntity<String> response = request(type, "/" + code(type), HttpMethod.GET, admin, null);
		assertEquals(200, response.getStatusCodeValue());
		JsonNode json = objectMapper.readTree(response.getBody());
		assertEquals(MASK, json.path("integrationKeys").path("secretKey").asText());
		assertEquals(MASK, json.path("integrationKeys").path("publishableKey").asText());
		assertFalse(response.getBody().contains("original-secret-167"));
		assertFalse(response.getBody().contains("original-publishable-167"));
		if (type.equals("shipping")) {
			HashSet<String> fields = new HashSet<>();
			json.fieldNames().forEachRemaining(fields::add);
			assertEquals(new HashSet<>(Arrays.asList("moduleCode", "active", "defaultSelected", "environment",
					"integrationKeys", "integrationOptions")), fields);
			assertEquals(code(type), json.path("moduleCode").asText());
			assertTrue(json.path("active").asBoolean());
			assertTrue(json.path("defaultSelected").asBoolean());
			assertEquals("TEST", json.path("environment").asText());
			assertEquals("one", json.path("integrationOptions").path("testOption").get(0).asText());
			for (String key : Arrays.asList("price", "note", "address")) {
				assertEquals(keys(type).get(key), json.path("integrationKeys").path(key).asText());
			}
		}
		config.getIntegrationKeys().put("secretKey", MASK);
		config.getIntegrationKeys().put("publishableKey", "changed-publishable-167");
		config.getIntegrationKeys().put("missing", MASK);
		assertEquals(200, request(type, "", HttpMethod.POST, admin, config).getStatusCodeValue());
		Map<String, String> saved = stored(type).getIntegrationKeys();
		assertEquals("original-secret-167", saved.get("secretKey"));
		assertEquals("changed-publishable-167", saved.get("publishableKey"));
		assertFalse(saved.containsKey("missing"));
		ResponseEntity<String> updated = request(type, "/" + code(type), HttpMethod.GET, admin, null);
		assertEquals(200, updated.getStatusCodeValue());
		assertFalse(updated.getBody().contains("original-secret-167"));
		assertFalse(updated.getBody().contains("changed-publishable-167"));
		assertEquals(MASK, objectMapper.readTree(updated.getBody()).path("integrationKeys").path("secretKey").asText());
	}
}
