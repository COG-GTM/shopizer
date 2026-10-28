package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class AdminJourneyUatTest extends ServicesTestSupport {

	@Test
	public void adminLoginAndPrivateRouteRequireValidToken() {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(200, login.getStatusCodeValue());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());

		HttpHeaders validHeaders = new HttpHeaders();
		validHeaders.setBearerAuth(login.getBody().getToken());
		ResponseEntity<String> valid = testRestTemplate.exchange(
				"/api/v1/private/customers?store=DEFAULT", HttpMethod.GET,
				new HttpEntity<>(validHeaders), String.class);
		assertEquals(200, valid.getStatusCodeValue());

		ResponseEntity<String> anonymous = testRestTemplate.exchange(
				"/api/v1/private/customers?store=DEFAULT", HttpMethod.GET,
				HttpEntity.EMPTY, String.class);
		assertTrue(anonymous.getStatusCodeValue() == 401 || anonymous.getStatusCodeValue() == 403);

	}
}
