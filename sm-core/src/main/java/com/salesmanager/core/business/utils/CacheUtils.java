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
		
		  List<String> returnKeys = new ArrayList<String>();
		  String storePrefix = String.valueOf(store.getId()) + KEY_DELIMITER;
		  Object nativeCache = cache.getNativeCache();
		  boolean enumerated = false;

		  // ConcurrentMap-backed caches (e.g. Spring SimpleCacheManager)
		  if (nativeCache instanceof java.util.concurrent.ConcurrentMap) {
			  @SuppressWarnings("unchecked")
			  java.util.concurrent.ConcurrentMap<Object, Object> map =
					  (java.util.concurrent.ConcurrentMap<Object, Object>) nativeCache;
			  for (Object key : map.keySet()) {
				  extractStoreKey(key, storePrefix, returnKeys);
			  }
			  enumerated = true;
		  }

		  // JCache (JSR-107) backed caches (e.g. EhCache 3.x via JCache)
		  if (!enumerated && nativeCache instanceof javax.cache.Cache) {
			  @SuppressWarnings("unchecked")
			  javax.cache.Cache<Object, Object> jcache =
					  (javax.cache.Cache<Object, Object>) nativeCache;
			  for (javax.cache.Cache.Entry<Object, Object> entry : jcache) {
				  extractStoreKey(entry.getKey(), storePrefix, returnKeys);
			  }
			  enumerated = true;
		  }

		  if (!enumerated) {
			  LOGGER.warn("getCacheKeys is not supported for native cache type {}",
					  nativeCache.getClass().getName());
		  }
		return returnKeys;
	}
	
	public void shutDownCache() throws Exception {
		
	}
	
	public void removeFromCache(String keyName) throws Exception {
		cache.evict(keyName);
	}
	
	public void removeAllFromCache(MerchantStore store) throws Exception {
		  // net.sf.ehcache is no longer available in Spring Boot 3.x.
		  // The generic Spring Cache API does not support key enumeration,
		  // so we use the native cache to iterate and selectively evict
		  // only entries belonging to the specified store (by key prefix).
		  String storePrefix = String.valueOf(store.getId()) + KEY_DELIMITER;
		  Object nativeCache = cache.getNativeCache();
		  boolean evicted = false;

		  // ConcurrentMap-backed caches (e.g. Spring SimpleCacheManager)
		  if (nativeCache instanceof java.util.concurrent.ConcurrentMap) {
			  @SuppressWarnings("unchecked")
			  java.util.concurrent.ConcurrentMap<Object, Object> map =
					  (java.util.concurrent.ConcurrentMap<Object, Object>) nativeCache;
			  for (Object key : map.keySet()) {
				  evictIfStoreKey(key, storePrefix);
			  }
			  evicted = true;
		  }

		  // JCache (JSR-107) backed caches (e.g. EhCache 3.x via JCache)
		  if (!evicted && nativeCache instanceof javax.cache.Cache) {
			  @SuppressWarnings("unchecked")
			  javax.cache.Cache<Object, Object> jcache =
					  (javax.cache.Cache<Object, Object>) nativeCache;
			  for (javax.cache.Cache.Entry<Object, Object> entry : jcache) {
				  evictIfStoreKey(entry.getKey(), storePrefix);
			  }
			  evicted = true;
		  }

		  if (!evicted) {
			  // Fallback: clear entire cache if native cache type is not supported
			  LOGGER.warn("Cannot selectively evict cache entries for store {}. "
					  + "Native cache type {} does not support key enumeration. Clearing entire cache.",
					  store.getId(), nativeCache.getClass().getName());
			  cache.clear();
		  }
	}

	private void extractStoreKey(Object key, String storePrefix, List<String> returnKeys) {
		  try {
			  String sKey = (String) key;
			  if (sKey.startsWith(storePrefix)) {
				  returnKeys.add(sKey.substring(storePrefix.length()));
			  }
		  } catch (Exception e) {
			  LOGGER.warn("key {} cannot be converted to a String or parsed", key);
		  }
	}

	private void evictIfStoreKey(Object key, String storePrefix) {
		  try {
			  String sKey = (String) key;
			  if (sKey.startsWith(storePrefix)) {
				  cache.evict(key);
			  }
		  } catch (Exception e) {
			  LOGGER.warn("key {} cannot be converted to a String or parsed", key);
		  }
	}
	


}
