package com.salesmanager.shop.store.facade.user;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.salesmanager.shop.store.api.exception.TooManyRequestsException;

class PasswordChangeAttemptLimiterTest {

	private Instant now = Instant.parse("2026-01-01T00:00:00Z");

	private final PasswordChangeAttemptLimiter limiter = new PasswordChangeAttemptLimiter(new Clock() {
		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	});

	@Test
	void locksAfterMaxFailuresAndUnlocksAfterDuration() {
		for (int i = 0; i < PasswordChangeAttemptLimiter.MAX_FAILED_ATTEMPTS - 1; i++) {
			limiter.recordFailure(1L);
		}
		assertDoesNotThrow(() -> limiter.checkAllowed(1L));

		limiter.recordFailure(1L);
		assertThrows(TooManyRequestsException.class, () -> limiter.checkAllowed(1L));
		assertDoesNotThrow(() -> limiter.checkAllowed(2L));

		now = now.plus(PasswordChangeAttemptLimiter.LOCK_DURATION);
		assertDoesNotThrow(() -> limiter.checkAllowed(1L));
	}

	@Test
	void resetClearsFailures() {
		for (int i = 0; i < PasswordChangeAttemptLimiter.MAX_FAILED_ATTEMPTS - 1; i++) {
			limiter.recordFailure(1L);
		}
		limiter.reset(1L);
		limiter.recordFailure(1L);
		assertDoesNotThrow(() -> limiter.checkAllowed(1L));
	}

	@Test
	void failuresOutsideWindowDoNotAccumulate() {
		for (int i = 0; i < PasswordChangeAttemptLimiter.MAX_FAILED_ATTEMPTS - 1; i++) {
			limiter.recordFailure(1L);
		}
		now = now.plus(PasswordChangeAttemptLimiter.LOCK_DURATION);
		limiter.recordFailure(1L);
		assertDoesNotThrow(() -> limiter.checkAllowed(1L));
	}
}
