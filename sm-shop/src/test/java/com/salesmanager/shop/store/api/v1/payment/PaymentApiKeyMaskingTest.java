package com.salesmanager.shop.store.api.v1.payment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class PaymentApiKeyMaskingTest {

	@Test
	public void classifiesPaymentKeyNames() {
		String[] secretKeys = { "password", "PASSWORD", "passwd", "merchantid", "merchant_id", "secretKey",
				"private_key", "signature", "api", "apiKey", "token", "accessToken", "secret" };
		for (String name : secretKeys) {
			assertTrue(name, PaymentApi.isSecretKey(name));
		}

		String[] publicOrNonSecretKeys = { "address", "transaction", "client", "publishableKey", "public_key",
				"publicKey", null, "" };
		for (String name : publicOrNonSecretKeys) {
			assertFalse(String.valueOf(name), PaymentApi.isSecretKey(name));
		}
	}

	@Test
	public void masksNonEmptySecretsWithoutChangingInput() {
		assertNull(PaymentApi.maskKeys(null));

		Map<String, String> keys = new HashMap<String, String>();
		keys.put("password", "gateway-password");
		keys.put("secretKey", "gateway-secret");
		keys.put("signature", "");
		keys.put("token", null);
		keys.put("address", "Payee address");
		keys.put("publishableKey", "pk_live_123");
		keys.put("transaction", "AUTHORIZECAPTURE");
		Map<String, String> original = new HashMap<String, String>(keys);

		Map<String, String> masked = PaymentApi.maskKeys(keys);

		assertEquals(PaymentApi.MASKED_VALUE, masked.get("password"));
		assertEquals(PaymentApi.MASKED_VALUE, masked.get("secretKey"));
		assertEquals("", masked.get("signature"));
		assertNull(masked.get("token"));
		assertEquals("Payee address", masked.get("address"));
		assertEquals("pk_live_123", masked.get("publishableKey"));
		assertEquals("AUTHORIZECAPTURE", masked.get("transaction"));
		assertEquals(keys.keySet(), masked.keySet());
		assertEquals(original, keys);
	}

	@Test
	public void unmasksSubmittedSecretsAndDropsOmittedKeys() {
		assertNull(PaymentApi.unmaskKeys(null, new HashMap<String, String>()));

		Map<String, String> submitted = new HashMap<String, String>();
		submitted.put("password", PaymentApi.MASKED_VALUE);
		submitted.put("api", PaymentApi.MASKED_VALUE);
		submitted.put("token", PaymentApi.MASKED_VALUE);
		submitted.put("address", PaymentApi.MASKED_VALUE);
		submitted.put("secretKey", "new-secret");
		Map<String, String> original = new HashMap<String, String>(submitted);

		Map<String, String> existing = new HashMap<String, String>();
		existing.put("password", "stored-password");
		existing.put("oldSecret", "not-submitted");
		Map<String, String> originalExisting = new HashMap<String, String>(existing);

		Map<String, String> unmasked = PaymentApi.unmaskKeys(submitted, existing);

		assertEquals("stored-password", unmasked.get("password"));
		assertEquals(PaymentApi.MASKED_VALUE, unmasked.get("token"));
		assertEquals(PaymentApi.MASKED_VALUE, unmasked.get("address"));
		assertEquals("new-secret", unmasked.get("secretKey"));
		assertFalse(unmasked.containsKey("oldSecret"));
		assertEquals(original, submitted);
		assertEquals(originalExisting, existing);

		Map<String, String> withoutExisting = PaymentApi.unmaskKeys(submitted, null);
		assertEquals(PaymentApi.MASKED_VALUE, withoutExisting.get("password"));
	}
}
