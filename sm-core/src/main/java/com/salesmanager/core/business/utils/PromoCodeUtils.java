package com.salesmanager.core.business.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

public final class PromoCodeUtils {

	public static final Duration PROMO_CODE_VALIDITY = Duration.ofDays(1);

	private PromoCodeUtils() {
	}

	/**
	 * A promo code applied to a cart stays valid for {@link #PROMO_CODE_VALIDITY}
	 * after it was added. A missing added date is treated as expired.
	 */
	public static boolean isPromoCodeActive(Date promoAdded, Instant now) {
		if (promoAdded == null || now == null) {
			return false;
		}
		Instant addedAt = promoAdded.toInstant();
		if (addedAt.isAfter(now)) {
			return false;
		}
		return now.isBefore(addedAt.plus(PROMO_CODE_VALIDITY));
	}

	public static boolean isPromoCodeActive(Date promoAdded) {
		return isPromoCodeActive(promoAdded, Instant.now());
	}
}
