package com.salesmanager.test.business.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.cache.ehcache.EhCacheCache;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.utils.CacheUtils;
import com.salesmanager.core.model.merchant.MerchantStore;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;

public class CacheUtilsTest {

	private CacheManager cacheManager;
	private CacheUtils cacheUtils;

	@Before
	public void setUp() {
		Configuration config = new Configuration().name("cacheUtilsTest-" + System.nanoTime());
		cacheManager = CacheManager.newInstance(config);
		net.sf.ehcache.Cache nativeCache = new net.sf.ehcache.Cache(
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
	public void removeAllFromCacheOnlyEvictsKeysOfTheGivenStore() throws Exception {
		cacheUtils.putInCache("a", "1_COUNTRIES");
		cacheUtils.putInCache("b", "1_ZONES_en");
		cacheUtils.putInCache("c", "12_COUNTRIES");
		cacheUtils.putInCache("d", "2_COUNTRIES");
		cacheUtils.putInCache("e", "REF_LANGUAGES");

		cacheUtils.removeAllFromCache(store(1));

		assertNull(cacheUtils.getFromCache("1_COUNTRIES"));
		assertNull(cacheUtils.getFromCache("1_ZONES_en"));
		assertNotNull(cacheUtils.getFromCache("12_COUNTRIES"));
		assertNotNull(cacheUtils.getFromCache("2_COUNTRIES"));
		assertNotNull(cacheUtils.getFromCache("REF_LANGUAGES"));
	}

	@Test
	public void removeAllFromCacheWithoutStoreIdEvictsNothing() throws Exception {
		cacheUtils.putInCache("a", "1_COUNTRIES");

		cacheUtils.removeAllFromCache(new MerchantStore());

		assertEquals("a", cacheUtils.getFromCache("1_COUNTRIES"));
	}

	private static MerchantStore store(int id) {
		MerchantStore store = new MerchantStore();
		store.setId(id);
		return store;
	}
}
