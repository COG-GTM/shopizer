package com.salesmanager.core.business.modules.cms.impl;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class CacheManagerImpl implements CacheManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(CacheManagerImpl.class);

  protected String location = null;

  private Cache<String, Object> cache = null;

  protected void init(String namedCache, String locationFolder) {

    try {

      this.location = locationFolder;

      VendorCacheManager manager = VendorCacheManager.getInstance();

      if (manager == null) {
        LOGGER.error("CacheManager is null");
        return;
      }

      Configuration config = new ConfigurationBuilder()
               .build();

      manager.getManager().defineConfiguration(namedCache, config);

      cache = manager.getManager().getCache(namedCache);
      cache.start();

      LOGGER.debug("CMS started");

    } catch (Exception e) {
      LOGGER.error("Error while instantiating CmsImageFileManager", e);
    }

  }

  public EmbeddedCacheManager getManager() {
    return VendorCacheManager.getInstance().getManager();
  }

  public Cache<String, Object> getCache() {
    return cache;
  }

}
