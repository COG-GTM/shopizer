package com.salesmanager.test.shop.uat;

import static org.junit.Assert.*;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.system.MerchantConfigurationService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.system.MerchantConfiguration;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.PersistableProductPrice;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class StoreInventoryJourneysTest extends ServicesTestSupport {

	private final ObjectMapper json = new ObjectMapper();
	@Autowired private MerchantConfigurationService configurations;
	@Autowired private MerchantStoreService stores;
	private MerchantStore defaultStore;
	private MerchantConfiguration paymentConfigurationBefore;
	private String paymentConfigurationValueBefore;

	@Before
	public void snapshotPaymentConfiguration() throws Exception {
		defaultStore = stores.getByCode(MerchantStore.DEFAULT_STORE);
		paymentConfigurationBefore = configurations.getMerchantConfiguration(Constants.PAYMENT_MODULES, defaultStore);
		paymentConfigurationValueBefore = paymentConfigurationBefore == null ? null : paymentConfigurationBefore.getValue();
	}

	@After
	public void restorePaymentConfiguration() throws Exception {
		if (paymentConfigurationBefore != null) {
			paymentConfigurationBefore.setValue(paymentConfigurationValueBefore);
			configurations.saveOrUpdate(paymentConfigurationBefore);
		} else if (defaultStore != null) {
			MerchantConfiguration created = configurations.getMerchantConfiguration(Constants.PAYMENT_MODULES, defaultStore);
			if (created != null) {
				configurations.delete(created);
			}
		}
	}

	@Test
	public void adminLoginProtectsInventoryManagement() throws Exception {
		String path = "/api/v1/private/product/uatProtected/inventory";
		assertDenied(call(HttpMethod.GET, path, null, new HttpHeaders()));
		sampleProduct("uatProtected");
		JsonNode inventory = ok(call(HttpMethod.GET, path, null, getHeader()));
		assertTrue(inventory.get("items").size() > 0);
	}

	@Test
	public void ownerCanUpdatePriceButCannotMoveItToAnotherProductsInventory() throws Exception {
		ReadableProduct first = sampleProduct("uatPriceOwner");
		sampleProduct("uatPriceTarget");
		HttpHeaders admin = getHeader();
		JsonNode original = inventory("uatPriceOwner", admin);
		JsonNode target = inventory("uatPriceTarget", admin);
		long inventoryId = original.get("id").asLong();
		long priceId = original.get("prices").get(0).get("id").asLong();
		PersistableProductPrice update = new PersistableProductPrice();
		update.setPrice(new BigDecimal("31.25"));
		update.setDefaultPrice(true);

		ResponseEntity<String> moved = call(HttpMethod.PUT, "/api/v1/private/product/uatPriceTarget/inventory/"
				+ target.get("id").asLong() + "/price/" + priceId, update, admin);
		assertEquals(HttpStatus.NOT_FOUND, moved.getStatusCode());
		assertEquals(original, inventory("uatPriceOwner", admin));
		assertEquals(target, inventory("uatPriceTarget", admin));

		ResponseEntity<String> saved = call(HttpMethod.PUT, "/api/v1/private/product/uatPriceOwner/inventory/"
				+ inventoryId + "/price/" + priceId, update, admin);
		assertEquals(HttpStatus.OK, saved.getStatusCode());
		JsonNode after = inventory("uatPriceOwner", admin);
		assertEquals(inventoryId, after.get("id").asLong());
		assertEquals(priceId, after.get("prices").get(0).get("id").asLong());
		assertTrue(after.toString(), after.get("prices").get(0).get("originalPrice").asText().contains("31.25"));
		assertEquals(HttpStatus.OK, call(HttpMethod.DELETE, "/api/v1/private/product/" + first.getId()
				+ "/inventory/" + inventoryId, null, admin).getStatusCode());
		assertEquals(0, ok(call(HttpMethod.GET, "/api/v1/private/product/uatPriceOwner/inventory", null, admin)).get("items").size());
	}

	@Test
	public void storefrontCanBrowseRegisterAuthenticateAndCheckoutCart() throws Exception {
		ReadableProduct product = sampleProduct("uatShopper");
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		assertTrue(ok(call(HttpMethod.GET, "/api/v1/store/DEFAULT", null, headers)).has("code"));
		assertNotNull(ok(call(HttpMethod.GET, "/api/v1/category/", null, headers)));
		assertTrue(ok(call(HttpMethod.GET, "/api/v1/products", null, headers)).get("products").isArray());
		assertEquals(product.getId().longValue(), ok(call(HttpMethod.GET, "/api/v2/product/uatShopper", null, headers)).get("id").asLong());

		String customer = "{\"emailAddress\":\"uatshopper@example.com\",\"password\":\"clear123\",\"gender\":\"M\","
				+ "\"language\":\"en\",\"storeCode\":\"DEFAULT\",\"billing\":{\"firstName\":\"Uat\",\"lastName\":\"Shopper\",\"country\":\"CA\","
				+ "\"address\":\"1 Test Street\",\"city\":\"Montreal\",\"postalCode\":\"H1H1H1\",\"stateProvince\":\"QC\",\"phone\":\"5145551234\"}}";
		ok(call(HttpMethod.POST, "/api/v1/customer/register", customer, headers));
		JsonNode login = ok(call(HttpMethod.POST, "/api/v1/customer/login",
				"{\"username\":\"uatshopper@example.com\",\"password\":\"clear123\"}", headers));
		assertTrue(login.get("token").isTextual());
		headers.setBearerAuth(login.get("token").asText());
		ResponseEntity<String> created = call(HttpMethod.POST, "/api/v1/cart/", "{\"product\":\"uatShopper\",\"quantity\":1}", headers);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());
		JsonNode cart = json.readTree(created.getBody());
		String cartCode = cart.get("code").asText();
		ResponseEntity<String> updated = call(HttpMethod.POST, "/api/v1/cart/" + cartCode + "/multi",
				"[{\"product\":\"uatShopper\",\"quantity\":2}]", headers);
		assertEquals(HttpStatus.CREATED, updated.getStatusCode());
		assertEquals(2, json.readTree(updated.getBody()).get("quantity").asInt());
		assertEquals(2, ok(call(HttpMethod.GET, "/api/v1/cart/" + cartCode, null, headers)).get("quantity").asInt());
		assertEquals(cartCode, ok(call(HttpMethod.GET, "/api/v1/auth/customer/cart?cart=" + cartCode, null, headers)).get("code").asText());

		assertEquals(HttpStatus.OK, call(HttpMethod.POST, "/api/v1/private/modules/payment",
				"{\"code\":\"moneyorder\",\"active\":true,\"defaultSelected\":true,"
				+ "\"integrationKeys\":{\"address\":\"1 Test Street\"}}", getHeader()).getStatusCode());
		JsonNode current = ok(call(HttpMethod.GET, "/api/v1/cart/" + cartCode, null, headers));
		String order = "{\"customerAgreement\":true,\"currency\":\"CAD\",\"payment\":{"
				+ "\"paymentModule\":\"moneyorder\",\"paymentType\":\"MONEYORDER\",\"transactionType\":\"AUTHORIZE\","
				+ "\"amount\":\"" + current.get("total").decimalValue().toPlainString() + "\"}}";
		JsonNode confirmation = ok(call(HttpMethod.POST, "/api/v1/auth/cart/" + cartCode + "/checkout", order, headers));
		assertTrue(confirmation.get("id").asLong() > 0);
		assertEquals("MONEYORDER", confirmation.get("payment").asText());
		assertEquals(2, confirmation.get("products").get(0).get("orderedQuantity").asInt());
		JsonNode savedOrder = ok(call(HttpMethod.GET, "/api/v1/auth/orders/" + confirmation.get("id").asLong(), null, headers));
		assertEquals(confirmation.get("id"), savedOrder.get("id"));
		assertEquals("uatshopper@example.com", savedOrder.get("billing").get("email").asText());
	}

	@Test
	public void healthDocsAndBasicAuthenticationRemainAvailable() throws Exception {
		HttpHeaders headers = new HttpHeaders();
		assertEquals("UP", ok(call(HttpMethod.GET, "/actuator/health", null, headers)).get("status").asText());
		assertEquals("2.0", ok(call(HttpMethod.GET, "/v2/api-docs", null, headers)).get("swagger").asText());
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBasicAuth("admin@shopizer.com", "incorrect");
		assertEquals(HttpStatus.UNAUTHORIZED, call(HttpMethod.POST, "/services/private/system/optin", "{}", headers).getStatusCode());
		headers.setBasicAuth("admin@shopizer.com", "password");
		assertEquals(200, ok(call(HttpMethod.POST, "/services/private/system/optin", "{}", headers)).get("status").asInt());
	}

	private JsonNode inventory(String sku, HttpHeaders headers) throws Exception {
		return ok(call(HttpMethod.GET, "/api/v1/private/product/" + sku + "/inventory", null, headers)).get("items").get(0);
	}

	private JsonNode ok(ResponseEntity<String> response) throws Exception {
		assertEquals(response.getBody(), HttpStatus.OK, response.getStatusCode());
		return json.readTree(response.getBody());
	}

	private void assertDenied(ResponseEntity<String> response) {
		assertTrue(response.toString(), response.getStatusCode() == HttpStatus.UNAUTHORIZED || response.getStatusCode() == HttpStatus.FORBIDDEN);
	}

	private ResponseEntity<String> call(HttpMethod method, String path, Object body, HttpHeaders headers) {
		return testRestTemplate.exchange(path, method, new HttpEntity<>(body, headers), String.class);
	}
}
