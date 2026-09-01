package com.salesmanager.test.content;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
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

    private Path tempDir;
    private CmsImageFileManagerImpl manager;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("cms-test");
        manager = CmsImageFileManagerImpl.getInstance();
        manager.setRootName(tempDir.toString());
    }

    @After
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(tempDir.toFile());
    }

    @Test
    public void testAddProductImageWritesUnderRoot() throws Exception {
        ProductImage productImage = productImage("image.jpg");
        ImageContentFile contentImage = contentImage("image.jpg", FileContentType.PRODUCT);

        manager.addProductImage(productImage, contentImage);

        assertTrue(Files.exists(tempDir.resolve("products/DEFAULT/SKU1/SMALL/image.jpg")));
    }

    @Test(expected = ServiceException.class)
    public void testAddProductImageRejectsTraversalFilename() throws Exception {
        ProductImage productImage = productImage("../../../evil.txt");
        ImageContentFile contentImage = contentImage("../../../evil.txt", FileContentType.PRODUCT);

        try {
            manager.addProductImage(productImage, contentImage);
        } finally {
            assertTrue(Files.notExists(tempDir.resolve("evil.txt")));
            assertTrue(Files.notExists(tempDir.getParent().resolve("evil.txt")));
        }
    }

    @Test(expected = ServiceException.class)
    public void testAddProductImageRejectsTraversalSku() throws Exception {
        ProductImage productImage = productImage("image.jpg");
        productImage.getProduct().setSku("../../x");
        ImageContentFile contentImage = contentImage("image.jpg", FileContentType.PRODUCT);

        manager.addProductImage(productImage, contentImage);
    }

    @Test(expected = ServiceException.class)
    public void testRemoveProductImageRejectsTraversal() throws Exception {
        ProductImage productImage = productImage("../../../etc/passwd-like");

        manager.removeProductImage(productImage);
    }

    private ProductImage productImage(String imageName) {
        MerchantStore merchantStore = new MerchantStore();
        merchantStore.setCode("DEFAULT");

        Product product = new Product();
        product.setMerchantStore(merchantStore);
        product.setSku("SKU1");

        ProductImage productImage = new ProductImage();
        productImage.setProduct(product);
        productImage.setProductImage(imageName);
        return productImage;
    }

    private ImageContentFile contentImage(String fileName, FileContentType contentType) {
        ImageContentFile contentImage = new ImageContentFile();
        contentImage.setFileName(fileName);
        contentImage.setFileContentType(contentType);
        contentImage.setFile(new ByteArrayInputStream("x".getBytes()));
        return contentImage;
    }
}
