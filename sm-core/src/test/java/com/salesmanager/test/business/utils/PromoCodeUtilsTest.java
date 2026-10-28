package com.salesmanager.test.business.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.junit.Test;

import com.salesmanager.core.business.utils.PromoCodeUtils;

public class PromoCodeUtilsTest {

	private static final Instant NOW = Instant.parse("2026-10-28T12:00:00Z");

	@Test
	public void promoAddedJustNowIsActive() {
		assertTrue(PromoCodeUtils.isPromoCodeActive(Date.from(NOW), NOW));
	}

	@Test
	public void promoAddedWithinOneDayIsActive() {
		Date added = Date.from(NOW.minus(Duration.ofHours(23)));
		assertTrue(PromoCodeUtils.isPromoCodeActive(added, NOW));
	}

	@Test
	public void promoAddedExactlyOneDayAgoIsExpired() {
		Date added = Date.from(NOW.minus(PromoCodeUtils.PROMO_CODE_VALIDITY));
		assertFalse(PromoCodeUtils.isPromoCodeActive(added, NOW));
	}

	@Test
	public void promoAddedTwoDaysAgoIsExpired() {
		Date added = Date.from(NOW.minus(Duration.ofDays(2)));
		assertFalse(PromoCodeUtils.isPromoCodeActive(added, NOW));
	}

	@Test
	public void promoAddedWeeksAgoIsExpired() {
		Date added = Date.from(NOW.minus(Duration.ofDays(30)));
		assertFalse(PromoCodeUtils.isPromoCodeActive(added, NOW));
	}

	@Test
	public void missingAddedDateIsExpired() {
		assertFalse(PromoCodeUtils.isPromoCodeActive(null, NOW));
	}

	@Test
	public void futureAddedDateIsRejected() {
		Date added = Date.from(NOW.plus(Duration.ofDays(1)));
		assertFalse(PromoCodeUtils.isPromoCodeActive(added, NOW));
	}
}
