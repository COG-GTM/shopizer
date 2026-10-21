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
public class AdminLoginJourneyUatTest extends ServicesTestSupport {

	@Test
	public void adminLogsInAndAccessRequiresValidToken() {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(login.getBody().getToken());
		ResponseEntity<ReadableUser> authorized = testRestTemplate.exchange("/api/v1/private/users/1",
				HttpMethod.GET, new HttpEntity<>(headers), ReadableUser.class);
		assertEquals(HttpStatus.OK, authorized.getStatusCode());

		ResponseEntity<ReadableUser> anonymous = testRestTemplate.exchange("/api/v1/private/users/1",
				HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), ReadableUser.class);
		assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatusCode());

	}
}
