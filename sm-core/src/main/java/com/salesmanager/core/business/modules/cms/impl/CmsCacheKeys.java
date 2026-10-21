package com.salesmanager.core.business.modules.cms.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.infinispan.Cache;

public final class CmsCacheKeys {
  private CmsCacheKeys() {
  }

  public static String join(String... segments) {
    StringBuilder key = new StringBuilder();
    for (String segment : segments) {
      if (segment == null || segment.isEmpty()) {
        continue;
      }
      if (key.length() > 0) {
        key.append('/');
      }
      key.append(segment);
    }
    return key.toString();
  }

  public static List<String> directChildren(Cache<String, byte[]> cache, String nodePath) {
    String prefix = join(nodePath) + "/";
    List<String> children = new ArrayList<>();
    try (Stream<String> keys = cache.keySet().stream()) {
      keys.filter(key -> key.startsWith(prefix))
          .filter(key -> !key.substring(prefix.length()).contains("/"))
          .sorted()
          .forEach(children::add);
    }
    return children;
  }

  public static void removePrefix(Cache<String, byte[]> cache, String prefix) {
    List<String> matchingKeys = new ArrayList<>();
    try (Stream<String> keys = cache.keySet().stream()) {
      keys.filter(key -> key.startsWith(prefix)).forEach(matchingKeys::add);
    }
    matchingKeys.forEach(cache::remove);
  }
}
