package com.salesmanager.core.business.configuration;

import java.net.URISyntaxException;
import javax.cache.CacheManager;
import javax.cache.Caching;

public final class JCacheConfiguration {
  private JCacheConfiguration() {}

  public static CacheManager createCacheManager() throws URISyntaxException {
    return Caching.getCachingProvider("org.ehcache.jsr107.EhcacheCachingProvider")
        .getCacheManager(JCacheConfiguration.class.getResource("/spring/ehcache.xml").toURI(),
            JCacheConfiguration.class.getClassLoader());
  }
}
