package com.salesmanager.test.order;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.configuration.DroolsBeanFactory;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.modules.order.total.PromoCodeCalculatorModule;
import com.salesmanager.core.business.services.catalog.pricing.PricingService;
import com.salesmanager.core.business.services.system.MerchantConfigurationService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.price.FinalPrice;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.OrderSummary;
import com.salesmanager.core.model.order.OrderTotal;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;
import com.salesmanager.core.model.system.MerchantConfiguration;

public class PromoCodeCalculatorModuleTest {

	private static final String RULES = "com/salesmanager/drools/rules/PromoCoupon.drl";

	private PromoCodeCalculatorModule module;
	private MerchantConfigurationService merchantConfigurationService;
	private MerchantStore acme;
	private MerchantStore globex;
	private Product product;
	private ShoppingCartItem item;

	@Before
	public void setUp() throws Exception {
		merchantConfigurationService = mock(MerchantConfigurationService.class);
		PricingService pricingService = mock(PricingService.class);

		FinalPrice price = new FinalPrice();
		price.setFinalPrice(new BigDecimal("50.00"));
		when(pricingService.calculateProductPrice(any(Product.class))).thenReturn(price);

		module = new PromoCodeCalculatorModule();
		ReflectionTestUtils.setField(module, "droolsBeanFactory", new DroolsBeanFactory());
		ReflectionTestUtils.setField(module, "pricingService", pricingService);
		ReflectionTestUtils.setField(module, "merchantConfigurationService", merchantConfigurationService);

		acme = new MerchantStore();
		acme.setCode("acme");
		globex = new MerchantStore();
		globex.setCode("globex");

		product = new Product();
		item = new ShoppingCartItem(product);
		item.setQuantity(2);
	}

	private void configure(MerchantStore store, String json) throws Exception {
		MerchantConfiguration configuration = new MerchantConfiguration();
		configuration.setKey(PromoCodeCalculatorModule.PROMO_CODES_CONFIGURATION);
		configuration.setMerchantStore(store);
		configuration.setValue(json);
		when(merchantConfigurationService.getMerchantConfiguration(eq(PromoCodeCalculatorModule.PROMO_CODES_CONFIGURATION), same(store)))
				.thenReturn(configuration);
	}

	private OrderTotal apply(String promoCode, MerchantStore store) throws Exception {
		OrderSummary summary = new OrderSummary();
		summary.setPromoCode(promoCode);
		return module.caculateProductPiceVariation(summary, item, product, null, store);
	}

	private static String coupon(String code, double discount, LocalDate start, LocalDate end, boolean active) {
		return String.format(java.util.Locale.ROOT,
				"{\"code\":\"%s\",\"discount\":%s,\"startDate\":\"%s\",\"endDate\":\"%s\",\"active\":%s}",
				code, discount, start, end, active);
	}

	private static LocalDate today() {
		return LocalDate.now(ZoneOffset.UTC);
	}

	@Test
	public void shippedRulesContainNoHardcodedCoupon() throws Exception {
		try (InputStream in = getClass().getClassLoader().getResourceAsStream(RULES)) {
			assertNotNull(in);
			String drl = IOUtils.toString(in, StandardCharsets.UTF_8);
			assertFalse(drl.contains("Test1234"));
		}
	}

	@Test
	public void unconfiguredCodeGivesNoDiscount() throws Exception {
		when(merchantConfigurationService.getMerchantConfiguration(anyString(), any(MerchantStore.class))).thenReturn(null);
		assertNull(apply("Test1234", acme));
		assertNull(apply("Test1234", globex));
	}

	@Test
	public void storeConfiguredCodeAppliesDiscount() throws Exception {
		configure(acme, "[" + coupon("SPRING10", 0.10, today().minusDays(1), today().plusDays(1), true) + "]");

		OrderTotal total = apply("spring10", acme);

		assertNotNull(total);
		assertEquals(Constants.OT_DISCOUNT_TITLE, total.getOrderTotalCode());
		assertEquals(0, new BigDecimal("10.00").compareTo(total.getValue()));
	}

	@Test
	public void codeIsScopedToItsStore() throws Exception {
		configure(acme, "[" + coupon("SPRING10", 0.10, today().minusDays(1), today().plusDays(1), true) + "]");

		assertNull(apply("SPRING10", globex));
	}

	@Test
	public void validityWindowIsEnforced() throws Exception {
		configure(acme, "["
				+ coupon("EXPIRED", 0.10, today().minusDays(10), today().minusDays(1), true) + ","
				+ coupon("FUTURE", 0.10, today().plusDays(1), today().plusDays(10), true) + ","
				+ coupon("LASTDAY", 0.10, today().minusDays(10), today(), true) + "]");

		assertNull(apply("EXPIRED", acme));
		assertNull(apply("FUTURE", acme));
		assertNotNull(apply("LASTDAY", acme));
	}

	@Test
	public void inactiveOrOutOfRangeCouponsAreIgnored() throws Exception {
		configure(acme, "["
				+ coupon("OFF", 0.10, today().minusDays(1), today().plusDays(1), false) + ","
				+ coupon("TOOMUCH", 1.5, today().minusDays(1), today().plusDays(1), true) + ","
				+ coupon("NEGATIVE", -0.2, today().minusDays(1), today().plusDays(1), true) + ","
				+ "{\"code\":\"NODATES\",\"discount\":0.1}]");

		assertNull(apply("OFF", acme));
		assertNull(apply("TOOMUCH", acme));
		assertNull(apply("NEGATIVE", acme));
		assertNull(apply("NODATES", acme));
	}

	@Test
	public void malformedConfigurationGivesNoDiscount() throws Exception {
		configure(acme, "not json");

		assertNull(apply("SPRING10", acme));
	}

}
