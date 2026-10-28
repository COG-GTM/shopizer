package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.UUID;

import javax.inject.Inject;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmanager.core.business.services.user.GroupService;
import com.salesmanager.core.model.user.Group;
import com.salesmanager.core.model.user.GroupType;
import com.salesmanager.test.shop.common.ServicesTestSupport;

public class IntegrationModulesAdminJourneyUatTest extends ServicesTestSupport {

	@Inject
	private GroupService groupService;
	@Inject
	private ObjectMapper mapper;

	private HttpHeaders createUser(String groupName) throws Exception {
		if (groupService.findByName(groupName) == null) {
			Group group = new Group(groupName);
			group.setGroupType(GroupType.ADMIN);
			groupService.save(group);
		}
		String email = "uat-admin-" + UUID.randomUUID() + "@example.com";
		ObjectNode user = mapper.createObjectNode().put("userName", email).put("emailAddress", email)
				.put("firstName", "Uat").put("lastName", "Admin").put("active", true).put("store", "DEFAULT")
				.put("defaultLanguage", "en").put("password", "Password1").put("repeatPassword", "Password1");
		user.putArray("groups").addObject().put("name", groupName);
		exchange("/api/v1/private/user/", HttpMethod.POST, getHeader(), user, 200);
		return getHeader(email, "Password1");
	}

	private JsonNode exchange(String path, HttpMethod method, HttpHeaders headers, Object body, int status)
			throws Exception {
		ResponseEntity<String> response = testRestTemplate.exchange(path, method, new HttpEntity<>(body, headers), String.class);
		assertEquals(status, response.getStatusCodeValue(), method + " " + path + ": " + response.getBody());
		return response.getBody() == null ? mapper.nullNode() : mapper.readTree(response.getBody());
	}

	@Test
	void shippingClerkCannotAccessPayments() throws Exception {
		HttpHeaders shipping = createUser("SHIPPING");
		exchange("/api/v1/private/modules/payment", HttpMethod.GET, shipping, null, 401);
		exchange("/api/v1/private/modules/payment/stripe", HttpMethod.GET, shipping, null, 401);
		ObjectNode payment = mapper.createObjectNode().put("code", "stripe").put("active", true);
		payment.putObject("integrationKeys").put("secretKey", "denied-secret").put("publishableKey", "denied-public");
		exchange("/api/v1/private/modules/payment", HttpMethod.POST, shipping, payment, 401);
	}

	@Test
	void retailAdminConfiguresStripeWithoutExposingCredentials() throws Exception {
		HttpHeaders retail = createUser("ADMIN_RETAIL");
		String secret = "uat-stripe-secret-" + UUID.randomUUID();
		String publishable = "uat-stripe-public-" + UUID.randomUUID();
		ObjectNode config = mapper.createObjectNode().put("code", "stripe").put("active", true).put("defaultSelected", true);
		config.putObject("integrationKeys").put("secretKey", secret).put("publishableKey", publishable);
		exchange("/api/v1/private/modules/payment", HttpMethod.POST, retail, config, 200);
		JsonNode read = exchange("/api/v1/private/modules/payment/stripe", HttpMethod.GET, retail, null, 200);
		assertEquals("********", read.path("integrationKeys").path("secretKey").asText());
		assertEquals("********", read.path("integrationKeys").path("publishableKey").asText());
		assertFalse(read.toString().contains(secret));
		assertFalse(read.toString().contains(publishable));
	}
}
