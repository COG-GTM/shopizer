package com.salesmanager.test.business.drools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.kie.api.definition.KiePackage;
import org.kie.api.definition.rule.Rule;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.configuration.DroolsBeanFactory;
import com.salesmanager.core.business.modules.integration.shipping.impl.DecisionResponse;
import com.salesmanager.core.business.modules.integration.shipping.impl.ShippingInputParameters;
import com.salesmanager.core.business.modules.order.total.OrderTotalInputParameters;
import com.salesmanager.core.business.modules.order.total.OrderTotalResponse;
import com.salesmanager.core.business.modules.order.total.PromoCodeCalculatorModule;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.OrderSummary;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;

public class DroolsRulesTest {

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
	public void rulesAreCompiledOncePerFile() {
		KieContainer first = factory.getKieContainer(PromoCodeCalculatorModule.PROMO_RULES);
		for (int i = 0; i < 50; i++) {
			factory.newKieSession(PromoCodeCalculatorModule.PROMO_RULES).dispose();
		}
		assertSame(first, factory.getKieContainer(PromoCodeCalculatorModule.PROMO_RULES));
	}

	@Test
	public void eachRuleFileGetsItsOwnRuleBase() {
		assertEquals(Collections.singleton("Bam0520"), ruleNames(PromoCodeCalculatorModule.PROMO_RULES));
		Set<String> shipping = ruleNames("ShippingDecision.drl");
		assertTrue(shipping.contains("Shipping decision Canada Post"));
		assertFalse(shipping.contains("Bam0520"));
		assertFalse(ruleNames("PriceByDistance.drl").isEmpty());
		assertFalse(ruleNames(PromoCodeCalculatorModule.PROMO_RULES).contains("Shipping decision Canada Post"));
	}

	@Test
	public void promoRuleStillApplies() {
		assertEquals(Double.valueOf(0.10), firePromo("Test1234", date(2024, Calendar.JANUARY, 15)));
		assertNull(firePromo("UNKNOWN", date(2024, Calendar.JANUARY, 15)));
	}

	@Test
	public void shippingDecisionRuleStillApplies() {
		ShippingInputParameters params = new ShippingInputParameters();
		params.setCountry("CA");
		params.setWeight(10);
		params.setSize(10);
		DecisionResponse resp = new DecisionResponse();
		KieSession session = factory.newKieSession("ShippingDecision.drl");
		try {
			session.setGlobal("decision", resp);
			session.insert(params);
			session.fireAllRules();
		} finally {
			session.dispose();
		}
		assertEquals("canadapost", resp.getModuleName());
	}

	@Test
	public void invalidPromoCodeSkipsRulesEngine() throws Exception {
		DroolsBeanFactory drools = mock(DroolsBeanFactory.class);
		PromoCodeCalculatorModule module = module(drools);

		for (String code : new String[] { "../../x", "a b", "x".repeat(33), "promo\n" }) {
			assertNull(module.caculateProductPiceVariation(summary(code), new ShoppingCartItem(), null, null,
					new MerchantStore()));
		}
		verify(drools, never()).newKieSession(anyString());
	}

	@Test
	public void promoSessionIsDisposed() throws Exception {
		DroolsBeanFactory drools = mock(DroolsBeanFactory.class);
		KieSession session = mock(KieSession.class);
		when(drools.newKieSession(PromoCodeCalculatorModule.PROMO_RULES)).thenReturn(session);
		when(session.fireAllRules()).thenThrow(new RuntimeException("boom"));

		try {
			module(drools).caculateProductPiceVariation(summary("Test1234"), new ShoppingCartItem(), null, null,
					new MerchantStore());
		} catch (RuntimeException expected) {
			// session must still be disposed
		}
		verify(session).dispose();
	}

	@Test
	public void promoCodeFormat() {
		assertTrue(PromoCodeCalculatorModule.isValidPromoCode("Test1234"));
		assertTrue(PromoCodeCalculatorModule.isValidPromoCode("SUMMER_2024-10"));
		assertFalse(PromoCodeCalculatorModule.isValidPromoCode(null));
		assertFalse(PromoCodeCalculatorModule.isValidPromoCode(""));
		assertFalse(PromoCodeCalculatorModule.isValidPromoCode("x".repeat(33)));
		assertFalse(PromoCodeCalculatorModule.isValidPromoCode("<script>"));
	}

	private Double firePromo(String code, Date date) {
		OrderTotalInputParameters params = new OrderTotalInputParameters();
		params.setPromoCode(code);
		params.setDate(date);
		OrderTotalResponse resp = new OrderTotalResponse();
		KieSession session = factory.newKieSession(PromoCodeCalculatorModule.PROMO_RULES);
		try {
			session.setGlobal("total", resp);
			session.insert(params);
			session.fireAllRules();
		} finally {
			session.dispose();
		}
		return resp.getDiscount();
	}

	private Set<String> ruleNames(String ruleFile) {
		Set<String> names = new HashSet<>();
		for (KiePackage pkg : factory.getKieContainer(ruleFile).getKieBase().getKiePackages()) {
			for (Rule rule : pkg.getRules()) {
				names.add(rule.getName());
			}
		}
		return names;
	}

	private static PromoCodeCalculatorModule module(DroolsBeanFactory drools) {
		PromoCodeCalculatorModule module = new PromoCodeCalculatorModule();
		ReflectionTestUtils.setField(module, "droolsBeanFactory", drools);
		return module;
	}

	private static OrderSummary summary(String promo) {
		OrderSummary summary = new OrderSummary();
		summary.setPromoCode(promo);
		return summary;
	}

	private static Date date(int year, int month, int day) {
		Calendar c = Calendar.getInstance();
		c.clear();
		c.set(year, month, day);
		return c.getTime();
	}
}
