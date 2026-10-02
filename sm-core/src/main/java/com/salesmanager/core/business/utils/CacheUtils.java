package com.salesmanager.core.business.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
		  for (Object key: nativeCacheKeys()) {
		    
			  
				try {
					String sKey = (String)key;
					
					// a key should be <storeId>_<rest of the key>
					int delimiterPosition = sKey.indexOf(KEY_DELIMITER);
					
					if(delimiterPosition>0 && Character.isDigit(sKey.charAt(0))) {
					
						String keyRemaining = sKey.substring(delimiterPosition+1);
						returnKeys.add(keyRemaining);
					
					}

				} catch (Exception e) {
					LOGGER.equals("key " + key + " cannot be converted to a String or parsed");
				}  
		  }

		return returnKeys;
	}
	
	@SuppressWarnings("unchecked")
	private Set<Object> nativeCacheKeys() {
		com.github.benmanes.caffeine.cache.Cache<Object, Object> cacheImpl =
				(com.github.benmanes.caffeine.cache.Cache<Object, Object>) cache.getNativeCache();
		return new HashSet<>(cacheImpl.asMap().keySet());
	}

	public void shutDownCache() throws Exception {
		
	}
	
	public void removeFromCache(String keyName) throws Exception {
		cache.evict(keyName);
	}
	
	public void removeAllFromCache(MerchantStore store) throws Exception {
		  for (Object key: nativeCacheKeys()) {
				try {
					String sKey = (String)key;
					
					// a key should be <storeId>_<rest of the key>
					int delimiterPosition = sKey.indexOf(KEY_DELIMITER);
					
					if(delimiterPosition>0 && Character.isDigit(sKey.charAt(0))) {
					

						cache.evict(key);
					
					}

				} catch (Exception e) {
					LOGGER.equals("key " + key + " cannot be converted to a String or parsed");
				}  
		  }
	}
	


}
