package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.core.model.payments.TransactionType;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.model.tax.PersistableTaxClass;
import com.salesmanager.shop.model.tax.PersistableTaxRate;
import com.salesmanager.shop.model.tax.TaxRateDescription;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ShopizerApiUATTest extends ServicesTestSupport {

	@Test
	public void adminAuthenticationBrowseAndPublicServicesWork() {
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")), AuthenticationResponse.class);
		assertEquals(200, login.getStatusCodeValue());
		assertNotNull(login.getBody().getToken());

		HttpHeaders adminHeaders = bearer(login.getBody().getToken());
		ResponseEntity<String> privateCall = testRestTemplate.exchange(
				"/api/v1/private/tax/class?store=" + Constants.DEFAULT_STORE, HttpMethod.GET,
				new HttpEntity<>(adminHeaders), String.class);
		assertEquals(200, privateCall.getStatusCodeValue());

		ResponseEntity<String> anonymousCall = testRestTemplate.exchange(
				"/api/v1/private/tax/class?store=" + Constants.DEFAULT_STORE, HttpMethod.GET,
				new HttpEntity<>(jsonHeaders()), String.class);
		assertTrue(anonymousCall.getStatusCodeValue() == 401 || anonymousCall.getStatusCodeValue() == 403);

		HttpHeaders badToken = bearer("not-a-valid-token");
		ResponseEntity<String> badTokenCall = testRestTemplate.exchange(
				"/api/v1/private/tax/class?store=" + Constants.DEFAULT_STORE, HttpMethod.GET,
				new HttpEntity<>(badToken), String.class);
		assertFalse("Invalid token unexpectedly accessed private tax data: " + badTokenCall.getStatusCodeValue(),
				badTokenCall.getStatusCode().is2xxSuccessful());

		ResponseEntity<ReadableMerchantStore> store = testRestTemplate.exchange(
				"/api/v1/store/" + Constants.DEFAULT_STORE, HttpMethod.GET, new HttpEntity<>(jsonHeaders()),
				ReadableMerchantStore.class);
		assertEquals(200, store.getStatusCodeValue());
		assertNotNull(store.getBody());

		ResponseEntity<String> categories = testRestTemplate.exchange(
				"/api/v1/category?store=" + Constants.DEFAULT_STORE, HttpMethod.GET, new HttpEntity<>(jsonHeaders()),
				String.class);
		assertEquals(200, categories.getStatusCodeValue());

		String productCode = unique("uat-product");
		ReadableProduct product = sampleProduct(productCode);
		ResponseEntity<String> products = testRestTemplate.exchange(
				"/api/v1/products?store=" + Constants.DEFAULT_STORE, HttpMethod.GET, new HttpEntity<>(jsonHeaders()),
				String.class);
		assertEquals(200, products.getStatusCodeValue());
		assertTrue(products.getBody().contains(productCode));
		ResponseEntity<ReadableProduct> detail = testRestTemplate.exchange("/api/v2/product/" + product.getSku(),
				HttpMethod.GET, new HttpEntity<>(jsonHeaders()), ReadableProduct.class);
		assertEquals(200, detail.getStatusCodeValue());
		assertEquals(productCode, detail.getBody().getSku());

		ResponseEntity<String> health = testRestTemplate.exchange("/actuator/health", HttpMethod.GET,
				new HttpEntity<>(jsonHeaders()), String.class);
		assertEquals(200, health.getStatusCodeValue());
		assertTrue(health.getBody().contains("\"status\":\"UP\""));
		ResponseEntity<String> docs = testRestTemplate.exchange("/v2/api-docs", HttpMethod.GET,
				new HttpEntity<>(jsonHeaders()), String.class);
		assertEquals(200, docs.getStatusCodeValue());

		HttpHeaders validBasic = jsonHeaders();
		validBasic.setBasicAuth("admin@shopizer.com", "password", StandardCharsets.UTF_8);
		ResponseEntity<String> basicValid = testRestTemplate.exchange("/services/private/system/optin", HttpMethod.POST,
				new HttpEntity<>("{\"code\":\"uat\"}", validBasic), String.class);
		assertEquals("Basic-auth endpoint rejected configured admin credentials", 200,
				basicValid.getStatusCodeValue());

		HttpHeaders invalidBasic = jsonHeaders();
		invalidBasic.setBasicAuth("admin@shopizer.com", "invalid-password", StandardCharsets.UTF_8);
		ResponseEntity<String> basicInvalid = testRestTemplate.exchange("/services/private/system/optin",
				HttpMethod.POST, new HttpEntity<>("{\"code\":\"uat\"}", invalidBasic), String.class);
		assertTrue(basicInvalid.getStatusCodeValue() == 401 || basicInvalid.getStatusCodeValue() == 403);
	}

	@Test
	public void customerCanRegisterLoginViewProfileCartAndCheckout() {
		String email = unique("uat") + "@example.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);
		customer.setFirstName("UAT");
		customer.setLastName("Customer");
		Address billing = new Address();
		billing.setFirstName("UAT");
		billing.setLastName("Customer");
		billing.setCountry("US");
		billing.setZone("NY");
		billing.setAddress("101 Test Street");
		billing.setCity("New York");
		billing.setPostalCode("10001");
		customer.setBilling(billing);
		ResponseEntity<PersistableCustomer> registration = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer, jsonHeaders()), PersistableCustomer.class);
		assertEquals(200, registration.getStatusCodeValue());

		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")), AuthenticationResponse.class);
		assertEquals(200, login.getStatusCodeValue());
		assertNotNull(login.getBody().getToken());
		HttpHeaders customerHeaders = bearer(login.getBody().getToken());
		ResponseEntity<String> profile = testRestTemplate.exchange(
				"/api/v1/auth/customer/profile?store=" + Constants.DEFAULT_STORE, HttpMethod.GET,
				new HttpEntity<>(customerHeaders), String.class);
		assertEquals(200, profile.getStatusCodeValue());

		String productCode = unique("uat-cart");
		ReadableProduct product = sampleProduct(productCode);
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> cartCreated = testRestTemplate.postForEntity(
				"/api/v1/cart?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(item, customerHeaders), ReadableShoppingCart.class);
		assertEquals(201, cartCreated.getStatusCodeValue());
		assertNotNull(cartCreated.getBody().getCode());

		item.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> cartUpdated = testRestTemplate.exchange(
				"/api/v1/cart/" + cartCreated.getBody().getCode() + "?store=" + Constants.DEFAULT_STORE,
				HttpMethod.PUT, new HttpEntity<>(item, customerHeaders), ReadableShoppingCart.class);
		assertEquals(201, cartUpdated.getStatusCodeValue());
		ResponseEntity<ReadableShoppingCart> cartRead = testRestTemplate.exchange(
				"/api/v1/cart/" + cartCreated.getBody().getCode() + "?store=" + Constants.DEFAULT_STORE,
				HttpMethod.GET, new HttpEntity<>(customerHeaders), ReadableShoppingCart.class);
		assertEquals(200, cartRead.getStatusCodeValue());
		assertEquals(2, cartRead.getBody().getQuantity());

		PersistablePayment payment = new PersistablePayment();
		configureMoneyOrder(true);
		payment.setPaymentType(PaymentType.MONEYORDER.name());
		payment.setTransactionType(TransactionType.AUTHORIZECAPTURE.name());
		payment.setPaymentModule("moneyorder");
		payment.setAmount(cartRead.getBody().getTotal().toPlainString());
		PersistableOrder order = new PersistableOrder();
		order.setCurrency("USD");
		order.setCustomerAgreement(true);
		order.setPayment(payment);
		ResponseEntity<String> checkout = testRestTemplate.exchange(
				"/api/v1/auth/cart/" + cartRead.getBody().getCode() + "/checkout?store=" + Constants.DEFAULT_STORE,
				HttpMethod.POST, new HttpEntity<>(order, customerHeaders), String.class);
		configureMoneyOrder(false);
		assertEquals("Checkout response: " + checkout.getBody(), 200, checkout.getStatusCodeValue());

		Long orderId = extractLong(checkout.getBody(), "id");
		assertNotNull(orderId);
		ResponseEntity<String> orderRead = testRestTemplate.exchange(
				"/api/v1/auth/orders/" + orderId + "?store=" + Constants.DEFAULT_STORE, HttpMethod.GET,
				new HttpEntity<>(customerHeaders), String.class);
		assertEquals(200, orderRead.getStatusCodeValue());
	}

	@Test
	public void adminCanCreateListUpdateRecreateAndDeleteTaxData() {
		HttpHeaders headers = getHeader();
		String classCode = unique("UT");
		Long classId = createClass(classCode, "Initial class", null, headers);
		assertTrue(listTax("/api/v1/private/tax/class?store=" + Constants.DEFAULT_STORE, classCode, headers));

		PersistableTaxRate rate = taxRate(unique("UR"), null, "Initial rate", null, classCode);
		Long rateId = createRate(rate, headers);
		assertTrue(listTax("/api/v1/private/tax/rates?store=" + Constants.DEFAULT_STORE, rate.getCode(), headers));

		ResponseEntity<Void> classUpdate = testRestTemplate.exchange(
				"/api/v1/private/tax/class/" + classId + "?store=" + Constants.DEFAULT_STORE, HttpMethod.PUT,
				new HttpEntity<>(taxClass(classCode, "Updated class", 999999L), headers), Void.class);
		assertTrue(classUpdate.getStatusCode().is2xxSuccessful());

		PersistableTaxRate updatedRate = taxRate(rate.getCode(), 999999L, "Updated rate", 888888L, classCode);
		updatedRate.setRate(new BigDecimal("8.00"));
		ResponseEntity<Void> rateUpdate = testRestTemplate.exchange(
				"/api/v1/private/tax/rate/" + rateId + "?store=" + Constants.DEFAULT_STORE, HttpMethod.PUT,
				new HttpEntity<>(updatedRate, headers), Void.class);
		assertTrue(rateUpdate.getStatusCode().is2xxSuccessful());

		Long copiedClassId = createClass(unique("UT"), "Copy class", classId, headers);
		assertFalse(classId.equals(copiedClassId));
		PersistableTaxRate copiedRate = taxRate(unique("UR"), rateId, "Copied rate", 777777L, classCode);
		Long copiedRateId = createRate(copiedRate, headers);
		assertFalse(rateId.equals(copiedRateId));

		assertTrue(deleteTax("/api/v1/private/tax/rate/" + copiedRateId + "?store=" + Constants.DEFAULT_STORE,
				headers));
		assertTrue(deleteTax("/api/v1/private/tax/rate/" + rateId + "?store=" + Constants.DEFAULT_STORE, headers));
		assertTrue(deleteTax("/api/v1/private/tax/class/" + copiedClassId + "?store=" + Constants.DEFAULT_STORE,
				headers));
		assertTrue(deleteTax("/api/v1/private/tax/class/" + classId + "?store=" + Constants.DEFAULT_STORE, headers));
	}

	private Long createClass(String code, String name, Long id, HttpHeaders headers) {
		ResponseEntity<Entity> response = testRestTemplate.postForEntity(
				"/api/v1/private/tax/class?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(taxClass(code, name, id), headers), Entity.class);
		assertTrue("Tax class create failed with " + response.getStatusCode(), response.getStatusCode().is2xxSuccessful());
		assertNotNull(response.getBody());
		return response.getBody().getId();
	}

	private Long createRate(PersistableTaxRate rate, HttpHeaders headers) {
		ResponseEntity<Entity> response = testRestTemplate.postForEntity(
				"/api/v1/private/tax/rate?store=" + Constants.DEFAULT_STORE, new HttpEntity<>(rate, headers),
				Entity.class);
		assertTrue("Tax rate create failed with " + response.getStatusCode(), response.getStatusCode().is2xxSuccessful());
		assertNotNull(response.getBody());
		return response.getBody().getId();
	}

	private boolean listTax(String url, String expected, HttpHeaders headers) {
		ResponseEntity<String> response = testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers),
				String.class);
		assertTrue(response.getStatusCode().is2xxSuccessful());
		return response.getBody().contains(expected);
	}

	private boolean deleteTax(String url, HttpHeaders headers) {
		return testRestTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class)
				.getStatusCode().is2xxSuccessful();
	}

	private void configureMoneyOrder(boolean active) {
		IntegrationModuleConfiguration configuration = new IntegrationModuleConfiguration();
		configuration.setCode("moneyorder");
		configuration.setActive(active);
		configuration.setDefaultSelected(active);
		configuration.setIntegrationKeys(Collections.singletonMap("address", "UAT money order address"));
		ResponseEntity<Void> response = testRestTemplate.postForEntity(
				"/api/v1/private/modules/payment?store=" + Constants.DEFAULT_STORE,
				new HttpEntity<>(configuration, getHeader()), Void.class);
		assertTrue("Money-order configuration failed with " + response.getStatusCode(),
				response.getStatusCode().is2xxSuccessful());
	}

	private static PersistableTaxClass taxClass(String code, String name, Long id) {
		PersistableTaxClass taxClass = new PersistableTaxClass();
		taxClass.setId(id);
		taxClass.setCode(code);
		taxClass.setName(name);
		return taxClass;
	}

	private static PersistableTaxRate taxRate(String code, Long id, String name, Long descriptionId, String taxClass) {
		PersistableTaxRate rate = new PersistableTaxRate();
		rate.setId(id);
		rate.setCode(code);
		rate.setRate(new BigDecimal("5.00"));
		rate.setCountry("US");
		rate.setZone("NY");
		rate.setPriority(0);
		rate.setStore(Constants.DEFAULT_STORE);
		rate.setTaxClass(taxClass);
		TaxRateDescription description = new TaxRateDescription();
		description.setId(descriptionId);
		description.setLanguage("en");
		description.setName(name);
		description.setDescription(name);
		rate.setDescriptions(Arrays.asList(description));
		return rate;
	}

	private static HttpHeaders bearer(String token) {
		HttpHeaders headers = jsonHeaders();
		headers.setBearerAuth(token);
		return headers;
	}

	private static HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private static String unique(String prefix) {
		return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 7).toLowerCase();
	}

	private static Long extractLong(String json, String key) {
		java.util.regex.Matcher matcher = java.util.regex.Pattern
				.compile("\"" + key + "\"\\s*:\\s*(\\d+)").matcher(json);
		return matcher.find() ? Long.valueOf(matcher.group(1)) : null;
	}
}
