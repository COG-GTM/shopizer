package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;

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

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ServicesBasicAuthUatTest extends ServicesTestSupport {

	@Test
	public void basicAuthProtectsPrivateServicesAndPublicStoreIsOpen() {
		HttpHeaders validHeaders = new HttpHeaders();
		validHeaders.setBasicAuth("admin@shopizer.com", "password");
		validHeaders.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> authorized = testRestTemplate.exchange("/services/private/system/optin",
				HttpMethod.POST, new HttpEntity<>("uat opt-in", validHeaders), String.class);
		assertEquals(HttpStatus.OK, authorized.getStatusCode());

		HttpHeaders invalidHeaders = new HttpHeaders();
		invalidHeaders.setBasicAuth("admin@shopizer.com", "wrong-password");
		invalidHeaders.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> rejected = testRestTemplate.exchange("/services/private/system/optin",
				HttpMethod.POST, new HttpEntity<>("uat opt-in", invalidHeaders), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, rejected.getStatusCode());

		ResponseEntity<String> store = testRestTemplate.getForEntity("/services/public/DEFAULT", String.class);
		assertEquals(HttpStatus.ACCEPTED, store.getStatusCode());
	}
}
