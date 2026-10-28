package com.salesmanager.test.shop.integration.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.NOT_IMPLEMENTED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class StoreSignupIntegrationTest extends ServicesTestSupport {

	private static final String EXPECTED_BODY =
			"{\"errorCode\":\"501\",\"message\":\"Store signup is not available\"}";

	@Test
	public void anonymousSignupDoesNotRevealAccountOrStoreState() {
		String[] requests = {
				"{\"email\":\"admin@shopizer.com\",\"code\":\"DEFAULT\"}",
				"{\"email\":\"nobody@example.invalid\",\"code\":\"NO_SUCH_STORE\"}",
				"{\"email\":\"admin@shopizer.com\",\"code\":\"NO_SUCH_STORE\"}",
				"{\"email\":\"nobody@example.invalid\",\"code\":\"DEFAULT\"}"
		};

		for (String request : requests) {
			ResponseEntity<String> response = postSignup(request, new HttpHeaders());
			assertEquals(NOT_IMPLEMENTED, response.getStatusCode());
			assertEquals(EXPECTED_BODY, response.getBody());
		}
	}

	@Test
	public void authenticatedSignupAndTokenVerificationReturnTheSameUnavailableResponse() {
		ResponseEntity<String> signup = postSignup(
				"{\"email\":\"admin@shopizer.com\",\"code\":\"DEFAULT\"}", getHeader());
		ResponseEntity<String> verify = testRestTemplate.getForEntity(
				"/api/v1/store/DEFAULT/signup/any-token", String.class);

		assertEquals(NOT_IMPLEMENTED, signup.getStatusCode());
		assertEquals(EXPECTED_BODY, signup.getBody());
		assertEquals(NOT_IMPLEMENTED, verify.getStatusCode());
		assertEquals(EXPECTED_BODY, verify.getBody());
	}

	@Test
	public void neighbouringStoreAndMarketplaceRoutesRemainAvailable() {
		ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity(
				"/api/v1/store/DEFAULT", ReadableMerchantStore.class);
		ResponseEntity<String> anonymousMarketplace = testRestTemplate.getForEntity(
				"/api/v1/private/marketplace/DEFAULT", String.class);
		ResponseEntity<String> adminMarketplace = testRestTemplate.exchange(
				"/api/v1/private/marketplace/DEFAULT", GET, new HttpEntity<>(getHeader()), String.class);

		assertEquals(OK, store.getStatusCode());
		assertNotNull(store.getBody());
		assertEquals("DEFAULT", store.getBody().getCode());
		assertEquals(UNAUTHORIZED, anonymousMarketplace.getStatusCode());
		assertEquals(OK, adminMarketplace.getStatusCode());
	}

	@Test
	public void swaggerDocumentsSignupRoutesWithoutSignupRequestBody() throws Exception {
		ResponseEntity<String> response = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(OK, response.getStatusCode());

		JsonNode paths = new ObjectMapper().readTree(response.getBody()).path("paths");
		JsonNode signupPost = paths.path("/api/v1/store/signup").path("post");
		JsonNode parameters = signupPost.path("parameters");
		JsonNode verifyGet = paths.path("/api/v1/store/{store}/signup/{token}").path("get");

		assertFalse(signupPost.isMissingNode());
		if (parameters.isArray()) {
			for (JsonNode parameter : parameters) {
				assertFalse("body".equals(parameter.path("in").asText()));
			}
		}
		assertFalse(verifyGet.isMissingNode());
	}

	private ResponseEntity<String> postSignup(String body, HttpHeaders headers) {
		headers.setContentType(MediaType.APPLICATION_JSON);
		return testRestTemplate.postForEntity(
				"/api/v1/store/signup", new HttpEntity<>(body, headers), String.class);
	}
}
