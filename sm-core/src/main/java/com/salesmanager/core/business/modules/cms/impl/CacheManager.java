package com.salesmanager.core.business.modules.cms.impl;

import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.Cache;

public interface CacheManager extends CMSManager {

  EmbeddedCacheManager getManager();

  Cache<String, byte[]> getCache();

}
