package com.salesmanager.core.business.modules.cms.impl;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.infinispan.Cache;

/**
 * Hierarchical (path based) view over a flat Infinispan {@link Cache}.
 * Replaces the infinispan-tree module (TreeCache / Node / Fqn) which no longer exists after Infinispan 9.4.
 *
 * Node markers are stored under {@code n:<path>} and node data under {@code d:<path>#<key>}.
 */
public class CmsTreeCache {

  static final String NODE_PREFIX = "n:";
  static final String DATA_PREFIX = "d:";
  static final char KEY_SEPARATOR = '#';
  static final String PATH_SEPARATOR = "/";

  private final Cache<String, Object> cache;

  public CmsTreeCache(Cache<String, Object> cache) {
    this.cache = cache;
  }

  public CmsNode getRoot() {
    return new CmsNode(this, "");
  }

  Cache<String, Object> getCache() {
    return cache;
  }

  static String normalize(String path) {
    if (path == null) {
      return "";
    }
    return Arrays.stream(path.split(PATH_SEPARATOR)).filter(s -> !s.isBlank())
        .collect(Collectors.joining(PATH_SEPARATOR));
  }

  static String childPath(String parent, String relativePath) {
    String relative = normalize(relativePath);
    if (parent.isEmpty()) {
      return relative;
    }
    return relative.isEmpty() ? parent : parent + PATH_SEPARATOR + relative;
  }

  static String dataKey(String path, String key) {
    return DATA_PREFIX + path + KEY_SEPARATOR + key;
  }

  static String dataPrefix(String path) {
    return DATA_PREFIX + path + KEY_SEPARATOR;
  }

  boolean exists(String path) {
    return path.isEmpty() || cache.containsKey(NODE_PREFIX + path);
  }

  void createNode(String path) {
    if (path.isEmpty()) {
      return;
    }
    StringBuilder current = new StringBuilder();
    for (String element : path.split(PATH_SEPARATOR)) {
      if (current.length() > 0) {
        current.append(PATH_SEPARATOR);
      }
      current.append(element);
      cache.putIfAbsent(NODE_PREFIX + current, Boolean.TRUE);
    }
  }

  boolean removeNode(String path) {
    if (!exists(path)) {
      return false;
    }
    List<String> keys = keys().filter(k -> belongsToSubtree(k, path)).collect(Collectors.toList());
    keys.forEach(cache::remove);
    return true;
  }

  java.util.stream.Stream<String> keys() {
    return cache.keySet().stream().collect(Collectors.toList()).stream();
  }

  private static boolean belongsToSubtree(String key, String path) {
    if (path.isEmpty()) {
      return true;
    }
    String descendants = path + PATH_SEPARATOR;
    return key.equals(NODE_PREFIX + path) || key.startsWith(NODE_PREFIX + descendants)
        || key.startsWith(dataPrefix(path)) || key.startsWith(DATA_PREFIX + descendants);
  }

}
