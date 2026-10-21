package com.salesmanager.test.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.net.URL;
import java.net.URLClassLoader;

import javax.cache.Caching;
import javax.cache.Cache;
import javax.cache.CacheManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.utils.CacheUtils;
import com.salesmanager.core.model.merchant.MerchantStore;

public class CacheUtilsJCacheTest {

	private CacheManager cacheManager;
	private URLClassLoader cacheClassLoader;
	private Cache<Object, Object> nativeCache;
	private CacheUtils cacheUtils;

	@Before
	public void setUp() throws Exception {
		cacheClassLoader = new URLClassLoader(new URL[0], getClass().getClassLoader());
		cacheManager = Caching.getCachingProvider("org.ehcache.jsr107.EhcacheCachingProvider")
				.getCacheManager(new ClassPathResource("spring/ehcache.xml").getURI(),
						cacheClassLoader);
		JCacheCacheManager springCacheManager = new JCacheCacheManager(cacheManager);
		cacheUtils = new CacheUtils();
		ReflectionTestUtils.setField(cacheUtils, "cache", springCacheManager.getCache("com.shopizer.OBJECT_CACHE"));
		nativeCache = cacheManager.getCache("com.shopizer.OBJECT_CACHE");
	}

	@After
	public void tearDown() throws Exception {
		cacheManager.close();
		cacheClassLoader.close();
	}

	@Test
	public void putAndGetRoundTrip() throws Exception {
		cacheUtils.putInCache("value", "key");

		assertEquals("value", cacheUtils.getFromCache("key"));
	}

	@Test
	public void getOfMissingKeyReturnsNull() throws Exception {
		assertNull(cacheUtils.getFromCache("missing"));
	}

	@Test
	public void getCacheKeysReturnsStoreKeyRemaindersOnly() throws Exception {
		nativeCache.put("1_FOO", "foo");
		nativeCache.put("12_BAR", "bar");
		nativeCache.put("REF_x", "reference");
		nativeCache.put("abc", "plain");
		nativeCache.put("_x", "no-prefix");

		assertEquals(new HashSet<>(Arrays.asList("FOO", "BAR")),
				new HashSet<>(cacheUtils.getCacheKeys(new MerchantStore())));
	}

	@Test
	public void nonStringCacheKeyIsSkipped() throws Exception {
		nativeCache.put(Integer.valueOf(9), "integer");

		assertTrue(cacheUtils.getCacheKeys(new MerchantStore()).isEmpty());
	}

	@Test
	public void removeFromCacheRemovesOnlyTheRequestedKey() throws Exception {
		nativeCache.put("1_FOO", "foo");
		nativeCache.put("12_BAR", "bar");

		cacheUtils.removeFromCache("1_FOO");

		assertFalse(nativeCache.containsKey("1_FOO"));
		assertTrue(nativeCache.containsKey("12_BAR"));
	}

	@Test
	public void removeAllRemovesAllStoreKeysAndRetainsOtherKeys() throws Exception {
		nativeCache.put("1_FOO", "foo");
		nativeCache.put("12_BAR", "bar");
		nativeCache.put("REF_x", "reference");
		nativeCache.put("abc", "plain");
		nativeCache.put(Integer.valueOf(9), "integer");

		cacheUtils.removeAllFromCache(new MerchantStore());

		assertFalse(nativeCache.containsKey("1_FOO"));
		assertFalse(nativeCache.containsKey("12_BAR"));
		assertTrue(nativeCache.containsKey("REF_x"));
		assertTrue(nativeCache.containsKey("abc"));
		assertTrue(nativeCache.containsKey(Integer.valueOf(9)));
	}
}
