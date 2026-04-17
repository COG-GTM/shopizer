package com.salesmanager.core.business.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.Cache.ValueWrapper;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import com.salesmanager.core.model.merchant.MerchantStore;

@Component("cache")
public class CacheUtils {
	
	
    @Inject
    @Qualifier("serviceCacheManager")
    private CacheManager cacheManager;
	
	
	public final static String REFERENCE_CACHE = "REF";
	
	private static final Logger LOGGER = LoggerFactory.getLogger(CacheUtils.class);

	private final static String KEY_DELIMITER = "_";
	
	private static final String CACHE_NAME = "com.shopizer.OBJECT_CACHE";


	public void putInCache(Object object, String keyName) throws Exception {
		Cache cache = cacheManager.getCache(CACHE_NAME);
		if (cache != null) {
			cache.put(keyName, object);
		}
	}
	

	public Object getFromCache(String keyName) throws Exception {
		Cache cache = cacheManager.getCache(CACHE_NAME);
		if (cache != null) {
			ValueWrapper vw = cache.get(keyName);
			if(vw!=null) {
				return vw.get();
			}
		}
		return null;
		
	}
	
	@SuppressWarnings("unchecked")
	public List<String> getCacheKeys(MerchantStore store) throws Exception {
		List<String> returnKeys = new ArrayList<String>();
		Cache cache = cacheManager.getCache(CACHE_NAME);
		if (cache == null) {
			return returnKeys;
		}
		
		Object nativeCache = cache.getNativeCache();
		
		if (nativeCache instanceof javax.cache.Cache) {
			javax.cache.Cache<Object, Object> jcache = (javax.cache.Cache<Object, Object>) nativeCache;
			for (javax.cache.Cache.Entry<Object, Object> entry : jcache) {
				try {
					String sKey = (String) entry.getKey();
					int delimiterPosition = sKey.indexOf(KEY_DELIMITER);
					if (delimiterPosition > 0 && Character.isDigit(sKey.charAt(0))) {
						String keyRemaining = sKey.substring(delimiterPosition + 1);
						returnKeys.add(keyRemaining);
					}
				} catch (Exception e) {
					LOGGER.error("key cannot be converted to a String or parsed", e);
				}
			}
		} else {
			LOGGER.warn("Unable to retrieve cache keys - unsupported native cache type: {}", 
					nativeCache.getClass().getName());
		}

		return returnKeys;
	}
	
	public void shutDownCache() throws Exception {
		
	}
	
	public void removeFromCache(String keyName) throws Exception {
		Cache cache = cacheManager.getCache(CACHE_NAME);
		if (cache != null) {
			cache.evict(keyName);
		}
	}
	
	@SuppressWarnings("unchecked")
	public void removeAllFromCache(MerchantStore store) throws Exception {
		Cache cache = cacheManager.getCache(CACHE_NAME);
		if (cache == null) {
			return;
		}
		
		Object nativeCache = cache.getNativeCache();
		
		if (nativeCache instanceof javax.cache.Cache) {
			javax.cache.Cache<Object, Object> jcache = (javax.cache.Cache<Object, Object>) nativeCache;
			HashSet<Object> keysToRemove = new HashSet<>();
			for (javax.cache.Cache.Entry<Object, Object> entry : jcache) {
				try {
					String sKey = (String) entry.getKey();
					int delimiterPosition = sKey.indexOf(KEY_DELIMITER);
					if (delimiterPosition > 0 && Character.isDigit(sKey.charAt(0))) {
						keysToRemove.add(entry.getKey());
					}
				} catch (Exception e) {
					LOGGER.error("key cannot be converted to a String or parsed", e);
				}
			}
			jcache.removeAll(keysToRemove);
		} else {
			cache.clear();
		}
	}
	


}
