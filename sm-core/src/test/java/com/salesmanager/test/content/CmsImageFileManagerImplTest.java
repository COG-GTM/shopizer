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

public class CmsImageFileManagerImplTest {

    private static final byte[] CONTENT = "image-bytes".getBytes(StandardCharsets.UTF_8);

    private Path tempDir;
    private Path cmsRoot;
    private CmsImageFileManagerImpl manager;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("cms-image-test");
        cmsRoot = tempDir.resolve("a").resolve("b").resolve("cms");
        Files.createDirectories(cmsRoot);
        manager = CmsImageFileManagerImpl.getInstance();
        manager.setRootName(cmsRoot.toString());
    }

    @After
    public void tearDown() throws IOException {
        try (Stream<Path> paths = Files.walk(tempDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    @Test
    public void writesImageUnderStoreProductAndSizeFolder() throws Exception {
        manager.addProductImage(productImage("GLOBEX", "SKU1", "main.jpg"),
                content("main.jpg", FileContentType.PRODUCTLG));
        manager.addProductImage(productImage("GLOBEX", "SKU1", "main.jpg"),
                content("main.jpg", FileContentType.PRODUCT));

        assertArrayEquals(CONTENT, Files.readAllBytes(cmsRoot.resolve("products/GLOBEX/SKU1/LARGE/main.jpg")));
        assertArrayEquals(CONTENT, Files.readAllBytes(cmsRoot.resolve("products/GLOBEX/SKU1/SMALL/main.jpg")));
    }

    @Test
    public void rejectsFileNameThatEscapesIntoAnotherStore() throws Exception {
        Path victim = cmsRoot.resolve("products/ACME/SKU1/LARGE/main.jpg");
        Files.createDirectories(victim.getParent());
        Files.write(victim, "original".getBytes(StandardCharsets.UTF_8));

        String name = "../../../ACME/SKU1/LARGE/main.jpg";
        assertRejected(productImage("GLOBEX", "SKU1", name), content(name, FileContentType.PRODUCTLG));

        assertArrayEquals("original".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(victim));
    }

    @Test
    public void rejectsFileNameThatEscapesTheCmsRoot() throws Exception {
        String name = "../../../../../evil.png";
        assertRejected(productImage("GLOBEX", "SKU1", name), content(name, FileContentType.PRODUCTLG));

        assertFalse(Files.exists(cmsRoot.resolve("evil.png")));
        assertFalse(Files.exists(cmsRoot.getParent().resolve("evil.png")));
        assertFalse(Files.exists(tempDir.resolve("evil.png")));
        assertFalse(Files.exists(tempDir.resolve("a").resolve("evil.png")));
    }

    @Test
    public void rejectsAbsoluteAndBackslashFileNames() throws Exception {
        String absolute = tempDir.resolve("abs.png").toString();
        assertRejected(productImage("GLOBEX", "SKU1", absolute), content(absolute, FileContentType.PRODUCT));
        assertFalse(Files.exists(tempDir.resolve("abs.png")));

        String backslash = "..\\..\\evil.png";
        assertRejected(productImage("GLOBEX", "SKU1", backslash), content(backslash, FileContentType.PRODUCT));
    }

    @Test
    public void rejectsTraversalInSku() throws Exception {
        assertRejected(productImage("GLOBEX", "../ACME", "main.jpg"), content("main.jpg", FileContentType.PRODUCT));
        assertFalse(Files.exists(cmsRoot.resolve("products/ACME/SMALL/main.jpg")));
    }

    @Test
    public void removeProductImageDoesNotDeleteOutsideProductFolder() throws Exception {
        Path victim = cmsRoot.resolve("products/ACME/SKU1/SMALL/main.jpg");
        Files.createDirectories(victim.getParent());
        Files.write(victim, CONTENT);

        manager.removeProductImage(productImage("GLOBEX", "SKU1", "../../../ACME/SKU1/SMALL/main.jpg"));

        assertTrue(Files.exists(victim));
    }

    @Test
    public void removeProductImageDeletesOwnFiles() throws Exception {
        manager.addProductImage(productImage("GLOBEX", "SKU1", "main.jpg"),
                content("main.jpg", FileContentType.PRODUCT));
        Path small = cmsRoot.resolve("products/GLOBEX/SKU1/SMALL/main.jpg");
        assertTrue(Files.exists(small));

        manager.removeProductImage(productImage("GLOBEX", "SKU1", "main.jpg"));

        assertFalse(Files.exists(small));
    }

    private void assertRejected(ProductImage productImage, ImageContentFile content) {
        try {
            manager.addProductImage(productImage, content);
            fail("Expected ServiceException for " + content.getFileName());
        } catch (ServiceException expected) {
            // expected
        }
    }

    private static ProductImage productImage(String storeCode, String sku, String imageName) {
        MerchantStore store = new MerchantStore();
        store.setCode(storeCode);
        Product product = new Product();
        product.setMerchantStore(store);
        product.setSku(sku);
        ProductImage productImage = new ProductImage();
        productImage.setProduct(product);
        productImage.setProductImage(imageName);
        return productImage;
    }

    private static ImageContentFile content(String fileName, FileContentType type) {
        ImageContentFile content = new ImageContentFile();
        content.setFileName(fileName);
        content.setFileContentType(type);
        content.setFile(new ByteArrayInputStream(CONTENT));
        return content;
    }
}
