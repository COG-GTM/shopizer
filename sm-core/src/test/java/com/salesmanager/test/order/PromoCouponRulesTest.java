package com.salesmanager.test.order;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Calendar;
import java.util.TimeZone;

import org.junit.Test;
import org.kie.api.runtime.KieSession;
import org.kie.internal.io.ResourceFactory;

import com.salesmanager.core.business.configuration.DroolsBeanFactory;
import com.salesmanager.core.business.modules.order.total.OrderTotalInputParameters;
import com.salesmanager.core.business.modules.order.total.OrderTotalResponse;

public class PromoCouponRulesTest {

	private static final String RULE = "com/salesmanager/drools/rules/PromoCoupon.drl";

	@Test
	public void discountsTestCouponBeforeExpiration() {
		assertDiscount("Test1234", date(2025, Calendar.OCTOBER, 1), 0.10);
		assertDiscount("Test1234", date(2025, Calendar.OCTOBER, 30), 0.10);
	}

	@Test
	public void doesNotDiscountExpiredOrNonMatchingCoupons() {
		assertDiscount("Test1234", date(2025, Calendar.NOVEMBER, 15), null);
		assertDiscount("OTHER", date(2025, Calendar.OCTOBER, 1), null);
		assertDiscount("test1234", date(2025, Calendar.OCTOBER, 1), null);
	}

	private void assertDiscount(String promoCode, java.util.Date date, Double expected) {
		OrderTotalInputParameters input = new OrderTotalInputParameters();
		input.setPromoCode(promoCode);
		input.setDate(date);

		OrderTotalResponse total = new OrderTotalResponse();
		KieSession session = new DroolsBeanFactory().getKieSession(ResourceFactory.newClassPathResource(RULE));
		try {
			session.insert(input);
			session.setGlobal("total", total);
			session.fireAllRules();
		} finally {
			session.dispose();
		}

		if (expected == null) {
			assertNull(total.getDiscount());
		} else {
			assertEquals(expected, total.getDiscount(), 0.0);
		}
	}

	private java.util.Date date(int year, int month, int day) {
		Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		calendar.clear();
		calendar.set(year, month, day, 0, 0, 0);
		return calendar.getTime();
	}
}
