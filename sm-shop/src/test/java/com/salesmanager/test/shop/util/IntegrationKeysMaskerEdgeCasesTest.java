package com.salesmanager.test.shop.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.salesmanager.shop.utils.IntegrationKeysMasker;

public class IntegrationKeysMaskerEdgeCasesTest {

	@Test
	void emptyInputsProduceIndependentEmptyMaps() {
		Map<String, String> empty = Collections.emptyMap();
		assertTrue(IntegrationKeysMasker.mask(empty).isEmpty());
		assertNotSame(empty, IntegrationKeysMasker.mask(empty));
		assertTrue(IntegrationKeysMasker.merge(empty, Collections.singletonMap("key", "old")).isEmpty());
		assertTrue(IntegrationKeysMasker.merge(null, null).isEmpty());
	}

	@Test
	void maskPreservesOrderNullEmptyAndAllPublicKeysWithoutMutatingInput() {
		Map<String, String> input = new LinkedHashMap<>();
		input.put("password", "secret");
		input.put("null", null);
		input.put("empty", "");
		input.put("address", "Main Street");
		input.put("note", "Pickup");
		input.put("price", "12.50");
		input.put("Price", "case-sensitive");
		input.put("space", " ");
		input.put("literal", IntegrationKeysMasker.MASK);
		Map<String, String> before = new LinkedHashMap<>(input);
		Map<String, String> result = IntegrationKeysMasker.mask(Collections.unmodifiableMap(input));
		assertEquals(before, input);
		assertEquals(new ArrayList<>(input.keySet()), new ArrayList<>(result.keySet()));
		assertNull(result.get("null"));
		assertTrue(result.containsKey("null"));
		assertEquals("", result.get("empty"));
		for (String key : Arrays.asList("address", "note", "price")) {
			assertEquals(input.get(key), result.get(key));
		}
		for (String key : Arrays.asList("password", "Price", "space", "literal")) {
			assertEquals(IntegrationKeysMasker.MASK, result.get(key));
		}
	}

	@Test
	void mergeWithoutStorageDropsMaskButKeepsNullEmptyAndLiteralValues() {
		Map<String, String> submitted = new LinkedHashMap<>();
		submitted.put("missing", IntegrationKeysMasker.MASK);
		submitted.put("null", null);
		submitted.put("empty", "");
		submitted.put("plain", "new");
		Map<String, String> result = IntegrationKeysMasker.merge(submitted, null);
		assertEquals(Arrays.asList("null", "empty", "plain"), new ArrayList<>(result.keySet()));
		assertNull(result.get("null"));
		assertEquals("", result.get("empty"));
		assertEquals("new", result.get("plain"));
		assertTrue(IntegrationKeysMasker.merge(Collections.singletonMap("missing", IntegrationKeysMasker.MASK),
				Collections.emptyMap()).isEmpty());
	}

	@Test
	void mergePreservesStoredNullEmptyAndMaskWithoutMutatingEitherMap() {
		Map<String, String> stored = new LinkedHashMap<>();
		stored.put("null", null);
		stored.put("empty", "");
		stored.put("literal", IntegrationKeysMasker.MASK);
		stored.put("note", "original note");
		stored.put("omitted", "not submitted");
		Map<String, String> submitted = new LinkedHashMap<>();
		for (String key : Arrays.asList("literal", "null", "empty", "note", "missing")) {
			submitted.put(key, IntegrationKeysMasker.MASK);
		}
		Map<String, String> storedBefore = new LinkedHashMap<>(stored);
		Map<String, String> submittedBefore = new LinkedHashMap<>(submitted);
		Map<String, String> result = IntegrationKeysMasker.merge(Collections.unmodifiableMap(submitted),
				Collections.unmodifiableMap(stored));
		assertEquals(storedBefore, stored);
		assertEquals(submittedBefore, submitted);
		assertEquals(Arrays.asList("literal", "null", "empty", "note"), new ArrayList<>(result.keySet()));
		assertNull(result.get("null"));
		assertEquals("", result.get("empty"));
		assertEquals(IntegrationKeysMasker.MASK, result.get("literal"));
		assertEquals("original note", result.get("note"));
	}
}
