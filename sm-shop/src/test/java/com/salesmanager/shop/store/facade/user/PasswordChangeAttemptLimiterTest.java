package com.salesmanager.shop.store.facade.user;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.salesmanager.shop.store.api.exception.TooManyRequestsException;

class PasswordChangeAttemptLimiterTest {

	private volatile Instant now = Instant.parse("2026-01-01T00:00:00Z");

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

	private void acquireAllowed(Long userId) {
		for (int i = 0; i < PasswordChangeAttemptLimiter.MAX_FAILED_ATTEMPTS; i++) {
			limiter.acquire(userId);
		}
	}

	@Test
	void locksAfterMaxAttemptsAndUnlocksAfterDuration() {
		acquireAllowed(1L);
		assertThrows(TooManyRequestsException.class, () -> limiter.acquire(1L));
		assertDoesNotThrow(() -> limiter.acquire(2L));

		now = now.plus(PasswordChangeAttemptLimiter.LOCK_DURATION);
		assertDoesNotThrow(() -> limiter.acquire(1L));
	}

	@Test
	void resetClearsAttempts() {
		acquireAllowed(1L);
		limiter.reset(1L);
		assertDoesNotThrow(() -> limiter.acquire(1L));
	}

	@Test
	void attemptsOutsideWindowDoNotAccumulate() {
		for (int i = 0; i < PasswordChangeAttemptLimiter.MAX_FAILED_ATTEMPTS - 1; i++) {
			limiter.acquire(1L);
		}
		now = now.plus(PasswordChangeAttemptLimiter.LOCK_DURATION);
		acquireAllowed(1L);
		assertThrows(TooManyRequestsException.class, () -> limiter.acquire(1L));
	}

	@Test
	void concurrentAttemptsCannotExceedLimit() throws Exception {
		int threads = 50;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> results = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			results.add(pool.submit(() -> {
				start.await();
				try {
					limiter.acquire(1L);
					return true;
				} catch (TooManyRequestsException e) {
					return false;
				}
			}));
		}
		start.countDown();
		int allowed = 0;
		for (Future<Boolean> result : results) {
			if (result.get(10, TimeUnit.SECONDS)) {
				allowed++;
			}
		}
		pool.shutdown();
		assertEquals(PasswordChangeAttemptLimiter.MAX_FAILED_ATTEMPTS, allowed);
	}

	@Test
	void expiredEntriesArePurged() {
		for (long id = 1; id <= 100; id++) {
			limiter.acquire(id);
		}
		assertEquals(100, limiter.trackedUsers());

		now = now.plus(PasswordChangeAttemptLimiter.LOCK_DURATION);
		limiter.acquire(1000L);
		assertEquals(1, limiter.trackedUsers());
	}
}
