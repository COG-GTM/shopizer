package com.salesmanager.test.content;

import com.salesmanager.core.business.modules.cms.content.infinispan.CmsStaticContentFileManagerImpl;
import com.salesmanager.core.business.modules.cms.impl.CacheManager;
import com.salesmanager.core.business.modules.cms.impl.DownloadCacheManagerImpl;
import com.salesmanager.core.business.modules.cms.impl.StaticContentCacheManagerImpl;
import com.salesmanager.core.business.modules.cms.impl.StoreCacheManagerImpl;
import com.salesmanager.core.model.content.FileContentType;
import com.salesmanager.core.model.content.InputContentFile;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CmsStoreRestartTest {
  @TempDir
  Path directory;

  @Test
  void allCmsStoresSurviveJvmRestarts() throws Exception {
    runProcess("write");
    runProcess("read-delete");
    runProcess("check-deleted");
  }

  @Test
  void legacyStoreIsRejectedWithoutChangingSource() throws Exception {
    Path store = directory.resolve("FilesRepository.dat");
    byte[] legacy;
    try (var input = getClass().getResourceAsStream("/cms/legacy-empty-store.dat")) {
      legacy = input.readAllBytes();
    }
    Files.write(store, legacy);
    IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> new StaticContentCacheManagerImpl(directory.toString(), "store-merchant"));
    assertTrue(error.getMessage().contains("offline migration"));
    assertArrayEquals(legacy, Files.readAllBytes(store));
    assertFalse(Files.exists(directory.resolve("FilesRepository_new.dat")));
  }

  private void runProcess(String action) throws Exception {
    Path log = directory.resolve(action + ".log");
    Process process = new ProcessBuilder(
        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
        "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
        CmsStoreRestartTest.class.getName(), directory.toString(), action)
        .redirectErrorStream(true).redirectOutput(log.toFile()).start();
    if (!process.waitFor(60, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      fail("CMS process timed out: " + Files.readString(log));
    }
    assertEquals(0, process.exitValue(), () -> {
      try {
        return Files.readString(log);
      } catch (Exception e) {
        return e.toString();
      }
    });
  }

  public static void main(String[] args) throws Exception {
    Path root = Path.of(args[0]);
    CacheManager[] managers = {
        new StoreCacheManagerImpl(root.resolve("products").toString(), "product-merchant"),
        new StaticContentCacheManagerImpl(root.resolve("content").toString(), "store-merchant"),
        new DownloadCacheManagerImpl(root.resolve("downloads").toString(), "product-file")
    };
    byte[] bytes = new byte[128 * 1024];
    for (int i = 0; i < bytes.length; i++) {
      bytes[i] = (byte) i;
    }
    for (CacheManager manager : managers) {
      CmsStaticContentFileManagerImpl content = new CmsStaticContentFileManagerImpl();
      content.setCacheManager(manager);
      content.setRootName(manager.getRootName());
      String name = "restart%/asset.bin";
      if ("write".equals(args[1])) {
        InputContentFile file = new InputContentFile();
        file.setFileName(name);
        file.setFileContentType(FileContentType.IMAGE);
        file.setFile(new ByteArrayInputStream(bytes));
        content.addFile("DEFAULT", Optional.empty(), file);
      } else if ("read-delete".equals(args[1])) {
        assertArrayEquals(bytes, content.getFile("DEFAULT", Optional.empty(),
            FileContentType.IMAGE, name).getFile().toByteArray());
        assertTrue(content.getFileNames("DEFAULT", Optional.empty(), FileContentType.IMAGE).contains(name));
        content.removeFile("DEFAULT", FileContentType.IMAGE, name, Optional.empty());
      } else {
        assertNull(content.getFile("DEFAULT", Optional.empty(), FileContentType.IMAGE, name));
        assertTrue(content.getFileNames("DEFAULT", Optional.empty(), FileContentType.IMAGE).isEmpty());
      }
    }
    // Exercise the production manager's shutdown hook, including the write-behind queue.
  }
}
