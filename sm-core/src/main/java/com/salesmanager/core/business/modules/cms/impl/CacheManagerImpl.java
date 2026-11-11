package com.salesmanager.core.business.modules.cms.impl;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;

public abstract class CacheManagerImpl implements CacheManager {
  protected String location;
  private Cache<String, Object> cache;

  protected void init(String namedCache, String locationFolder) {
    this.location = locationFolder;
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
          + ": stores using the removed Infinispan tree layout must be migrated", e);
    }
  }

  public EmbeddedCacheManager getManager() {
    return VendorCacheManager.getInstance().getManager();
  }

  public Cache<String, Object> getCache() {
    return cache;
  }
}
