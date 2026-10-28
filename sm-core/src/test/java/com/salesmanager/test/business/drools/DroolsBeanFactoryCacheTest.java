package com.salesmanager.test.business.drools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.configuration.DroolsBeanFactory;
import com.salesmanager.core.business.modules.integration.shipping.impl.CustomShippingQuoteRules;
import com.salesmanager.core.business.modules.integration.shipping.impl.DecisionResponse;
import com.salesmanager.core.business.modules.integration.shipping.impl.ShippingDecisionPreProcessorImpl;
import com.salesmanager.core.business.modules.integration.shipping.impl.ShippingInputParameters;
import com.salesmanager.core.business.modules.order.total.OrderTotalInputParameters;
import com.salesmanager.core.business.modules.order.total.OrderTotalResponse;
import com.salesmanager.core.business.modules.order.total.PromoCodeCalculatorModule;
import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.country.Country;
import com.salesmanager.core.model.shipping.PackageDetails;
import com.salesmanager.core.model.shipping.ShippingQuote;
import com.salesmanager.core.model.system.IntegrationModule;

public class DroolsBeanFactoryCacheTest {

	private DroolsBeanFactory factory;

	@Before
	public void setUp() {
		factory = new DroolsBeanFactory();
	}

	@After
	public void tearDown() {
		factory.dispose();
	}

	@Test
	public void concurrentCallsShareOneContainer() throws Exception {
		int threadCount = 8;
		CountDownLatch ready = new CountDownLatch(threadCount);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);

		try {
			List<Future<KieContainer>> containers = new ArrayList<>();
			for (int i = 0; i < threadCount; i++) {
				containers.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					return factory.getKieContainer(PromoCodeCalculatorModule.PROMO_RULES);
				}));
			}

			assertTrue(ready.await(10, TimeUnit.SECONDS));
			start.countDown();
			KieContainer first = containers.get(0).get();
			for (Future<KieContainer> container : containers) {
				assertSame(first, container.get());
			}
		} finally {
			start.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	public void containersAreCachedSeparatelyByRuleFile() {
		KieContainer promo = factory.getKieContainer(PromoCodeCalculatorModule.PROMO_RULES);
		KieContainer shipping = factory.getKieContainer("ShippingDecision.drl");

		assertNotSame(promo, shipping);
		assertSame(promo, factory.getKieContainer(PromoCodeCalculatorModule.PROMO_RULES));
		assertSame(shipping, factory.getKieContainer("ShippingDecision.drl"));
	}

	@Test
	public void disposedContainerCanBeRebuiltAndUsed() {
		KieContainer first = factory.getKieContainer(PromoCodeCalculatorModule.PROMO_RULES);
		factory.dispose();

		KieContainer rebuilt = factory.getKieContainer(PromoCodeCalculatorModule.PROMO_RULES);
		assertNotSame(first, rebuilt);

		OrderTotalInputParameters parameters = new OrderTotalInputParameters();
		parameters.setPromoCode("Test1234");
		parameters.setDate(date(2024, Calendar.JANUARY, 15));
		OrderTotalResponse response = new OrderTotalResponse();
		KieSession session = factory.newKieSession(PromoCodeCalculatorModule.PROMO_RULES);
		try {
			session.setGlobal("total", response);
			session.insert(parameters);
			session.fireAllRules();
		} finally {
			session.dispose();
		}

		assertEquals(Double.valueOf(0.10), response.getDiscount());
	}

	@Test
	public void failedContainerBuildIsRetriedOnNextCall() {
		assertThrows(RuntimeException.class, () -> factory.getKieContainer("DoesNotExist.drl"));
		assertThrows(RuntimeException.class, () -> factory.getKieContainer("DoesNotExist.drl"));
	}

	@Test
	public void priceByDistanceRuleSetsCustomPrice() throws Exception {
		String resourcePath = DroolsBeanFactory.RULES_PATH + "PriceByDistance.drl";
		try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
			assertNotNull(input);
			String drl = new String(input.readAllBytes(), StandardCharsets.UTF_8);
			assertTrue(drl.contains("530 kilometers or less"));
		}

		ShippingInputParameters parameters = new ShippingInputParameters();
		parameters.setDistance(1000);
		DecisionResponse response = new DecisionResponse();
		KieSession session = factory.newKieSession("PriceByDistance.drl");
		try {
			session.setGlobal("decision", response);
			session.insert(parameters);
			session.fireAllRules();
		} finally {
			session.dispose();
		}

		assertEquals("140", response.getCustomPrice());
	}

	@Test
	public void promoCodeValidatorEnforcesFormatBoundaries() {
		assertTrue(PromoCodeCalculatorModule.isValidPromoCode("A".repeat(32)));
		assertTrue(PromoCodeCalculatorModule.isValidPromoCode("a"));
		assertTrue(PromoCodeCalculatorModule.isValidPromoCode("-_"));
		assertFalse(PromoCodeCalculatorModule.isValidPromoCode("A".repeat(33)));
		assertFalse(PromoCodeCalculatorModule.isValidPromoCode(" A"));
		assertFalse(PromoCodeCalculatorModule.isValidPromoCode("A "));
		assertFalse(PromoCodeCalculatorModule.isValidPromoCode("é"));
		assertFalse(PromoCodeCalculatorModule.isValidPromoCode("A\n"));
		assertFalse(PromoCodeCalculatorModule.isValidPromoCode("A;B"));
		assertFalse(PromoCodeCalculatorModule.isValidPromoCode("%41"));
	}

	@Test
	public void shippingDecisionPreProcessorDisposesSessionWhenRulesThrow() throws Exception {
		DroolsBeanFactory drools = mock(DroolsBeanFactory.class);
		KieSession session = mock(KieSession.class);
		when(drools.newKieSession("ShippingDecision.drl")).thenReturn(session);
		when(session.fireAllRules()).thenThrow(new IllegalStateException("boom"));

		ShippingDecisionPreProcessorImpl processor = new ShippingDecisionPreProcessorImpl();
		ReflectionTestUtils.setField(processor, "droolsBeanFactory", drools);
		Delivery delivery = new Delivery();
		delivery.setCountry(new Country("CA"));
		List<PackageDetails> packages = Collections.singletonList(new PackageDetails());

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> processor.prePostProcessShippingQuotes(null, packages, null, delivery, null,
						new MerchantStore(), null, new IntegrationModule(), null, Collections.emptyList(),
						Locale.US));

		assertEquals("boom", exception.getMessage());
		verify(drools).newKieSession("ShippingDecision.drl");
		verify(session).dispose();
	}

	@Test
	public void customShippingRulesDisposesSessionWhenRulesThrow() throws Exception {
		DroolsBeanFactory drools = mock(DroolsBeanFactory.class);
		KieSession session = mock(KieSession.class);
		when(drools.newKieSession("PriceByDistance.drl")).thenReturn(session);
		when(session.fireAllRules()).thenThrow(new IllegalStateException("boom"));

		CustomShippingQuoteRules rules = new CustomShippingQuoteRules();
		ReflectionTestUtils.setField(rules, "droolsBeanFactory", drools);
		Delivery delivery = new Delivery();
		delivery.setCountry(new Country("CA"));
		delivery.setPostalCode("H0H0H0");
		List<PackageDetails> packages = Collections.singletonList(new PackageDetails());
		IntegrationModule module = new IntegrationModule();
		module.setCode(CustomShippingQuoteRules.MODULE_CODE);

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> rules.getShippingQuotes(new ShippingQuote(), packages, null, delivery, null,
						new MerchantStore(), null, module, null, Locale.US));

		assertEquals("boom", exception.getMessage());
		verify(drools).newKieSession("PriceByDistance.drl");
		verify(session).dispose();
	}

	private static Date date(int year, int month, int day) {
		Calendar calendar = Calendar.getInstance();
		calendar.clear();
		calendar.set(year, month, day);
		return calendar.getTime();
	}
}
