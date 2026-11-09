package com.salesmanager.test.cms;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.salesmanager.core.business.modules.cms.impl.CmsTreeCache;
import com.salesmanager.core.business.modules.cms.impl.CmsTreeNode;

class CmsTreeCacheTest {

	private static final byte[] LOGO = "logo".getBytes(StandardCharsets.UTF_8);
	private static final byte[] IMAGE = "image".getBytes(StandardCharsets.UTF_8);

	@TempDir
	Path storeDir;

	@Test
	void storesEntriesPerNodeAndListsChildren() throws Exception {
		try (DefaultCacheManager manager = new DefaultCacheManager()) {
			manager.defineConfiguration("tree", new ConfigurationBuilder().build());
			CmsTreeCache tree = new CmsTreeCache(manager.getCache("tree"));

			tree.getOrCreateNode("/store-DEFAULT/LOGO/").put("logo.png", LOGO);
			tree.getOrCreateNode("store-DEFAULT/sku1/SMALL").put("a.jpg", IMAGE);
			tree.getOrCreateNode("store-DEFAULT/sku2/SMALL");

			CmsTreeNode logo = tree.getOrCreateNode("store-DEFAULT/LOGO");
			assertArrayEquals(LOGO, logo.get("logo.png"));
			assertEquals(Set.of("logo.png"), logo.getKeys());

			Set<String> children = tree.getOrCreateNode("store-DEFAULT").getChildren().stream()
					.map(CmsTreeNode::getPath).collect(Collectors.toSet());
			assertEquals(Set.of("store-DEFAULT/LOGO", "store-DEFAULT/sku1", "store-DEFAULT/sku2"), children);

			logo.remove("logo.png");
			assertNull(logo.get("logo.png"));
			assertTrue(logo.getKeys().isEmpty());

			tree.removeNode("store-DEFAULT/sku1");
			assertNull(tree.getOrCreateNode("store-DEFAULT/sku1/SMALL").get("a.jpg"));

			tree.removeNode("store-DEFAULT");
			assertTrue(tree.getOrCreateNode("store-DEFAULT").getChildren().isEmpty());
		}
	}

	@Test
	void entriesSurviveRestartWithSoftIndexFileStore() throws Exception {
		ConfigurationBuilder config = new ConfigurationBuilder();
		config.persistence().addSoftIndexFileStore()
				.dataLocation(storeDir.resolve("data").toString())
				.indexLocation(storeDir.resolve("index").toString());

		try (DefaultCacheManager manager = new DefaultCacheManager()) {
			manager.defineConfiguration("files", config.build());
			new CmsTreeCache(manager.getCache("files")).getOrCreateNode("store-DEFAULT/IMAGE").put("a.jpg", IMAGE);
		}

		try (DefaultCacheManager manager = new DefaultCacheManager()) {
			manager.defineConfiguration("files", config.build());
			CmsTreeNode node = new CmsTreeCache(manager.getCache("files")).getOrCreateNode("store-DEFAULT/IMAGE");
			assertArrayEquals(IMAGE, node.get("a.jpg"));
			assertEquals(Set.of("a.jpg"), node.getKeys());
		}
	}

}
