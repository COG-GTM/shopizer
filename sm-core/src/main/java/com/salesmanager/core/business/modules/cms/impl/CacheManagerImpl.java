package com.salesmanager.core.business.modules.cms.impl;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class CacheManagerImpl implements CacheManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(CacheManagerImpl.class);

  //private static final String LOCATION_PROPERTIES = "location";

  protected String location = null;

  private CmsTreeCache treeCache = null;

  @SuppressWarnings("unchecked")
  protected void init(String namedCache, String locationFolder) {


    try {

      this.location = locationFolder;
      // manager = new DefaultCacheManager(repositoryFileName);

      VendorCacheManager manager = VendorCacheManager.getInstance();

      if (manager == null) {
        LOGGER.error("CacheManager is null");
        return;
      }
      
      
      
      
      
      Configuration config = new ConfigurationBuilder()
    		   .persistence().passivation(false)
    		   .addSingleFileStore()
    		   .segmented(false)
    		   .location(location).async().enable()
    		   .preload(false).shared(false)
    		   .invocationBatching().enable()
    		   .build();
      
      manager.getManager().defineConfiguration(namedCache, config);

      final Cache<String, Object> cache = manager.getManager().getCache(namedCache);
      
      treeCache = new CmsTreeCache(cache);
      cache.start();

      LOGGER.debug("CMS started");



    } catch (Exception e) {
      LOGGER.error("Error while instantiating CmsImageFileManager", e);
    } finally {

    }



  }

  public EmbeddedCacheManager getManager() {
    return VendorCacheManager.getInstance().getManager();
  }

  public CmsTreeCache getTreeCache() {
    return treeCache;
  }



}
