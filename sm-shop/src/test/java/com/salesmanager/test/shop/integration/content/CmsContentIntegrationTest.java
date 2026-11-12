package com.salesmanager.test.shop.integration.content;

import com.salesmanager.core.business.services.content.ContentService;
import com.salesmanager.core.model.content.FileContentType;
import com.salesmanager.core.model.content.InputContentFile;
import com.salesmanager.test.shop.common.ServicesTestSupport;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.*;

/** Uses the real Spring CMS wiring and embedded persistent cache, with no cache mocks. */
class CmsContentIntegrationTest extends ServicesTestSupport {
  @Autowired ContentService content;

  @ParameterizedTest
  @EnumSource(value = FileContentType.class, names = {"IMAGE", "STATIC_FILE"})
  void overwriteAndDeleteKeepOtherMerchantBytesIntact(FileContentType type) throws Exception {
    String merchant = "cms" + UUID.randomUUID().toString().replace("-", "");
    String sibling = merchant + "2";
    String name = "same-name.bin";
    byte[] original = {0, 1, 2, -1};
    byte[] replacement = new byte[128 * 1024];
    for (int i = 0; i < replacement.length; i++) replacement[i] = (byte) (i * 31);
    try {
      put(merchant, type, name, original);
      put(sibling, type, name, original);
      put(merchant, type, name, replacement);
      assertArrayEquals(replacement, content.getContentFile(merchant, type, name).getFile().toByteArray());
      assertArrayEquals(original, content.getContentFile(sibling, type, name).getFile().toByteArray());
      assertEquals(1, content.getContentFilesNames(merchant, type).stream().filter(name::equals).count());
      content.removeFile(merchant, type, name);
      assertNull(content.getContentFile(merchant, type, name));
      assertFalse(content.getContentFilesNames(merchant, type).contains(name));
      assertArrayEquals(original, content.getContentFile(sibling, type, name).getFile().toByteArray());
    } finally {
      content.removeFile(merchant, type, name);
      content.removeFile(sibling, type, name);
    }
  }

  private void put(String merchant, FileContentType type, String name, byte[] bytes) throws Exception {
    InputContentFile file = new InputContentFile();
    file.setFileName(name);
    file.setFileContentType(type);
    file.setFile(new ByteArrayInputStream(bytes));
    content.addContentFile(merchant, file);
  }
}
