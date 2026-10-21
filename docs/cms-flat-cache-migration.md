# CMS flat-cache migration

AB-540 changes the CMS stores from TreeCache entries to flat
`Cache<String, byte[]>` keys. Before upgrading an installation that has CMS
data in `files/`, run this converter with a pre-AB-540 build while
`infinispan-tree` is still on the classpath. Back up the complete `files/`
directory first, stop the application, convert each `.dat` store into a
separate output directory, and replace the three stores with the converted
files. The new build cannot read legacy TreeCache data because it no longer
includes `infinispan-tree`.

Build the converter classpath against the old checkout with:

```sh
mvn -pl sm-core dependency:build-classpath \
  -Dmdep.outputFile=/path/to/converter/classpath.txt
javac -cp "$(cat /path/to/converter/classpath.txt)" \
  -d /path/to/converter/classes CmsTreeCacheConverter.java
java -cp "/path/to/converter/classes:$(cat /path/to/converter/classpath.txt)" \
  CmsTreeCacheConverter /path/to/legacy-files /path/to/flat-files
```

The converter writes stores named `StoreRepository.dat`,
`DownlaodRepository.dat` (the existing spelling), and `FilesRepository.dat`.
It logs every flat key and its source FQN and SHA-256. Repeated source node
wrappers mapping to the same flat key are safe only when their values have the
same SHA-256; review the output for differing hashes before replacing data.

```java
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.tree.Fqn;
import org.infinispan.tree.Node;
import org.infinispan.tree.TreeCache;
import org.infinispan.tree.TreeCacheFactory;

public final class CmsTreeCacheConverter {
  private static final CacheSpec[] CACHES = {
      new CacheSpec("StoreRepository", "product-merchant"),
      new CacheSpec("DownlaodRepository", "product-file"),
      new CacheSpec("FilesRepository", "store-merchant")
  };

  private CmsTreeCacheConverter() {
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      throw new IllegalArgumentException("Usage: CmsTreeCacheConverter <legacy-dir> <flat-dir>");
    }
    Path legacyLocation = Paths.get(args[0]).toAbsolutePath();
    Path flatLocation = Paths.get(args[1]).toAbsolutePath();
    int converted = 0;
    for (CacheSpec spec : CACHES) {
      converted += convert(spec, legacyLocation, flatLocation);
    }
    System.out.println("Converted entries: " + converted);
  }

  private static int convert(CacheSpec spec, Path legacyLocation, Path flatLocation) throws Exception {
    DefaultCacheManager legacyManager = new DefaultCacheManager();
    DefaultCacheManager flatManager = new DefaultCacheManager();
    TreeCache<Object, Object> tree = null;
    Cache<Object, Object> flatCache = null;
    try {
      legacyManager.defineConfiguration(spec.cacheName, configuration(legacyLocation, true));
      Cache<Object, Object> legacyCache = legacyManager.getCache(spec.cacheName);
      tree = new TreeCacheFactory().createTreeCache(legacyCache);
      flatManager.defineConfiguration(spec.cacheName, configuration(flatLocation, false));
      flatCache = flatManager.getCache(spec.cacheName);
      int count = copyNode(spec, tree.getRoot(), flatCache);
      System.out.println(spec.cacheName + " entries: " + count);
      return count;
    } finally {
      if (tree != null) {
        tree.stop();
      }
      if (flatCache != null) {
        flatCache.stop();
      }
      legacyManager.stop();
      flatManager.stop();
    }
  }

  private static Configuration configuration(Path location, boolean legacy) {
    ConfigurationBuilder builder = new ConfigurationBuilder();
    builder.persistence().passivation(false).addSingleFileStore().segmented(false)
        .location(location.toString()).async().enable().preload(false).shared(false);
    if (legacy) {
      builder.invocationBatching().enable();
    }
    return builder.build();
  }

  private static int copyNode(CacheSpec spec, Node<Object, Object> node,
      Cache<Object, Object> flatCache) throws Exception {
    int count = 0;
    for (Map.Entry<Object, Object> entry : node.getData().entrySet()) {
      if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof byte[])) {
        throw new IllegalStateException("Unexpected tree entry in " + spec.cacheName + " at " + node.getFqn());
      }
      String key = flatKey(spec.root, node.getFqn(), (String) entry.getKey());
      byte[] value = (byte[]) entry.getValue();
      flatCache.put(key, value);
      System.out.println(spec.cacheName + "\t" + node.getFqn() + "\t" + key
          + "\t" + value.length + "\t" + digest(value));
      count++;
    }
    Set<Node<Object, Object>> children = node.getChildren();
    for (Node<Object, Object> child : children) {
      count += copyNode(spec, child, flatCache);
    }
    return count;
  }

  private static String flatKey(String root, Fqn fqn, String dataKey) {
    List<String> segments = new ArrayList<>();
    if (fqn.size() > 0) {
      String first = String.valueOf(fqn.get(0));
      if (first.startsWith(root)) {
        segments.add(root);
        segments.add(first.substring(root.length()));
        for (int i = 1; i < fqn.size(); i++) {
          segments.add(String.valueOf(fqn.get(i)));
        }
      } else {
        for (int i = 0; i < fqn.size(); i++) {
          segments.add(String.valueOf(fqn.get(i)));
        }
      }
    }
    segments.add(dataKey);
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

  private static String digest(byte[] value) throws Exception {
    byte[] hash = MessageDigest.getInstance("SHA-256").digest(value);
    StringBuilder result = new StringBuilder();
    for (byte b : hash) {
      result.append(String.format("%02x", b));
    }
    return result.toString();
  }

  private static final class CacheSpec {
    private final String cacheName;
    private final String root;

    private CacheSpec(String cacheName, String root) {
      this.cacheName = cacheName;
      this.root = root;
    }
  }
}
```
