package com.salesmanager.test.shop.common;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Gives every test JVM its own random JWT_SECRET so the embedded test server never
 * accepts tokens signed with a published key.
 */
public class RandomJwtSecretInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	static final String JWT_SECRET = "JWT_SECRET";

	@Override
	public void initialize(ConfigurableApplicationContext context) {
		synchronized (RandomJwtSecretInitializer.class) {
			if (System.getenv(JWT_SECRET) == null && System.getProperty(JWT_SECRET) == null) {
				byte[] key = new byte[64];
				new SecureRandom().nextBytes(key);
				System.setProperty(JWT_SECRET, Base64.getEncoder().encodeToString(key));
			}
		}
	}
}
