package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;

import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableImage;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontAdminJourneyUatTest extends ServicesTestSupport {

	@Inject
	private TestRestTemplate testRestTemplate;

	@Test
	public void adminCustomerStorefrontAndImageAuthorizationJourney() {
		HttpHeaders adminHeaders = login("admin@shopizer.com", "password");
		ResponseEntity<String> adminCustomers = testRestTemplate.exchange("/api/v1/private/customers",
				HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);
		ResponseEntity<String> noTokenCustomers = testRestTemplate.exchange("/api/v1/private/customers",
				HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);
		HttpHeaders invalidHeaders = bearerHeaders("garbage");
		ResponseEntity<String> invalidTokenCustomers = testRestTemplate.exchange("/api/v1/private/customers",
				HttpMethod.GET, new HttpEntity<>(invalidHeaders), String.class);
		assertEquals(HttpStatus.OK, adminCustomers.getStatusCode());
		assertEquals("Anonymous private customer response: " + noTokenCustomers.getBody(),
				HttpStatus.UNAUTHORIZED, noTokenCustomers.getStatusCode());
		assertFalse("A garbage bearer token must not authenticate",
				invalidTokenCustomers.getStatusCode().is2xxSuccessful());

		String customerEmail = "journey-" + System.nanoTime() + "@example.test";
		String customerToken = registerCustomer(customerEmail);
		HttpHeaders customerHeaders = bearerHeaders(customerToken);
		ResponseEntity<String> customerProfile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile", HttpMethod.GET,
				new HttpEntity<>(customerHeaders), String.class);
		ResponseEntity<String> unauthenticatedProfile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile", HttpMethod.GET,
				new HttpEntity<>(new HttpHeaders()), String.class);
		assertEquals(HttpStatus.OK, customerProfile.getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, unauthenticatedProfile.getStatusCode());

		ReadableProduct product = sampleProduct("journey-" + System.nanoTime());
		ReadableProduct secondProduct = sampleProduct("journey-cart-" + System.nanoTime());
		ResponseEntity<String> store = testRestTemplate.getForEntity("/api/v1/store/DEFAULT", String.class);
		ResponseEntity<String> categories = testRestTemplate.getForEntity(
				"/api/v1/category?store=DEFAULT", String.class);
		ResponseEntity<String> products = testRestTemplate.getForEntity(
				"/api/v1/products?store=DEFAULT", String.class);
		ResponseEntity<String> productDetail = testRestTemplate.getForEntity(
				"/api/v2/product/" + product.getSku(), String.class);
		assertEquals(HttpStatus.OK, store.getStatusCode());
		assertNotNull(store.getBody());
		assertEquals(HttpStatus.OK, categories.getStatusCode());
		assertNotNull(categories.getBody());
		assertEquals(HttpStatus.OK, products.getStatusCode());
		assertTrue(products.getBody().contains(product.getSku()));
		assertEquals(HttpStatus.OK, productDetail.getStatusCode());
		assertTrue(productDetail.getBody().contains(product.getSku()));

		PersistableShoppingCartItem cartItem = cartItem(product.getSku(), 1);
		ResponseEntity<ReadableShoppingCart> createdCart = testRestTemplate.postForEntity(
				"/api/v1/cart", new HttpEntity<>(cartItem), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, createdCart.getStatusCode());
		assertNotNull(createdCart.getBody());
		String cartCode = createdCart.getBody().getCode();
		PersistableShoppingCartItem secondCartItem = cartItem(secondProduct.getSku(), 1);
		ResponseEntity<ReadableShoppingCart> updatedCart = testRestTemplate.exchange(
				"/api/v1/cart/" + cartCode, HttpMethod.PUT,
				new HttpEntity<>(secondCartItem), ReadableShoppingCart.class);
		ResponseEntity<ReadableShoppingCart> readCart = testRestTemplate.getForEntity(
				"/api/v1/cart/" + cartCode, ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, updatedCart.getStatusCode());
		assertEquals(HttpStatus.OK, readCart.getStatusCode());
		assertEquals(2, readCart.getBody().getQuantity());
		assertEquals(2, readCart.getBody().getProducts().size());

		ResponseEntity<ReadableOrderConfirmation> checkout = testRestTemplate.exchange(
				"/api/v1/auth/cart/" + cartCode + "/checkout", HttpMethod.POST,
				new HttpEntity<>(new PersistableOrder(), customerHeaders), ReadableOrderConfirmation.class);
		if (checkout.getStatusCode() == HttpStatus.OK && checkout.getBody() != null
				&& checkout.getBody().getId() != null) {
			ResponseEntity<String> order = testRestTemplate.exchange(
					"/api/v1/auth/orders/" + checkout.getBody().getId(), HttpMethod.GET,
					new HttpEntity<>(customerHeaders), String.class);
			assertEquals(HttpStatus.OK, order.getStatusCode());
			System.out.println("Checkout journey completed with order " + checkout.getBody().getId());
		} else {
			System.out.println("Checkout journey unavailable: status=" + checkout.getStatusCodeValue()
					+ " body=" + checkout.getBody());
		}

		uploadImage(product.getId(), adminHeaders);
		ReadableImage[] images = testRestTemplate.getForObject(
				"/api/v1/product/" + product.getId() + "/images?store=DEFAULT", ReadableImage[].class);
		assertNotNull(images);
		assertEquals(1, images.length);
		HttpHeaders customerMultipartHeaders = bearerHeaders(customerToken);
		customerMultipartHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
		LinkedMultiValueMap<String, Object> file = multipartFile();
		ResponseEntity<Void> aliasUpload = testRestTemplate.exchange(
				"/api/v1/auth/product/" + product.getId() + "/image", HttpMethod.POST,
				new HttpEntity<>(file, customerMultipartHeaders), Void.class);
		ResponseEntity<Void> aliasDelete = testRestTemplate.exchange(
				"/api/v1/auth/product/images/" + images[0].getId(), HttpMethod.DELETE,
				new HttpEntity<>(customerHeaders), Void.class);
		ResponseEntity<Void> aliasPatch = testRestTemplate.exchange(
				"/api/v1/auth/product/" + product.getId() + "/image/" + images[0].getId() + "?order=5",
				HttpMethod.PATCH, new HttpEntity<>(customerHeaders), Void.class);
		assertEquals(HttpStatus.NOT_FOUND, aliasUpload.getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND, aliasDelete.getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND, aliasPatch.getStatusCode());
		assertEquals(1, testRestTemplate.getForObject(
				"/api/v1/product/" + product.getId() + "/images?store=DEFAULT", ReadableImage[].class).length);

		ResponseEntity<Void> patch = testRestTemplate.exchange(
				"/api/v1/private/product/" + product.getId() + "/image/" + images[0].getId()
						+ "?order=3&store=DEFAULT",
				HttpMethod.PATCH, new HttpEntity<>(adminHeaders), Void.class);
		assertEquals(HttpStatus.OK, patch.getStatusCode());
		ReadableImage[] patched = testRestTemplate.getForObject(
				"/api/v1/product/" + product.getId() + "/images?store=DEFAULT", ReadableImage[].class);
		assertTrue(Arrays.stream(patched).anyMatch(image -> image.getId().equals(images[0].getId())
				&& image.getOrder() == 3));
		ResponseEntity<Void> imageDelete = testRestTemplate.exchange(
				"/api/v1/private/product/image/" + images[0].getId(), HttpMethod.DELETE,
				new HttpEntity<>(adminHeaders), Void.class);
		assertEquals(HttpStatus.OK, imageDelete.getStatusCode());
		assertEquals(0, testRestTemplate.getForObject(
				"/api/v1/product/" + product.getId() + "/images?store=DEFAULT", ReadableImage[].class).length);

		HttpHeaders validBasicAuth = new HttpHeaders();
		validBasicAuth.setBasicAuth("admin@shopizer.com", "password");
		HttpHeaders invalidBasicAuth = new HttpHeaders();
		invalidBasicAuth.setBasicAuth("admin@shopizer.com", "not-the-password");
		ResponseEntity<String> servicesValid = testRestTemplate.exchange("/services/private/system/optin",
				HttpMethod.POST, new HttpEntity<>("\"uat\"", validBasicAuth), String.class);
		ResponseEntity<String> servicesInvalid = testRestTemplate.exchange("/services/private/system/optin",
				HttpMethod.POST, new HttpEntity<>("\"uat\"", invalidBasicAuth), String.class);
		assertTrue(servicesValid.getStatusCode() != HttpStatus.UNAUTHORIZED
				&& servicesValid.getStatusCode() != HttpStatus.FORBIDDEN);
		assertEquals(HttpStatus.UNAUTHORIZED, servicesInvalid.getStatusCode());

		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertTrue(health.getBody().contains("\"status\":\"UP\""));
		ResponseEntity<String> apiDocs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(HttpStatus.OK, apiDocs.getStatusCode());
		assertTrue(apiDocs.getBody().contains("\"swagger\":\"2.0\""));
		assertTrue(apiDocs.getBody().contains("/api/v1/private/product/image/{id}"));
		assertTrue(!apiDocs.getBody().contains("/api/v1/auth/product/{id}/image"));
		System.out.println("Journey auth/statuses admin=" + adminCustomers.getStatusCodeValue()
				+ " no-token=" + noTokenCustomers.getStatusCodeValue()
				+ " bad-token=" + invalidTokenCustomers.getStatusCodeValue()
				+ " customer-profile=" + customerProfile.getStatusCodeValue()
				+ " anonymous-profile=" + unauthenticatedProfile.getStatusCodeValue()
				+ " services-valid/invalid=" + servicesValid.getStatusCodeValue() + "/"
				+ servicesInvalid.getStatusCodeValue());
	}

	private HttpHeaders login(String email, String password) {
		ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity(
				"/api/v1/private/login", new HttpEntity<>(new AuthenticationRequest(email, password)),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		return bearerHeaders(response.getBody().getToken());
	}

	private String registerCustomer(String email) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode("DEFAULT");
		Address billing = new Address();
		billing.setFirstName("Storefront");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);
		ResponseEntity<PersistableCustomer> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer), PersistableCustomer.class);
		assertEquals(HttpStatus.OK, registration.getStatusCode());
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")), AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		return login.getBody().getToken();
	}

	private PersistableShoppingCartItem cartItem(String sku, int quantity) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(quantity);
		return item;
	}

	private void uploadImage(Long productId, HttpHeaders headers) {
		HttpHeaders multipartHeaders = new HttpHeaders();
		multipartHeaders.putAll(headers);
		multipartHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
		ResponseEntity<Void> response = testRestTemplate.exchange(
				"/api/v1/private/product/" + productId + "/image?store=DEFAULT",
				HttpMethod.POST, new HttpEntity<>(multipartFile(), multipartHeaders), Void.class);
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
	}

	private LinkedMultiValueMap<String, Object> multipartFile() {
		LinkedMultiValueMap<String, Object> file = new LinkedMultiValueMap<>();
		file.add("file", new ClassPathResource("image.jpg"));
		return file;
	}

	private HttpHeaders bearerHeaders(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}
}
