package com.salesmanager.test.content;

import com.salesmanager.core.business.modules.cms.impl.CacheManagerImpl;
import java.nio.file.Files;
import java.nio.file.Path;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CacheManagerPersistenceTest {
  @TempDir
  Path directory;

  @Test
  void emptyStorePersistsFlatEntries() throws Exception {
    try (DefaultCacheManager embedded = new DefaultCacheManager()) {
      TestManager manager = new TestManager(embedded);
      manager.start(directory);
      assertTrue(manager.getCache().isEmpty());
      manager.getCache().put("store-merchantDEFAULT/IMAGE/image.jpg", new byte[] {1, 2, 3});
    }
    try (DefaultCacheManager embedded = new DefaultCacheManager()) {
      TestManager manager = new TestManager(embedded);
      manager.start(directory);
      assertArrayEquals(new byte[] {1, 2, 3},
          (byte[]) manager.getCache().get("store-merchantDEFAULT/IMAGE/image.jpg"));
    }
  }

  @Test
  void legacyStoreRequiresMigration() throws Exception {
    try (java.io.InputStream fixture = getClass().getResourceAsStream("/cms/legacy-empty-store.dat")) {
      Files.copy(fixture, directory.resolve("FilesRepository.dat"));
    }
    try (DefaultCacheManager embedded = new DefaultCacheManager()) {
      TestManager manager = new TestManager(embedded);
      IllegalStateException error = assertThrows(IllegalStateException.class,
          () -> manager.start(directory));
      assertTrue(error.getMessage().contains(directory.toString()));
      assertTrue(error.getMessage().contains("removed Infinispan tree layout"));
      assertTrue(error.getMessage().contains("must be migrated"));
      assertNotNull(error.getCause());
    }
  }

  private static class TestManager extends CacheManagerImpl {
    private final EmbeddedCacheManager embedded;

    TestManager(EmbeddedCacheManager embedded) {
      this.embedded = embedded;
    }

    void start(Path directory) {
      init("FilesRepository", directory.toString());
    }

    @Override
    public EmbeddedCacheManager getManager() {
      return embedded;
    }

    @Override
    public String getLocation() {
      return location;
    }

    @Override
    public String getRootName() {
      return "store-merchant";
    }
  }
}
