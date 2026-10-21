package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.TEXT_PLAIN;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import com.salesmanager.test.shop.common.ServicesTestSupport;

class ServicesBasicAuthJourneyUatTest extends ServicesTestSupport {

	@Test
	void servicesPrivateRequiresBasicAuthAndPublicContactNeedsNoCredentials() {
		HttpHeaders validCredentials = new HttpHeaders();
		validCredentials.setBasicAuth("admin@shopizer.com", "password");
		validCredentials.setContentType(TEXT_PLAIN);
		ResponseEntity<String> authorized = testRestTemplate.exchange(
				"/services/private/system/optin", POST,
				new HttpEntity<>("uat-readiness-probe", validCredentials), String.class);
		assertEquals(OK, authorized.getStatusCode());

		HttpHeaders badCredentials = new HttpHeaders();
		badCredentials.setBasicAuth("admin@shopizer.com", "wrong-password");
		badCredentials.setContentType(TEXT_PLAIN);
		ResponseEntity<String> rejected = testRestTemplate.exchange(
				"/services/private/system/optin", POST,
				new HttpEntity<>("uat-readiness-probe", badCredentials), String.class);
		assertEquals(401, rejected.getStatusCode().value());

		ResponseEntity<String> publicContact = testRestTemplate.exchange(
				"/services/public/DEFAULT", GET, HttpEntity.EMPTY, String.class);
		assertEquals(ACCEPTED, publicContact.getStatusCode());
	}
}
