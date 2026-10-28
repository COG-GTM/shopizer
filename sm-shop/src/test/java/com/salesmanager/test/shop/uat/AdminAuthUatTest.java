package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class AdminAuthUatTest extends ServicesTestSupport {

	@Test
	public void adminTokenAuthorizesPrivateUserRouteAndRejectsMissingTokens() {
		ResponseEntity<AuthenticationResponse> loginResponse = testRestTemplate.postForEntity(
				"/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
		assertNotNull(loginResponse.getBody());
		assertNotNull(loginResponse.getBody().getToken());

		HttpHeaders authenticatedHeaders = new HttpHeaders();
		authenticatedHeaders.setBearerAuth(loginResponse.getBody().getToken());
		ResponseEntity<ReadableUser> authorizedResponse = testRestTemplate.exchange("/api/v1/private/users/1",
				HttpMethod.GET, new HttpEntity<>(authenticatedHeaders), ReadableUser.class);
		assertEquals(HttpStatus.OK, authorizedResponse.getStatusCode());

		ResponseEntity<String> noTokenResponse = testRestTemplate.getForEntity("/api/v1/private/users/1",
				String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, noTokenResponse.getStatusCode());

	}
}
