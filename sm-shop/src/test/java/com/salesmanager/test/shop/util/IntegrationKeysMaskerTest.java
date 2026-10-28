package com.salesmanager.test.shop.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.salesmanager.shop.utils.IntegrationKeysMasker;

public class IntegrationKeysMaskerTest {

	@Test
	public void masksCredentialValuesButKeepsKeyNames() {
		Map<String, String> keys = new HashMap<>();
		keys.put("accessKey", "AK-123");
		keys.put("userId", "acme");
		keys.put("password", "s3cret");
		keys.put("price", "5.00");
		keys.put("note", "Pick up at the back door");
		keys.put("empty", "");

		Map<String, String> masked = IntegrationKeysMasker.mask(keys);

		assertEquals(IntegrationKeysMasker.MASK, masked.get("accessKey"));
		assertEquals(IntegrationKeysMasker.MASK, masked.get("userId"));
		assertEquals(IntegrationKeysMasker.MASK, masked.get("password"));
		assertEquals("5.00", masked.get("price"));
		assertEquals("Pick up at the back door", masked.get("note"));
		assertEquals("", masked.get("empty"));
		assertEquals("s3cret", keys.get("password"));
	}

	@Test
	public void maskOfNullIsEmpty() {
		assertTrue(IntegrationKeysMasker.mask(null).isEmpty());
	}

	@Test
	public void mergeKeepsStoredValueForMaskedEntries() {
		Map<String, String> stored = new HashMap<>();
		stored.put("accessKey", "AK-123");
		stored.put("password", "s3cret");

		Map<String, String> submitted = new HashMap<>();
		submitted.put("accessKey", IntegrationKeysMasker.MASK);
		submitted.put("password", "n3w");
		submitted.put("userId", IntegrationKeysMasker.MASK);

		Map<String, String> merged = IntegrationKeysMasker.merge(submitted, stored);

		assertEquals("AK-123", merged.get("accessKey"));
		assertEquals("n3w", merged.get("password"));
		assertFalse(merged.containsKey("userId"));
	}

	@Test
	public void mergeOfNullIsEmpty() {
		assertTrue(IntegrationKeysMasker.merge(null, new HashMap<>()).isEmpty());
	}

}
