package com.salesmanager.test.cms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayInputStream;
import org.infinispan.manager.DefaultCacheManager;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.modules.cms.impl.*;
import com.salesmanager.core.business.modules.cms.product.infinispan.CmsImageFileManagerImpl;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.file.ProductImageSize;
import com.salesmanager.core.model.catalog.product.image.ProductImage;
import com.salesmanager.core.model.content.*;
import com.salesmanager.core.model.merchant.MerchantStore;

class CmsImageFileManagerUnitTest {
    private DefaultCacheManager embedded;
    private CmsTreeCache tree;
    private CmsImageFileManagerImpl files;
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        embedded = new DefaultCacheManager();
        embedded.defineConfiguration("tree", new org.infinispan.configuration.cache.ConfigurationBuilder().build());
        tree = new CmsTreeCache(embedded.getCache("tree"));
        cacheManager = mock(CacheManager.class);
        when(cacheManager.getTreeCache()).thenReturn(tree);
        when(cacheManager.getRootName()).thenReturn("product-merchant");
        files = org.springframework.beans.BeanUtils.instantiateClass(CmsImageFileManagerImpl.class);
        files.setCacheManager(cacheManager);
        ReflectionTestUtils.invokeMethod(files, "init");
    }

    @AfterEach
    void close() { embedded.stop(); }

    private ProductImage image(String storeCode, String sku, String name) {
        MerchantStore store = new MerchantStore();
        store.setCode(storeCode);
        Product product = new Product();
        product.setMerchantStore(store);
        product.setSku(sku);
        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setProductImage(name);
        return image;
    }

    private void add(ProductImage image, FileContentType type) throws Exception {
        ImageContentFile content = new ImageContentFile();
        content.setFileName(image.getProductImage());
        content.setFileContentType(type);
        content.setFile(new ByteArrayInputStream(new byte[] {(byte) type.ordinal()}));
        files.addProductImage(image, content);
    }

    @Test
    void roundTripsBothSizesAndRemovesOnlyNamedImage() throws Exception {
        ProductImage target = image("A", "sku", "target.png");
        ProductImage other = image("A", "sku", "other.png");
        for (FileContentType type : new FileContentType[] {FileContentType.PRODUCT, FileContentType.PRODUCTLG}) {
            add(target, type);
            add(other, type);
        }
        assertEquals("target.png", files.getProductImage(target).getFileName());
        assertArrayEquals(new byte[] {(byte) FileContentType.PRODUCT.ordinal()},
                files.getProductImage(target).getFile().toByteArray());
        assertArrayEquals(new byte[] {(byte) FileContentType.PRODUCTLG.ordinal()},
                files.getProductImage("A", "sku", "target.png", ProductImageSize.LARGE).getFile().toByteArray());
        files.removeProductImage(target);
        for (ProductImageSize size : new ProductImageSize[] {ProductImageSize.SMALL, ProductImageSize.LARGE}) {
            assertNull(files.getProductImage("A", "sku", "target.png", size));
            assertNotNull(files.getProductImage("A", "sku", "other.png", size));
        }
        files.removeProductImage(target);
    }

    @Test
    void removesProductSubtreeWithoutRemovingPrefixSiblingThenRemovesMerchant() throws Exception {
        ProductImage first = image("A", "sku", "one.png");
        ProductImage second = image("A", "sku2", "two.png");
        ProductImage merchant = image("AB", "sku", "three.png");
        add(first, FileContentType.PRODUCT);
        add(first, FileContentType.PRODUCTLG);
        add(second, FileContentType.PRODUCT);
        add(merchant, FileContentType.PRODUCT);
        files.removeProductImages(first.getProduct());
        assertFalse(tree.getCache().containsKey("product-merchantA/sku/SMALL\u0000"));
        assertNull(files.getProductImage(first));
        assertNotNull(files.getProductImage(second));
        files.removeImages("A");
        assertNull(files.getProductImage(second));
        assertNotNull(files.getProductImage(merchant));
    }

    @Test
    void missingImageReturnsNullAndUnavailableCacheThrows() throws Exception {
        ProductImage missing = image("A", "sku", "missing.png");
        assertNull(files.getProductImage(missing));
        when(cacheManager.getTreeCache()).thenReturn(null);
        assertThrows(ServiceException.class, () -> files.getProductImage(missing));
        assertThrows(ServiceException.class, () -> add(missing, FileContentType.PRODUCT));
        assertThrows(ServiceException.class, () -> files.removeProductImages(missing.getProduct()));
    }
}
