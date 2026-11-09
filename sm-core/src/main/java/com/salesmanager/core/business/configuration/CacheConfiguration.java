package com.salesmanager.core.business.configuration;

import java.io.IOException;
import java.net.URISyntaxException;

import javax.cache.Caching;

import org.springframework.cache.Cache;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * Spring cache abstraction backed by Ehcache 3 through JCache (JSR-107).
 */
@Configuration
public class CacheConfiguration {

	public static final String OBJECT_CACHE = "com.shopizer.OBJECT_CACHE";

	private static final String EHCACHE_CONFIG = "spring/ehcache.xml";

	@Bean(destroyMethod = "close")
	public javax.cache.CacheManager springCacheManager() throws IOException, URISyntaxException {
		return Caching.getCachingProvider("org.ehcache.jsr107.EhcacheCachingProvider")
				.getCacheManager(new ClassPathResource(EHCACHE_CONFIG).getURL().toURI(), getClass().getClassLoader());
	}

	@Bean
	public JCacheCacheManager serviceCacheManager(javax.cache.CacheManager springCacheManager) {
		return new JCacheCacheManager(springCacheManager);
	}

	@Bean
	public Cache serviceCache(JCacheCacheManager serviceCacheManager) {
		return serviceCacheManager.getCache(OBJECT_CACHE);
	}
}
