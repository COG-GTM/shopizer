package com.salesmanager.core.business.modules.cms.product.aws;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.modules.cms.impl.CMSManager;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.image.ProductImage;
import com.salesmanager.core.model.content.FileContentType;
import com.salesmanager.core.model.content.ImageContentFile;
import com.salesmanager.core.model.merchant.MerchantStore;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

class S3ProductContentFileManagerTest {
    private S3Client client;
    private S3ProductContentFileManager manager;
    private Product product;
    private ProductImage image;

    @BeforeEach
    void setUp() {
        client = mock(S3Client.class);
        manager = new S3ProductContentFileManager();
        manager.setClient(client);
        CMSManager cms = mock(CMSManager.class);
        when(cms.getRootName()).thenReturn("images-bucket");
        manager.setCmsManager(cms);
        MerchantStore store = new MerchantStore();
        store.setCode("DEFAULT");
        product = new Product();
        product.setMerchantStore(store);
        product.setSku("SKU");
        image = new ProductImage();
        image.setProduct(product);
        image.setProductImage("photo.png");
    }

    @Test
    void uploadKeepsSmallAndLargePaths() throws Exception {
        for (FileContentType type : new FileContentType[] {FileContentType.PRODUCT, FileContentType.PRODUCTLG}) {
            ImageContentFile file = new ImageContentFile();
            file.setFileContentType(type);
            file.setMimeType("image/png");
            file.setFile(new ByteArrayInputStream(new byte[] {3}));
            manager.addProductImage(image, file);
        }
        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client, times(2)).putObject(request.capture(), any(RequestBody.class));
        assertEquals("products/DEFAULT/SKU/SMALL/photo.png", request.getAllValues().get(0).key());
        assertEquals("products/DEFAULT/SKU/LARGE/photo.png", request.getAllValues().get(1).key());
        assertEquals("images-bucket", request.getValue().bucket());
        assertEquals("public-read", request.getValue().aclAsString());
        assertEquals("image/png", request.getValue().contentType());
    }

    @Test
    void deleteKeepsExistingKeysIncludingSizeOmission() throws Exception {
        manager.removeProductImage(image);
        manager.removeProductImages(product);
        manager.removeImages("DEFAULT");
        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client, times(3)).deleteObject(request.capture());
        assertEquals("products/DEFAULT/SKU/photo.png", request.getAllValues().get(0).key());
        assertEquals("products/DEFAULT/SKU/", request.getAllValues().get(1).key());
        assertEquals("products/DEFAULT/", request.getAllValues().get(2).key());
    }

    @Test
    void listReadsImagesUsingOriginalKeys() throws Exception {
        String key = "products/DEFAULT/SKU/SMALL/photo.png";
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
            .contents(S3Object.builder().key(key).build()).build());
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(new ResponseInputStream<>(
            GetObjectResponse.builder().build(), new ByteArrayInputStream(new byte[] {3})));
        assertArrayEquals(new byte[] {3}, manager.getImages("DEFAULT", FileContentType.PRODUCT).get(0).getFile().toByteArray());
        ArgumentCaptor<ListObjectsV2Request> listing = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(client).listObjectsV2(listing.capture());
        assertEquals("products/DEFAULT/", listing.getValue().prefix());
        ArgumentCaptor<GetObjectRequest> get = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(client).getObject(get.capture());
        assertEquals(key, get.getValue().key());
    }

    @Test
    void emptyListRetainsNullResult() throws Exception {
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder().build());
        assertNull(manager.getImages("DEFAULT", FileContentType.PRODUCT));
    }

    @Test
    void wrapsSdkFailure() {
        when(client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(new IllegalStateException("offline"));
        assertThrows(ServiceException.class, () -> manager.removeProductImage(image));
    }
}
