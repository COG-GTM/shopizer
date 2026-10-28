package com.salesmanager.shop.utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

/**
 * Integration keys of shipping and payment modules hold carrier / gateway
 * credentials. Values are masked before being returned by the admin API and
 * a masked value sent back on update keeps the stored value.
 */
public final class IntegrationKeysMasker {

	public static final String MASK = "********";

	private static final Set<String> NON_SECRET_KEYS = Collections
			.unmodifiableSet(new HashSet<>(Arrays.asList("price", "note", "address")));

	private IntegrationKeysMasker() {
	}

	public static Map<String, String> mask(Map<String, String> keys) {
		Map<String, String> masked = new LinkedHashMap<>();
		if (keys == null) {
			return masked;
		}
		keys.forEach((name, value) -> masked.put(name,
				StringUtils.isEmpty(value) || NON_SECRET_KEYS.contains(name) ? value : MASK));
		return masked;
	}

	public static Map<String, String> merge(Map<String, String> submitted, Map<String, String> stored) {
		Map<String, String> merged = new LinkedHashMap<>();
		if (submitted == null) {
			return merged;
		}
		submitted.forEach((name, value) -> {
			if (MASK.equals(value)) {
				if (stored != null && stored.containsKey(name)) {
					merged.put(name, stored.get(name));
				}
			} else {
				merged.put(name, value);
			}
		});
		return merged;
	}

}
