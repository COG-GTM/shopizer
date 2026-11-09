package com.salesmanager.core.business.modules.cms.impl;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class CacheManagerImpl implements CacheManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(CacheManagerImpl.class);

  protected String location = null;

  private CmsTreeCache treeCache = null;

  protected void init(String namedCache, String locationFolder) {

    try {

      this.location = locationFolder;

      VendorCacheManager manager = VendorCacheManager.getInstance();

      if (manager == null || manager.getManager() == null) {
        LOGGER.error("CacheManager is null");
        return;
      }

      // one soft-index file store per named cache, several caches may share the same location
      Path storeLocation = Paths.get(location, namedCache).toAbsolutePath().normalize();

      Configuration config = new ConfigurationBuilder()
          .persistence().passivation(false)
          .addSoftIndexFileStore()
          .dataLocation(storeLocation.resolve("data").toString())
          .indexLocation(storeLocation.resolve("index").toString())
          .async().enable()
          .preload(false).shared(false)
          .build();

      manager.getManager().defineConfiguration(namedCache, config);

      final Cache<String, byte[]> cache = manager.getManager().getCache(namedCache);
      treeCache = new CmsTreeCache(cache);

      LOGGER.debug("CMS started");

    } catch (Exception e) {
      LOGGER.error("Error while instantiating CmsImageFileManager", e);
    }

  }

  public EmbeddedCacheManager getManager() {
    return VendorCacheManager.getInstance().getManager();
  }

  public CmsTreeCache getTreeCache() {
    return treeCache;
  }

}
