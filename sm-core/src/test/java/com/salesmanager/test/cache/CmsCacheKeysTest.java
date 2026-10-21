package com.salesmanager.test.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.salesmanager.core.business.modules.cms.impl.CmsCacheKeys;

public class CmsCacheKeysTest {

	private DefaultCacheManager manager;
	private Cache<String, byte[]> cache;

	@Before
	public void setUp() {
		manager = new DefaultCacheManager(new ConfigurationBuilder().build());
		cache = manager.getCache();
	}

	@After
	public void tearDown() {
		manager.stop();
	}

	@Test
	public void joinSkipsNullAndEmptySegments() {
		assertEquals("a/b", CmsCacheKeys.join("a", null, "", "b"));
		assertEquals("", CmsCacheKeys.join());
		assertEquals("a", CmsCacheKeys.join("a"));
	}

	@Test
	public void directChildrenAreSortedAndExcludeDescendantsAndSiblingPrefixes() {
		cache.put("root/DEFAULT/z", new byte[0]);
		cache.put("root/DEFAULT/a", new byte[0]);
		cache.put("root/DEFAULT/deep/b", new byte[0]);
		cache.put("root/DEFAULT/folder/", new byte[0]);
		cache.put("root/DEFAULT2/x", new byte[0]);
		cache.put("root/DEFAULTX/x", new byte[0]);

		assertEquals(Arrays.asList("root/DEFAULT/a", "root/DEFAULT/z"),
				CmsCacheKeys.directChildren(cache, "root/DEFAULT"));
	}

	@Test
	public void directChildrenOfEmptyCacheAreEmpty() {
		assertEquals(Collections.emptyList(), CmsCacheKeys.directChildren(cache, "root/DEFAULT"));
	}

	@Test
	public void removePrefixRemovesAllDepthsAndFolderMarkersWithoutTouchingSiblings() {
		cache.put("root/DEFAULT/a", new byte[0]);
		cache.put("root/DEFAULT/deep/b", new byte[0]);
		cache.put("root/DEFAULT/folder/", new byte[0]);
		cache.put("root/DEFAULT2/x", new byte[0]);
		cache.put("root/DEFAULTX/x", new byte[0]);

		CmsCacheKeys.removePrefix(cache, "root/DEFAULT/");

		assertFalse(cache.containsKey("root/DEFAULT/a"));
		assertFalse(cache.containsKey("root/DEFAULT/deep/b"));
		assertFalse(cache.containsKey("root/DEFAULT/folder/"));
		assertTrue(cache.containsKey("root/DEFAULT2/x"));
		assertTrue(cache.containsKey("root/DEFAULTX/x"));
	}
}
