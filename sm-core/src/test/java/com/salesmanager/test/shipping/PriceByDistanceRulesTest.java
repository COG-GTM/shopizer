package com.salesmanager.test.shipping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.kie.api.runtime.KieSession;
import org.kie.internal.io.ResourceFactory;

import com.salesmanager.core.business.configuration.DroolsBeanFactory;
import com.salesmanager.core.business.modules.integration.shipping.impl.DecisionResponse;
import com.salesmanager.core.business.modules.integration.shipping.impl.ShippingInputParameters;

public class PriceByDistanceRulesTest {

	private static final String RULES_PATH = "com/salesmanager/drools/rules/";

	@Test
	public void pricesDistancesWithPriceByDistanceRules() {
		assertPrice("PriceByDistance.drl", 1000, "140");
		assertPrice("PriceByDistance.drl", 3550, "140");
		assertPrice("PriceByDistance.drl", 3551, null);
		assertPrice("PriceByDistance.drl", 5000, null);
		assertPrice("PriceByDistance.drl", 0, "140");
		assertPrice("PriceByDistance.drl", 100, "140");
		assertPrice("PriceByDistance.drl", 530, "140");
		assertPrice("PriceByDistance.drl", 531, "140");
	}

	@Test
	public void pricesDistancesWithPriceByDistance2Rules() {
		assertPrice("PriceByDistance2.drl", 0, "75");
		assertPrice("PriceByDistance2.drl", 40, "75");
		assertPrice("PriceByDistance2.drl", 41, "120");
		assertPrice("PriceByDistance2.drl", 80, "120");
		assertPrice("PriceByDistance2.drl", 81, "140");
		assertPrice("PriceByDistance2.drl", 2550, "140");
		assertPrice("PriceByDistance2.drl", 2551, null);
	}

	private void assertPrice(String ruleFile, long distance, String expected) {
		ShippingInputParameters input = new ShippingInputParameters();
		input.setDistance(distance);

		DecisionResponse decision = new DecisionResponse();
		KieSession session = new DroolsBeanFactory().getKieSession(
				ResourceFactory.newClassPathResource(RULES_PATH + ruleFile));
		try {
			session.insert(input);
			session.setGlobal("decision", decision);
			session.fireAllRules();
		} finally {
			session.dispose();
		}

		if (expected == null) {
			assertNull(ruleFile + " distance " + distance, decision.getCustomPrice());
		} else {
			assertEquals(ruleFile + " distance " + distance, expected, decision.getCustomPrice());
		}
	}
}
