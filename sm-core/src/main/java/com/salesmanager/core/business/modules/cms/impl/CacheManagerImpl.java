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
        .persistence().passivation(false)
        .addSingleFileStore().segmented(false)
        .location(location).async().enable()
        .preload(false).shared(false)
        .build();
    manager.defineConfiguration(namedCache, config);
    cache = manager.getCache(namedCache);
    cache.start();
  }

  public EmbeddedCacheManager getManager() {
    return VendorCacheManager.getInstance().getManager();
  }

  public Cache<String, Object> getCache() {
    return cache;
  }
}
