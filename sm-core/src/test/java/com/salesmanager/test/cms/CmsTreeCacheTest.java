package com.salesmanager.test.cms;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.salesmanager.core.business.modules.cms.impl.CmsNode;
import com.salesmanager.core.business.modules.cms.impl.CmsTreeCache;

class CmsTreeCacheTest {

  @TempDir
  Path storeLocation;

  private DefaultCacheManager startManager() {
    // same persistence settings as CacheManagerImpl
    Configuration config = new ConfigurationBuilder()
        .persistence().passivation(false)
        .addSingleFileStore()
        .segmented(false)
        .location(storeLocation.toString())
        .preload(false).shared(false)
        .invocationBatching().enable()
        .build();
    DefaultCacheManager manager = new DefaultCacheManager();
    manager.defineConfiguration("test-cms", config);
    return manager;
  }

  private CmsTreeCache tree(DefaultCacheManager manager) {
    Cache<String, Object> cache = manager.getCache("test-cms");
    return new CmsTreeCache(cache);
  }

  @Test
  void hierarchicalOperations() throws Exception {
    try (DefaultCacheManager manager = startManager()) {
      CmsTreeCache tree = tree(manager);
      CmsNode root = tree.getRoot();

      assertNull(root.getChild("files/DEFAULT/IMAGE"));
      CmsNode images = root.addChild("files/DEFAULT/IMAGE");
      assertNotNull(root.getChild("/files/DEFAULT/IMAGE/"));
      assertNotNull(root.getChild("files/DEFAULT"));

      images.put("logo.png", new byte[] {1, 2, 3});
      images.put("banner.png", new byte[] {4});
      root.addChild("files/DEFAULT/IMAGE/folder");
      root.addChild("files/OTHER/IMAGE").put("other.png", new byte[] {9});

      assertEquals(Set.of("logo.png", "banner.png"), images.getKeys());
      assertArrayEquals(new byte[] {1, 2, 3}, (byte[]) images.get("logo.png"));
      assertEquals(Set.of("files/DEFAULT/IMAGE/folder"),
          images.getChildren().stream().map(CmsNode::getPath).collect(Collectors.toSet()));
      assertEquals(Set.of("DEFAULT", "OTHER"),
          root.getChild("files").getChildren().stream().map(n -> n.getPath().substring("files/".length()))
              .collect(Collectors.toSet()));

      images.remove("banner.png");
      assertEquals(Set.of("logo.png"), images.getKeys());

      assertTrue(root.removeChild("files/DEFAULT"));
      assertNull(root.getChild("files/DEFAULT/IMAGE"));
      assertNull(root.getChild("files/DEFAULT"));
      assertFalse(root.removeChild("files/DEFAULT"));
      assertArrayEquals(new byte[] {9}, (byte[]) root.getChild("files/OTHER/IMAGE").get("other.png"));
    }
  }

  @Test
  void contentSurvivesRestart() throws Exception {
    try (DefaultCacheManager manager = startManager()) {
      tree(manager).getRoot().addChild("store/DEFAULT").put("file.txt", "hello".getBytes());
    }
    try (DefaultCacheManager manager = startManager()) {
      CmsNode node = tree(manager).getRoot().getChild("store/DEFAULT");
      assertNotNull(node);
      assertArrayEquals("hello".getBytes(), (byte[]) node.get("file.txt"));
    }
  }

}
