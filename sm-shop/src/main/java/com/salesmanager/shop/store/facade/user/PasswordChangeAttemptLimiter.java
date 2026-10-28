package com.salesmanager.shop.store.facade.user;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.salesmanager.shop.store.api.exception.TooManyRequestsException;

/**
 * Locks password changes for a user after repeated wrong current-password attempts.
 */
@Component
public class PasswordChangeAttemptLimiter {

	static final int MAX_FAILED_ATTEMPTS = 5;
	static final Duration LOCK_DURATION = Duration.ofMinutes(15);

	private final Map<Long, Attempts> attempts = new ConcurrentHashMap<>();
	private final Clock clock;

	public PasswordChangeAttemptLimiter() {
		this(Clock.systemUTC());
	}

	PasswordChangeAttemptLimiter(Clock clock) {
		this.clock = clock;
	}

	public void checkAllowed(Long userId) {
		Attempts current = attempts.get(userId);
		if (current == null) {
			return;
		}
		Instant now = clock.instant();
		if (current.isExpired(now)) {
			attempts.remove(userId, current);
			return;
		}
		if (current.lockedUntil != null) {
			throw new TooManyRequestsException(
					"Too many failed password change attempts for user [" + userId + "], try again later");
		}
	}

	public void recordFailure(Long userId) {
		Instant now = clock.instant();
		attempts.compute(userId, (id, current) -> {
			Attempts next = (current == null || current.isExpired(now)) ? new Attempts(now) : current;
			next.failures++;
			if (next.failures >= MAX_FAILED_ATTEMPTS) {
				next.lockedUntil = now.plus(LOCK_DURATION);
			}
			return next;
		});
	}

	public void reset(Long userId) {
		attempts.remove(userId);
	}

	private static final class Attempts {
		private final Instant firstFailure;
		private int failures;
		private Instant lockedUntil;

		private Attempts(Instant firstFailure) {
			this.firstFailure = firstFailure;
		}

		private boolean isExpired(Instant now) {
			if (lockedUntil != null) {
				return !now.isBefore(lockedUntil);
			}
			return !now.isBefore(firstFailure.plus(LOCK_DURATION));
		}
	}
}
