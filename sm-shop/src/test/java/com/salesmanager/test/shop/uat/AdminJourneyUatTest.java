package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.util.UUID;
import java.util.Date;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.category.PersistableCategory;
import com.salesmanager.shop.model.catalog.category.ReadableCategory;
import com.salesmanager.test.shop.common.ServicesTestSupport;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class AdminJourneyUatTest extends ServicesTestSupport {

	@Autowired
	private TestRestTemplate testRestTemplate;

	@Value("${jwt.secret}")
	private String jwtSecret;

	@Test
	public void administratorCanAuthenticateAndReadHiddenCategoryPrivately() {
		String adminPath = "/api/v1/private/users?store=DEFAULT";
		ResponseEntity<String> users = testRestTemplate.exchange(adminPath, HttpMethod.GET,
				new HttpEntity<>(getHeader()), String.class);
		assertEquals(OK, users.getStatusCode());

		ResponseEntity<String> anonymous = testRestTemplate.exchange(adminPath, HttpMethod.GET,
				new HttpEntity<>(new HttpHeaders()), String.class);
		assertTrue(anonymous.getStatusCode().is4xxClientError());

		HttpHeaders invalidToken = new HttpHeaders();
		invalidToken.set("Authorization", "Bearer " + expiredToken());
		ResponseEntity<String> invalid = testRestTemplate.exchange(adminPath, HttpMethod.GET,
				new HttpEntity<>(invalidToken), String.class);
		assertTrue(invalid.getStatusCode().is4xxClientError());

		String code = "uat-hidden-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		PersistableCategory hidden = category(code, "UAT hidden category");
		hidden.setVisible(false);
		ResponseEntity<PersistableCategory> created = testRestTemplate.postForEntity(
				"/api/v1/private/category?store=DEFAULT", new HttpEntity<>(hidden, getHeader()),
				PersistableCategory.class);
		assertEquals(CREATED, created.getStatusCode());

		ResponseEntity<ReadableCategory> read = testRestTemplate.exchange(
				"/api/v1/private/category/" + created.getBody().getId() + "?store=DEFAULT&lang=en",
				HttpMethod.GET, new HttpEntity<>(getHeader()), ReadableCategory.class);
		assertEquals(OK, read.getStatusCode());
		assertEquals(created.getBody().getId(), read.getBody().getId());
		assertTrue(!read.getBody().isVisible());
	}

	private String expiredToken() {
		Date now = new Date();
		return Jwts.builder().setSubject("admin@shopizer.com").setIssuedAt(now)
				.setExpiration(new Date(now.getTime() - 1000))
				.signWith(SignatureAlgorithm.HS512, jwtSecret).compact();
	}
}
