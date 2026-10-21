package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

class AdminJourneyUatTest extends UatTestSupport {

	@Test
	void adminLoginProtectsPrivateCustomersFromAnonymousAndWrongSignatureTokens() {
		String path = "/api/v1/private/customers";
		String token = adminToken();
		ResponseEntity<String> authorized = testRestTemplate.exchange(path, HttpMethod.GET,
				new HttpEntity<>(bearerHeaders(token)), String.class);
		assertEquals(200, authorized.getStatusCodeValue(), authorized.getBody());

		ResponseEntity<String> anonymous = testRestTemplate.exchange(path, HttpMethod.GET, HttpEntity.EMPTY,
				String.class);
		assertTrue(anonymous.getStatusCodeValue() == 401 || anonymous.getStatusCodeValue() == 403,
				"Anonymous status was " + anonymous.getStatusCodeValue());

		Date now = new Date();
		String wrongSignature = Jwts.builder()
				.setSubject("admin@shopizer.com")
				.setIssuedAt(now)
				.setExpiration(new Date(now.getTime() + 60 * 60 * 1000))
				.signWith(SignatureAlgorithm.HS512, new byte[64])
				.compact();
		ResponseEntity<String> rejected = testRestTemplate.exchange(path, HttpMethod.GET,
				new HttpEntity<>(bearerHeaders(wrongSignature)), String.class);
		assertFalse(rejected.getStatusCode().is2xxSuccessful(),
				"Wrong-signature token unexpectedly accessed " + path);
		if (rejected.getStatusCodeValue() != 401 && rejected.getStatusCodeValue() != 403) {
			System.out.println("Wrong-signature JWT status=" + rejected.getStatusCodeValue()
					+ ", body=" + rejected.getBody());
		}
	}
}
