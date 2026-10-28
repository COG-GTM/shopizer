package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.springframework.http.HttpStatus.NOT_IMPLEMENTED;
import static org.springframework.http.HttpStatus.OK;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class PlatformUatTest extends ServicesTestSupport {

	private static final String EXPECTED_SIGNUP_BODY =
			"{\"errorCode\":\"501\",\"message\":\"Store signup is not available\"}";

	@Test
	public void healthDocsAndMerchantSignupJourneyAreAvailable() throws Exception {
		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertEquals(OK, health.getStatusCode());
		assertEquals("UP", new ObjectMapper().readTree(health.getBody()).path("status").asText());

		ResponseEntity<String> apiDocs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(OK, apiDocs.getStatusCode());

		String[] requests = {
				"{\"email\":\"admin@shopizer.com\",\"code\":\"DEFAULT\"}",
				"{\"email\":\"nobody@example.invalid\",\"code\":\"NO_SUCH_STORE\"}",
				"{\"email\":\"admin@shopizer.com\",\"code\":\"NO_SUCH_STORE\"}",
				"{\"email\":\"nobody@example.invalid\",\"code\":\"DEFAULT\"}",
				"{not json",
				""
		};
		for (String request : requests) {
			ResponseEntity<String> response = postSignup(request);
			assertEquals(NOT_IMPLEMENTED, response.getStatusCode());
			assertEquals(EXPECTED_SIGNUP_BODY, response.getBody());
		}

		ResponseEntity<String> verify = testRestTemplate.getForEntity(
				"/api/v1/store/DEFAULT/signup/any-token", String.class);
		assertEquals(NOT_IMPLEMENTED, verify.getStatusCode());
		assertEquals(EXPECTED_SIGNUP_BODY, verify.getBody());
	}

	private ResponseEntity<String> postSignup(String body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return testRestTemplate.postForEntity(
				"/api/v1/store/signup", new HttpEntity<>(body, headers), String.class);
	}
}
