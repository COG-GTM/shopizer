package com.salesmanager.test.shop.integration.system;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;
import static org.springframework.http.HttpStatus.OK;

import java.nio.charset.Charset;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class CacheApiIntegrationTest extends ServicesTestSupport {

	private static final String CUSTOMER_EMAIL = "cache-customer@test.com";
	private static final String CUSTOMER_PASSWORD = "clear123";

	@Test
	public void customerCannotClearCache() {
		HttpHeaders customer = customerHeader();

		ResponseEntity<String> legacyRoute = clear("/api/v1/auth/cache/store/DEFAULT/clear", customer);
		assertThat(legacyRoute.getStatusCode(), not(is(OK)));

		ResponseEntity<String> privateRoute = clear("/api/v1/private/cache/store/DEFAULT/clear", customer);
		assertThat(privateRoute.getStatusCode(), not(is(OK)));
	}

	@Test
	public void anonymousCannotClearCache() {
		ResponseEntity<String> response = clear("/api/v1/private/cache/store/DEFAULT/clear", new HttpHeaders());
		assertThat(response.getStatusCode(), not(is(OK)));
	}

	@Test
	public void adminCanClearOwnStoreCache() {
		ResponseEntity<String> response = clear("/api/v1/private/cache/store/DEFAULT/clear", getHeader());
		assertThat(response.getStatusCode(), is(OK));
	}

	private ResponseEntity<String> clear(String url, HttpHeaders headers) {
		return testRestTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
	}

	private HttpHeaders customerHeader() {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(CUSTOMER_EMAIL);
		customer.setPassword(CUSTOMER_PASSWORD);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		Address billing = new Address();
		billing.setFirstName("cache");
		billing.setLastName("customer");
		billing.setCountry("BE");
		customer.setBilling(billing);
		customer.setStoreCode(Constants.DEFAULT_STORE);
		testRestTemplate.postForEntity("/api/v1/customer/register", new HttpEntity<>(customer, getHeader()),
				PersistableCustomer.class);

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(CUSTOMER_EMAIL, CUSTOMER_PASSWORD)),
				AuthenticationResponse.class);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(new MediaType("application", "json", Charset.forName("UTF-8")));
		headers.add("Authorization", "Bearer " + login.getBody().getToken());
		return headers;
	}
}
