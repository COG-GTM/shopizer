package com.salesmanager.test.content;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.modules.cms.product.local.CmsImageFileManagerImpl;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.image.ProductImage;
import com.salesmanager.core.model.content.FileContentType;
import com.salesmanager.core.model.content.ImageContentFile;
import com.salesmanager.core.model.merchant.MerchantStore;

public class CmsImageFileManagerImplRemovalTest {

    private static final byte[] CONTENT = "image-bytes".getBytes(StandardCharsets.UTF_8);

    private Path tempDir;
    private Path cmsRoot;
    private CmsImageFileManagerImpl manager;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("cms-image-removal-test");
        cmsRoot = tempDir.resolve("a").resolve("b").resolve("cms");
        Files.createDirectories(cmsRoot);
        manager = CmsImageFileManagerImpl.getInstance();
        manager.setRootName(cmsRoot.toString());
    }

    @After
    public void tearDown() throws IOException {
        try (Stream<Path> paths = Files.walk(tempDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    public void removesBothSmallAndLargeCopiesForSafeImageName() throws Exception {
        Path smallFile = cmsRoot.resolve("products/GLOBEX/SKU1/SMALL/main.jpg");
        Path largeFile = cmsRoot.resolve("products/GLOBEX/SKU1/LARGE/main.jpg");
        Files.createDirectories(smallFile.getParent());
        Files.createDirectories(largeFile.getParent());
        Files.write(smallFile, CONTENT);
        Files.write(largeFile, CONTENT);

        manager.removeProductImage(productImage("GLOBEX", "SKU1", "main.jpg"));

        assertFalse(Files.exists(smallFile));
        assertFalse(Files.exists(largeFile));
    }

    @Test
    public void ignoresUnsafePersistedImageNameWithoutDeletingFiles() throws Exception {
        Path wouldBeTarget = cmsRoot.resolve("products/GLOBEX/x.jpg");
        Path productFile = cmsRoot.resolve("products/GLOBEX/SKU1/SMALL/kept.jpg");
        Files.createDirectories(wouldBeTarget.getParent());
        Files.createDirectories(productFile.getParent());
        Files.write(wouldBeTarget, CONTENT);
        Files.write(productFile, CONTENT);

        manager.removeProductImage(productImage("GLOBEX", "SKU1", "../../x.jpg"));

        assertTrue(Files.exists(wouldBeTarget));
        assertTrue(Files.exists(productFile));
    }

    @Test
    public void rejectsUnsafeProductSkusWithoutTouchingOtherStoreFiles() throws Exception {
        Path otherStoreFile = cmsRoot.resolve("products/OTHER/SMALL/main.jpg");
        Files.createDirectories(otherStoreFile.getParent());
        Files.write(otherStoreFile, CONTENT);

        assertRejected(() -> manager.removeProductImage(productImage("GLOBEX", "..", "main.jpg")));
        assertRejected(() -> manager.removeProductImage(productImage("GLOBEX", "../OTHER", "main.jpg")));

        assertArrayEquals(CONTENT, Files.readAllBytes(otherStoreFile));
    }

    @Test
    public void rejectsUnsafeSkuWhenRemovingAllProductImages() throws Exception {
        Product product = new Product();
        product.setMerchantStore(store("GLOBEX"));
        product.setSku("../../x");

        assertRejected(() -> manager.removeProductImages(product));
    }

    @Test
    public void rejectsUnsafeStoreNameWhenRemovingImages() throws Exception {
        assertRejected(() -> manager.removeImages(".."));
    }

    @Test
    public void rejectsUnsupportedImageContentTypeBeforeWritingUnderSku() throws Exception {
        ImageContentFile content = content("main.jpg", FileContentType.VARIANT);

        assertRejected(() -> manager.addProductImage(productImage("GLOBEX", "SKU1", "main.jpg"), content));

        assertFalse(Files.exists(cmsRoot.resolve("products/GLOBEX/SKU1")));
    }

    @Test
    public void createsNestedDirectoriesWhenCmsRootIsMissing() throws Exception {
        cmsRoot = tempDir.resolve("missing").resolve("parents").resolve("cms");
        manager.setRootName(cmsRoot.toString());

        manager.addProductImage(productImage("GLOBEX", "SKU1", "main.jpg"),
                content("main.jpg", FileContentType.PRODUCTLG));

        assertArrayEquals(CONTENT, Files.readAllBytes(cmsRoot.resolve("products/GLOBEX/SKU1/LARGE/main.jpg")));
    }

    private static void assertRejected(CheckedOperation operation) throws Exception {
        try {
            operation.run();
            fail("Expected ServiceException");
        } catch (ServiceException expected) {
            assertTrue(true);
        }
    }

    private static ProductImage productImage(String storeCode, String sku, String imageName) {
        Product product = new Product();
        product.setMerchantStore(store(storeCode));
        product.setSku(sku);
        ProductImage productImage = new ProductImage();
        productImage.setProduct(product);
        productImage.setProductImage(imageName);
        return productImage;
    }

    private static MerchantStore store(String code) {
        MerchantStore store = new MerchantStore();
        store.setCode(code);
        return store;
    }

    private static ImageContentFile content(String fileName, FileContentType type) {
        ImageContentFile content = new ImageContentFile();
        content.setFileName(fileName);
        content.setFileContentType(type);
        content.setFile(new ByteArrayInputStream(CONTENT));
        return content;
    }

    private interface CheckedOperation {
        void run() throws Exception;
    }
}
