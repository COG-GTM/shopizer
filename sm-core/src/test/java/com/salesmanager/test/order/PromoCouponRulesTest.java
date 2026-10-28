package com.salesmanager.test.order;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.time.Instant;
import java.util.Date;

import org.junit.Test;
import org.kie.api.runtime.KieSession;
import org.kie.internal.io.ResourceFactory;

import com.salesmanager.core.business.configuration.DroolsBeanFactory;
import com.salesmanager.core.business.modules.order.total.OrderTotalInputParameters;
import com.salesmanager.core.business.modules.order.total.OrderTotalResponse;

public class PromoCouponRulesTest {

	private static final String PROMO_RULES = "com/salesmanager/drools/rules/PromoCoupon.drl";

	@Test
	public void appliesDiscountWhenAllRequiredFieldsAreSet() {
		OrderTotalResponse total = fireRule("DEFAULT", "SPRING10", 0.25, Date.from(Instant.now()));

		assertEquals(Double.valueOf(0.25), total.getDiscount());
	}

	@Test
	public void doesNotApplyDiscountWithoutStoreCode() {
		OrderTotalResponse total = fireRule(null, "SPRING10", 0.25, Date.from(Instant.now()));

		assertNull(total.getDiscount());
	}

	@Test
	public void doesNotApplyDiscountWithoutConfiguredDiscount() {
		OrderTotalResponse total = fireRule("DEFAULT", "SPRING10", null, Date.from(Instant.now()));

		assertNull(total.getDiscount());
	}

	@Test
	public void oldHardcodedCouponTriggerDoesNotApply() {
		OrderTotalResponse total = fireRule(null, "Test1234", null,
				Date.from(Instant.parse("2025-10-30T00:00:00Z")));

		assertNull(total.getDiscount());
	}

	private static OrderTotalResponse fireRule(String storeCode, String promoCode, Double discount, Date date) {
		KieSession session = new DroolsBeanFactory()
				.getKieSession(ResourceFactory.newClassPathResource(PROMO_RULES));
		OrderTotalResponse total = new OrderTotalResponse();
		OrderTotalInputParameters input = new OrderTotalInputParameters();
		input.setMerchantStoreCode(storeCode);
		input.setPromoCode(promoCode);
		input.setPromoCodeDiscount(discount);
		input.setDate(date);
		try {
			session.insert(input);
			session.setGlobal("total", total);
			session.fireAllRules();
			return total;
		} finally {
			session.dispose();
		}
	}
}
