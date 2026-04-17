package com.salesmanager.core.business.utils;

import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.Cache.ValueWrapper;
import org.springframework.stereotype.Component;

import com.salesmanager.core.model.merchant.MerchantStore;

@Component("cache")
public class CacheUtils {
	
	
    @Inject
    @Qualifier("serviceCache")
    private Cache cache;
	
	
	public final static String REFERENCE_CACHE = "REF";
	
	private static final Logger LOGGER = LoggerFactory.getLogger(CacheUtils.class);

	private final static String KEY_DELIMITER = "_";
	


	public void putInCache(Object object, String keyName) throws Exception {

		cache.put(keyName, object);
		
	}
	

	public Object getFromCache(String keyName) throws Exception {

		ValueWrapper vw = cache.get(keyName);
		if(vw!=null) {
			return vw.get();
		}
		
		return null;
		
	}
	
	public List<String> getCacheKeys(MerchantStore store) throws Exception {
		
		  // Spring Cache abstraction does not expose keys directly.
		  // Return empty list as a safe fallback.
		  LOGGER.warn("getCacheKeys is not supported with the current cache abstraction");
		  return new ArrayList<String>();
	}
	
	public void shutDownCache() throws Exception {
		
	}
	
	public void removeFromCache(String keyName) throws Exception {
		cache.evict(keyName);
	}
	
	public void removeAllFromCache(MerchantStore store) throws Exception {
		  // Use native cache to iterate keys and evict only entries belonging to the given store.
		  // Keys follow the pattern: <storeId>_<rest of the key>
		  String storePrefix = String.valueOf(store.getId()) + KEY_DELIMITER;
		  Object nativeCache = cache.getNativeCache();
		  if (nativeCache instanceof java.util.concurrent.ConcurrentMap) {
			  @SuppressWarnings("unchecked")
			  java.util.concurrent.ConcurrentMap<Object, Object> map = (java.util.concurrent.ConcurrentMap<Object, Object>) nativeCache;
			  map.keySet().removeIf(key -> String.valueOf(key).startsWith(storePrefix));
		  } else if (nativeCache instanceof org.ehcache.Cache) {
			  // EHCache 3: iterate entries and remove matching keys
			  @SuppressWarnings("unchecked")
			  org.ehcache.Cache<Object, Object> ehCache = (org.ehcache.Cache<Object, Object>) nativeCache;
			  java.util.List<Object> keysToRemove = new java.util.ArrayList<>();
			  for (org.ehcache.Cache.Entry<Object, Object> entry : ehCache) {
				  String sKey = String.valueOf(entry.getKey());
				  if (sKey.startsWith(storePrefix)) {
					  keysToRemove.add(entry.getKey());
				  }
			  }
			  for (Object key : keysToRemove) {
				  ehCache.remove(key);
			  }
		  } else {
			  // Fallback: clear entire cache if native cache type is unknown
			  LOGGER.warn("Cannot perform store-specific cache eviction, clearing entire cache");
			  cache.clear();
		  }
	}
	


}
