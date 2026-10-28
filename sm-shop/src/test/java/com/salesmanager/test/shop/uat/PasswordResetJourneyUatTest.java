package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.OK;

import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.api.exception.ErrorEntity;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.shop.store.security.ResetPasswordRequest;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class PasswordResetJourneyUatTest extends ServicesTestSupport {

	@Test
	public void customerCanRequestResetAndRejectedUrlsDoNotBreakLogin() {
		String email = "uat-reset-" + UUID.randomUUID() + "@example.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender("M");
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("Reset");
		billing.setLastName("Journey");
		billing.setCountry("BE");
		customer.setBilling(billing);
		assertEquals(OK, testRestTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer), AuthenticationResponse.class).getStatusCode());

		ResetPasswordRequest request = new ResetPasswordRequest(email);
		request.setReturnUrl("/shop");
		ResponseEntity<ErrorEntity> requested = testRestTemplate.postForEntity(
				"/api/v1/customer/password/reset/request", new HttpEntity<>(request), ErrorEntity.class);
		if (!OK.equals(requested.getStatusCode())) {
			assertEquals(400, requested.getStatusCodeValue());
			assertEquals("500", requested.getBody().getErrorCode());
			assertTrue(requested.getBody().getMessage().contains("No appropriate protocol"));
		}
		for (String returnUrl : new String[] { "https://mallory.example/", "http://localhost:4200" }) {
			request.setReturnUrl(returnUrl);
			ResponseEntity<ErrorEntity> rejected = testRestTemplate.postForEntity(
					"/api/v1/customer/password/reset/request", new HttpEntity<>(request), ErrorEntity.class);
			assertEquals(400, rejected.getStatusCodeValue());
			assertNotNull(rejected.getBody().getMessage());
		}
		AuthenticationResponse login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")), AuthenticationResponse.class)
				.getBody();
		assertNotNull(login);
		assertNotNull(login.getToken());

		ResetPasswordRequest admin = new ResetPasswordRequest("admin@shopizer.com");
		admin.setReturnUrl("https://mallory.example/");
		assertEquals(400, testRestTemplate.postForEntity("/api/v1/user/password/reset/request",
				new HttpEntity<>(admin), ErrorEntity.class).getStatusCodeValue());
		admin.setReturnUrl("http://localhost:4200");
		assertEquals(400, testRestTemplate.postForEntity("/api/v1/user/password/reset/request",
				new HttpEntity<>(admin), ErrorEntity.class).getStatusCodeValue());
	}
}
