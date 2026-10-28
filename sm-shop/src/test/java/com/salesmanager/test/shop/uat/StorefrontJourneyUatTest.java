package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.category.ReadableCategoryList;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.store.api.exception.ErrorEntity;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public class StorefrontJourneyUatTest extends ServicesTestSupport {

	@Autowired
	private TestRestTemplate testRestTemplate;

	@Test
	public void storefrontJourney() {
		ResponseEntity<AuthenticationResponse> adminLogin = testRestTemplate.postForEntity(
				"/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(OK, adminLogin.getStatusCode());
		assertNotNull(adminLogin.getBody());
		String adminToken = adminLogin.getBody().getToken();

		ResponseEntity<String> adminCustomers = testRestTemplate.exchange("/api/v1/private/customers",
				GET, new HttpEntity<>(bearerHeaders(adminToken)), String.class);
		assertEquals(OK, adminCustomers.getStatusCode());
		ResponseEntity<String> noTokenCustomers = testRestTemplate.getForEntity(
				"/api/v1/private/customers", String.class);
		ResponseEntity<String> invalidTokenCustomers = testRestTemplate.exchange(
				"/api/v1/private/customers", GET, new HttpEntity<>(bearerHeaders("garbage")), String.class);
		assertTrue(isUnauthorized(noTokenCustomers.getStatusCode().value()));
		assertTrue(isUnauthorized(invalidTokenCustomers.getStatusCode().value())
				|| invalidTokenCustomers.getStatusCode().value() == 500);
		System.out.println("UAT admin customer access: no-token="
				+ noTokenCustomers.getStatusCode().value() + ", invalid-token="
				+ invalidTokenCustomers.getStatusCode().value());

		PersistableCustomer customer = newCustomer("uat-" + System.currentTimeMillis() + "@test.com");
		ResponseEntity<String> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer, getHeader()), String.class);
		assertTrue(registration.getStatusCode().is2xxSuccessful());
		ResponseEntity<AuthenticationResponse> customerLogin = testRestTemplate.postForEntity(
				"/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(customer.getEmailAddress(), customer.getPassword())),
				AuthenticationResponse.class);
		assertEquals(OK, customerLogin.getStatusCode());
		assertNotNull(customerLogin.getBody());
		String customerToken = customerLogin.getBody().getToken();
		ResponseEntity<String> customerProfile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile", GET, new HttpEntity<>(bearerHeaders(customerToken)),
				String.class);
		assertEquals(OK, customerProfile.getStatusCode());

		ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity(
				"/api/v1/store/DEFAULT", ReadableMerchantStore.class);
		assertEquals(OK, store.getStatusCode());
		assertNotNull(store.getBody());
		assertEquals("DEFAULT", store.getBody().getCode());
		ResponseEntity<ReadableCategoryList> categories = testRestTemplate.getForEntity(
				"/api/v1/category?store=DEFAULT", ReadableCategoryList.class);
		assertEquals(OK, categories.getStatusCode());
		assertNotNull(categories.getBody());

		ReadableProduct product = sampleProduct("uatStorefrontJourneyProduct");
		ReadableProduct secondProduct = sampleProduct("uatStorefrontJourneySecondProduct");
		ResponseEntity<ReadableProductList> productList = testRestTemplate.getForEntity(
				"/api/v1/products?store=DEFAULT&sku={sku}", ReadableProductList.class, product.getSku());
		assertEquals(OK, productList.getStatusCode());
		assertNotNull(productList.getBody());
		assertTrue(productList.getBody().getProducts().stream()
				.anyMatch(listedProduct -> product.getSku().equals(listedProduct.getSku())));
		ResponseEntity<ReadableProduct> productDetail = testRestTemplate.getForEntity(
				"/api/v2/product/{code}", ReadableProduct.class, product.getSku());
		assertEquals(OK, productDetail.getStatusCode());
		assertNotNull(productDetail.getBody());
		assertEquals(product.getSku(), productDetail.getBody().getSku());

		ResponseEntity<ReadableShoppingCart> createdCart = testRestTemplate.postForEntity(
				"/api/v1/cart", new HttpEntity<>(cartItem(product.getSku(), 1, null), getHeader()),
				ReadableShoppingCart.class);
		assertEquals(CREATED, createdCart.getStatusCode());
		assertNotNull(createdCart.getBody());
		String cartCode = createdCart.getBody().getCode();

		ResponseEntity<ReadableShoppingCart> addedSecondProduct = testRestTemplate.exchange(
				"/api/v1/cart/{code}", PUT,
				new HttpEntity<>(cartItem(secondProduct.getSku(), 1, null), getHeader()),
				ReadableShoppingCart.class, cartCode);
		assertTrue(addedSecondProduct.getStatusCode().is2xxSuccessful());
		ResponseEntity<ReadableShoppingCart> updatedQuantity = testRestTemplate.exchange(
				"/api/v1/cart/{code}", PUT,
				new HttpEntity<>(cartItem(product.getSku(), 2, null), getHeader()),
				ReadableShoppingCart.class, cartCode);
		assertTrue(updatedQuantity.getStatusCode().is2xxSuccessful());
		ResponseEntity<ReadableShoppingCart> readCart = testRestTemplate.exchange(
				"/api/v1/cart/{code}", GET, new HttpEntity<>(getHeader()),
				ReadableShoppingCart.class, cartCode);
		assertEquals(OK, readCart.getStatusCode());
		assertNotNull(readCart.getBody());
		assertEquals(3, readCart.getBody().getQuantity());

		ResponseEntity<ReadableShoppingCart> validPromo = testRestTemplate.exchange(
				"/api/v1/cart/{code}/promo/{promo}", POST, new HttpEntity<>(getHeader()),
				ReadableShoppingCart.class, cartCode, "Test1234");
		assertEquals(CREATED, validPromo.getStatusCode());
		ResponseEntity<ErrorEntity> invalidPromo = testRestTemplate.exchange(
				"/api/v1/cart/{code}/promo/{promo}", POST, new HttpEntity<>(getHeader()),
				ErrorEntity.class, cartCode, "bad$code");
		assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, invalidPromo.getStatusCode());
		assertNotNull(invalidPromo.getBody());
		assertEquals("400", invalidPromo.getBody().getErrorCode());
		assertEquals("Invalid promo code", invalidPromo.getBody().getMessage());

		HttpHeaders validBasicAuth = new HttpHeaders();
		validBasicAuth.setBasicAuth("admin@shopizer.com", "password");
		HttpHeaders invalidBasicAuth = new HttpHeaders();
		invalidBasicAuth.setBasicAuth("admin@shopizer.com", "invalid");
		ResponseEntity<String> basicAuthSuccess = testRestTemplate.exchange(
				"/services/private/system/optin", POST,
				new HttpEntity<>("{}", validBasicAuth), String.class);
		ResponseEntity<String> basicAuthFailure = testRestTemplate.exchange(
				"/services/private/system/optin", POST,
				new HttpEntity<>("{}", invalidBasicAuth), String.class);
		assertEquals(OK, basicAuthSuccess.getStatusCode());
		assertTrue(isUnauthorized(basicAuthFailure.getStatusCode().value()));
		System.out.println("UAT /services basic auth: valid="
				+ basicAuthSuccess.getStatusCode().value() + ", invalid="
				+ basicAuthFailure.getStatusCode().value());

		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertEquals(OK, health.getStatusCode());
		assertNotNull(health.getBody());
		assertTrue(health.getBody().contains("\"status\":\"UP\""));
		assertEquals(OK, testRestTemplate.getForEntity("/v2/api-docs", String.class).getStatusCode());
	}

	private static PersistableCustomer newCustomer(String email) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);
		return customer;
	}

	private static HttpHeaders bearerHeaders(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return headers;
	}

	private static boolean isUnauthorized(int status) {
		return status == 401 || status == 403;
	}

	private static PersistableShoppingCartItem cartItem(String sku, int quantity, String promoCode) {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(sku);
		item.setQuantity(quantity);
		item.setPromoCode(promoCode);
		return item;
	}
}
