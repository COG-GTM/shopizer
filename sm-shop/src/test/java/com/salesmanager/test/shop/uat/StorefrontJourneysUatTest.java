package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.category.Category;
import com.salesmanager.shop.model.catalog.category.CategoryDescription;
import com.salesmanager.shop.model.catalog.category.PersistableCategory;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.catalog.product.ProductDescription;
import com.salesmanager.shop.model.catalog.product.PersistableProductPrice;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.PersistableProductInventory;
import com.salesmanager.shop.model.catalog.product.product.ProductSpecification;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StorefrontJourneysUatTest {

	private static final String STORE = "DEFAULT";
	private static final String CUSTOMER_PASSWORD = "Customer1";

	@Autowired
	private TestRestTemplate testRestTemplate;

	@Test
	public void customerCanBrowseShopManageCartAndCheckout() {
		HttpHeaders admin = login("/api/v1/private/login", "admin@shopizer.com", "password");
		String privateEndpoint = "/api/v1/private/modules/payment?store=" + STORE;
		assertEquals(HttpStatus.OK, testRestTemplate.exchange(privateEndpoint, HttpMethod.GET,
				new HttpEntity<String>(admin), String.class).getStatusCode());
		assertEquals(HttpStatus.UNAUTHORIZED, testRestTemplate.getForEntity(privateEndpoint, String.class)
				.getStatusCode());
		HttpHeaders invalidBearer = bearerHeader("garbage");
		assertFalse(testRestTemplate.exchange(privateEndpoint, HttpMethod.GET,
				new HttpEntity<String>(invalidBearer), String.class).getStatusCode().is2xxSuccessful());

		Map<String, String> moneyOrderKeys = new HashMap<String, String>();
		moneyOrderKeys.put("address", "Storefront UAT Payee");
		IntegrationModuleConfiguration paymentConfiguration = new IntegrationModuleConfiguration();
		paymentConfiguration.setCode("moneyorder");
		paymentConfiguration.setActive(true);
		paymentConfiguration.setIntegrationKeys(moneyOrderKeys);
		assertEquals(HttpStatus.OK, testRestTemplate.exchange("/api/v1/private/modules/payment", HttpMethod.POST,
				new HttpEntity<IntegrationModuleConfiguration>(paymentConfiguration, admin), Void.class)
				.getStatusCode());

		String customerEmail = "u" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(customerEmail);
		customer.setFirstName("Storefront");
		customer.setLastName("Customer");
		customer.setPassword(CUSTOMER_PASSWORD);
		customer.setRepeatPassword(CUSTOMER_PASSWORD);
		customer.setLanguage("en");
		Address billing = new Address();
		billing.setFirstName("Storefront");
		billing.setLastName("Customer");
		billing.setAddress("1 UAT Street");
		billing.setCity("Test City");
		billing.setStateProvince("CA");
		billing.setCountry("US");
		billing.setPhone("555-0101");
		customer.setBilling(billing);
		assertEquals(HttpStatus.OK, testRestTemplate.postForEntity(
				"/api/v1/customer/register?store=" + STORE, new HttpEntity<PersistableCustomer>(customer),
				String.class).getStatusCode());
		HttpHeaders customerHeaders = login("/api/v1/customer/login", customerEmail, CUSTOMER_PASSWORD);
		assertEquals(HttpStatus.OK, testRestTemplate.exchange("/api/v1/auth/customer/profile?store=" + STORE,
				HttpMethod.GET, new HttpEntity<String>(customerHeaders), String.class).getStatusCode());

		PersistableCategory category = new PersistableCategory();
		category.setCode("storefront-" + UUID.randomUUID());
		category.setSortOrder(1);
		category.setVisible(true);
		category.setDepth(4);
		category.setParent(new Category());
		CategoryDescription categoryDescription = new CategoryDescription();
		categoryDescription.setLanguage("en");
		categoryDescription.setName("Storefront UAT");
		categoryDescription.setFriendlyUrl(category.getCode());
		categoryDescription.setTitle("Storefront UAT");
		List<CategoryDescription> categoryDescriptions = new ArrayList<CategoryDescription>();
		categoryDescriptions.add(categoryDescription);
		category.setDescriptions(categoryDescriptions);
		ResponseEntity<PersistableCategory> createdCategory = testRestTemplate.postForEntity(
				"/api/v1/private/category?store=" + STORE,
				new HttpEntity<PersistableCategory>(category, admin), PersistableCategory.class);
		assertEquals(HttpStatus.CREATED, createdCategory.getStatusCode());
		assertNotNull(createdCategory.getBody());

		String productSku = "storefront-" + UUID.randomUUID();
		PersistableProduct productToCreate = new PersistableProduct();
		PersistableProductInventory inventory = new PersistableProductInventory();
		inventory.setQuantity(5);
		inventory.setSku(productSku);
		PersistableProductPrice price = new PersistableProductPrice();
		price.setDefaultPrice(true);
		price.setPrice(BigDecimal.TEN);
		price.setDiscountedPrice(BigDecimal.ZERO);
		inventory.setPrice(price);
		productToCreate.setInventory(inventory);
		ProductDescription productDescription = new ProductDescription();
		productDescription.setName("Storefront UAT Product");
		productDescription.setLanguage("en");
		productToCreate.getDescriptions().add(productDescription);
		List<Category> categories = new ArrayList<Category>();
		categories.add(createdCategory.getBody());
		productToCreate.setCategories(categories);
		ProductSpecification specifications = new ProductSpecification();
		specifications.setManufacturer(
				com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer.DEFAULT_MANUFACTURER);
		productToCreate.setProductSpecifications(specifications);
		productToCreate.setAvailable(true);
		productToCreate.setPrice(BigDecimal.TEN);
		productToCreate.setSku(productSku);
		productToCreate.setQuantity(5);
		assertEquals(HttpStatus.CREATED, testRestTemplate.postForEntity("/api/v1/private/product?store=" + STORE,
				new HttpEntity<PersistableProduct>(productToCreate, admin), String.class).getStatusCode());

		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/api/v1/store/" + STORE, String.class)
				.getStatusCode());
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/api/v1/category?store=" + STORE, String.class)
				.getStatusCode());
		ResponseEntity<ReadableProductList> products = testRestTemplate.getForEntity(
				"/api/v1/products?store=" + STORE, ReadableProductList.class);
		assertEquals(HttpStatus.OK, products.getStatusCode());
		assertNotNull(products.getBody());
		assertFalse(products.getBody().getProducts().isEmpty());
		ReadableProduct product = products.getBody().getProducts().get(0);
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity(
				"/api/v2/product/" + product.getSku() + "?store=" + STORE, ReadableProduct.class)
				.getStatusCode());

		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> createdCart = testRestTemplate.postForEntity(
				"/api/v1/cart?store=" + STORE, new HttpEntity<PersistableShoppingCartItem>(item, customerHeaders),
				ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, createdCart.getStatusCode());
		assertNotNull(createdCart.getBody());
		String cartCode = createdCart.getBody().getCode();

		item.setQuantity(2);
		assertEquals(HttpStatus.CREATED, testRestTemplate.exchange("/api/v1/cart/" + cartCode + "?store=" + STORE,
				HttpMethod.PUT, new HttpEntity<PersistableShoppingCartItem>(item, customerHeaders),
				ReadableShoppingCart.class).getStatusCode());
		ResponseEntity<ReadableShoppingCart> currentCart = testRestTemplate.getForEntity(
				"/api/v1/cart/" + cartCode + "?store=" + STORE, ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, currentCart.getStatusCode());
		assertEquals(2, currentCart.getBody().getQuantity());

		PersistableOrder order = new PersistableOrder();
		order.setCurrency("USD");
		order.setCustomerAgreement(true);
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("AUTHORIZECAPTURE");
		payment.setAmount(currentCart.getBody().getTotal().toPlainString());
		order.setPayment(payment);
		ResponseEntity<ReadableOrderConfirmation> checkout = testRestTemplate.exchange(
				"/api/v1/auth/cart/" + cartCode + "/checkout?store=" + STORE, HttpMethod.POST,
				new HttpEntity<PersistableOrder>(order, customerHeaders), ReadableOrderConfirmation.class);
		assertEquals(HttpStatus.OK, checkout.getStatusCode());
		assertNotNull(checkout.getBody());
		assertNotNull(checkout.getBody().getId());
		ResponseEntity<ReadableOrder> savedOrder = testRestTemplate.exchange(
				"/api/v1/auth/orders/" + checkout.getBody().getId() + "?store=" + STORE, HttpMethod.GET,
				new HttpEntity<String>(customerHeaders), ReadableOrder.class);
		assertEquals(HttpStatus.OK, savedOrder.getStatusCode());

		HttpHeaders validBasic = new HttpHeaders();
		validBasic.setBasicAuth("admin@shopizer.com", "password");
		validBasic.setContentType(MediaType.TEXT_PLAIN);
		assertEquals(HttpStatus.OK, testRestTemplate.exchange("/services/private/system/optin",
				HttpMethod.POST, new HttpEntity<String>("{}", validBasic), String.class).getStatusCode());
		HttpHeaders invalidBasic = new HttpHeaders();
		invalidBasic.setBasicAuth("admin@shopizer.com", "invalid-password");
		invalidBasic.setContentType(MediaType.TEXT_PLAIN);
		assertEquals(HttpStatus.UNAUTHORIZED, testRestTemplate.exchange("/services/private/system/optin",
				HttpMethod.POST, new HttpEntity<String>("{}", invalidBasic), String.class).getStatusCode());

		ResponseEntity<JsonNode> health = testRestTemplate.getForEntity("/actuator/health", JsonNode.class);
		assertEquals(HttpStatus.OK, health.getStatusCode());
		assertTrue(health.getBody().path("status").asText().equals("UP"));
		assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/v2/api-docs", String.class).getStatusCode());
	}

	private HttpHeaders login(String endpoint, String username, String password) {
		ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity(endpoint,
				new HttpEntity<AuthenticationRequest>(new AuthenticationRequest(username, password)),
				AuthenticationResponse.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(response.getBody().getToken());
		return headers;
	}

	private HttpHeaders bearerHeader(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(token);
		return headers;
	}
}
