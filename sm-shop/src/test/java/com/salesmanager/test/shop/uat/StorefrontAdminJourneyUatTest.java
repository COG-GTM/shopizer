package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.category.Category;
import com.salesmanager.shop.model.catalog.category.PersistableCategory;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.ProductSpecification;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.customer.address.AddressLocation;
import com.salesmanager.shop.model.order.shipping.ReadableShippingSummary;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.shipping.ExpeditionConfiguration;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public class StorefrontAdminJourneyUatTest extends ServicesTestSupport {

	private static final String ADMIN_EMAIL = "admin@shopizer.com";
	private static final String ADMIN_PASSWORD = "password";
	private static final String CUSTOMER_PASSWORD = "UatPass123!";

	@Autowired
	private TestRestTemplate testRestTemplate;

	@Test
	public void exercisesAdminCustomerStoreCartShippingAndServiceJourneys() {
		String adminToken = loginAdmin();
		assertPrivateApiAuthorization(adminToken);

		String email = "uat-" + System.nanoTime() + "@example.com";
		PersistableCustomer customer = customer(email);
		ResponseEntity<AuthenticationResponse> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register", customer, AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, registration.getStatusCode());
		assertNotNull(registration.getBody());
		assertNotNull(registration.getBody().getToken());

		ResponseEntity<AuthenticationResponse> customerLogin = testRestTemplate.postForEntity(
				"/api/v1/customer/login",
				new AuthenticationRequest(email, CUSTOMER_PASSWORD),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, customerLogin.getStatusCode());
		assertNotNull(customerLogin.getBody());
		String customerToken = customerLogin.getBody().getToken();
		assertNotNull(customerToken);

		ResponseEntity<String> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile",
				HttpMethod.GET,
				new HttpEntity<Void>(bearerHeaders(customerToken)),
				String.class);
		assertEquals(HttpStatus.OK, profile.getStatusCode());

		assertEquals(HttpStatus.OK,
				testRestTemplate.getForEntity("/api/v1/store/DEFAULT", String.class).getStatusCode());
		assertEquals(HttpStatus.OK,
				testRestTemplate.getForEntity("/api/v1/category", String.class).getStatusCode());
		ReadableProduct product = sampleShippableProduct("uatProduct" + System.nanoTime());
		ResponseEntity<ReadableProductList> productList = testRestTemplate.getForEntity(
				"/api/v1/products?count=100", ReadableProductList.class);
		assertEquals(HttpStatus.OK, productList.getStatusCode());
		assertNotNull(productList.getBody());
		assertTrue(productList.getBody().getProducts().stream()
				.anyMatch(listedProduct -> product.getSku().equals(listedProduct.getSku())));
		ResponseEntity<ReadableProduct> productDetail = testRestTemplate.getForEntity(
				"/api/v2/product/" + product.getSku(), ReadableProduct.class);
		assertEquals(HttpStatus.OK, productDetail.getStatusCode());
		assertNotNull(productDetail.getBody());

		HttpHeaders customerHeaders = bearerHeaders(customerToken);
		PersistableShoppingCartItem cartItem = new PersistableShoppingCartItem();
		cartItem.setProduct(product.getSku());
		cartItem.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> cartCreate = testRestTemplate.postForEntity(
				"/api/v1/cart",
				new HttpEntity<PersistableShoppingCartItem>(cartItem, customerHeaders),
				ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, cartCreate.getStatusCode());
		assertNotNull(cartCreate.getBody());
		String cartCode = cartCreate.getBody().getCode();

		cartItem.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> cartUpdate = testRestTemplate.exchange(
				"/api/v1/cart/" + cartCode + "/multi",
				HttpMethod.POST,
				new HttpEntity<PersistableShoppingCartItem[]>(new PersistableShoppingCartItem[] { cartItem },
						customerHeaders),
				ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, cartUpdate.getStatusCode());
		assertEquals(2, cartUpdate.getBody().getQuantity());
		ResponseEntity<ReadableShoppingCart> cartRead = testRestTemplate.exchange(
				"/api/v1/cart/" + cartCode,
				HttpMethod.GET,
				new HttpEntity<Void>(customerHeaders),
				ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, cartRead.getStatusCode());
		assertEquals(2, cartRead.getBody().getQuantity());

		configureInternationalShipping(adminToken);
		boolean customShippingEnabled = configureCustomShippingModule(adminToken);
		AddressLocation shipTo = new AddressLocation();
		shipTo.setCountryCode("CA");
		shipTo.setPostalCode("H3Z 2Y7");
		ResponseEntity<ReadableShippingSummary> shipping = testRestTemplate.postForEntity(
				"/api/v1/cart/" + cartCode + "/shipping", shipTo, ReadableShippingSummary.class);
		assertEquals(HttpStatus.OK, shipping.getStatusCode());
		assertNotNull(shipping.getBody());
		if (customShippingEnabled) {
			assertNotNull(shipping.getBody().getShippingOptions());
			assertTrue(shipping.getBody().getShippingOptions().stream()
					.anyMatch(option -> "customQuotesRules".equals(option.getOptionCode())));
		}

		ResponseEntity<ReadableShoppingCart> cartBeforePromo = testRestTemplate.exchange(
				"/api/v1/cart/" + cartCode,
				HttpMethod.GET,
				new HttpEntity<Void>(customerHeaders),
				ReadableShoppingCart.class);
		ResponseEntity<ReadableShoppingCart> promo = testRestTemplate.exchange(
				"/api/v1/cart/" + cartCode + "/promo/Test1234",
				HttpMethod.POST,
				new HttpEntity<Void>(customerHeaders),
				ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, promo.getStatusCode());
		assertEquals(cartBeforePromo.getBody().getTotal(), promo.getBody().getTotal());

		configureMoneyOrderPayment(adminToken);
		assertCheckoutAndReadOrder(cartCode, customerHeaders);
		assertServicesBasicAuthentication();

		ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertTrue(health.getBody().contains("\"status\":\"UP\""));
		assertEquals(HttpStatus.OK,
				testRestTemplate.getForEntity("/v2/api-docs", String.class).getStatusCode());
	}

	private String loginAdmin() {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/private/login",
				new AuthenticationRequest(ADMIN_EMAIL, ADMIN_PASSWORD),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(login.getBody());
		assertNotNull(login.getBody().getToken());
		return login.getBody().getToken();
	}

	private void assertPrivateApiAuthorization(String adminToken) {
		ResponseEntity<String> allowed = testRestTemplate.exchange(
				"/api/v1/private/modules/shipping",
				HttpMethod.GET,
				new HttpEntity<Void>(bearerHeaders(adminToken)),
				String.class);
		assertEquals(HttpStatus.OK, allowed.getStatusCode());

		ResponseEntity<String> missingToken = testRestTemplate.getForEntity(
				"/api/v1/private/modules/shipping", String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, missingToken.getStatusCode());

		HttpHeaders garbageTokenHeaders = bearerHeaders("not-a-valid-token");
		ResponseEntity<String> garbageToken = testRestTemplate.exchange(
				"/api/v1/private/modules/shipping",
				HttpMethod.GET,
				new HttpEntity<Void>(garbageTokenHeaders),
				String.class);
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, garbageToken.getStatusCode());
	}

	private PersistableCustomer customer(String email) {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword(CUSTOMER_PASSWORD);
		customer.setRepeatPassword(CUSTOMER_PASSWORD);
		customer.setFirstName("UAT");
		customer.setLastName("Customer");
		customer.setGender("M");
		customer.setLanguage("en");
		customer.setStoreCode("DEFAULT");
		Address address = new Address();
		address.setFirstName("UAT");
		address.setLastName("Customer");
		address.setAddress("100 Main Street");
		address.setCity("Montreal");
		address.setZone("QC");
		address.setCountry("CA");
		address.setPostalCode("H3Z 2Y7");
		customer.setBilling(address);
		customer.setDelivery(address);
		return customer;
	}

	private boolean configureCustomShippingModule(String adminToken) {
		IntegrationModuleConfiguration configuration = new IntegrationModuleConfiguration();
		configuration.setCode("customQuotesRules");
		configuration.setActive(true);
		configuration.setDefaultSelected(true);
		ResponseEntity<Void> response = testRestTemplate.postForEntity(
				"/api/v1/private/modules/shipping",
				new HttpEntity<IntegrationModuleConfiguration>(configuration, bearerHeaders(adminToken)),
				Void.class);
		assertTrue(response.getStatusCode().is2xxSuccessful()
				|| response.getStatusCode() == HttpStatus.BAD_REQUEST);
		return response.getStatusCode().is2xxSuccessful();
	}

	private void configureInternationalShipping(String adminToken) {
		ExpeditionConfiguration configuration = new ExpeditionConfiguration();
		configuration.setIternationalShipping(true);
		configuration.setShipToCountry(Collections.singletonList("CA"));
		ResponseEntity<Void> response = testRestTemplate.postForEntity(
				"/api/v1/private/shipping/expedition",
				new HttpEntity<ExpeditionConfiguration>(configuration, bearerHeaders(adminToken)),
				Void.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	private ReadableProduct sampleShippableProduct(String code) {
		PersistableCategory newCategory = category(code);
		ResponseEntity<PersistableCategory> categoryResponse = testRestTemplate.postForEntity(
				"/api/v1/private/category?store=DEFAULT",
				new HttpEntity<PersistableCategory>(newCategory, getHeader()),
				PersistableCategory.class);
		assertEquals(HttpStatus.CREATED, categoryResponse.getStatusCode());
		assertNotNull(categoryResponse.getBody());

		PersistableProduct product = product(code);
		product.setCategories(Collections.<Category>singletonList(categoryResponse.getBody()));
		ProductSpecification specifications = new ProductSpecification();
		specifications.setManufacturer(
				com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer.DEFAULT_MANUFACTURER);
		product.setProductSpecifications(specifications);
		product.setAvailable(true);
		product.setProductShipeable(true);
		product.setPrice(BigDecimal.TEN);
		product.setSku(code);
		product.setQuantity(100);
		ResponseEntity<Entity> productResponse = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=DEFAULT",
				new HttpEntity<PersistableProduct>(product, getHeader()),
				Entity.class);
		assertEquals(HttpStatus.CREATED, productResponse.getStatusCode());

		ResponseEntity<ReadableProduct> readableProduct = testRestTemplate.getForEntity(
				"/api/v2/product/" + code, ReadableProduct.class);
		assertEquals(HttpStatus.OK, readableProduct.getStatusCode());
		assertNotNull(readableProduct.getBody());
		return readableProduct.getBody();
	}

	private void assertCheckoutAndReadOrder(String cartCode, HttpHeaders customerHeaders) {
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("AUTHORIZECAPTURE");
		payment.setAmount("250.00");

		PersistableOrder order = new PersistableOrder();
		order.setCurrency("CAD");
		order.setCustomerAgreement(true);
		order.setPayment(payment);
		ResponseEntity<ReadableOrderConfirmation> checkout = testRestTemplate.postForEntity(
				"/api/v1/auth/cart/" + cartCode + "/checkout",
				new HttpEntity<PersistableOrder>(order, customerHeaders),
				ReadableOrderConfirmation.class);
		assertEquals(HttpStatus.OK, checkout.getStatusCode());
		assertNotNull(checkout.getBody());
		assertNotNull(checkout.getBody().getId());

		ResponseEntity<String> orderReadBack = testRestTemplate.exchange(
				"/api/v1/auth/orders/" + checkout.getBody().getId(),
				HttpMethod.GET,
				new HttpEntity<Void>(customerHeaders),
				String.class);
		assertEquals(HttpStatus.OK, orderReadBack.getStatusCode());
	}

	private void configureMoneyOrderPayment(String adminToken) {
		IntegrationModuleConfiguration configuration = new IntegrationModuleConfiguration();
		configuration.setCode("moneyorder");
		configuration.setActive(true);
		configuration.setDefaultSelected(true);
		configuration.setIntegrationKeys(Collections.singletonMap("address", "Shopizer test"));
		ResponseEntity<Void> response = testRestTemplate.postForEntity(
				"/api/v1/private/modules/payment",
				new HttpEntity<IntegrationModuleConfiguration>(configuration, bearerHeaders(adminToken)),
				Void.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	private void assertServicesBasicAuthentication() {
		HttpHeaders validHeaders = new HttpHeaders();
		validHeaders.setBasicAuth(ADMIN_EMAIL, ADMIN_PASSWORD, StandardCharsets.UTF_8);
		validHeaders.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> validCredentials = testRestTemplate.postForEntity(
				"/services/private/system/optin",
				new HttpEntity<String>("{}", validHeaders),
				String.class);
		assertEquals(HttpStatus.OK, validCredentials.getStatusCode());

		HttpHeaders invalidHeaders = new HttpHeaders();
		invalidHeaders.setBasicAuth(ADMIN_EMAIL, "wrong-password", StandardCharsets.UTF_8);
		invalidHeaders.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> invalidCredentials = testRestTemplate.postForEntity(
				"/services/private/system/optin",
				new HttpEntity<String>("{}", invalidHeaders),
				String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, invalidCredentials.getStatusCode());
	}

	private HttpHeaders bearerHeaders(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(token);
		return headers;
	}
}
