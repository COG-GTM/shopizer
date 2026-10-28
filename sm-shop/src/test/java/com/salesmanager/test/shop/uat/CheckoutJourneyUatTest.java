package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class CheckoutJourneyUatTest extends ServicesTestSupport {

	private static final ObjectMapper JSON = new ObjectMapper();

	@Test
	public void authenticatedCustomerChecksOutAndReadsOrderBack() throws Exception {
		sampleProduct("uatcheckout");
		String admin = JSON.readTree(call(HttpMethod.POST, "/api/v1/private/login",
				map("username", "admin@shopizer.com", "password", "password"), null).getBody()).get("token").asText();
		Map<String, String> keys = new HashMap<>();
		keys.put("address", "Pay to UAT Store, 1 test street");
		assertEquals(HttpStatus.OK, call(HttpMethod.POST, "/api/v1/private/modules/payment?store=DEFAULT",
				map("code", "moneyorder", "active", true, "defaultSelected", true, "integrationKeys", keys), admin)
						.getStatusCode());
		String email = "uatcheckout@test.com";
		Map<String, Object> billing = map("firstName", "Uat", "lastName", "Buyer", "country", "CA", "address",
				"1 test street", "city", "Toronto", "postalCode", "M5V2T6", "stateProvince", "ON");
		assertEquals(HttpStatus.OK, call(HttpMethod.POST, "/api/v1/customer/register", map("emailAddress", email,
				"password", "clear123", "gender", "M", "language", "en", "storeCode", "DEFAULT", "billing", billing),
				null).getStatusCode());
		String token = JSON.readTree(call(HttpMethod.POST, "/api/v1/customer/login",
				map("username", email, "password", "clear123"), null).getBody()).get("token").asText();

		ResponseEntity<String> cartResponse = call(HttpMethod.POST, "/api/v1/cart?store=DEFAULT",
				map("product", "uatcheckout", "quantity", 1), null);
		assertEquals(HttpStatus.CREATED, cartResponse.getStatusCode());
		JsonNode cart = JSON.readTree(cartResponse.getBody());
		String code = cart.get("code").asText();

		Map<String, Object> payment = map("paymentType", "MONEYORDER", "transactionType", "CAPTURE",
				"paymentModule", "moneyorder", "amount", cart.get("total").asText());
		ResponseEntity<String> checkout = call(HttpMethod.POST, "/api/v1/auth/cart/" + code + "/checkout?store=DEFAULT",
				map("payment", payment, "currency", "CAD", "customerAgreement", true), token);
		assertEquals(checkout.getBody(), HttpStatus.OK, checkout.getStatusCode());
		long orderId = JSON.readTree(checkout.getBody()).get("id").asLong();
		assertTrue(orderId > 0);

		ResponseEntity<String> order = call(HttpMethod.GET, "/api/v1/auth/orders/" + orderId + "?store=DEFAULT", null,
				token);
		assertEquals(HttpStatus.OK, order.getStatusCode());
		assertEquals(orderId, JSON.readTree(order.getBody()).get("id").asLong());
	}

	private ResponseEntity<String> call(HttpMethod method, String url, Object payload, String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		if (token != null) {
			headers.add("Authorization", "Bearer " + token);
		}
		return testRestTemplate.exchange(url, method, new HttpEntity<>(payload, headers), String.class);
	}

	private static Map<String, Object> map(Object... kv) {
		Map<String, Object> map = new HashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			map.put((String) kv[i], kv[i + 1]);
		}
		return map;
	}
}
