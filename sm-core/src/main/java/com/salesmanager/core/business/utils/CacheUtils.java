package com.salesmanager.core.business.utils;

import java.util.ArrayList;
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
		return new ArrayList<>();
	}
	
	public void shutDownCache() throws Exception {
	}
	
	public void removeFromCache(String keyName) throws Exception {
		cache.evict(keyName);
	}
	
	public void removeAllFromCache(MerchantStore store) throws Exception {
		cache.clear();
	}

}
