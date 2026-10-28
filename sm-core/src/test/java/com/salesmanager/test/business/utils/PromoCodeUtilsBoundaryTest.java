package com.salesmanager.test.business.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.junit.Test;

import com.salesmanager.core.business.utils.PromoCodeUtils;

public class PromoCodeUtilsBoundaryTest {

	@Test
	public void oneMillisecondBeforeExpiryIsActive() {
		Instant now = Instant.parse("2026-10-28T12:00:00Z");
		assertTrue(PromoCodeUtils.isPromoCodeActive(
				Date.from(now.minus(Duration.ofDays(1)).plusMillis(1)), now));
	}

	@Test
	public void missingNowIsInactive() {
		assertFalse(PromoCodeUtils.isPromoCodeActive(new Date(), null));
	}
}
