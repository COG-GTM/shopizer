package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import javax.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Black-box journeys over HTTP: a forgotten-password admin flow plus the core
 * storefront/admin journeys the frontends rely on. Only JSON over HTTP is used,
 * except reading the emailed reset token, which has no API (no SMTP in tests).
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public class AdminPasswordResetJourneyUatTest extends ServicesTestSupport {

	private static final ObjectMapper JSON = new ObjectMapper();

	@Inject
	private UserService userService;

	@Test
	public void adminWhoForgotPasswordCanResetItOnceViaTrustedLinkOnly() throws Exception {
		String admin = "uat-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
		ResponseEntity<String> created = exchange(HttpMethod.POST, "/api/v1/private/user/",
				"{\"userName\":\"" + admin + "\",\"emailAddress\":\"" + admin + "\",\"firstName\":\"Uat\","
						+ "\"lastName\":\"Admin\",\"defaultLanguage\":\"en\",\"password\":\"Password1\","
						+ "\"repeatPassword\":\"Password1\",\"groups\":[{\"name\":\"ADMIN\"}]}",
				getHeader());
		assertEquals(HttpStatus.OK, created.getStatusCode(), created.getBody());

		ResponseEntity<String> poisoned = resetRequest(admin, "https://attacker.example/admin");
		assertEquals(HttpStatus.BAD_REQUEST, poisoned.getStatusCode());
		JsonNode error = JSON.readTree(poisoned.getBody());
		assertTrue(error.get("message").asText().contains("is not allowed"), poisoned.getBody());
		assertTrue(error.has("errorCode"));
		assertTrue(userService.getByUserName(admin, "DEFAULT").getCredentialsResetRequest() == null
				|| userService.getByUserName(admin, "DEFAULT").getCredentialsResetRequest()
						.getCredentialsRequest() == null);

		ResponseEntity<String> trusted = resetRequest(admin, "http://localhost:8080/admin");
		assertFalse(String.valueOf(trusted.getBody()).contains("is not allowed"), trusted.getBody());
		String token = userService.getByUserName(admin, "DEFAULT").getCredentialsResetRequest()
				.getCredentialsRequest();
		assertNotNull(token);

		assertEquals(HttpStatus.OK,
				exchange(HttpMethod.GET, "/api/v1/user/DEFAULT/reset/" + token, null, null).getStatusCode());
		String newPassword = "{\"username\":\"" + admin + "\",\"password\":\"Password7\",\"repeatPassword\":\"Password7\"}";
		assertEquals(HttpStatus.OK, exchange(HttpMethod.POST, "/api/v1/user/DEFAULT/password/" + token,
				newPassword, null).getStatusCode());

		ResponseEntity<String> login = exchange(HttpMethod.POST, "/api/v1/private/login",
				"{\"username\":\"" + admin + "\",\"password\":\"Password7\"}", null);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertTrue(JSON.readTree(login.getBody()).get("token").isTextual());

		assertEquals(HttpStatus.UNAUTHORIZED, exchange(HttpMethod.POST, "/api/v1/private/login",
				"{\"username\":\"" + admin + "\",\"password\":\"Password1\"}", null).getStatusCode());
		assertTrue(exchange(HttpMethod.GET, "/api/v1/user/DEFAULT/reset/" + token, null, null).getStatusCode()
				.is4xxClientError());
		assertTrue(exchange(HttpMethod.POST, "/api/v1/user/DEFAULT/password/" + token, newPassword, null)
				.getStatusCode().is4xxClientError());
	}

	@Test
	public void adminLoginAndPrivateApiRequireValidJwt() throws Exception {
		ResponseEntity<String> login = exchange(HttpMethod.POST, "/api/v1/private/login",
				"{\"username\":\"admin@shopizer.com\",\"password\":\"password\"}", null);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		JsonNode body = JSON.readTree(login.getBody());
		assertTrue(body.get("token").isTextual());
		assertTrue(body.get("id").isNumber());

		HttpHeaders bearer = new HttpHeaders();
		bearer.setBearerAuth(body.get("token").asText());
		ResponseEntity<String> store = exchange(HttpMethod.GET, "/api/v1/private/store/DEFAULT", null, bearer);
		assertEquals(HttpStatus.OK, store.getStatusCode());
		assertEquals("DEFAULT", JSON.readTree(store.getBody()).get("code").asText());

		assertEquals(HttpStatus.UNAUTHORIZED,
				exchange(HttpMethod.GET, "/api/v1/private/store/DEFAULT", null, null).getStatusCode());
		HttpHeaders bad = new HttpHeaders();
		bad.setBearerAuth("abc.def.ghi");
		assertFalse(exchange(HttpMethod.GET, "/api/v1/private/store/DEFAULT", null, bad).getStatusCode()
				.is2xxSuccessful());
		assertEquals(HttpStatus.UNAUTHORIZED, exchange(HttpMethod.POST, "/api/v1/private/login",
				"{\"username\":\"admin@shopizer.com\",\"password\":\"wrong\"}", null).getStatusCode());
	}

	@Test
	public void customerRegistersLogsInBrowsesAndFillsCart() throws Exception {
		String email = "uat-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
		ResponseEntity<String> registered = exchange(HttpMethod.POST, "/api/v1/customer/register?store=DEFAULT",
				"{\"emailAddress\":\"" + email + "\",\"password\":\"Clear123\",\"gender\":\"M\",\"language\":\"en\","
						+ "\"storeCode\":\"DEFAULT\",\"billing\":{\"firstName\":\"Uat\",\"lastName\":\"Customer\","
						+ "\"country\":\"CA\",\"stateProvince\":\"QC\",\"address\":\"1 rue\",\"city\":\"Montreal\","
						+ "\"postalCode\":\"H2H2H2\",\"phone\":\"5145551234\"}}",
				null);
		assertEquals(HttpStatus.OK, registered.getStatusCode(), registered.getBody());

		ResponseEntity<String> login = exchange(HttpMethod.POST, "/api/v1/customer/login?store=DEFAULT",
				"{\"username\":\"" + email + "\",\"password\":\"Clear123\"}", null);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		HttpHeaders customer = new HttpHeaders();
		customer.setBearerAuth(JSON.readTree(login.getBody()).get("token").asText());
		ResponseEntity<String> profile = exchange(HttpMethod.GET, "/api/v1/auth/customer/profile?store=DEFAULT", null,
				customer);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertEquals(email, JSON.readTree(profile.getBody()).get("emailAddress").asText());
		assertEquals(HttpStatus.UNAUTHORIZED,
				exchange(HttpMethod.GET, "/api/v1/auth/customer/profile?store=DEFAULT", null, null).getStatusCode());

		String sku = sampleProduct("uatjourney").getSku();
		assertEquals(HttpStatus.OK, exchange(HttpMethod.GET, "/api/v1/store/DEFAULT", null, null).getStatusCode());
		assertEquals(HttpStatus.OK,
				exchange(HttpMethod.GET, "/api/v1/category?store=DEFAULT&lang=en", null, null).getStatusCode());
		ResponseEntity<String> products = exchange(HttpMethod.GET, "/api/v1/products?store=DEFAULT&lang=en", null,
				null);
		assertEquals(HttpStatus.OK, products.getStatusCode());
		assertTrue(JSON.readTree(products.getBody()).get("products").isArray());
		assertEquals(HttpStatus.OK,
				exchange(HttpMethod.GET, "/api/v2/product/" + sku + "?store=DEFAULT&lang=en", null, null)
						.getStatusCode());

		ResponseEntity<String> cart = exchange(HttpMethod.POST, "/api/v1/cart?store=DEFAULT",
				"{\"product\":\"" + sku + "\",\"quantity\":1}", null);
		assertEquals(HttpStatus.CREATED, cart.getStatusCode(), cart.getBody());
		String cartCode = JSON.readTree(cart.getBody()).get("code").asText();
		assertEquals(HttpStatus.CREATED, exchange(HttpMethod.PUT, "/api/v1/cart/" + cartCode + "?store=DEFAULT",
				"{\"product\":\"" + sku + "\",\"quantity\":3}", null).getStatusCode());
		ResponseEntity<String> readBack = exchange(HttpMethod.GET, "/api/v1/cart/" + cartCode + "?store=DEFAULT",
				null, null);
		assertEquals(HttpStatus.OK, readBack.getStatusCode());
		JsonNode cartJson = JSON.readTree(readBack.getBody());
		assertEquals(cartCode, cartJson.get("code").asText());
		assertEquals(3, cartJson.get("quantity").asInt(), readBack.getBody());
	}

	@Test
	public void servicesBasicAuthActuatorAndApiDocs() {
		HttpHeaders basic = new HttpHeaders();
		basic.setBasicAuth("admin@shopizer.com", "password");
		String optin = "{\"code\":\"UAT" + UUID.randomUUID().toString().substring(0, 6) + "\",\"optinType\":\"PROMOTIONS\"}";
		assertEquals(HttpStatus.OK,
				exchange(HttpMethod.POST, "/services/private/system/optin", optin, basic).getStatusCode());
		HttpHeaders wrong = new HttpHeaders();
		wrong.setBasicAuth("admin@shopizer.com", "wrong");
		assertEquals(HttpStatus.UNAUTHORIZED,
				exchange(HttpMethod.POST, "/services/private/system/optin", optin, wrong).getStatusCode());
		HttpStatus anonymous = exchange(HttpMethod.POST, "/services/private/system/optin", optin, null)
				.getStatusCode();
		// no credentials: basic-auth challenge (401) or form-login redirect (302), never served
		assertTrue(anonymous == HttpStatus.UNAUTHORIZED || anonymous == HttpStatus.FOUND, anonymous.toString());

		ResponseEntity<String> health = exchange(HttpMethod.GET, "/actuator/health", null, null);
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertTrue(health.getBody().contains("\"status\":\"UP\""), health.getBody());
		assertEquals(HttpStatus.OK, exchange(HttpMethod.GET, "/v2/api-docs", null, null).getStatusCode());
	}

	private ResponseEntity<String> resetRequest(String user, String returnUrl) {
		return exchange(HttpMethod.POST, "/api/v1/user/password/reset/request?store=DEFAULT",
				"{\"username\":\"" + user + "\",\"returnUrl\":\"" + returnUrl + "\"}", null);
	}

	private ResponseEntity<String> exchange(HttpMethod method, String path, String body, HttpHeaders auth) {
		HttpHeaders headers = new HttpHeaders();
		if (auth != null) {
			headers.putAll(auth);
		}
		headers.setContentType(MediaType.APPLICATION_JSON);
		return testRestTemplate.exchange(path, method, new HttpEntity<>(body, headers), String.class);
	}
}
