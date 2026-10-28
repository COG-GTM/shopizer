package com.salesmanager.test.shop.integration.customer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Base64;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
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
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class CustomerStoreScopedAuthIntegrationTest extends ServicesTestSupport {

	private static final String PASSWORD = "StorePass123";

	@Autowired
	private CustomerService customerService;

	@Autowired
	private MerchantStoreService merchantStoreService;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	public void sameNickIsPersistedIndependentlyForEachStore() throws Exception {
		String storeCode = newStoreCode();
		String email = newEmail();
		createStore(storeCode);
		register(Constants.DEFAULT_STORE, email);
		register(storeCode, email);

		MerchantStore store = merchantStoreService.getByCode(storeCode);
		Customer defaultCustomer = customerService.getByNick(email, Constants.DEFAULT_STORE);
		Customer storeCustomer = customerService.getByNick(email, storeCode);

		assertNotNull(store);
		assertNotNull(defaultCustomer);
		assertNotNull(storeCustomer);
		assertNotEquals(defaultCustomer.getId(), storeCustomer.getId());
		assertEquals(Constants.DEFAULT_STORE, defaultCustomer.getMerchantStore().getCode());
		assertEquals(storeCode, storeCustomer.getMerchantStore().getCode());
		assertEquals(storeCustomer.getId(), customerService.getByNick(email, store.getId()).getId());
	}

	@Test
	public void duplicateRegistrationInOneStoreIsRejected() {
		String email = newEmail();
		register(Constants.DEFAULT_STORE, email);

		ResponseEntity<String> duplicate = testRestTemplate.postForEntity(
				"/api/v1/customer/register?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(customer(Constants.DEFAULT_STORE, email), getHeader()), String.class);

		assertNotEquals(HttpStatus.OK, duplicate.getStatusCode());
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, duplicate.getStatusCode());
	}

	@Test
	public void customerAndAdminTokensHaveTheExpectedStoreClaims() throws Exception {
		String storeCode = newStoreCode();
		String email = newEmail();
		createStore(storeCode);
		register(Constants.DEFAULT_STORE, email);
		register(storeCode, email);

		AuthenticationResponse defaultLogin = login(Constants.DEFAULT_STORE, email);
		AuthenticationResponse storeLogin = login(storeCode, email);
		AuthenticationResponse adminLogin = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class).getBody();

		assertNotNull(adminLogin);
		assertEquals(Constants.DEFAULT_STORE, tokenPayload(defaultLogin.getToken()).path("store").asText());
		assertEquals(storeCode, tokenPayload(storeLogin.getToken()).path("store").asText());
		assertTrue(!tokenPayload(adminLogin.getToken()).has("store"));
	}

	@Test
	public void storeScopedOrdersAndCustomerCartAuthorizationWork() throws Exception {
		String storeCode = newStoreCode();
		String email = newEmail();
		createStore(storeCode);
		register(Constants.DEFAULT_STORE, email);
		register(storeCode, email);
		AuthenticationResponse defaultLogin = login(Constants.DEFAULT_STORE, email);
		AuthenticationResponse storeLogin = login(storeCode, email);
		Customer defaultCustomer = customerService.getByNick(email, Constants.DEFAULT_STORE);

		ResponseEntity<String> defaultOrders = testRestTemplate.exchange(
				"/api/v1/auth/orders?store=" + Constants.DEFAULT_STORE, HttpMethod.GET,
				new HttpEntity<Void>(bearer(defaultLogin.getToken())), String.class);
		ResponseEntity<String> storeOrders = testRestTemplate.exchange(
				"/api/v1/auth/orders?store=" + storeCode, HttpMethod.GET,
				new HttpEntity<Void>(bearer(storeLogin.getToken())), String.class);
		assertEquals(HttpStatus.OK, defaultOrders.getStatusCode());
		assertEquals(HttpStatus.OK, storeOrders.getStatusCode());

		ResponseEntity<String> crossCustomerCart = testRestTemplate.exchange(
				"/api/v1/auth/customer/" + defaultCustomer.getId() + "/cart?store=" + Constants.DEFAULT_STORE,
				HttpMethod.GET, new HttpEntity<Void>(bearer(storeLogin.getToken())), String.class);
		assertNotEquals(HttpStatus.OK, crossCustomerCart.getStatusCode());

		ResponseEntity<String> ownCart = testRestTemplate.exchange(
				"/api/v1/auth/customer/" + defaultCustomer.getId() + "/cart?store=" + Constants.DEFAULT_STORE,
				HttpMethod.GET, new HttpEntity<Void>(bearer(defaultLogin.getToken())), String.class);
		assertNotEquals(HttpStatus.UNAUTHORIZED, ownCart.getStatusCode());
		assertNotEquals(HttpStatus.FORBIDDEN, ownCart.getStatusCode());
	}

	@Test
	public void customerTokenIsRejectedWhenRequestDefaultsToAnotherStore() {
		String storeCode = newStoreCode();
		String email = newEmail();
		createStore(storeCode);
		register(storeCode, email);
		AuthenticationResponse storeLogin = login(storeCode, email);

		ResponseEntity<String> defaultProfile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile", HttpMethod.GET,
				new HttpEntity<Void>(bearer(storeLogin.getToken())), String.class);

		assertEquals(HttpStatus.UNAUTHORIZED, defaultProfile.getStatusCode());
	}

	@Test
	public void adminTokenWithoutStoreClaimCanAccessPrivateCustomers() {
		HttpHeaders headers = getHeader();
		ResponseEntity<String> customers = testRestTemplate.exchange(
				"/api/v1/private/customers?store=" + Constants.DEFAULT_STORE, HttpMethod.GET,
				new HttpEntity<Void>(headers), String.class);

		assertEquals(HttpStatus.OK, customers.getStatusCode());
	}

	private void createStore(String code) {
		PersistableAddress address = new PersistableAddress();
		address.setAddress("121212 simple address");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("FT LD");
		address.setStateProvince("FL");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(code);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail("store-" + code + "@example.com");
		store.setName(code);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);

		ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/store/", HttpMethod.POST,
				new HttpEntity<>(store, getHeader()), Void.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	private void register(String storeCode, String email) {
		ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity(
				"/api/v1/customer/register?store=" + storeCode,
				new HttpEntity<>(customer(storeCode, email), getHeader()), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
	}

	private PersistableCustomer customer(String storeCode, String email) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword(PASSWORD);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		Address billing = new Address();
		billing.setFirstName("store");
		billing.setLastName("customer");
		billing.setCountry("BE");
		customer.setBilling(billing);
		customer.setStoreCode(storeCode);
		return customer;
	}

	private AuthenticationResponse login(String storeCode, String email) {
		ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity(
				"/api/v1/customer/login?store=" + storeCode,
				new HttpEntity<>(new AuthenticationRequest(email, PASSWORD)), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertNotNull(response.getBody().getToken());
		return response.getBody();
	}

	private HttpHeaders bearer(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("Authorization", "Bearer " + token);
		return headers;
	}

	private JsonNode tokenPayload(String token) throws Exception {
		String payload = token.split("\\.")[1];
		return objectMapper.readTree(Base64.getUrlDecoder().decode(payload));
	}

	private String newStoreCode() {
		return "s" + nanoTimeSuffix(7);
	}

	private String newEmail() {
		return "customer." + nanoTimeSuffix(13) + "@example.com";
	}

	private String nanoTimeSuffix(int length) {
		String value = Long.toUnsignedString(System.nanoTime(), 36);
		return value.substring(Math.max(0, value.length() - length));
	}
}
