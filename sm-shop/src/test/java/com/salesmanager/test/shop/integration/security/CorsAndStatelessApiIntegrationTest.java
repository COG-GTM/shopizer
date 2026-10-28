package com.salesmanager.test.shop.integration.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public class CorsAndStatelessApiIntegrationTest extends ServicesTestSupport {

	@Autowired
	private ServletWebServerApplicationContext webServerApplicationContext;

	@Test
	public void disallowedOriginIsRejected() {
		ResponseEntity<String> response = exchange("/api/v1/products?store=DEFAULT", HttpMethod.GET,
				headersWithOrigin("https://evil.example"), String.class);

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
		assertNull(response.getHeaders().getFirst("Access-Control-Allow-Origin"));
	}

	@Test
	public void sameOriginAndRequestsWithoutOriginAreAccepted() {
		ResponseEntity<String> sameOrigin = exchange("/api/v1/store/DEFAULT", HttpMethod.GET,
				headersWithOrigin("http://localhost:" + webServerApplicationContext.getWebServer().getPort()),
				String.class);
		assertEquals(HttpStatus.OK, sameOrigin.getStatusCode());

		ResponseEntity<String> noOrigin = exchange("/api/v1/store/DEFAULT", HttpMethod.GET,
				new HttpHeaders(), String.class);
		assertEquals(HttpStatus.OK, noOrigin.getStatusCode());
		assertNull(noOrigin.getHeaders().getFirst("Access-Control-Allow-Origin"));
	}

	@Test
	public void disallowedPreflightOriginIsRejected() {
		HttpHeaders evilHeaders = headersWithOrigin("https://evil.example");
		evilHeaders.set("Access-Control-Request-Method", "POST");
		ResponseEntity<String> evil = exchange("/api/v1/auth/customer/profile", HttpMethod.OPTIONS, evilHeaders,
				String.class);
		assertEquals(HttpStatus.FORBIDDEN, evil.getStatusCode());
		assertNull(evil.getHeaders().getFirst("Access-Control-Allow-Origin"));
	}

	@Test
	public void disallowedServiceOriginIsRejected() {
		ResponseEntity<String> evil = exchange("/services/public/DEFAULT", HttpMethod.GET,
				headersWithOrigin("https://evil.example"), String.class);
		assertEquals(HttpStatus.FORBIDDEN, evil.getStatusCode());
		assertNull(evil.getHeaders().getFirst("Access-Control-Allow-Origin"));
	}

	@Test
	public void adminBearerRequestsDoNotCreateSessionCookies() {
		HttpHeaders headers = getHeader();
		ResponseEntity<String> profile = exchange("/api/v1/private/user/profile", HttpMethod.GET, headers,
				String.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertNoSessionCookie(profile.getHeaders().get(HttpHeaders.SET_COOKIE));

		ResponseEntity<String> localizedProfile = exchange("/api/v1/private/user/profile?locale=de",
				HttpMethod.GET, headers, String.class);
		assertEquals(HttpStatus.OK, localizedProfile.getStatusCode());
		assertNoSessionCookie(localizedProfile.getHeaders().get(HttpHeaders.SET_COOKIE));
	}

	@Test
	public void customerBearerRequestsAreStatelessAndBasicAuthIsUnsupported() {
		String email = "cors-it-" + System.nanoTime() + "@test.com";
		registerCustomer(email);
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());

		HttpHeaders customerHeaders = bearerHeaders(login.getBody().getToken());
		ResponseEntity<ReadableCustomer> profile = exchange("/api/v1/auth/customer/profile", HttpMethod.GET,
				customerHeaders, ReadableCustomer.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertEquals(email, profile.getBody().getEmailAddress());
		assertNoSessionCookie(profile.getHeaders().get(HttpHeaders.SET_COOKIE));

		ResponseEntity<String> localizedProfile = exchange("/api/v1/auth/customer/profile?locale=de",
				HttpMethod.GET, customerHeaders, String.class);
		assertEquals(HttpStatus.OK, localizedProfile.getStatusCode());
		assertNoSessionCookie(localizedProfile.getHeaders().get(HttpHeaders.SET_COOKIE));

		ResponseEntity<String> unauthenticated = exchange("/api/v1/auth/customer/profile", HttpMethod.GET,
				new HttpHeaders(), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, unauthenticated.getStatusCode());
		assertNull(unauthenticated.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));

		HttpHeaders basicHeaders = new HttpHeaders();
		basicHeaders.setBasicAuth(email, "clear123");
		ResponseEntity<String> basic = exchange("/api/v1/auth/customer/profile", HttpMethod.GET, basicHeaders,
				String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, basic.getStatusCode());
	}

	@Test
	public void privateAdminApiReturnsUnauthorizedWithoutBearerChallenge() {
		ResponseEntity<String> unauthenticated = exchange("/api/v1/private/user/profile", HttpMethod.GET,
				new HttpHeaders(), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, unauthenticated.getStatusCode());
		assertNull(unauthenticated.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));
	}

	private void registerCustomer(String email) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("Cors");
		billing.setLastName("Integration");
		billing.setCountry("BE");
		customer.setBilling(billing);

		ResponseEntity<PersistableCustomer> response = testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer, getHeader()), PersistableCustomer.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	private HttpHeaders headersWithOrigin(String origin) {
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.ORIGIN, origin);
		return headers;
	}

	private HttpHeaders bearerHeaders(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return headers;
	}

	private <T> ResponseEntity<T> exchange(String path, HttpMethod method, HttpHeaders headers, Class<T> responseType) {
		return testRestTemplate.exchange(path, method, new HttpEntity<>(headers), responseType);
	}

	private void assertNoSessionCookie(List<String> setCookies) {
		if (setCookies != null) {
			assertFalse(setCookies.stream().anyMatch(cookie -> cookie.contains("JSESSIONID")));
		}
	}
}
