package com.salesmanager.core.business.configuration;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.cache.Caching;
import javax.cache.spi.CachingProvider;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * Ehcache 3 through JCache (JSR-107). The same {@link javax.cache.CacheManager} backs the Spring cache abstraction
 * and the Hibernate second-level cache (see {@link DataConfiguration}).
 */
@Configuration
public class CacheConfiguration {

	private static final String EHCACHE_PROVIDER = "org.ehcache.jsr107.EhcacheCachingProvider";
	private static final String EHCACHE_CONFIG = "spring/ehcache3.xml";
	public static final String SERVICE_CACHE = "com.shopizer.OBJECT_CACHE";

	@Bean(destroyMethod = "close")
	public javax.cache.CacheManager jCacheManager() throws IOException, URISyntaxException {
		CachingProvider provider = Caching.getCachingProvider(EHCACHE_PROVIDER);
		URI config = new ClassPathResource(EHCACHE_CONFIG).getURL().toURI();
		return provider.getCacheManager(config, getClass().getClassLoader());
	}

	@Bean({ "cacheManager", "serviceCacheManager" })
	public CacheManager cacheManager(javax.cache.CacheManager jCacheManager) {
		return new JCacheCacheManager(jCacheManager);
	}

	@Bean("serviceCache")
	public Cache serviceCache(@Qualifier("cacheManager") CacheManager cacheManager) {
		return cacheManager.getCache(SERVICE_CACHE);
	}

}
