package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.shop.model.catalog.category.ReadableCategoryList;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.catalog.product.attribute.PersistableProductAttribute;
import com.salesmanager.shop.model.catalog.product.attribute.PersistableProductOptionValue;
import com.salesmanager.shop.model.catalog.product.attribute.ProductOptionDescription;
import com.salesmanager.shop.model.catalog.product.attribute.ProductOptionValueDescription;
import com.salesmanager.shop.model.catalog.product.attribute.ProductPropertyOption;
import com.salesmanager.shop.model.catalog.product.attribute.api.PersistableProductOptionEntity;
import com.salesmanager.shop.model.catalog.product.attribute.api.ReadableProductAttributeList;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class ShopUatSmokeTest extends ServicesTestSupport {

	private String productCode;
	private String customerEmail;
	private String customerPassword = "uatPass123";
	private String customerToken;
	private String cartCode;
	private Long productId;
	private Long optionId;
	private Long optionValueId;

	@BeforeAll
	void createProduct() {
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		productCode = "uat" + suffix;
		customerEmail = "uat" + suffix + "@example.com";
		ReadableProduct product = sampleProduct(productCode);
		assertNotNull(product);
		productId = product.getId();
	}

	@Test
	@Order(1)
	void adminLoginAndProtectedEndpointRequireValidJwt() {
		HttpHeaders headers = getHeader();
		ResponseEntity<String> authorized = testRestTemplate.exchange(
				"/api/v1/private/stores?store=DEFAULT", HttpMethod.GET, new HttpEntity<>(headers), String.class);
		ResponseEntity<String> anonymous = testRestTemplate.exchange(
				"/api/v1/private/stores?store=DEFAULT", HttpMethod.GET, HttpEntity.EMPTY, String.class);
		HttpHeaders invalidHeaders = new HttpHeaders();
		invalidHeaders.set(HttpHeaders.AUTHORIZATION, "not-a-valid-token");
		ResponseEntity<String> invalid = testRestTemplate.exchange(
				"/api/v1/private/stores?store=DEFAULT", HttpMethod.GET,
				new HttpEntity<>(invalidHeaders), String.class);

		assertEquals(HttpStatus.OK, authorized.getStatusCode());
		assertTrue(isUnauthorized(anonymous.getStatusCode()));
		assertTrue(isUnauthorized(invalid.getStatusCode()));
	}

	@Test
	@Order(2)
	void customerCanRegisterLoginAndReadAuthenticatedProfile() {
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(customerEmail);
		customer.setPassword(customerPassword);
		customer.setGender("M");
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Customer");
		billing.setCountry("US");
		customer.setBilling(billing);

		ResponseEntity<AuthenticationResponse> registered = testRestTemplate.postForEntity(
				"/api/v1/customer/register?store=DEFAULT",
				new HttpEntity<>(customer, getHeader()), AuthenticationResponse.class);
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
				"/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(customerEmail, customerPassword)),
				AuthenticationResponse.class);
		customerToken = login.getBody().getToken();
		ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile?store=DEFAULT", HttpMethod.GET,
				new HttpEntity<>(bearer(customerToken)), ReadableCustomer.class);

		assertTrue(registered.getStatusCode().is2xxSuccessful());
		assertEquals(HttpStatus.OK, login.getStatusCode());
		assertNotNull(customerToken);
		assertEquals(HttpStatus.OK, profile.getStatusCode());
		assertEquals(customerEmail, profile.getBody().getEmailAddress());
	}

	@Test
	@Order(3)
	void storeCategoryProductListAndProductDetailAreBrowsable() {
		ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity(
				"/api/v1/store/DEFAULT", ReadableMerchantStore.class);
		ResponseEntity<ReadableCategoryList> categories = testRestTemplate.getForEntity(
				"/api/v1/category?store=DEFAULT", ReadableCategoryList.class);
		ResponseEntity<ReadableProductList> products = testRestTemplate.getForEntity(
				"/api/v1/products?store=DEFAULT&sku=" + productCode, ReadableProductList.class);
		ResponseEntity<ReadableProduct> detail = testRestTemplate.getForEntity(
				"/api/v2/product/" + productCode + "?store=DEFAULT", ReadableProduct.class);

		assertEquals(HttpStatus.OK, store.getStatusCode());
		assertEquals(Constants.DEFAULT_STORE, store.getBody().getCode());
		assertEquals(HttpStatus.OK, categories.getStatusCode());
		assertTrue(categories.getBody().getRecordsTotal() > 0);
		assertEquals(HttpStatus.OK, products.getStatusCode());
		assertEquals(1, products.getBody().getProducts().size());
		assertEquals(productCode, products.getBody().getProducts().get(0).getSku());
		assertEquals(HttpStatus.OK, detail.getStatusCode());
		assertEquals(productCode, detail.getBody().getSku());
	}

	@Test
	@Order(4)
	void customerCartQuantityAndSubtotalStayConsistent() {
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(productCode);
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity(
				"/api/v1/cart?store=DEFAULT", new HttpEntity<>(item, bearer(customerToken)),
				ReadableShoppingCart.class);
		cartCode = created.getBody().getCode();

		item.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
				"/api/v1/cart/" + cartCode + "?store=DEFAULT", HttpMethod.PUT,
				new HttpEntity<>(item, bearer(customerToken)), ReadableShoppingCart.class);
		ResponseEntity<ReadableShoppingCart> read = testRestTemplate.exchange(
				"/api/v1/cart/" + cartCode + "?store=DEFAULT", HttpMethod.GET,
				new HttpEntity<>(bearer(customerToken)), ReadableShoppingCart.class);

		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		assertEquals(HttpStatus.CREATED, updated.getStatusCode());
		assertEquals(HttpStatus.OK, read.getStatusCode());
		assertEquals(2, read.getBody().getQuantity());
		assertTrue(read.getBody().getSubtotal().compareTo(BigDecimal.ZERO) > 0);
		assertEquals(0, read.getBody().getSubtotal().compareTo(updated.getBody().getSubtotal()));
		assertEquals(1, read.getBody().getProducts().size());
		assertEquals(2, read.getBody().getProducts().get(0).getQuantity());
	}

	@Test
	@Order(5)
	void authenticatedCustomerCanPlaceAndReadBackOrderWhenCheckoutIsAvailable() {
		PersistableOrder order = new PersistableOrder();
		order.setCurrency("USD");
		order.setCustomerAgreement(true);
		ResponseEntity<ReadableOrderConfirmation> checkout = testRestTemplate.postForEntity(
				"/api/v1/auth/cart/" + cartCode + "/checkout?store=DEFAULT",
				new HttpEntity<>(order, bearer(customerToken)), ReadableOrderConfirmation.class);

		assertTrue(checkout.getStatusCode() == HttpStatus.OK
				|| checkout.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE);
		if (checkout.getStatusCode() == HttpStatus.OK) {
			assertNotNull(checkout.getBody());
			ResponseEntity<String> orderReadback = testRestTemplate.exchange(
					"/api/v1/auth/orders/" + checkout.getBody().getId() + "?store=DEFAULT",
					HttpMethod.GET, new HttpEntity<>(bearer(customerToken)), String.class);
			assertEquals(HttpStatus.OK, orderReadback.getStatusCode());
		}
	}

	@Test
	@Order(6)
	void servicesBasicAuthHealthAndSwaggerAreAvailable() {
		HttpHeaders validBasic = new HttpHeaders();
		validBasic.setBasicAuth("admin@shopizer.com", "password", StandardCharsets.UTF_8);
		validBasic.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> validServices = testRestTemplate.exchange(
				"/services/private/system/optin", HttpMethod.POST,
				new HttpEntity<>("\"uat\"", validBasic), String.class);
		HttpHeaders invalidBasic = new HttpHeaders();
		invalidBasic.setBasicAuth("admin@shopizer.com", "wrong-password", StandardCharsets.UTF_8);
		ResponseEntity<String> invalidServices = testRestTemplate.exchange(
				"/services/private/system/optin", HttpMethod.POST,
				new HttpEntity<>("\"uat\"", invalidBasic), String.class);
		ResponseEntity<String> health =
				testRestTemplate.getForEntity("/actuator/health", String.class);
		ResponseEntity<String> swagger =
				testRestTemplate.getForEntity("/v2/api-docs", String.class);

		assertTrue(validServices.getStatusCode().is2xxSuccessful());
		assertTrue(isUnauthorized(invalidServices.getStatusCode()));
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertTrue(health.getBody().contains("\"status\":\"UP\""));
		assertEquals(HttpStatus.OK, swagger.getStatusCode());
		assertTrue(swagger.getBody().contains("\"swagger\""));
	}

	@Test
	@Order(7)
	void adminCanCreateListAndDeleteProductAttribute() {
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		String optionCode = "uatopt" + suffix;
		String valueCode = "uatval" + suffix;
		optionId = createOption(optionCode);
		optionValueId = createOptionValue(valueCode);
		PersistableProductAttribute attribute = new PersistableProductAttribute();
		attribute.setProductAttributePrice(BigDecimal.ONE);
		ProductPropertyOption option = new ProductPropertyOption();
		option.setCode(optionCode);
		attribute.setOption(option);
		PersistableProductOptionValue value = new PersistableProductOptionValue();
		value.setCode(valueCode);
		attribute.setOptionValue(value);
		ResponseEntity<Entity> created = testRestTemplate.postForEntity(
				"/api/v1/private/product/" + productId + "/attribute?store=DEFAULT",
				new HttpEntity<>(attribute, getHeader()), Entity.class);
		ResponseEntity<ReadableProductAttributeList> listed =
				testRestTemplate.exchange("/api/v1/private/product/" + productId + "/attributes?store=DEFAULT",
						HttpMethod.GET, new HttpEntity<>(getHeader()),
						ReadableProductAttributeList.class);
		ResponseEntity<Void> deleted = testRestTemplate.exchange(
				"/api/v1/private/product/" + productId + "/attribute/" + created.getBody().getId()
						+ "?store=DEFAULT",
				HttpMethod.DELETE, new HttpEntity<>(getHeader()), Void.class);

		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		assertTrue(listed.getBody().getAttributes().stream()
				.anyMatch(existing -> optionCode.equals(existing.getOption().getCode())));
		assertEquals(HttpStatus.OK, deleted.getStatusCode());
		assertNotNull(optionId);
		assertNotNull(optionValueId);
	}

	private Long createOption(String code) {
		PersistableProductOptionEntity option = new PersistableProductOptionEntity();
		option.setCode(code);
		option.setType("select");
		ProductOptionDescription description = new ProductOptionDescription();
		description.setName(code);
		description.setLanguage("en");
		option.getDescriptions().add(description);
		ResponseEntity<Entity> response = testRestTemplate.postForEntity(
				"/api/v1/private/product/option?store=DEFAULT",
				new HttpEntity<>(option, getHeader()), Entity.class);
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		return response.getBody().getId();
	}

	private Long createOptionValue(String code) {
		PersistableProductOptionValue optionValue = new PersistableProductOptionValue();
		optionValue.setCode(code);
		ProductOptionValueDescription description = new ProductOptionValueDescription();
		description.setName(code);
		description.setLanguage("en");
		optionValue.getDescriptions().add(description);
		ResponseEntity<Entity> response = testRestTemplate.postForEntity(
				"/api/v1/private/product/option/value?store=DEFAULT",
				new HttpEntity<>(optionValue, getHeader()), Entity.class);
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		return response.getBody().getId();
	}

	private static HttpHeaders bearer(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private static boolean isUnauthorized(HttpStatus status) {
		return status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN;
	}
}
