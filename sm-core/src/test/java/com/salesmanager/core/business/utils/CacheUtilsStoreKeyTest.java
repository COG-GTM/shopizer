package com.salesmanager.core.business.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.cache.ehcache.EhCacheCache;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.model.merchant.MerchantStore;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.Element;

public class CacheUtilsStoreKeyTest {

	private CacheManager cacheManager;
	private CacheUtils cacheUtils;
	private net.sf.ehcache.Cache nativeCache;

	@Before
	public void setUp() {
		Configuration config = new Configuration().name("cacheUtilsStoreKeyTest-" + System.nanoTime());
		cacheManager = CacheManager.newInstance(config);
		nativeCache = new net.sf.ehcache.Cache(
				new CacheConfiguration("serviceCache", 100).eternal(true));
		cacheManager.addCache(nativeCache);

		cacheUtils = new CacheUtils();
		ReflectionTestUtils.setField(cacheUtils, "cache", new EhCacheCache(nativeCache));
	}

	@After
	public void tearDown() {
		cacheManager.shutdown();
	}

	@Test
	public void isStoreKeyMatchesOnlyTheExactStorePrefix() throws Exception {
		MerchantStore store1 = store(1);
		assertTrue(isStoreKey("1_COUNTRIES", store1));
		assertTrue(isStoreKey("1_ZONES_en", store1));
		assertTrue(isStoreKey("1_", store1));
		assertFalse(isStoreKey("12_COUNTRIES", store1));
		assertFalse(isStoreKey("01_X", store1));
		assertFalse(isStoreKey("2_X", store1));
		assertFalse(isStoreKey("REF_LANGUAGES", store1));
		assertFalse(isStoreKey("_1", store1));
		assertFalse(isStoreKey("1", store1));
		assertFalse(isStoreKey("", store1));

		MerchantStore store12 = store(12);
		assertTrue(isStoreKey("12_X", store12));
		assertFalse(isStoreKey("1_X", store12));
		assertFalse(isStoreKey("123_X", store12));
		assertFalse(isStoreKey(Integer.valueOf(1), store1));
		assertFalse(isStoreKey(null, store1));
		assertFalse(isStoreKey("1_X", null));
		assertFalse(isStoreKey("1_X", new MerchantStore()));
	}

	@Test
	public void removeAllFromCacheEvictsOnlyTheRequestedStoreAndIgnoresNativeKeys() throws Exception {
		nativeCache.put(new Element(5, "non-string"));
		cacheUtils.putInCache("keep", "1_A");
		cacheUtils.putInCache("remove", "2_A");
		cacheUtils.putInCache("keep", "12_A");

		cacheUtils.removeAllFromCache(store(2));

		assertNull(cacheUtils.getFromCache("2_A"));
		assertEquals("keep", cacheUtils.getFromCache("1_A"));
		assertEquals("keep", cacheUtils.getFromCache("12_A"));
		assertNotNull(nativeCache.get(5));
	}

	@Test
	public void removeFromCacheEvictsOnlyTheRequestedKey() throws Exception {
		cacheUtils.putInCache("remove", "1_A");
		cacheUtils.putInCache("keep", "1_B");

		cacheUtils.removeFromCache("1_A");

		assertNull(cacheUtils.getFromCache("1_A"));
		assertEquals("keep", cacheUtils.getFromCache("1_B"));
	}

	private static boolean isStoreKey(Object key, MerchantStore store) throws Exception {
		Method method;
		try {
			method = CacheUtils.class.getDeclaredMethod("isStoreKey", Object.class, MerchantStore.class);
		} catch (NoSuchMethodException absentOnBase) {
			return false;
		}
		return (Boolean) method.invoke(null, key, store);
	}

	private static MerchantStore store(int id) {
		MerchantStore store = new MerchantStore();
		store.setId(id);
		return store;
	}
}
