package com.salesmanager.test.business.modules.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.salesmanager.core.business.modules.integration.shipping.impl.DecisionResponse;
import com.salesmanager.core.business.modules.integration.shipping.impl.PriceByDistanceRules;
import com.salesmanager.core.business.modules.integration.shipping.impl.ShippingDecisionRules;
import com.salesmanager.core.business.modules.integration.shipping.impl.ShippingInputParameters;
import com.salesmanager.core.business.modules.order.total.OrderTotalInputParameters;
import com.salesmanager.core.business.modules.order.total.OrderTotalResponse;
import com.salesmanager.core.business.modules.order.total.PromoCouponRules;

/**
 * Expected values were recorded by running the former Drools DRL files
 * (Drools 7.32) against the same inputs.
 */
class BusinessRulesTest {

	@ParameterizedTest
	@CsvSource({ "0,140", "40,140", "530,140", "531,140", "2550,140", "3550,140", "3551,", "5000," })
	void priceByDistance(long distance, String expectedPrice) {
		ShippingInputParameters input = new ShippingInputParameters();
		input.setDistance(distance);
		DecisionResponse decision = new DecisionResponse();

		PriceByDistanceRules.apply(input, decision);

		assertEquals(expectedPrice, decision.getCustomPrice());
	}

	@ParameterizedTest
	@CsvSource({
		"10,10,CA,QC,canadapost", "10,10,CA,ON,canadapost", "10,10,CA,,canadapost",
		"10,10,US,QC,", "10,10,,,",
		"10,66,CA,QC,", "62,10,CA,QC,", "62,66,CA,QC,",
		"10,70,CA,QC,priceByDistance", "62,70,CA,QC,priceByDistance", "70,10,CA,QC,priceByDistance",
		"70,66,CA,QC,priceByDistance", "70,70,CA,QC,priceByDistance",
		"70,70,CA,ON,", "70,70,CA,,", "70,70,US,QC,", "70,70,,QC," })
	void shippingDecision(long weight, long size, String country, String province, String expectedModule) {
		ShippingInputParameters input = new ShippingInputParameters();
		input.setWeight(weight);
		input.setSize(size);
		input.setCountry(country);
		input.setProvince(province);
		DecisionResponse decision = new DecisionResponse();

		ShippingDecisionRules.apply(input, decision);

		assertEquals(expectedModule, decision.getModuleName());
	}

	@ParameterizedTest
	@CsvSource({
		"Test1234,2025-10-30T23:59,0.1", "Test1234,2025-10-31T00:00,", "Test1234,2026-10-02T10:00,",
		"test1234,2025-10-30T23:59,", "OTHER,2025-10-30T23:59," })
	void promoCoupon(String code, LocalDateTime date, Double expectedDiscount) {
		OrderTotalInputParameters input = new OrderTotalInputParameters();
		input.setPromoCode(code);
		input.setDate(Date.from(date.atZone(ZoneId.systemDefault()).toInstant()));
		OrderTotalResponse total = new OrderTotalResponse();

		PromoCouponRules.apply(input, total);

		assertEquals(expectedDiscount, total.getDiscount());
	}

	@Test
	void promoCouponWithoutDate() {
		OrderTotalInputParameters input = new OrderTotalInputParameters();
		input.setPromoCode("Test1234");
		input.setDate(null);
		OrderTotalResponse total = new OrderTotalResponse();

		PromoCouponRules.apply(input, total);

		assertNull(total.getDiscount());
	}

}
