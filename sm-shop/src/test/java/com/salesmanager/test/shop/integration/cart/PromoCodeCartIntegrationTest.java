package com.salesmanager.test.shop.integration.cart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.system.MerchantConfigurationService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.system.MerchantConfiguration;
import com.salesmanager.core.model.system.MerchantConfigurationType;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.order.total.ReadableOrderTotal;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public class PromoCodeCartIntegrationTest extends ServicesTestSupport {

	@Autowired
	private MerchantStoreService merchantStoreService;

	@Autowired
	private MerchantConfigurationService merchantConfigurationService;

	private MerchantStore defaultStore;
	private MerchantConfiguration originalConfiguration;
	private String originalValue;
	private MerchantConfigurationType originalType;
	private Boolean originalActive;
	private String createdStoreCode;

	@BeforeEach
	public void removeExistingPromoConfiguration() throws Exception {
		defaultStore = merchantStoreService.getByCode(Constants.DEFAULT_STORE);
		originalConfiguration = merchantConfigurationService.getMerchantConfiguration("PROMO_CODES", defaultStore);
		if (originalConfiguration != null) {
			originalValue = originalConfiguration.getValue();
			originalType = originalConfiguration.getMerchantConfigurationType();
			originalActive = originalConfiguration.getActive();
			merchantConfigurationService.delete(originalConfiguration);
		}
	}

	@AfterEach
	public void cleanUpPromoConfigurationsAndStore() throws Exception {
		deleteConfiguration(defaultStore);
		if (originalConfiguration != null) {
			saveConfiguration(defaultStore, originalValue, originalType, originalActive);
		}

		if (createdStoreCode != null) {
			MerchantStore createdStore = merchantStoreService.getByCode(createdStoreCode);
			if (createdStore != null) {
				deleteConfiguration(createdStore);
				merchantStoreService.delete(createdStore);
			}
		}
	}

	@Test
	public void unconfiguredLegacyPromoHasNoDiscountAndIsHiddenOnRead() throws Exception {
		ReadableShoppingCart cart = createCart();

		ResponseEntity<ReadableShoppingCart> applied = applyPromo(cart.getCode(), "Test1234");

		assertEquals(HttpStatus.CREATED, applied.getStatusCode());
		assertNoDiscount(applied.getBody());

		ResponseEntity<ReadableShoppingCart> fetched = testRestTemplate.getForEntity(
				"/api/v1/cart/" + cart.getCode() + "?store=DEFAULT", ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, fetched.getStatusCode());
		assertNoDiscount(fetched.getBody());
	}

	@Test
	public void configuredPromoDiscountPersistsAndExpiredCodeDoesNotApply() throws Exception {
		ReadableShoppingCart beforePromo = createCart();
		BigDecimal prePromoSubtotal = beforePromo.getSubtotal();
		saveConfiguration(defaultStore, "[" + coupon("IT10", 0.10, today().minusDays(1), today().plusDays(1))
				+ "," + coupon("ITOLD", 0.10, today().minusDays(10), today().minusDays(2)) + "]");

		ResponseEntity<ReadableShoppingCart> applied = applyPromo(beforePromo.getCode(), "it10");

		assertEquals(HttpStatus.CREATED, applied.getStatusCode());
		BigDecimal expectedDiscount = prePromoSubtotal.multiply(new BigDecimal("0.10"));
		assertEquals(0, expectedDiscount.compareTo(discount(applied.getBody())));
		assertEquals(0, beforePromo.getTotal().subtract(expectedDiscount).compareTo(applied.getBody().getTotal()));

		ResponseEntity<ReadableShoppingCart> fetched = testRestTemplate.getForEntity(
				"/api/v1/cart/" + beforePromo.getCode() + "?store=DEFAULT", ReadableShoppingCart.class);
		assertEquals(HttpStatus.OK, fetched.getStatusCode());
		assertEquals(0, expectedDiscount.compareTo(discount(fetched.getBody())));

		ResponseEntity<ReadableShoppingCart> expired = applyPromo(beforePromo.getCode(), "ITOLD");
		assertEquals(HttpStatus.CREATED, expired.getStatusCode());
		assertNoDiscount(expired.getBody());
		assertNull(expired.getBody().getPromoCode());
	}

	@Test
	public void promoCodeConfigurationIsScopedToItsStore() throws Exception {
		ReadableShoppingCart cart = createCart();
		createdStoreCode = "promo-" + UUID.randomUUID().toString().replace("-", "");
		PersistableMerchantStore newStore = new PersistableMerchantStore();
		newStore.setCode(createdStoreCode);
		newStore.setCurrency("CAD");
		newStore.setDefaultLanguage("en");
		newStore.setEmail(createdStoreCode + "@example.test");
		newStore.setName(createdStoreCode);
		newStore.setPhone("444-555-6666");
		newStore.setSupportedLanguages(Collections.singletonList("en"));
		PersistableAddress address = new PersistableAddress();
		address.setAddress("121212 simple address");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("FT LD");
		address.setStateProvince("FL");
		newStore.setAddress(address);

		ResponseEntity<Void> created = testRestTemplate.exchange("/api/v1/private/store/?store=DEFAULT",
				HttpMethod.POST, new HttpEntity<>(newStore, getHeader()), Void.class);
		assertEquals(HttpStatus.OK, created.getStatusCode());
		MerchantStore otherStore = merchantStoreService.getByCode(createdStoreCode);
		assertNotNull(otherStore);
		saveConfiguration(otherStore, "[" + coupon("OTHER10", 0.10, today().minusDays(1), today().plusDays(1))
				+ "]");

		ResponseEntity<ReadableShoppingCart> applied = applyPromo(cart.getCode(), "OTHER10");

		assertEquals(HttpStatus.CREATED, applied.getStatusCode());
		assertNoDiscount(applied.getBody());
		assertNull(applied.getBody().getPromoCode());
	}

	@Test
	public void malformedConfigurationDoesNotApplyPromo() throws Exception {
		ReadableShoppingCart cart = createCart();
		saveConfiguration(defaultStore, "not json");

		ResponseEntity<ReadableShoppingCart> applied = applyPromo(cart.getCode(), "ANYCODE");

		assertEquals(HttpStatus.CREATED, applied.getStatusCode());
		assertNoDiscount(applied.getBody());
		assertNull(applied.getBody().getPromoCode());
	}

	private ReadableShoppingCart createCart() {
		ReadableProduct product = sampleProduct("promo" + UUID.randomUUID().toString().replace("-", ""));
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> response = testRestTemplate.postForEntity("/api/v1/cart/?store=DEFAULT",
				new HttpEntity<>(item, getHeader()), ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		return response.getBody();
	}

	private ResponseEntity<ReadableShoppingCart> applyPromo(String cartCode, String promoCode) {
		return testRestTemplate.exchange(
				"/api/v1/cart/" + cartCode + "/promo/" + promoCode + "?store=DEFAULT",
				HttpMethod.POST, new HttpEntity<>(getHeader()), ReadableShoppingCart.class);
	}

	private BigDecimal discount(ReadableShoppingCart cart) {
		assertNotNull(cart);
		List<ReadableOrderTotal> totals = cart.getTotals();
		assertNotNull(totals);
		return totals.stream()
				.filter(total -> Constants.OT_DISCOUNT_TITLE.equals(total.getCode()))
				.map(ReadableOrderTotal::getValue)
				.findFirst()
				.orElse(null);
	}

	private void assertNoDiscount(ReadableShoppingCart cart) {
		assertNotNull(cart);
		List<ReadableOrderTotal> totals = cart.getTotals();
		assertTrue(totals == null || totals.stream()
				.noneMatch(total -> Constants.OT_DISCOUNT_TITLE.equals(total.getCode())));
	}

	private void saveConfiguration(MerchantStore store, String value) throws Exception {
		saveConfiguration(store, value, MerchantConfigurationType.CONFIG, true);
	}

	private void saveConfiguration(MerchantStore store, String value, MerchantConfigurationType type,
			Boolean active) throws Exception {
		MerchantConfiguration configuration = merchantConfigurationService
				.getMerchantConfiguration("PROMO_CODES", store);
		if (configuration == null) {
			configuration = new MerchantConfiguration();
			configuration.setKey("PROMO_CODES");
			configuration.setMerchantStore(store);
		}
		configuration.setValue(value);
		configuration.setMerchantConfigurationType(type);
		configuration.setActive(active);
		merchantConfigurationService.saveOrUpdate(configuration);
	}

	private void deleteConfiguration(MerchantStore store) throws Exception {
		if (store == null) {
			return;
		}
		MerchantConfiguration configuration = merchantConfigurationService
				.getMerchantConfiguration("PROMO_CODES", store);
		if (configuration != null) {
			merchantConfigurationService.delete(configuration);
		}
	}

	private static String coupon(String code, double discount, LocalDate start, LocalDate end) {
		return String.format(java.util.Locale.ROOT,
				"{\"code\":\"%s\",\"discount\":%s,\"startDate\":\"%s\",\"endDate\":\"%s\",\"active\":true}",
				code, discount, start, end);
	}

	private static LocalDate today() {
		return LocalDate.now(ZoneOffset.UTC);
	}
}
