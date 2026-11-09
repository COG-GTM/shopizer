package com.salesmanager.core.business.modules.cms.impl;

import org.infinispan.manager.EmbeddedCacheManager;

public interface CacheManager extends CMSManager {

  EmbeddedCacheManager getManager();

  CmsTreeCache getTreeCache();

}
