package com.salesmanager.test.content;

import com.salesmanager.core.business.modules.cms.impl.CacheManagerImpl;
import java.nio.file.Files;
import java.nio.file.Path;
import org.infinispan.manager.EmbeddedCacheManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Reject incompatible stores before a cache manager can mutate them. */
class CmsStorePreflightTest {
  @TempDir Path directory;

  @ParameterizedTest
  @ValueSource(strings = {"StoreRepository", "FilesRepository", "DownlaodRepository"})
  void legacyHeaderFailsBeforeOpeningAnyCache(String cacheName) throws Exception {
    byte[] source = new byte[] {'F', 'C', 'S', '1', 0, 19, 42, -1};
    Path store = directory.resolve(cacheName + ".dat");
    Files.write(store, source);
    IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> new PreflightOnlyManager().open(cacheName, directory));
    assertTrue(error.getMessage().contains("offline migration"));
    assertTrue(error.getMessage().contains(directory.toString()));
    assertNotNull(error.getCause());
    assertArrayEquals(source, Files.readAllBytes(store));
    try (var files = Files.list(directory)) {
      assertEquals(1, files.count(), "Preflight must not create migration/cache files");
    }
  }

  @Test
  void unreadableStoreReportsItsPathBeforeOpeningAnyCache() throws Exception {
    Path store = Files.createDirectory(directory.resolve("FilesRepository.dat"));
    IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> new PreflightOnlyManager().open("FilesRepository", directory));
    assertTrue(error.getMessage().contains("Cannot inspect CMS store"));
    assertTrue(error.getMessage().contains(store.toString()));
    assertInstanceOf(java.io.IOException.class, error.getCause());
    assertTrue(Files.isDirectory(store));
  }

  private static class PreflightOnlyManager extends CacheManagerImpl {
    void open(String name, Path path) { init(name, path.toString()); }
    @Override public EmbeddedCacheManager getManager() {
      throw new AssertionError("Infinispan was reached before preflight rejected the store");
    }
    @Override public String getLocation() { return location; }
    @Override public String getRootName() { return "store-merchant"; }
  }
}
