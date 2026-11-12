package com.salesmanager.core.business.modules.cms.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.infinispan.Cache;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;

public abstract class CacheManagerImpl implements CacheManager {
  protected String location;
  private Cache<String, Object> cache;

  protected void init(String namedCache, String locationFolder) {
    this.location = locationFolder;
    rejectLegacyStore(namedCache);
    EmbeddedCacheManager manager = getManager();
    Configuration config = new ConfigurationBuilder()
        .persistence().passivation(false).connectionAttempts(1)
        .addSingleFileStore().segmented(false)
        .location(location).async().enable()
        .preload(false).shared(false)
        .build();
    manager.defineConfiguration(namedCache, config);
    try {
      cache = manager.getCache(namedCache);
      cache.start();
    } catch (RuntimeException e) {
      throw new IllegalStateException("Cannot start CMS store at " + location
          + ": check permissions and store format; see docs/upgrade/infinispan-15.md", e);
    }
  }

  private void rejectLegacyStore(String namedCache) {
    Path store = Path.of(location, namedCache + ".dat");
    if (!Files.exists(store)) {
      return;
    }
    try (InputStream input = Files.newInputStream(store)) {
      if (Arrays.equals(input.readNBytes(4), new byte[] {'F', 'C', 'S', '1'})) {
        throw new IllegalStateException("Cannot start CMS store at " + location
            + ": stores using the removed Infinispan tree layout must be migrated; "
            + "Infinispan 9.x flat stores also require offline migration. "
            + "See docs/upgrade/infinispan-15.md",
            new IOException("Unsupported pre-11 SingleFileStore: " + store));
      }
    } catch (IOException e) {
      throw new IllegalStateException("Cannot inspect CMS store at " + store, e);
    }
  }

  public EmbeddedCacheManager getManager() {
    return VendorCacheManager.getInstance().getManager();
  }

  public Cache<String, Object> getCache() {
    return cache;
  }
}
