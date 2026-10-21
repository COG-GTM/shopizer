package com.salesmanager.test.shipping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.kie.api.runtime.KieSession;
import org.kie.internal.io.ResourceFactory;

import com.salesmanager.core.business.configuration.DroolsBeanFactory;
import com.salesmanager.core.business.modules.integration.shipping.impl.DecisionResponse;
import com.salesmanager.core.business.modules.integration.shipping.impl.ShippingInputParameters;

public class ShippingDecisionRulesTest {

	private static final String RULE = "com/salesmanager/drools/rules/ShippingDecision.drl";

	@Test
	public void selectsCanadaPostForLightPackageInQuebec() {
		assertModuleName(10, 20, "CA", "QC", "canadapost");
	}

	@Test
	public void selectsDistancePricingForHeavyPackageInQuebec() {
		assertModuleName(70, 78, "CA", "QC", "priceByDistance");
	}

	@Test
	public void selectsDistancePricingForOversizedPackageInQuebec() {
		assertModuleName(10, 70, "CA", "QC", "priceByDistance");
	}

	@Test
	public void leavesModuleUnsetForHeavyPackageInOntario() {
		assertModuleName(70, 20, "CA", "ON", null);
	}

	@Test
	public void leavesModuleUnsetForUsPackage() {
		assertModuleName(10, 20, "US", "NY", null);
	}

	@Test
	public void leavesModuleUnsetAtRuleBoundaries() {
		assertModuleName(62, 66, "CA", "QC", null);
	}

	private void assertModuleName(long weight, long size, String country, String province, String expected) {
		ShippingInputParameters input = new ShippingInputParameters();
		input.setWeight(weight);
		input.setSize(size);
		input.setCountry(country);
		input.setProvince(province);

		DecisionResponse decision = new DecisionResponse();
		KieSession session = new DroolsBeanFactory().getKieSession(ResourceFactory.newClassPathResource(RULE));
		try {
			session.insert(input);
			session.setGlobal("decision", decision);
			session.fireAllRules();
		} finally {
			session.dispose();
		}

		if (expected == null) {
			assertNull(decision.getModuleName());
		} else {
			assertEquals(expected, decision.getModuleName());
		}
	}
}
