package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.modules.email.Email;
import com.salesmanager.core.business.services.system.EmailService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.PasswordRequest;
import com.salesmanager.shop.store.security.ResetPasswordRequest;
import com.salesmanager.test.shop.common.CapturingEmailSender;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Black-box journeys an admin / storefront user relies on. Uses only HTTP
 * calls except for email capture and restoring the seeded admin afterwards.
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class AdminAndStorefrontJourneyUatTest extends ServicesTestSupport {

	private static final String ADMIN = "admin@shopizer.com";
	private static final String ADMIN_PASSWORD = "password";
	private static final ObjectMapper JSON = new ObjectMapper();

	@Inject
	private EmailService emailService;

	@Inject
	private UserService userService;

	@Inject
	private PasswordEncoder passwordEncoder;

	private CapturingEmailSender emails;

	@Before
	public void setUp() {
		emails = CapturingEmailSender.install(emailService);
	}

	@After
	public void tearDown() throws Exception {
		emails.uninstall();
		User admin = userService.getByUserName(ADMIN, "DEFAULT");
		admin.setAdminPassword(passwordEncoder.encode(ADMIN_PASSWORD));
		admin.setCredentialsResetRequest(null);
		userService.save(admin);
	}

	@Test
	public void adminLoginGivesJwtThatOpensPrivateApi() throws Exception {
		ResponseEntity<String> login = adminLogin(ADMIN, ADMIN_PASSWORD);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		String token = json(login).get("token").asText();
		assertFalse(token.isEmpty());

		assertEquals(HttpStatus.OK, get("/api/v1/private/users?store=DEFAULT", bearer(token)).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/private/users?store=DEFAULT", jsonHeaders()).getStatusCode());
		assertFalse(get("/api/v1/private/users?store=DEFAULT", bearer(token + "x")).getStatusCode().is2xxSuccessful());
		assertTrue(adminLogin(ADMIN, "wrong-password").getStatusCode().is4xxClientError());
	}

	@Test
	public void adminForgotPasswordJourneyFromEmailLinkToNewLogin() throws Exception {
		ResetPasswordRequest request = new ResetPasswordRequest(ADMIN);
		request.setReturnUrl("http://localhost:4200");
		assertEquals(HttpStatus.OK,
				post("/api/v1/user/password/reset/request?store=DEFAULT", request, jsonHeaders()).getStatusCode());

		assertEquals(1, emails.sent().size());
		String link = resetLink(emails.sent().get(0));
		assertNotNull(link);
		assertTrue(link, link.startsWith("http://localhost:4200"));
		String token = link.substring(link.lastIndexOf('/') + 1);

		assertEquals(HttpStatus.OK, get("/api/v1/user/DEFAULT/reset/" + token, jsonHeaders()).getStatusCode());

		PasswordRequest change = new PasswordRequest();
		change.setUsername(ADMIN);
		change.setPassword("Journey1");
		change.setRepeatPassword("Journey1");
		assertEquals(HttpStatus.OK,
				post("/api/v1/user/DEFAULT/password/" + token, change, jsonHeaders()).getStatusCode());

		assertEquals(HttpStatus.OK, adminLogin(ADMIN, "Journey1").getStatusCode());
		assertTrue(adminLogin(ADMIN, ADMIN_PASSWORD).getStatusCode().is4xxClientError());
		assertFalse(post("/api/v1/user/DEFAULT/password/" + token, change, jsonHeaders()).getStatusCode()
				.is2xxSuccessful());
	}

	@Test
	public void forgotPasswordWithForeignReturnUrlIsRefused() throws Exception {
		ResetPasswordRequest request = new ResetPasswordRequest(ADMIN);
		request.setReturnUrl("https://mallory.example/reset");
		assertEquals(HttpStatus.BAD_REQUEST,
				post("/api/v1/user/password/reset/request?store=DEFAULT", request, jsonHeaders()).getStatusCode());
		assertTrue(emails.sent().isEmpty());
	}

	@Test
	public void customerRegistersLogsInAndReadsProfile() throws Exception {
		String email = "uat" + System.nanoTime() + "@test.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.F.name());
		customer.setLanguage("en");
		Address billing = new Address();
		billing.setFirstName("Uat");
		billing.setLastName("Customer");
		billing.setCountry("CA");
		customer.setBilling(billing);
		customer.setStoreCode("DEFAULT");
		assertEquals(HttpStatus.OK, post("/api/v1/customer/register", customer, jsonHeaders()).getStatusCode());

		ResponseEntity<String> login = post("/api/v1/customer/login", new AuthenticationRequest(email, "clear123"),
				jsonHeaders());
		assertEquals(HttpStatus.OK, login.getStatusCode());
		String token = json(login).get("token").asText();

		ResponseEntity<String> profile = get("/api/v1/auth/customer/profile?store=DEFAULT", bearer(token));
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertEquals(email, json(profile).get("emailAddress").asText());
		assertTrue(get("/api/v1/auth/customer/profile?store=DEFAULT", jsonHeaders()).getStatusCode()
				.is4xxClientError());
	}

	@Test
	public void shopperBrowsesStoreCategoriesAndProducts() throws Exception {
		ResponseEntity<String> store = get("/api/v1/store/DEFAULT", jsonHeaders());
		assertEquals(HttpStatus.OK, store.getStatusCode());
		assertEquals("DEFAULT", json(store).get("code").asText());

		ResponseEntity<String> categories = get("/api/v1/category?store=DEFAULT&lang=en", jsonHeaders());
		assertEquals(HttpStatus.OK, categories.getStatusCode());
		assertTrue(json(categories).has("categories"));

		ResponseEntity<String> products = get("/api/v1/products?store=DEFAULT&lang=en", jsonHeaders());
		assertEquals(HttpStatus.OK, products.getStatusCode());
		assertTrue(json(products).has("products"));
	}

	@Test
	public void servicesApiRequiresValidBasicAuth() {
		HttpHeaders bad = jsonHeaders();
		bad.set(HttpHeaders.AUTHORIZATION, basic(ADMIN, "wrong-password"));
		assertEquals(HttpStatus.UNAUTHORIZED, get("/services/private/system/module", bad).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, get("/services/private/system/module", jsonHeaders()).getStatusCode());

		HttpHeaders good = jsonHeaders();
		good.set(HttpHeaders.AUTHORIZATION, basic(ADMIN, ADMIN_PASSWORD));
		assertFalse(get("/services/private/system/module", good).getStatusCode() == HttpStatus.UNAUTHORIZED);
	}

	@Test
	public void healthAndApiDocsAreServed() throws Exception {
		assertEquals(HttpStatus.OK, get("/actuator/health/ping/", jsonHeaders()).getStatusCode());

		ResponseEntity<String> docs = get("/v2/api-docs", jsonHeaders());
		assertEquals(HttpStatus.OK, docs.getStatusCode());
		JsonNode paths = json(docs).get("paths");
		assertNotNull(paths);
		assertTrue(paths.has("/api/v1/user/password/reset/request"));
		assertTrue(paths.has("/api/v1/private/login"));
	}

	private ResponseEntity<String> adminLogin(String user, String password) {
		return post("/api/v1/private/login", new AuthenticationRequest(user, password), jsonHeaders());
	}

	private ResponseEntity<String> get(String path, HttpHeaders headers) {
		return testRestTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	private ResponseEntity<String> post(String path, Object body, HttpHeaders headers) {
		return testRestTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
	}

	private static HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		return headers;
	}

	private static HttpHeaders bearer(String token) {
		HttpHeaders headers = jsonHeaders();
		headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		return headers;
	}

	private static String basic(String user, String password) {
		return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
	}

	private static JsonNode json(ResponseEntity<String> response) throws Exception {
		return JSON.readTree(response.getBody());
	}

	private static String resetLink(Email email) {
		return email.getTemplateTokens().values().stream().filter(v -> v != null && v.startsWith("http"))
				.findFirst().orElse(null);
	}
}
