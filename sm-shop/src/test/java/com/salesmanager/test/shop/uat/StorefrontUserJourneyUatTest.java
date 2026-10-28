package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.util.UUID;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.PersistableProductReview;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.populator.customer.ReadableCustomerList;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontUserJourneyUatTest extends ServicesTestSupport {

	@Test
	public void storefrontJourneyCoversCustomerCommerceAndPublicReview() throws Exception {
		ResponseEntity<AuthenticationResponse> adminLogin = testRestTemplate.postForEntity(
				"/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(OK, adminLogin.getStatusCode());
		assertNotNull(adminLogin.getBody());
		HttpHeaders adminHeaders = bearerHeaders(adminLogin.getBody().getToken());

		ResponseEntity<String> privateCustomers = testRestTemplate.exchange(
				"/api/v1/private/customers?store=" + Constants.DEFAULT_STORE,
				HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);
		assertEquals(OK, privateCustomers.getStatusCode());

		ResponseEntity<String> withoutToken = testRestTemplate.exchange(
				"/api/v1/private/customers?store=" + Constants.DEFAULT_STORE,
				HttpMethod.GET, HttpEntity.EMPTY, String.class);
		assertUnauthorizedOrForbidden(withoutToken.getStatusCode());

		String suffix = UUID.randomUUID().toString().replace("-", "");
		String email = "journey-" + suffix + "@example.test";
		String lastName = "Customer" + suffix;
		String phone = "555-" + suffix.substring(0, 7);
		String street = "Journey Street " + suffix;
		String company = "Journey Company " + suffix;
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("Journey");
		billing.setLastName(lastName);
		billing.setPhone(phone);
		billing.setCompany(company);
		billing.setAddress(street);
		billing.setCity("Journey City");
		billing.setPostalCode("12345");
		billing.setCountry("BE");
		billing.setCountryCode("BE");
		customer.setBilling(billing);
		ResponseEntity<PersistableCustomer> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(customer, adminHeaders), PersistableCustomer.class);
		assertEquals(OK, registration.getStatusCode());

		ResponseEntity<AuthenticationResponse> customerLogin = testRestTemplate.postForEntity(
				"/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")),
				AuthenticationResponse.class);
		assertEquals(OK, customerLogin.getStatusCode());
		assertNotNull(customerLogin.getBody());
		HttpHeaders customerHeaders = bearerHeaders(customerLogin.getBody().getToken());

		ResponseEntity<String> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile?store=" + Constants.DEFAULT_STORE,
				HttpMethod.GET, new HttpEntity<>(customerHeaders), String.class);
		assertEquals(OK, profile.getStatusCode());

		ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity(
				"/api/v1/store/" + Constants.DEFAULT_STORE + "?lang=en", ReadableMerchantStore.class);
		assertEquals(OK, store.getStatusCode());
		assertNotNull(store.getBody());
		ResponseEntity<String> categories = testRestTemplate.getForEntity(
				"/api/v1/category?store=" + Constants.DEFAULT_STORE + "&lang=en", String.class);
		assertEquals(OK, categories.getStatusCode());
		ResponseEntity<String> products = testRestTemplate.getForEntity(
				"/api/v1/products?store=" + Constants.DEFAULT_STORE + "&lang=en", String.class);
		assertEquals(OK, products.getStatusCode());

		ReadableProduct product = sampleProduct("uat-" + suffix);
		ResponseEntity<ReadableProduct> productDetail = testRestTemplate.getForEntity(
				"/api/v2/product/" + product.getSku() + "?store=" + Constants.DEFAULT_STORE + "&lang=en",
				ReadableProduct.class);
		assertEquals(OK, productDetail.getStatusCode());

		PersistableShoppingCartItem cartItem = new PersistableShoppingCartItem();
		cartItem.setProduct(product.getSku());
		cartItem.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> createdCart = testRestTemplate.postForEntity(
				"/api/v1/cart?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(cartItem), ReadableShoppingCart.class);
		assertEquals(CREATED, createdCart.getStatusCode());
		assertNotNull(createdCart.getBody());
		assertNotNull(createdCart.getBody().getCode());

		cartItem.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> updatedCart = testRestTemplate.exchange(
				"/api/v1/cart/" + createdCart.getBody().getCode() + "?store=" + Constants.DEFAULT_STORE,
				HttpMethod.PUT, new HttpEntity<>(cartItem), ReadableShoppingCart.class);
		assertEquals(CREATED, updatedCart.getStatusCode());
		ResponseEntity<ReadableShoppingCart> cart = testRestTemplate.getForEntity(
				"/api/v1/cart/" + createdCart.getBody().getCode() + "?store=" + Constants.DEFAULT_STORE,
				ReadableShoppingCart.class);
		assertEquals(OK, cart.getStatusCode());
		assertNotNull(cart.getBody());
		assertEquals(2, cart.getBody().getQuantity());

		HttpHeaders validBasic = new HttpHeaders();
		validBasic.setBasicAuth("admin@shopizer.com", "password");
		ResponseEntity<String> serviceAccess = testRestTemplate.postForEntity(
				"/services/private/system/optin",
				new HttpEntity<>("{\"email\":\"" + email + "\"}", validBasic), String.class);
		assertEquals(OK, serviceAccess.getStatusCode());
		HttpHeaders invalidBasic = new HttpHeaders();
		invalidBasic.setBasicAuth("admin@shopizer.com", "invalid-password");
		ResponseEntity<String> rejectedServiceAccess = testRestTemplate.postForEntity(
				"/services/private/system/optin",
				new HttpEntity<>("{\"email\":\"" + email + "\"}", invalidBasic), String.class);
		assertUnauthorizedOrForbidden(rejectedServiceAccess.getStatusCode());

		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertEquals(OK, health.getStatusCode());
		assertTrue(health.getBody().contains("\"status\":\"UP\""));
		ResponseEntity<String> apiDocumentation = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(OK, apiDocumentation.getStatusCode());

		ReadableCustomerList customers = fetchCustomers();
		Long customerId = customers.getCustomers().stream()
				.filter(found -> email.equals(found.getEmailAddress()))
				.map(found -> found.getId())
				.findFirst()
				.orElse(null);
		assertNotNull(customerId);
		PersistableProductReview review = new PersistableProductReview();
		review.setCustomerId(customerId);
		review.setProductId(product.getId());
		review.setRating(4.0);
		review.setDescription("A storefront journey review");
		review.setLanguage("en");
		ResponseEntity<PersistableProductReview> createdReview = testRestTemplate.postForEntity(
				"/api/v1/auth/products/" + product.getId() + "/reviews?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(review, customerHeaders), PersistableProductReview.class);
		assertEquals(CREATED, createdReview.getStatusCode());
		ResponseEntity<String> publicReviews = testRestTemplate.getForEntity(
				"/api/v1/product/" + product.getId() + "/reviews?store=" + Constants.DEFAULT_STORE,
				String.class);
		assertEquals(OK, publicReviews.getStatusCode());
		JsonNode reviewer = new ObjectMapper().readTree(publicReviews.getBody()).get(0).get("customer");
		assertEquals(customerId.longValue(), reviewer.get("id").asLong());
		assertEquals("Journey", reviewer.get("firstName").asText());
		assertEquals(lastName.substring(0, 1) + ".", reviewer.get("lastName").asText());
		assertTrue(!reviewer.has("emailAddress") || reviewer.get("emailAddress").isNull());
		assertTrue(!reviewer.has("billing") || reviewer.get("billing").isNull());
		assertTrue(!reviewer.has("delivery") || reviewer.get("delivery").isNull());
		String reviewJson = publicReviews.getBody();
		assertFalse(reviewJson.contains(email));
		assertFalse(reviewJson.contains(lastName));
		assertFalse(reviewJson.contains(phone));
		assertFalse(reviewJson.contains(street));
		assertFalse(reviewJson.contains(company));
	}

	private HttpHeaders bearerHeaders(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return headers;
	}

	private void assertUnauthorizedOrForbidden(HttpStatus status) {
		assertTrue("Unexpected authorization status: " + status,
				status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN);
	}
}
