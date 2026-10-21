package com.salesmanager.test.shop.integration.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.cache.CacheManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class CacheWiringIntegrationTest extends ServicesTestSupport {

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private CacheManager jCacheManager;

	@Autowired
	@Qualifier("serviceCacheManager")
	private JCacheCacheManager serviceCacheManager;

	@Test
	public void shopContextUsesOnlyJCacheWithAllConfiguredRegions() throws Exception {
		assertEquals(1, applicationContext.getBeansOfType(CacheManager.class).size());
		assertSame(jCacheManager, serviceCacheManager.getCacheManager());
		Set<String> cacheNames = new HashSet<>();
		jCacheManager.getCacheNames().forEach(cacheNames::add);
		assertTrue(cacheNames.containsAll(Arrays.asList(
				"com.shopizer.OBJECT_CACHE", "store", "countriesMap", "countrByCode", "zoneByCode",
				"languageByCode", "com.salesmanager.core.model.reference.currency.Currency",
				"com.salesmanager.core.model.reference.country.Country",
				"com.salesmanager.core.model.reference.language.Language",
				"default-query-results-region", "default-update-timestamps-region")));
		assertClassNotPresent("net.sf.ehcache.CacheManager");
		assertClassNotPresent("org.infinispan.tree.TreeCache");
	}

	private static void assertClassNotPresent(String className) throws Exception {
		try {
			Class.forName(className);
			fail("Expected " + className + " to be absent from the classpath");
		} catch (ClassNotFoundException expected) {
			return;
		}
	}
}
