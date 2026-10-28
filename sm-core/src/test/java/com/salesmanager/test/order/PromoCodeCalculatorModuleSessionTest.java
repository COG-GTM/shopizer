package com.salesmanager.test.order;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import org.kie.api.io.Resource;
import org.kie.api.runtime.KieSession;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.configuration.DroolsBeanFactory;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.modules.order.total.OrderTotalInputParameters;
import com.salesmanager.core.business.modules.order.total.OrderTotalResponse;
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

public class PromoCodeCalculatorModuleSessionTest {

	private PromoCodeCalculatorModule module;
	private DroolsBeanFactory droolsBeanFactory;
	private KieSession kieSession;
	private MerchantConfigurationService merchantConfigurationService;
	private MerchantStore store;
	private Product product;
	private ShoppingCartItem item;

	@Before
	public void setUp() throws Exception {
		droolsBeanFactory = mock(DroolsBeanFactory.class);
		kieSession = mock(KieSession.class);
		merchantConfigurationService = mock(MerchantConfigurationService.class);
		PricingService pricingService = mock(PricingService.class);
		FinalPrice finalPrice = new FinalPrice();
		finalPrice.setFinalPrice(new BigDecimal("50.00"));
		when(pricingService.calculateProductPrice(any(Product.class))).thenReturn(finalPrice);
		when(droolsBeanFactory.getKieSession(any(Resource.class))).thenReturn(kieSession);

		module = new PromoCodeCalculatorModule();
		ReflectionTestUtils.setField(module, "droolsBeanFactory", droolsBeanFactory);
		ReflectionTestUtils.setField(module, "pricingService", pricingService);
		ReflectionTestUtils.setField(module, "merchantConfigurationService", merchantConfigurationService);

		store = new MerchantStore();
		store.setCode("DEFAULT");
		product = new Product();
		item = new ShoppingCartItem(product);
		item.setQuantity(2);
	}

	@Test
	public void insertsCanonicalCodeAndStoreDetailsAndDisposesSession() throws Exception {
		configure("[" + coupon("SPRING10", 0.10, today().minusDays(1), today().plusDays(1), true) + "]");
		AtomicReference<OrderTotalResponse> total = captureTotalGlobal();
		when(kieSession.fireAllRules()).thenAnswer(invocation -> {
			total.get().setDiscount(0.10);
			return 1;
		});

		module.caculateProductPiceVariation(summary(" spring10 "), item, product, null, store);

		ArgumentCaptor<OrderTotalInputParameters> input = ArgumentCaptor.forClass(OrderTotalInputParameters.class);
		verify(kieSession).insert(input.capture());
		assertEquals("DEFAULT", input.getValue().getMerchantStoreCode());
		assertEquals(Double.valueOf(0.10), input.getValue().getPromoCodeDiscount());
		assertEquals("SPRING10", input.getValue().getPromoCode());
		verify(kieSession).dispose();
	}

	@Test
	public void disposesSessionWhenRulesThrow() throws Exception {
		configure("[" + coupon("SPRING10", 0.10, today().minusDays(1), today().plusDays(1), true) + "]");
		when(kieSession.fireAllRules()).thenThrow(new IllegalStateException("rule failure"));

		try {
			module.caculateProductPiceVariation(summary("SPRING10"), item, product, null, store);
			fail("Expected the rule exception to propagate");
		} catch (IllegalStateException expected) {
			assertEquals("rule failure", expected.getMessage());
		}

		verify(kieSession).dispose();
	}

	@Test
	public void doesNotCreateSessionWhenNoCodeMatches() throws Exception {
		configure("[" + coupon("OTHER", 0.10, today().minusDays(1), today().plusDays(1), true) + "]");

		assertNull(module.caculateProductPiceVariation(summary("SPRING10"), item, product, null, store));

		verify(droolsBeanFactory, never()).getKieSession(any(Resource.class));
	}

	@Test
	public void blankOrNullPromoCodeDoesNotReadConfiguration() throws Exception {
		assertNull(module.caculateProductPiceVariation(summary("  "), item, product, null, store));
		assertNull(module.caculateProductPiceVariation(summary(null), item, product, null, store));

		verify(merchantConfigurationService, never()).getMerchantConfiguration(anyString(), any(MerchantStore.class));
	}

	@Test
	public void nullAndMalformedConfigurationValuesAreIgnored() throws Exception {
		MerchantConfiguration configuration = new MerchantConfiguration();
		configuration.setKey(PromoCodeCalculatorModule.PROMO_CODES_CONFIGURATION);
		configuration.setMerchantStore(store);
		when(merchantConfigurationService.getMerchantConfiguration(
				eq(PromoCodeCalculatorModule.PROMO_CODES_CONFIGURATION), same(store))).thenReturn(configuration);

		for (String value : new String[] { "[]", "null", "[null]" }) {
			configuration.setValue(value);
			assertNull(module.caculateProductPiceVariation(summary("SPRING10"), item, product, null, store));
		}

		verify(droolsBeanFactory, never()).getKieSession(any(Resource.class));
	}

	@Test
	public void skipsExpiredDuplicateAndUsesFirstValidConfiguredEntry() throws Exception {
		configure("[" + coupon("SPRING10", 0.90, today().minusDays(10), today().minusDays(2), true) + ","
				+ coupon("SPRING10", 0.25, today().minusDays(1), today().plusDays(1), true) + "]");
		PromoCodeCalculatorModule realRulesModule = new PromoCodeCalculatorModule();
		ReflectionTestUtils.setField(realRulesModule, "droolsBeanFactory", new DroolsBeanFactory());
		ReflectionTestUtils.setField(realRulesModule, "pricingService", pricingService());
		ReflectionTestUtils.setField(realRulesModule, "merchantConfigurationService", merchantConfigurationService);

		OrderTotal total = realRulesModule.caculateProductPiceVariation(
				summary("SPRING10"), item, product, null, store);

		assertEquals(Constants.OT_DISCOUNT_TITLE, total.getOrderTotalCode());
		assertEquals(0, new BigDecimal("25.0000").compareTo(total.getValue()));
		assertTrue(total.getValue().compareTo(new BigDecimal("45.00")) < 0);
	}

	private AtomicReference<OrderTotalResponse> captureTotalGlobal() {
		AtomicReference<OrderTotalResponse> total = new AtomicReference<>();
		doAnswer(invocation -> {
			total.set(invocation.getArgument(1));
			return null;
		}).when(kieSession).setGlobal(eq("total"), any(OrderTotalResponse.class));
		return total;
	}

	private void configure(String value) throws Exception {
		MerchantConfiguration configuration = new MerchantConfiguration();
		configuration.setKey(PromoCodeCalculatorModule.PROMO_CODES_CONFIGURATION);
		configuration.setMerchantStore(store);
		configuration.setValue(value);
		when(merchantConfigurationService.getMerchantConfiguration(
				eq(PromoCodeCalculatorModule.PROMO_CODES_CONFIGURATION), same(store))).thenReturn(configuration);
	}

	private static PricingService pricingService() throws Exception {
		PricingService pricingService = mock(PricingService.class);
		FinalPrice finalPrice = new FinalPrice();
		finalPrice.setFinalPrice(new BigDecimal("50.00"));
		when(pricingService.calculateProductPrice(any(Product.class))).thenReturn(finalPrice);
		return pricingService;
	}

	private static OrderSummary summary(String promoCode) {
		OrderSummary summary = new OrderSummary();
		summary.setPromoCode(promoCode);
		return summary;
	}

	private static String coupon(String code, double discount, LocalDate start, LocalDate end, boolean active) {
		return String.format(java.util.Locale.ROOT,
				"{\"code\":\"%s\",\"discount\":%s,\"startDate\":\"%s\",\"endDate\":\"%s\",\"active\":%s}",
				code, discount, start, end, active);
	}

	private static LocalDate today() {
		return LocalDate.now(ZoneOffset.UTC);
	}
}
