package com.salesmanager.core.business.modules.cms.impl;

import java.util.HashSet;
import java.util.Set;
import org.infinispan.Cache;

public final class FlatCacheFiles {
  private final Cache<String, Object> cache;
  private final String prefix;

  public FlatCacheFiles(Cache<String, Object> cache, String path) {
    this.cache = cache;
    this.prefix = path.replaceAll("^/+|/+$", "") + "/";
  }

  public void put(String name, Object value) {
    cache.put(prefix + name, value);
  }

  public Object get(String name) {
    return cache.get(prefix + name);
  }

  public void remove(String name) {
    cache.remove(prefix + name);
  }

  public Set<String> getKeys() {
    Set<String> names = new HashSet<>();
    for (String key : cache.keySet()) {
      if (key.startsWith(prefix)) {
        String name = key.substring(prefix.length());
        if (!name.contains("/")) {
          names.add(name);
        }
      }
    }
    return names;
  }

  public Set<String> getChildren() {
    Set<String> children = new HashSet<>();
    for (String key : cache.keySet()) {
      if (key.startsWith(prefix)) {
        String name = key.substring(prefix.length());
        int separator = name.indexOf('/');
        if (separator >= 0) {
          children.add(name.substring(0, separator));
        }
      }
    }
    return children;
  }

  public void removeAll() {
    for (String key : new HashSet<>(cache.keySet())) {
      if (key.startsWith(prefix)) {
        cache.remove(key);
      }
    }
  }
}
