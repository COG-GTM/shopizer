package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class AdminJourneyUatTest extends ServicesTestSupport {

	@Test
	public void adminCanLoginAndCustomerListRequiresValidAuthentication() {
		AuthenticationRequest request = new AuthenticationRequest("admin@shopizer.com", "password");
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/private/login", new HttpEntity<>(request), AuthenticationResponse.class);

		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());

		HttpHeaders validHeaders = new HttpHeaders();
		validHeaders.setBearerAuth(login.getBody().getToken());
		ResponseEntity<String> authorized = testRestTemplate.exchange(
				"/api/v1/private/customers", GET, new HttpEntity<>(validHeaders), String.class);
		ResponseEntity<String> anonymous = testRestTemplate.getForEntity(
				"/api/v1/private/customers", String.class);

		assertEquals(OK, authorized.getStatusCode());
		assertEquals(UNAUTHORIZED, anonymous.getStatusCode());
	}
}
