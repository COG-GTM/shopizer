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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.populator.customer.ReadableCustomerList;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class AdminJourneyUatTest extends ServicesTestSupport {

	@Test
	public void adminCanLogInAndAccessAdminOnlyResource() {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(login.getBody().getToken());
		ResponseEntity<ReadableCustomerList> authorized = testRestTemplate.exchange(
				"/api/v1/private/customers?count=1", HttpMethod.GET, new HttpEntity<>(headers),
				ReadableCustomerList.class);
		assertEquals(HttpStatus.OK, authorized.getStatusCode());

		ResponseEntity<String> anonymous = testRestTemplate.getForEntity(
				"/api/v1/private/customers?count=1", String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatusCode());

		HttpHeaders badToken = new HttpHeaders();
		badToken.setBearerAuth("not-a-valid-token");
		ResponseEntity<String> rejected = testRestTemplate.exchange(
				"/api/v1/private/customers?count=1", HttpMethod.GET, new HttpEntity<>(badToken), String.class);
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, rejected.getStatusCode());

	}
}
