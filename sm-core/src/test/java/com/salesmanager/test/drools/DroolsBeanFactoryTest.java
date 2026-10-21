package com.salesmanager.test.drools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;

import org.junit.Test;
import org.kie.api.io.Resource;
import org.kie.api.runtime.KieSession;
import org.kie.internal.io.ResourceFactory;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.configuration.DroolsBeanFactory;
import com.salesmanager.core.business.modules.integration.shipping.impl.DecisionResponse;
import com.salesmanager.core.business.modules.integration.shipping.impl.ShippingInputParameters;
import com.salesmanager.core.business.modules.order.total.OrderTotalInputParameters;
import com.salesmanager.core.business.modules.order.total.OrderTotalResponse;

public class DroolsBeanFactoryTest {

	private static final String RULES_PATH = "com/salesmanager/drools/rules/";

	@Test
	public void resourceSessionsRemainIsolatedWhenReusingFactory() {
		DroolsBeanFactory factory = newFactory();

		KieSession promoSession = factory.getKieSession(rule("PromoCoupon.drl"));
		try {
			assertRules(promoSession, "Bam0520");
			OrderTotalInputParameters input = new OrderTotalInputParameters();
			input.setPromoCode("Test1234");
			input.setDate(date(2025, Calendar.OCTOBER, 1));
			OrderTotalResponse total = new OrderTotalResponse();
			promoSession.insert(input);
			promoSession.setGlobal("total", total);
			promoSession.fireAllRules();
			assertEquals(0.10, total.getDiscount(), 0.0);
		} finally {
			promoSession.dispose();
		}

		assertShippingDecision(factory, "Shipping decision Canada Post", "canadapost");
		assertPriceByDistance(factory);
		assertShippingDecision(factory, "Shipping decision Home Delivery", "priceByDistance");
	}

	@Test
	public void configuredSessionCombinesShippingAndPriceRules() {
		KieSession session = newFactory().getKieSession();
		try {
			Set<String> rules = ruleNames(session);
			assertEquals(new HashSet<String>(Arrays.asList(
					"Shipping decision Canada Post",
					"Shipping decision Home Delivery",
					"530 kilometers or less",
					"3550 kilometers or less")), rules);

			ShippingInputParameters input = shippingInput(70, 78, 1000);
			DecisionResponse decision = new DecisionResponse();
			session.insert(input);
			session.setGlobal("decision", decision);
			session.fireAllRules();

			assertEquals("priceByDistance", decision.getModuleName());
			assertEquals("140", decision.getCustomPrice());
		} finally {
			session.dispose();
		}
	}

	private void assertShippingDecision(
			DroolsBeanFactory factory,
			String expectedRule,
			String expectedModule) {
		KieSession session = factory.getKieSession(rule("ShippingDecision.drl"));
		try {
			assertRules(session, "Shipping decision Canada Post", "Shipping decision Home Delivery");
			ShippingInputParameters input = expectedModule.equals("canadapost")
					? shippingInput(10, 20, 0)
					: shippingInput(70, 78, 1000);
			DecisionResponse decision = new DecisionResponse();
			session.insert(input);
			session.setGlobal("decision", decision);
			session.fireAllRules();
			assertEquals(expectedModule, decision.getModuleName());
			assertTrue(ruleNames(session).contains(expectedRule));
		} finally {
			session.dispose();
		}
	}

	private void assertPriceByDistance(DroolsBeanFactory factory) {
		KieSession session = factory.getKieSession(rule("PriceByDistance.drl"));
		try {
			assertRules(session, "530 kilometers or less", "3550 kilometers or less");
			DecisionResponse decision = new DecisionResponse();
			session.insert(shippingInput(10, 10, 1000));
			session.setGlobal("decision", decision);
			session.fireAllRules();
			assertEquals("140", decision.getCustomPrice());
		} finally {
			session.dispose();
		}
	}

	private void assertRules(KieSession session, String... expectedRules) {
		assertEquals(new HashSet<String>(Arrays.asList(expectedRules)), ruleNames(session));
	}

	private Set<String> ruleNames(KieSession session) {
		return session.getKieBase().getKiePackages().stream()
				.flatMap(kiePackage -> kiePackage.getRules().stream())
				.map(rule -> rule.getName())
				.collect(Collectors.toSet());
	}

	private DroolsBeanFactory newFactory() {
		DroolsBeanFactory factory = new DroolsBeanFactory();
		ReflectionTestUtils.setField(factory, "priceByDistance", "PriceByDistance.drl");
		ReflectionTestUtils.setField(factory, "shippingDecision", "ShippingDecision.drl");
		return factory;
	}

	private Resource rule(String fileName) {
		return ResourceFactory.newClassPathResource(RULES_PATH + fileName);
	}

	private ShippingInputParameters shippingInput(long weight, long size, long distance) {
		ShippingInputParameters input = new ShippingInputParameters();
		input.setWeight(weight);
		input.setSize(size);
		input.setDistance(distance);
		input.setCountry("CA");
		input.setProvince("QC");
		return input;
	}

	private java.util.Date date(int year, int month, int day) {
		Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		calendar.clear();
		calendar.set(year, month, day, 0, 0, 0);
		return calendar.getTime();
	}
}
