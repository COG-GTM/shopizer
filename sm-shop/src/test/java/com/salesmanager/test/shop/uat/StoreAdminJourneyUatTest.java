package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StoreAdminJourneyUatTest extends ServicesTestSupport {

	private static final ObjectMapper JSON = new ObjectMapper();
	private static final String PASSWORD = "Password1";
	private static final String USER_PREFIX = "pr151uat-" + UUID.randomUUID().toString().replace("-", "");

	@Test
	public void storeAdminCanUpdateStoreAndCatalogueAdminIsRejected() throws Exception {
		String superadmin = login("admin@shopizer.com", "password");
		String storeAdminName = USER_PREFIX + "-storeadmin@example.com";
		String catalogueAdminName = USER_PREFIX + "-catalogue@example.com";
		createUser(superadmin, storeAdminName, "ADMIN_STORE");
		createUser(superadmin, catalogueAdminName, "ADMIN_CATALOGUE");

		String storeAdmin = login(storeAdminName, PASSWORD);
		String catalogueAdmin = login(catalogueAdminName, PASSWORD);
		JsonNode initialStore = json(get("/api/v1/store/DEFAULT", null));
		String originalPhone = initialStore.get("phone").asText();
		String updatedPhone = "555-151-0101";
		Map<String, Object> update = storeBody(initialStore, updatedPhone);

		try {
			ResponseEntity<String> updateResponse = call(HttpMethod.PUT, "/api/v1/private/store/DEFAULT", update,
					storeAdmin);
			assertEquals(HttpStatus.OK, updateResponse.getStatusCode());
			assertEquals(updatedPhone, json(get("/api/v1/store/DEFAULT", null)).get("phone").asText());

			ResponseEntity<String> denied = call(HttpMethod.PUT, "/api/v1/private/store/DEFAULT",
					storeBody(initialStore, "555-151-0202"), catalogueAdmin);
			assertEquals(HttpStatus.UNAUTHORIZED, denied.getStatusCode());
			JsonNode error = json(denied);
			assertTrue(error.hasNonNull("errorCode"));
			assertTrue(error.hasNonNull("message"));
			assertFalse(error.get("message").asText().isEmpty());
			assertEquals(updatedPhone, json(get("/api/v1/store/DEFAULT", null)).get("phone").asText());
		} finally {
			ResponseEntity<String> restore = call(HttpMethod.PUT, "/api/v1/private/store/DEFAULT",
					storeBody(initialStore, originalPhone), storeAdmin);
			assertEquals(HttpStatus.OK, restore.getStatusCode());
		}

		assertEquals(originalPhone, json(get("/api/v1/store/DEFAULT", null)).get("phone").asText());
		ResponseEntity<String> marketing = call(HttpMethod.GET, "/api/v1/private/store/DEFAULT/marketing", null,
				storeAdmin);
		assertEquals(HttpStatus.OK, marketing.getStatusCode());
		assertNotNull(marketing.getHeaders().getContentType());
		assertTrue(marketing.getHeaders().getContentType().includes(MediaType.APPLICATION_JSON));
		assertTrue(json(marketing).isObject());
	}

	private void createUser(String token, String username, String group) throws Exception {
		Map<String, Object> user = body("userName", username, "emailAddress", username, "password", PASSWORD,
				"repeatPassword", PASSWORD, "defaultLanguage", "en", "store", "DEFAULT", "firstName", "PR151 UAT",
				"lastName", group, "active", true, "groups", List.of(body("name", group)));
		ResponseEntity<String> response = call(HttpMethod.POST, "/api/v1/private/user/?store=DEFAULT", user, token);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	private String login(String username, String password) throws Exception {
		ResponseEntity<String> response = call(HttpMethod.POST, "/api/v1/private/login",
				body("username", username, "password", password), null);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		String token = json(response).get("token").asText();
		assertFalse(token.isEmpty());
		return token;
	}

	private ResponseEntity<String> get(String path, String token) {
		return call(HttpMethod.GET, path, null, token);
	}

	private ResponseEntity<String> call(HttpMethod method, String path, Object payload, String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		if (token != null) {
			headers.set("Authorization", "Bearer " + token);
		}
		try {
			String body = payload == null ? null : JSON.writeValueAsString(payload);
			return testRestTemplate.exchange(path, method, new HttpEntity<>(body, headers), String.class);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static Map<String, Object> storeBody(JsonNode store, String phone) {
		Map<String, Object> body = body("code", store.get("code").asText(), "name", store.get("name").asText(),
				"email", store.get("email").asText(), "phone", phone,
				"defaultLanguage", store.path("defaultLanguage").asText("en"),
				"currency", store.path("currency").asText("CAD"),
				"currencyFormatNational", store.path("currencyFormatNational").asBoolean(),
				"useCache", store.path("useCache").asBoolean(), "retailer", store.path("retailer").asBoolean());
		if (store.hasNonNull("template")) {
			body.put("template", store.get("template").asText());
		}
		if (store.hasNonNull("inBusinessSince")) {
			body.put("inBusinessSince", store.get("inBusinessSince").asText());
		}
		if (store.hasNonNull("dimension")) {
			body.put("dimension", store.get("dimension").asText());
		}
		if (store.hasNonNull("weight")) {
			body.put("weight", store.get("weight").asText());
		}
		JsonNode parent = store.path("parent");
		if (parent.hasNonNull("code")) {
			body.put("retailerStore", parent.get("code").asText());
		}
		JsonNode address = store.path("address");
		if (address.isObject()) {
			body.put("address", JSON.convertValue(address, Map.class));
		}
		JsonNode supportedLanguages = store.path("supportedLanguages");
		if (supportedLanguages.isArray()) {
			List<String> codes = new ArrayList<>();
			for (JsonNode language : supportedLanguages) {
				codes.add(language.isTextual() ? language.asText() : language.path("code").asText());
			}
			body.put("supportedLanguages", codes);
		}
		return body;
	}

	private static Map<String, Object> body(Object... kv) {
		Map<String, Object> map = new HashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			map.put((String) kv[i], kv[i + 1]);
		}
		return map;
	}

	private static JsonNode json(ResponseEntity<String> response) throws Exception {
		return JSON.readTree(response.getBody());
	}
}
