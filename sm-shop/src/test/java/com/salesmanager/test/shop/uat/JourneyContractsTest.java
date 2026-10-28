package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.UUID;

import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmanager.core.business.modules.email.HtmlEmailSender;
import com.salesmanager.core.business.services.system.EmailService;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
public class JourneyContractsTest extends ServicesTestSupport {

	@Autowired private EmailService emailService;

	@Before
	public void stubOutboundEmail() {
		ReflectionTestUtils.setField(emailService, "sender", mock(HtmlEmailSender.class));
	}

	@Test
	public void adminJwtAndBasicCredentialsProtectPrivateEndpoints() {
		ResponseEntity<JsonNode> users = testRestTemplate.exchange("/api/v1/private/users",
				HttpMethod.GET, new HttpEntity<>(getHeader()), JsonNode.class);
		assertEquals(200, users.getStatusCodeValue());
		assertEquals(401, testRestTemplate.getForEntity("/api/v1/private/users", String.class).getStatusCodeValue());

		String service = "/services/private/system/optin";
		ResponseEntity<JsonNode> valid = testRestTemplate.withBasicAuth("admin@shopizer.com", "password")
				.postForEntity(service, JsonNodeFactory.instance.objectNode(), JsonNode.class);
		assertEquals(200, valid.getStatusCodeValue());
		ResponseEntity<String> invalid = testRestTemplate.withBasicAuth("admin@shopizer.com", "incorrect")
				.postForEntity(service, JsonNodeFactory.instance.objectNode(), String.class);
		assertEquals(401, invalid.getStatusCodeValue());
	}

	@Test
	public void registeredCustomerCanBrowseManageCartAndPlaceAnOrder() {
		String email = "journey-" + UUID.randomUUID().toString().replace("-", "") + "@x.test";
		ObjectNode customer = JsonNodeFactory.instance.objectNode();
		customer.put("emailAddress", email).put("password", "clear123").put("gender", "M")
				.put("language", "en").put("storeCode", "DEFAULT");
		customer.putObject("billing").put("firstName", "Journey").put("lastName", "Test")
				.put("country", "CA").put("address", "1 Test Street").put("city", "Montreal")
				.put("postalCode", "H1H1H1").put("stateProvince", "QC");
		ResponseEntity<JsonNode> registered = testRestTemplate.postForEntity(
				"/api/v1/customer/register", customer, JsonNode.class);
		assertEquals(200, registered.getStatusCodeValue());
		ObjectNode login = JsonNodeFactory.instance.objectNode().put("username", email).put("password", "clear123");
		ResponseEntity<JsonNode> authenticated = testRestTemplate.postForEntity(
				"/api/v1/customer/login", login, JsonNode.class);
		assertEquals(200, authenticated.getStatusCodeValue());
		String token = authenticated.getBody().path("token").textValue();
		assertFalse(token.isEmpty());
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		ResponseEntity<JsonNode> profile = testRestTemplate.exchange("/api/v1/auth/customer/profile",
				HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
		assertEquals(200, profile.getStatusCodeValue());
		assertEquals(email, profile.getBody().path("emailAddress").textValue());
		assertEquals(registered.getBody().path("id"), profile.getBody().path("id"));

		String sku = "journey-" + UUID.randomUUID();
		ReadableProduct product = sampleProduct(sku);
		ResponseEntity<JsonNode> detail = testRestTemplate.getForEntity("/api/v2/product/" + sku, JsonNode.class);
		assertEquals(200, detail.getStatusCodeValue());
		assertEquals(product.getId().longValue(), detail.getBody().path("id").longValue());
		ObjectNode item = JsonNodeFactory.instance.objectNode().put("product", sku).put("quantity", 1);
		ResponseEntity<JsonNode> createdCart = testRestTemplate.postForEntity("/api/v1/cart", item, JsonNode.class);
		assertEquals(201, createdCart.getStatusCodeValue());
		String cartPath = "/api/v1/cart/" + createdCart.getBody().path("code").textValue();
		assertEquals(201, testRestTemplate.exchange(cartPath, HttpMethod.PUT,
				new HttpEntity<>(item), JsonNode.class).getStatusCodeValue());
		item.put("quantity", 2);
		assertEquals(201, testRestTemplate.postForEntity(cartPath + "/multi",
				JsonNodeFactory.instance.arrayNode().add(item), JsonNode.class).getStatusCodeValue());
		ResponseEntity<JsonNode> cart = testRestTemplate.getForEntity(cartPath, JsonNode.class);
		assertEquals(200, cart.getStatusCodeValue());
		assertEquals(2, cart.getBody().path("quantity").intValue());

		ObjectNode module = JsonNodeFactory.instance.objectNode().put("code", "moneyorder")
				.put("active", true).put("defaultSelected", true);
		module.putObject("integrationKeys").put("address", "1 Test Street");
		module.putObject("integrationOptions");
		assertEquals(200, testRestTemplate.postForEntity("/api/v1/private/modules/payment",
				new HttpEntity<>(module, getHeader()), String.class).getStatusCodeValue());
		ObjectNode order = JsonNodeFactory.instance.objectNode().put("customerAgreement", true).put("currency", "CAD");
		order.putObject("payment").put("paymentType", "MONEYORDER").put("transactionType", "AUTHORIZECAPTURE")
				.put("paymentModule", "moneyorder").put("amount", cart.getBody().path("total").asText());
		ResponseEntity<JsonNode> confirmation = testRestTemplate.postForEntity(
				"/api/v1/auth/cart/" + createdCart.getBody().path("code").textValue() + "/checkout",
				new HttpEntity<>(order, headers), JsonNode.class);
		assertEquals(200, confirmation.getStatusCodeValue());
		assertTrue(confirmation.getBody().path("id").isIntegralNumber());
		ResponseEntity<JsonNode> savedOrder = testRestTemplate.exchange(
				"/api/v1/auth/orders/" + confirmation.getBody().path("id").longValue(), HttpMethod.GET,
				new HttpEntity<>(headers), JsonNode.class);
		assertEquals(200, savedOrder.getStatusCodeValue());
		assertEquals(confirmation.getBody().path("id"), savedOrder.getBody().path("id"));
	}

	@Test
	public void storefrontDiscoveryHealthAndSwaggerContractsRemainAvailable() {
		ResponseEntity<JsonNode> store = testRestTemplate.getForEntity("/api/v1/store/DEFAULT", JsonNode.class);
		assertEquals(200, store.getStatusCodeValue());
		assertEquals("DEFAULT", store.getBody().path("code").textValue());
		assertEquals(200, testRestTemplate.getForEntity("/api/v1/category", JsonNode.class).getStatusCodeValue());
		ResponseEntity<JsonNode> products = testRestTemplate.getForEntity("/api/v1/products", JsonNode.class);
		assertEquals(200, products.getStatusCodeValue());
		assertTrue(products.getBody().path("products").isArray());
		ResponseEntity<JsonNode> health = testRestTemplate.getForEntity("/actuator/health", JsonNode.class);
		assertEquals(200, health.getStatusCodeValue());
		assertEquals("UP", health.getBody().path("status").textValue());
		ResponseEntity<JsonNode> docs = testRestTemplate.getForEntity("/v2/api-docs", JsonNode.class);
		assertEquals(200, docs.getStatusCodeValue());
		assertEquals("2.0", docs.getBody().path("swagger").textValue());
		assertTrue(docs.getBody().path("paths").has("/api/v2/private/product/variation"));
	}
}
