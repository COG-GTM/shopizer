package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableImage;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontAdminJourneyUatTest extends ServicesTestSupport {

	private static final String CUSTOMER_PASSWORD = "clear123";

	@Test
	public void adminLoginAndPrivateEndpointRequireValidToken() {
		String path = "/api/v1/private/users/1";
		HttpHeaders admin = getHeader();
		ResponseEntity<String> authenticated = testRestTemplate.exchange(
				path, org.springframework.http.HttpMethod.GET,
				new HttpEntity<>(admin), String.class);
		assertEquals(OK, authenticated.getStatusCode());

		ResponseEntity<String> anonymous = testRestTemplate.getForEntity(
				path, String.class);
		assertEquals(UNAUTHORIZED, anonymous.getStatusCode());

		HttpHeaders badToken = new HttpHeaders();
		badToken.setBearerAuth("invalid-token");
		ResponseEntity<String> invalid = testRestTemplate.exchange(
				path, org.springframework.http.HttpMethod.GET,
				new HttpEntity<>(badToken), String.class);
		assertFalse(path + " returned " + invalid.getStatusCode(),
				invalid.getStatusCode().is2xxSuccessful());
	}

	@Test
	public void registeredCustomerCanLoginAndReadAuthenticatedProfile() {
		String email = registerCustomer();
		HttpHeaders customer = customerHeaders(email);

		ResponseEntity<String> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile?store=" + Constants.DEFAULT_STORE,
				org.springframework.http.HttpMethod.GET, new HttpEntity<>(customer), String.class);
		assertEquals(OK, profile.getStatusCode());
		assertTrue(profile.getBody().contains(email));
	}

	@Test
	public void storefrontCanBrowseStoreCategoriesProductsAndProductDetail() {
		ReadableProduct sample = sampleProduct(unique("uat-browse-"));
		ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity(
				"/api/v1/store/" + Constants.DEFAULT_STORE, ReadableMerchantStore.class);
		assertEquals(OK, store.getStatusCode());
		assertEquals(Constants.DEFAULT_STORE, store.getBody().getCode());

		ResponseEntity<String> categories = testRestTemplate.getForEntity(
				"/api/v1/category?store=" + Constants.DEFAULT_STORE, String.class);
		assertEquals(OK, categories.getStatusCode());

		ResponseEntity<ReadableProductList> products = testRestTemplate.getForEntity(
				"/api/v1/products?store=" + Constants.DEFAULT_STORE, ReadableProductList.class);
		assertEquals(OK, products.getStatusCode());
		assertNotNull(products.getBody());
		assertFalse(products.getBody().getProducts().isEmpty());

		Long productId = sample.getId();
		assertTrue(products.getBody().getProducts().stream()
				.anyMatch(product -> productId.equals(product.getId())));
		ResponseEntity<ReadableProduct> detail = testRestTemplate.getForEntity(
				"/api/v2/product/" + sample.getSku() + "?store=" + Constants.DEFAULT_STORE,
				ReadableProduct.class);
		assertEquals(OK, detail.getStatusCode());
		assertEquals(productId, detail.getBody().getId());
	}

	@Test
	public void cartCanBeCreatedUpdatedAndRead() {
		ReadableProduct product = sampleProduct(unique("uat-cart-"));
		PersistableShoppingCartItem item = cartItem(product.getSku(), 1);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity(
				"/api/v1/cart?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(item, jsonHeaders()), ReadableShoppingCart.class);
		assertEquals(CREATED, created.getStatusCode());
		assertNotNull(created.getBody());

		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
				"/api/v1/cart/" + created.getBody().getCode() + "?store=" + Constants.DEFAULT_STORE,
				PUT, new HttpEntity<>(cartItem(product.getSku(), 2), jsonHeaders()),
				ReadableShoppingCart.class);
		assertEquals(CREATED, updated.getStatusCode());
		assertEquals(2, updated.getBody().getQuantity());

		ResponseEntity<ReadableShoppingCart> read = testRestTemplate.getForEntity(
				"/api/v1/cart/" + created.getBody().getCode()
						+ "?store=" + Constants.DEFAULT_STORE,
				ReadableShoppingCart.class);
		assertEquals(OK, read.getStatusCode());
		assertEquals(2, read.getBody().getQuantity());
	}

	@Test
	public void servicesEndpointAcceptsAdminBasicAuthAndRejectsWrongPassword() {
		HttpHeaders validBasicAuth = new HttpHeaders();
		validBasicAuth.setBasicAuth("admin@shopizer.com", "password");
		validBasicAuth.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> valid = testRestTemplate.exchange(
				"/services/private/system/optin", POST,
				new HttpEntity<>("\"journey\"", validBasicAuth), String.class);
		assertEquals(OK, valid.getStatusCode());

		HttpHeaders invalidBasicAuth = new HttpHeaders();
		invalidBasicAuth.setBasicAuth("admin@shopizer.com", "wrong-password");
		invalidBasicAuth.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> invalid = testRestTemplate.exchange(
				"/services/private/system/optin", POST,
				new HttpEntity<>("\"journey\"", invalidBasicAuth), String.class);
		assertEquals(UNAUTHORIZED, invalid.getStatusCode());
	}

	@Test
	public void actuatorAndOpenApiExposeHealthAndPrivateImageContract() {
		ResponseEntity<JsonNode> health = testRestTemplate.getForEntity(
				"/actuator/health", JsonNode.class);
		assertEquals(OK, health.getStatusCode());
		assertEquals("UP", health.getBody().path("status").asText());

		ResponseEntity<String> apiDocs = testRestTemplate.getForEntity(
				"/v2/api-docs", String.class);
		assertEquals(OK, apiDocs.getStatusCode());
		assertTrue(apiDocs.getBody().contains("/api/v1/private/product/{id}/image"));
		assertFalse(apiDocs.getBody().contains("/api/v1/auth/product/{id}/image"));
	}

	@Test
	public void adminProductImageJourneyAppearsInStorefrontAndProductDetail() {
		ReadableProduct product = sampleProduct(unique("uat-image-"));
		HttpHeaders admin = getHeader();
		LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
		form.add("file", new ClassPathResource("image.jpg"));
		HttpHeaders multipartHeaders = new HttpHeaders();
		multipartHeaders.putAll(admin);
		multipartHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
		ResponseEntity<Void> upload = testRestTemplate.exchange(
				"/api/v1/private/product/" + product.getId() + "/image?store="
						+ Constants.DEFAULT_STORE,
				POST, new HttpEntity<>(form, multipartHeaders), Void.class);
		assertEquals(CREATED, upload.getStatusCode());

		ResponseEntity<ReadableImage[]> images = testRestTemplate.getForEntity(
				"/api/v1/product/" + product.getId() + "/images?store="
						+ Constants.DEFAULT_STORE,
				ReadableImage[].class);
		assertEquals(OK, images.getStatusCode());
		assertEquals(1, images.getBody().length);
		assertNotNull(images.getBody()[0].getId());
		assertNotNull(images.getBody()[0].getImageUrl());
		assertEquals(0, images.getBody()[0].getOrder());

		ResponseEntity<ReadableProduct> detail = testRestTemplate.getForEntity(
				"/api/v2/product/" + product.getSku() + "?store=" + Constants.DEFAULT_STORE,
				ReadableProduct.class);
		assertEquals(OK, detail.getStatusCode());
		assertFalse(detail.getBody().getImages().isEmpty());
		assertEquals(images.getBody()[0].getId(), detail.getBody().getImages().get(0).getId());
	}

	private String registerCustomer() {
		String email = unique("uat-customer-") + "@test.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword(CUSTOMER_PASSWORD);
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);
		Address delivery = new Address();
		delivery.setFirstName("UAT");
		delivery.setLastName("Customer");
		delivery.setCountry("BE");
		customer.setDelivery(delivery);
		ResponseEntity<PersistableCustomer> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register",
				new HttpEntity<>(customer, jsonHeaders()), PersistableCustomer.class);
		assertEquals(OK, registration.getStatusCode());
		return email;
	}

	private HttpHeaders customerHeaders(String email) {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, CUSTOMER_PASSWORD), jsonHeaders()),
				AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody());
		HttpHeaders headers = jsonHeaders();
		headers.setBearerAuth(login.getBody().getToken());
		return headers;
	}

	private HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private PersistableShoppingCartItem cartItem(String sku, int quantity) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(quantity);
		return item;
	}

	private String unique(String prefix) {
		return prefix + UUID.randomUUID().toString().replace("-", "");
	}
}
