package com.salesmanager.shop.store.facade.user;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

import com.salesmanager.shop.store.api.exception.TooManyRequestsException;

/**
 * Limits current-password guesses on the change password flow. Every attempt is
 * counted when it starts and only cleared by {@link #reset(Long)} after a
 * successful change, so concurrent requests cannot exceed the limit.
 */
@Component
public class PasswordChangeAttemptLimiter {

	static final int MAX_FAILED_ATTEMPTS = 5;
	static final Duration LOCK_DURATION = Duration.ofMinutes(15);

	private final ConcurrentMap<Long, Attempts> attempts = new ConcurrentHashMap<>();
	private final Clock clock;
	private volatile Instant nextPurge;

	public PasswordChangeAttemptLimiter() {
		this(Clock.systemUTC());
	}

	PasswordChangeAttemptLimiter(Clock clock) {
		this.clock = clock;
		this.nextPurge = clock.instant().plus(LOCK_DURATION);
	}

	/**
	 * Registers a password change attempt for the user.
	 *
	 * @throws TooManyRequestsException if the user is locked out
	 */
	public void acquire(Long userId) {
		Instant now = clock.instant();
		purgeExpired(now);
		boolean[] locked = { false };
		attempts.compute(userId, (id, current) -> {
			Attempts next = (current == null || current.isExpired(now)) ? new Attempts(now) : current;
			if (next.lockedUntil != null) {
				locked[0] = true;
				return next;
			}
			next.count++;
			if (next.count >= MAX_FAILED_ATTEMPTS) {
				next.lockedUntil = now.plus(LOCK_DURATION);
			}
			return next;
		});
		if (locked[0]) {
			throw new TooManyRequestsException(
					"Too many failed password change attempts for user [" + userId + "], try again later");
		}
	}

	public void reset(Long userId) {
		attempts.remove(userId);
	}

	int trackedUsers() {
		return attempts.size();
	}

	private void purgeExpired(Instant now) {
		if (now.isBefore(nextPurge)) {
			return;
		}
		nextPurge = now.plus(LOCK_DURATION);
		for (Long id : attempts.keySet()) {
			attempts.computeIfPresent(id, (key, value) -> value.isExpired(now) ? null : value);
		}
	}

	private static final class Attempts {
		private final Instant firstAttempt;
		private int count;
		private Instant lockedUntil;

		private Attempts(Instant firstAttempt) {
			this.firstAttempt = firstAttempt;
		}

		private boolean isExpired(Instant now) {
			if (lockedUntil != null) {
				return !now.isBefore(lockedUntil);
			}
			return !now.isBefore(firstAttempt.plus(LOCK_DURATION));
		}
	}
}
