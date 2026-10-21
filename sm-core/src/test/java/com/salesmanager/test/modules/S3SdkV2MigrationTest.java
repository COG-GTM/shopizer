package com.salesmanager.test.modules;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import com.salesmanager.core.business.modules.cms.content.aws.S3StaticContentAssetsManagerImpl;
import com.salesmanager.core.business.modules.cms.impl.CMSManager;
import com.salesmanager.core.business.modules.cms.product.aws.S3ProductContentFileManager;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.image.ProductImage;
import com.salesmanager.core.model.content.FileContentType;
import com.salesmanager.core.model.content.ImageContentFile;
import com.salesmanager.core.model.content.InputContentFile;
import com.salesmanager.core.model.merchant.MerchantStore;

public class S3SdkV2MigrationTest {

  @Test
  public void uploadsStaticFileWithExistingKeyAndContentType() throws Exception {
    S3Client s3 = mock(S3Client.class);
    CMSManager cms = cmsManager();
    S3StaticContentAssetsManagerImpl manager = new S3StaticContentAssetsManagerImpl() {
      @Override
      protected S3Client s3Client() {
        return s3;
      }
    };
    manager.setCmsManager(cms);
    InputContentFile file = new InputContentFile();
    file.setFileName("banner.png");
    file.setMimeType("image/png");
    file.setFileContentType(FileContentType.IMAGE);
    file.setFile(new ByteArrayInputStream(new byte[] { 1, 2, 3 }));

    manager.addFile("store-a", Optional.<String>empty(), file);

    ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3).putObject(request.capture(), any(RequestBody.class));
    assertEquals("media-bucket", request.getValue().bucket());
    assertEquals("files/store-a/banner.png", request.getValue().key());
    assertEquals("image/png", request.getValue().contentType());
    assertEquals(ObjectCannedACL.PUBLIC_READ, request.getValue().acl());
  }

  @Test
  public void deletesStaticFileAtExistingKey() throws Exception {
    S3Client s3 = mock(S3Client.class);
    S3StaticContentAssetsManagerImpl manager = new S3StaticContentAssetsManagerImpl() {
      @Override
      protected S3Client s3Client() {
        return s3;
      }
    };
    manager.setCmsManager(cmsManager());

    manager.removeFile("store-a", FileContentType.IMAGE, "banner.png", Optional.<String>empty());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Consumer<DeleteObjectRequest.Builder>> request =
        (ArgumentCaptor<Consumer<DeleteObjectRequest.Builder>>) (ArgumentCaptor<?>) ArgumentCaptor
            .forClass(Consumer.class);
    verify(s3).deleteObject(request.capture());
    DeleteObjectRequest.Builder builder = DeleteObjectRequest.builder();
    request.getValue().accept(builder);
    DeleteObjectRequest deleteRequest = builder.build();
    assertEquals("media-bucket", deleteRequest.bucket());
    assertEquals("files/store-a/banner.png", deleteRequest.key());
  }

  @Test
  public void listsStaticFilesUsingPrefixAndMapsNames() throws Exception {
    S3Client s3 = mock(S3Client.class);
    when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
        ListObjectsV2Response.builder().contents(
            S3Object.builder().key("files/store-a/banner.png").build(),
            S3Object.builder().key("files/store-a/readme.txt").build()).build());
    S3StaticContentAssetsManagerImpl manager = new S3StaticContentAssetsManagerImpl() {
      @Override
      protected S3Client s3Client() {
        return s3;
      }
    };
    manager.setCmsManager(cmsManager());

    List<String> names = manager.getFileNames("store-a", Optional.<String>empty(),
        FileContentType.IMAGE);

    ArgumentCaptor<ListObjectsV2Request> request = ArgumentCaptor.forClass(ListObjectsV2Request.class);
    verify(s3).listObjectsV2(request.capture());
    assertEquals("files/store-a/", request.getValue().prefix());
    assertEquals(Arrays.asList("banner.png", "readme.txt"), names);
  }

  @Test
  public void uploadsProductImageWithExistingKeyAndContentType() throws Exception {
    S3Client s3 = mock(S3Client.class);
    S3ProductContentFileManager manager = new S3ProductContentFileManager() {
      @Override
      protected S3Client s3Client() {
        return s3;
      }
    };
    manager.setCmsManager(cmsManager());
    MerchantStore store = new MerchantStore();
    store.setCode("store-a");
    Product product = new Product();
    product.setMerchantStore(store);
    product.setSku("sku-123");
    ProductImage productImage = mock(ProductImage.class);
    when(productImage.getProduct()).thenReturn(product);
    when(productImage.getProductImage()).thenReturn("front.png");
    ImageContentFile content = new ImageContentFile();
    content.setFileContentType(FileContentType.PRODUCT);
    content.setMimeType("image/png");
    content.setFile(new ByteArrayInputStream(new byte[] { 4, 5, 6 }));

    manager.addProductImage(productImage, content);

    ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3).putObject(request.capture(), any(RequestBody.class));
    assertEquals("media-bucket", request.getValue().bucket());
    assertEquals("products/store-a/sku-123/SMALL/front.png", request.getValue().key());
    assertEquals("image/png", request.getValue().contentType());
  }

  private CMSManager cmsManager() {
    CMSManager cms = mock(CMSManager.class);
    when(cms.getRootName()).thenReturn("media-bucket");
    when(cms.getLocation()).thenReturn("us-east-1");
    return cms;
  }
}
