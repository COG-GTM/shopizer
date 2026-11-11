package com.salesmanager.test.content;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.modules.cms.content.infinispan.CmsStaticContentFileManagerImpl;
import com.salesmanager.core.business.modules.cms.impl.CacheManager;
import com.salesmanager.core.business.modules.cms.impl.FlatCacheFiles;
import com.salesmanager.core.business.modules.cms.product.infinispan.CmsImageFileManagerImpl;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.file.ProductImageSize;
import com.salesmanager.core.model.catalog.product.image.ProductImage;
import com.salesmanager.core.model.content.FileContentType;
import com.salesmanager.core.model.content.ImageContentFile;
import com.salesmanager.core.model.content.InputContentFile;
import com.salesmanager.core.model.merchant.MerchantStore;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.Optional;
import org.infinispan.Cache;
import org.infinispan.manager.DefaultCacheManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FlatCacheContentTest {
  private DefaultCacheManager embedded;
  private Cache<String, Object> cache;
  private CacheManager manager;
  private final byte[] bytes = {1, 2, 3};
  private CmsStaticContentFileManagerImpl content;
  private CmsImageFileManagerImpl images;
  private Product product;
  private ProductImage image;

  @BeforeEach
  void setUp() {
    embedded = new DefaultCacheManager();
    embedded.defineConfiguration("files", new org.infinispan.configuration.cache.ConfigurationBuilder().build());
    cache = embedded.getCache("files");
    manager = mock(CacheManager.class);
    when(manager.getCache()).thenReturn(cache);
    content = new CmsStaticContentFileManagerImpl();
    content.setCacheManager(manager);
    content.setRootName("store-merchant");
    images = CmsImageFileManagerImpl.getInstance();
    images.setCacheManager(manager);
    images.setRootName("product-merchant");
    MerchantStore store = new MerchantStore();
    store.setCode("DEFAULT");
    product = new Product();
    product.setMerchantStore(store);
    product.setSku("sku");
    image = new ProductImage();
    image.setProduct(product);
    image.setProductImage("image.png");
  }

  @AfterEach
  void tearDown() {
    embedded.stop();
  }

  @Test
  void staticContentRoundTrip() throws Exception {
    assertTrue(content.getFileNames("DEFAULT", Optional.empty(), FileContentType.IMAGE).isEmpty());
    assertNull(content.getFile("DEFAULT", Optional.empty(), FileContentType.IMAGE, "image.png"));
    InputContentFile file = new InputContentFile();
    file.setFileName("image.png");
    file.setFileContentType(FileContentType.IMAGE);
    file.setFile(new ByteArrayInputStream(bytes));
    content.addFile("DEFAULT", Optional.empty(), file);
    assertArrayEquals(bytes, (byte[]) cache.get("store-merchantDEFAULT/IMAGE/image.png"));
    assertArrayEquals(bytes, content.getFile("DEFAULT", Optional.empty(), FileContentType.IMAGE, "image.png").getFile().toByteArray());
    assertEquals(Collections.singletonList("image.png"), content.getFileNames("DEFAULT", Optional.empty(), FileContentType.IMAGE));
    assertEquals("image.png", content.getFiles("DEFAULT", Optional.empty(), FileContentType.IMAGE).get(0).getFileName());
    content.removeFile("DEFAULT", FileContentType.IMAGE, "image.png", Optional.empty());
    assertNull(content.getFile("DEFAULT", Optional.empty(), FileContentType.IMAGE, "image.png"));
    file.setFile(new ByteArrayInputStream(bytes));
    content.addFiles("DEFAULT", Optional.empty(), Collections.singletonList(file));
    cache.put("store-merchantDEFAULT2/IMAGE/other.png", bytes);
    content.removeFiles("DEFAULT", Optional.empty());
    assertTrue(content.getFiles("DEFAULT", Optional.empty(), FileContentType.IMAGE).isEmpty());
    assertNotNull(cache.get("store-merchantDEFAULT2/IMAGE/other.png"));
  }

  @Test
  void productImageFlatLayoutRoundTrip() {
    FlatCacheFiles small = new FlatCacheFiles(cache, "product-merchantDEFAULT/sku/SMALL");
    FlatCacheFiles large = new FlatCacheFiles(cache, "product-merchantDEFAULT/sku/LARGE");
    assertTrue(small.getKeys().isEmpty());
    assertNull(small.get("image.png"));
    small.put("image.png", bytes);
    large.put("image.png", bytes);
    assertArrayEquals(bytes, (byte[]) small.get("image.png"));
    assertArrayEquals(bytes, (byte[]) cache.get("product-merchantDEFAULT/sku/LARGE/image.png"));
    assertEquals(Collections.singleton("image.png"), small.getKeys());
    FlatCacheFiles sku = new FlatCacheFiles(cache, "product-merchantDEFAULT/sku");
    assertEquals(2, sku.getChildren().size());
    assertTrue(sku.getKeys().isEmpty());
    small.remove("image.png");
    assertNull(small.get("image.png"));
    assertNotNull(large.get("image.png"));
    sku.removeAll();
    assertTrue(cache.isEmpty());
  }

  private void addImage(FileContentType type) throws ServiceException {
    ImageContentFile file = new ImageContentFile();
    file.setFileName("image.png");
    file.setFileContentType(type);
    file.setFile(new ByteArrayInputStream(bytes));
    images.addProductImage(image, file);
  }

  @Test
  void productManagerPutGetAndMerchantRemoval() throws Exception {
    assertNull(images.getProductImage(image));
    addImage(FileContentType.PRODUCT);
    addImage(FileContentType.PRODUCTLG);
    assertArrayEquals(bytes, images.getProductImage(image).getFile().toByteArray());
    assertArrayEquals(bytes, images.getProductImage("DEFAULT", "sku", "image.png", ProductImageSize.LARGE).getFile().toByteArray());
    cache.put("product-merchantDEFAULT2/sku/SMALL/image.png", bytes);
    images.removeImages("DEFAULT");
    assertNull(images.getProductImage(image));
    assertNotNull(cache.get("product-merchantDEFAULT2/sku/SMALL/image.png"));
  }

  @Test
  void productListingMirrorsTreeEraBehaviour() throws Exception {
    addImage(FileContentType.PRODUCT);
    // The tree-era implementation lists merchant attributes, not product/size descendants.
    assertTrue(images.getImages(product).isEmpty());
  }

  @Test
  void missingCacheAndUnreadableFilesAreReported() {
    when(manager.getCache()).thenReturn(null);
    assertThrows(ServiceException.class, () -> images.getProductImage(image));
    assertThrows(ServiceException.class, () -> content.getFileNames("DEFAULT", Optional.empty(), FileContentType.IMAGE));
    when(manager.getCache()).thenReturn(cache);
    InputContentFile file = new InputContentFile();
    file.setFileName("broken.png");
    file.setFileContentType(FileContentType.IMAGE);
    assertThrows(ServiceException.class, () -> content.addFile("DEFAULT", Optional.empty(), file));
  }
}
