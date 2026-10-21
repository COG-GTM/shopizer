package com.salesmanager.test.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.time.Duration;
import java.net.URL;
import java.net.URLClassLoader;

import javax.cache.Caching;
import javax.cache.Cache;
import javax.cache.CacheManager;

import org.ehcache.config.CacheRuntimeConfiguration;
import org.ehcache.config.ResourceType;
import org.ehcache.jsr107.Eh107Configuration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;

public class EhcacheRegionConfigTest {

	private CacheManager cacheManager;
	private URLClassLoader cacheClassLoader;
	private final Map<String, RegionConfiguration> expectedRegions = new LinkedHashMap<>();

	@Before
	public void setUp() throws Exception {
		cacheClassLoader = new URLClassLoader(new URL[0], getClass().getClassLoader());
		cacheManager = Caching.getCachingProvider("org.ehcache.jsr107.EhcacheCachingProvider")
				.getCacheManager(new ClassPathResource("spring/ehcache.xml").getURI(),
						cacheClassLoader);
		expectedRegions.put("com.shopizer.OBJECT_CACHE", new RegionConfiguration(10000, 1200));
		expectedRegions.put("store", new RegionConfiguration(1000, null));
		expectedRegions.put("countriesMap", new RegionConfiguration(500, null));
		expectedRegions.put("countrByCode", new RegionConfiguration(300, null));
		expectedRegions.put("zoneByCode", new RegionConfiguration(800, null));
		expectedRegions.put("languageByCode", new RegionConfiguration(50, null));
		expectedRegions.put("com.salesmanager.core.model.reference.currency.Currency",
				new RegionConfiguration(10000, 120));
		expectedRegions.put("com.salesmanager.core.model.reference.country.Country",
				new RegionConfiguration(10000, 120));
		expectedRegions.put("com.salesmanager.core.model.reference.language.Language",
				new RegionConfiguration(10000, 120));
		expectedRegions.put("default-query-results-region", new RegionConfiguration(10000, 120));
		expectedRegions.put("default-update-timestamps-region", new RegionConfiguration(10000, 120));
	}

	@After
	public void tearDown() throws Exception {
		cacheManager.close();
		cacheClassLoader.close();
	}

	@Test
	public void allRegionsHaveExpectedHeapAndExpiryConfiguration() {
		Set<String> cacheNames = new HashSet<>();
		cacheManager.getCacheNames().forEach(cacheNames::add);
		assertTrue(cacheNames.containsAll(expectedRegions.keySet()));
		assertTrue(cacheNames.containsAll(Arrays.asList(
				"com.shopizer.OBJECT_CACHE", "store", "countriesMap", "countrByCode", "zoneByCode",
				"languageByCode", "com.salesmanager.core.model.reference.currency.Currency",
				"com.salesmanager.core.model.reference.country.Country",
				"com.salesmanager.core.model.reference.language.Language",
				"default-query-results-region", "default-update-timestamps-region")));

		for (Map.Entry<String, RegionConfiguration> entry : expectedRegions.entrySet()) {
			Cache<Object, Object> cache = cacheManager.getCache(entry.getKey());
			assertNotNull(entry.getKey(), cache);
			Eh107Configuration<?, ?> configuration = cache.getConfiguration(Eh107Configuration.class);
			CacheRuntimeConfiguration runtimeConfiguration =
					configuration.unwrap(CacheRuntimeConfiguration.class);
			assertEquals(entry.getKey(), entry.getValue().heapEntries,
					runtimeConfiguration.getResourcePools().getPoolForResource(ResourceType.Core.HEAP).getSize());

			Duration expiry = runtimeConfiguration.getExpiryPolicy().getExpiryForCreation("key", "value");
			if (entry.getValue().ttlSeconds == null) {
				assertTrue(entry.getKey(), expiry == null || expiry.equals(Duration.ofNanos(Long.MAX_VALUE)));
			} else {
				assertNotNull(entry.getKey(), expiry);
				assertEquals(entry.getKey(), entry.getValue().ttlSeconds.longValue(), expiry.getSeconds());
			}
		}
	}

	private static final class RegionConfiguration {
		private final long heapEntries;
		private final Long ttlSeconds;

		private RegionConfiguration(long heapEntries, Integer ttlSeconds) {
			this.heapEntries = heapEntries;
			this.ttlSeconds = ttlSeconds == null ? null : ttlSeconds.longValue();
		}
	}
}
