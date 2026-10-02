package com.salesmanager.core.business.modules.order.total;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

/**
 * Promotion codes applied as a discount on the order total.
 */
public final class PromoCouponRules {

	private static final String PROMO_CODE = "Test1234";
	private static final LocalDate PROMO_END = LocalDate.of(2025, 10, 31);
	private static final double PROMO_DISCOUNT = 0.10;

	private PromoCouponRules() {
	}

	public static void apply(OrderTotalInputParameters input, OrderTotalResponse total) {
		Date end = Date.from(PROMO_END.atStartOfDay(ZoneId.systemDefault()).toInstant());
		if (PROMO_CODE.equals(input.getPromoCode()) && input.getDate() != null && input.getDate().before(end)) {
			total.setDiscount(PROMO_DISCOUNT);
		}
	}

}
