package com.salesmanager.shop.store.facade.user;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.salesmanager.shop.store.api.exception.TooManyRequestsException;

class PasswordChangeAttemptLimiterEdgeCasesTest {

	private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
	private PasswordChangeAttemptLimiter limiter;

	@BeforeEach
	void setUp() {
		limiter = new PasswordChangeAttemptLimiter(clock);
	}

	@Test
	void lockDurationStartsAtTheFifthFailure() {
		Instant start = clock.instant();
		for (int minute : new int[] { 0, 1, 2, 3, 10 }) {
			clock.setInstant(start.plusSeconds(minute * 60L));
			assertDoesNotThrow(() -> limiter.checkAllowed(1L));
			limiter.recordFailure(1L);
		}

		clock.setInstant(start.plusSeconds(20 * 60L));
		assertThrows(TooManyRequestsException.class, () -> limiter.checkAllowed(1L));

		clock.setInstant(start.plusSeconds(25 * 60L));
		assertDoesNotThrow(() -> limiter.checkAllowed(1L));
	}

	@Test
	void attemptsWhileLockedDoNotExtendTheLock() {
		recordFiveFailures(1L);
		clock.setInstant(clock.instant().plusSeconds(5 * 60L));

		for (int i = 0; i < 3; i++) {
			assertThrows(TooManyRequestsException.class, () -> limiter.checkAllowed(1L));
		}

		clock.setInstant(Instant.parse("2026-01-01T00:15:00Z"));
		assertDoesNotThrow(() -> limiter.checkAllowed(1L));
	}

	@Test
	void resetAfterTheLockingFailureAllowsANewSetOfAttempts() {
		recordFiveFailures(1L);
		assertThrows(TooManyRequestsException.class, () -> limiter.checkAllowed(1L));

		limiter.reset(1L);
		assertDoesNotThrow(() -> limiter.checkAllowed(1L));

		for (int i = 0; i < PasswordChangeAttemptLimiter.MAX_FAILED_ATTEMPTS - 1; i++) {
			limiter.recordFailure(1L);
		}
		assertDoesNotThrow(() -> limiter.checkAllowed(1L));
		limiter.recordFailure(1L);
		assertThrows(TooManyRequestsException.class, () -> limiter.checkAllowed(1L));
	}

	@Test
	void resetOfAnUnknownUserIsANoOp() {
		assertDoesNotThrow(() -> limiter.reset(99L));
		assertDoesNotThrow(() -> limiter.checkAllowed(99L));

		recordFiveFailures(1L);
		limiter.reset(99L);
		assertThrows(TooManyRequestsException.class, () -> limiter.checkAllowed(1L));
	}

	@Test
	void tooManyRequestsExceptionIncludesTheErrorCodeAndUserId() {
		recordFiveFailures(42L);

		TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
				() -> limiter.checkAllowed(42L));

		assertEquals("429", exception.getErrorCode());
		assertTrue(exception.getErrorMessage().contains("42"));
	}

	private void recordFiveFailures(Long userId) {
		for (int i = 0; i < PasswordChangeAttemptLimiter.MAX_FAILED_ATTEMPTS; i++) {
			limiter.recordFailure(userId);
		}
	}

	private static final class MutableClock extends Clock {

		private Instant instant;

		private MutableClock(Instant instant) {
			this.instant = instant;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}

		private void setInstant(Instant instant) {
			this.instant = instant;
		}
	}
}
