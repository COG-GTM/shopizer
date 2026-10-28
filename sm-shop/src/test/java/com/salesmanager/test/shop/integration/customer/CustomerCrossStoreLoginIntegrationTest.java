package com.salesmanager.test.shop.integration.customer;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.springframework.http.HttpStatus.OK;

import java.util.Arrays;

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

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Customer nicks are unique per store: the same username registered in two stores
 * must not lock either customer out of their own store.
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class CustomerCrossStoreLoginIntegrationTest extends ServicesTestSupport {

	private static final String OTHER_STORE = "globex";
	private static final String EMAIL = "dupnick@test.com";

	@Test
	public void sameNickInTwoStoresCanLoginInEachStore() {
		createStore(OTHER_STORE);

		register(Constants.DEFAULT_STORE, "defaultPass1");
		register(OTHER_STORE, "otherPass1");

		AuthenticationResponse defaultLogin = login(Constants.DEFAULT_STORE, "defaultPass1");
		AuthenticationResponse otherLogin = login(OTHER_STORE, "otherPass1");
		assertNotEquals(defaultLogin.getId(), otherLogin.getId());

		assertThat(profile(Constants.DEFAULT_STORE, defaultLogin.getToken()).getBody().getId(), is(defaultLogin.getId()));
		assertThat(profile(OTHER_STORE, otherLogin.getToken()).getBody().getId(), is(otherLogin.getId()));

		// password of one store does not authenticate the customer of the other store
		ResponseEntity<String> wrongStore = testRestTemplate.postForEntity(
				"/api/v1/customer/login?store=" + OTHER_STORE,
				new HttpEntity<>(new AuthenticationRequest(EMAIL, "defaultPass1")), String.class);
		assertThat(wrongStore.getStatusCode(), is(HttpStatus.UNAUTHORIZED));

		// token issued in one store is not accepted in the other store
		assertNotEquals(OK, profile(OTHER_STORE, defaultLogin.getToken()).getStatusCode());
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
		store.setEmail("test@test.com");
		store.setName(code);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);

		ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/store/", HttpMethod.POST,
				new HttpEntity<>(store, getHeader()), Void.class);
		assertThat(response.getStatusCode(), is(OK));
	}

	private void register(String store, String password) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(EMAIL);
		customer.setPassword(password);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		Address billing = new Address();
		billing.setFirstName("dup");
		billing.setLastName("nick");
		billing.setCountry("BE");
		customer.setBilling(billing);
		customer.setStoreCode(store);

		ResponseEntity<String> response = testRestTemplate.postForEntity("/api/v1/customer/register?store=" + store,
				new HttpEntity<>(customer, getHeader()), String.class);
		assertThat(response.getStatusCode(), is(OK));
	}

	private AuthenticationResponse login(String store, String password) {
		ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity(
				"/api/v1/customer/login?store=" + store, new HttpEntity<>(new AuthenticationRequest(EMAIL, password)),
				AuthenticationResponse.class);
		assertThat(response.getStatusCode(), is(OK));
		assertNotNull(response.getBody().getToken());
		return response.getBody();
	}

	private ResponseEntity<ReadableCustomer> profile(String store, String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.add("Authorization", "Bearer " + token);
		return testRestTemplate.exchange("/api/v1/auth/customer/profile?store=" + store, HttpMethod.GET,
				new HttpEntity<>(headers), ReadableCustomer.class);
	}
}
