package com.salesmanager.test.configuration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import org.junit.Test;
import org.kie.api.runtime.KieSession;
import org.kie.internal.io.ResourceFactory;

import com.salesmanager.core.business.configuration.DroolsBeanFactory;
import com.salesmanager.core.business.modules.integration.shipping.impl.DecisionResponse;
import com.salesmanager.core.business.modules.integration.shipping.impl.ShippingInputParameters;
import com.salesmanager.core.business.modules.order.total.OrderTotalInputParameters;
import com.salesmanager.core.business.modules.order.total.OrderTotalResponse;

/**
 * Compiles the bundled .drl / decision table resources and checks the rules fire as expected.
 */
public class DroolsRulesTest {

	private static final String RULES_PATH = "com/salesmanager/drools/rules/";

	private final DroolsBeanFactory droolsBeanFactory = new DroolsBeanFactory();

	private DecisionResponse runShippingRules(String drl, ShippingInputParameters input) {
		KieSession kieSession = droolsBeanFactory.getKieSession(ResourceFactory.newClassPathResource(RULES_PATH + drl));
		DecisionResponse resp = new DecisionResponse();
		try {
			kieSession.insert(input);
			kieSession.setGlobal("decision", resp);
			kieSession.fireAllRules();
		} finally {
			kieSession.dispose();
		}
		return resp;
	}

	@Test
	public void shippingDecisionSelectsCanadaPostForSmallCanadianParcel() {
		ShippingInputParameters input = new ShippingInputParameters();
		input.setWeight(10L);
		input.setSize(20L);
		input.setCountry("CA");
		input.setProvince("QC");

		assertEquals("canadapost", runShippingRules("ShippingDecision.drl", input).getModuleName());
	}

	@Test
	public void shippingDecisionSelectsHomeDeliveryForLargeQuebecParcel() {
		ShippingInputParameters input = new ShippingInputParameters();
		input.setWeight(100L);
		input.setSize(20L);
		input.setCountry("CA");
		input.setProvince("QC");

		assertEquals("priceByDistance", runShippingRules("ShippingDecision.drl", input).getModuleName());
	}

	@Test
	public void shippingDecisionHasNoMatchOutsideCanada() {
		ShippingInputParameters input = new ShippingInputParameters();
		input.setWeight(10L);
		input.setSize(20L);
		input.setCountry("US");
		input.setProvince("NY");

		assertNull(runShippingRules("ShippingDecision.drl", input).getModuleName());
	}

	@Test
	public void priceByDistanceShortDistanceMatchesBothBracketsLastWins() {
		ShippingInputParameters input = new ShippingInputParameters();
		input.setDistance(100L);
		input.setCountry("CA");
		input.setProvince("QC");

		// both "<= 530" and "<= 3550" match; the 3550 bracket fires last
		assertEquals("140", runShippingRules("PriceByDistance.drl", input).getCustomPrice());
	}

	@Test
	public void priceByDistanceHasNoPriceBeyondLongestBracket() {
		ShippingInputParameters input = new ShippingInputParameters();
		input.setDistance(5000L);
		input.setCountry("CA");
		input.setProvince("QC");

		assertNull(runShippingRules("PriceByDistance.drl", input).getCustomPrice());
	}

	@Test
	public void priceByDistanceUsesLongDistancePrice() {
		ShippingInputParameters input = new ShippingInputParameters();
		input.setDistance(2000L);
		input.setCountry("CA");
		input.setProvince("QC");

		assertEquals("140", runShippingRules("PriceByDistance.drl", input).getCustomPrice());
	}

	private OrderTotalResponse runPromoRules(String promoCode, Date date) {
		KieSession kieSession = droolsBeanFactory.getKieSession(ResourceFactory.newClassPathResource(RULES_PATH + "PromoCoupon.drl"));
		OrderTotalResponse resp = new OrderTotalResponse();
		try {
			OrderTotalInputParameters input = new OrderTotalInputParameters();
			input.setPromoCode(promoCode);
			input.setDate(date);
			kieSession.insert(input);
			kieSession.setGlobal("total", resp);
			kieSession.fireAllRules();
		} finally {
			kieSession.dispose();
		}
		return resp;
	}

	@Test
	public void promoCouponAppliesDiscountBeforeExpiration() {
		Date beforeExpiration = Date.from(LocalDate.of(2024, 1, 15).atStartOfDay(ZoneId.systemDefault()).toInstant());
		assertEquals(Double.valueOf(0.10), runPromoRules("Test1234", beforeExpiration).getDiscount());
	}

	@Test
	public void promoCouponIgnoresUnknownCode() {
		assertNull(runPromoRules("UNKNOWN", new Date()).getDiscount());
	}

	@Test
	public void decisionTableCompilesToDrl() {
		String drl = droolsBeanFactory.getDrlFromExcel("/rules/manufacturer-shipping-ordertotal-rules.xls");
		assertNotNull(drl);
	}
}
