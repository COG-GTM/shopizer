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
		  // Cache key enumeration is not supported with the generic Spring Cache API.
		  // This method now returns an empty list. For full cache key enumeration,
		  // consider using a CacheManager-specific approach.
		  LOGGER.warn("getCacheKeys is not fully supported with the generic Spring Cache API");
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
		  Object nativeCache = cache.getNativeCache();
		  if (nativeCache instanceof java.util.concurrent.ConcurrentMap) {
			  @SuppressWarnings("unchecked")
			  java.util.concurrent.ConcurrentMap<Object, Object> map =
					  (java.util.concurrent.ConcurrentMap<Object, Object>) nativeCache;
			  String storePrefix = String.valueOf(store.getId()) + KEY_DELIMITER;
			  for (Object key : map.keySet()) {
				  try {
					  String sKey = (String) key;
					  if (sKey.startsWith(storePrefix)) {
						  cache.evict(key);
					  }
				  } catch (Exception e) {
					  LOGGER.warn("key " + key + " cannot be converted to a String or parsed");
				  }
			  }
		  } else {
			  // Fallback: clear entire cache if native cache type is not supported
			  LOGGER.warn("Cannot selectively evict cache entries for store {}. "
					  + "Native cache type {} does not support key enumeration. Clearing entire cache.",
					  store.getId(), nativeCache.getClass().getName());
			  cache.clear();
		  }
	}
	


}
