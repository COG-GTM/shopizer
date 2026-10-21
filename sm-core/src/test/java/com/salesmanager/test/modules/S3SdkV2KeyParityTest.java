package com.salesmanager.test.modules;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
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

public class S3SdkV2KeyParityTest {

  @Test
  public void getsStaticFileFromLiteralObjectKey() throws Exception {
    S3Client s3 = mock(S3Client.class);
    when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
        ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[] { 1, 2 }));
    S3StaticContentAssetsManagerImpl manager = staticManager(s3, "us-east-1");

    manager.getFile("store-a", Optional.<String>empty(), FileContentType.IMAGE, "banner.png");

    ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
    verify(s3).getObjectAsBytes(request.capture());
    assertEquals("media-bucket", request.getValue().bucket());
    assertEquals("files/store-a/banner.png", request.getValue().key());
  }

  @Test
  public void listsStaticNamesFromOnlyDirectKeysWithKnownMimeTypes() throws Exception {
    S3Client s3 = mock(S3Client.class);
    when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
        .contents(S3Object.builder().key("files/store-a/banner.png").build(),
            S3Object.builder().key("files/store-a/nested/ignored.png").build(),
            S3Object.builder().key("files/store-a/unknown-format").build(),
            S3Object.builder().key("files/store-a/terms.txt").build())
        .build());
    S3StaticContentAssetsManagerImpl manager = staticManager(s3, "us-east-1");

    List<String> names = manager.getFileNames("store-a", Optional.<String>empty(),
        FileContentType.IMAGE);

    ArgumentCaptor<ListObjectsV2Request> request = ArgumentCaptor.forClass(ListObjectsV2Request.class);
    verify(s3).listObjectsV2(request.capture());
    assertEquals("media-bucket", request.getValue().bucket());
    assertEquals("files/store-a/", request.getValue().prefix());
    assertEquals(Arrays.asList("banner.png", "terms.txt"), names);
  }

  @Test
  public void fetchesEachStaticObjectWithKnownMimeType() throws Exception {
    S3Client s3 = mock(S3Client.class);
    when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
        .contents(S3Object.builder().key("files/store-a/banner.png").build(),
            S3Object.builder().key("files/store-a/nested/terms.txt").build(),
            S3Object.builder().key("files/store-a/no-extension").build())
        .build());
    when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
        ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[] { 3 }));
    S3StaticContentAssetsManagerImpl manager = staticManager(s3, "us-east-1");

    manager.getFiles("store-a", Optional.<String>empty(), FileContentType.IMAGE);

    ArgumentCaptor<GetObjectRequest> requests = ArgumentCaptor.forClass(GetObjectRequest.class);
    verify(s3, times(2)).getObjectAsBytes(requests.capture());
    assertEquals(Arrays.asList("files/store-a/banner.png", "files/store-a/nested/terms.txt"),
        Arrays.asList(requests.getAllValues().get(0).key(), requests.getAllValues().get(1).key()));
  }

  @Test
  public void removesStaticFolderUsingLiteralPrefixKey() throws Exception {
    S3Client s3 = mock(S3Client.class);
    staticManager(s3, "us-east-1").removeFiles("store-a", Optional.<String>empty());
    assertDeleteKey(s3, "files/store-a/");
  }

  @Test
  public void readsAndRemovesProductImagesUsingLiteralKeys() throws Exception {
    S3Client s3 = mock(S3Client.class);
    when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
        .contents(S3Object.builder().key("products/store-a/sku-123/SMALL/front.png").build(),
            S3Object.builder().key("products/store-a/sku-123/LARGE/back.jpg").build())
        .build());
    when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
        ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[] { 4 }));
    S3ProductContentFileManager manager = productManager(s3, "us-east-1");
    manager.getImages("store-a", FileContentType.PRODUCT);

    ArgumentCaptor<ListObjectsV2Request> list = ArgumentCaptor.forClass(ListObjectsV2Request.class);
    verify(s3).listObjectsV2(list.capture());
    assertEquals("media-bucket", list.getValue().bucket());
    assertEquals("products/store-a/", list.getValue().prefix());
    ArgumentCaptor<GetObjectRequest> gets = ArgumentCaptor.forClass(GetObjectRequest.class);
    verify(s3, times(2)).getObjectAsBytes(gets.capture());
    assertEquals(Arrays.asList("products/store-a/sku-123/SMALL/front.png",
        "products/store-a/sku-123/LARGE/back.jpg"),
        Arrays.asList(gets.getAllValues().get(0).key(), gets.getAllValues().get(1).key()));

    manager.removeImages("store-a");
    manager.removeProductImage(productImage());
    manager.removeProductImages(product());
    ArgumentCaptor<Consumer<DeleteObjectRequest.Builder>> deletes =
        (ArgumentCaptor<Consumer<DeleteObjectRequest.Builder>>) (ArgumentCaptor<?>) ArgumentCaptor
            .forClass(Consumer.class);
    verify(s3, times(3)).deleteObject(deletes.capture());
    assertEquals(Arrays.asList("products/store-a/", "products/store-a/sku-123/front.png",
        "products/store-a/sku-123/"),
        Arrays.asList(deleteKey(deletes.getAllValues().get(0)),
            deleteKey(deletes.getAllValues().get(1)), deleteKey(deletes.getAllValues().get(2))));
  }

  @Test
  public void defaultsBlankStaticCmsRegion() {
    CMSManager cms = mock(CMSManager.class);
    when(cms.getLocation()).thenReturn("  ");
    ExposedStaticManager manager = new ExposedStaticManager();
    manager.setCmsManager(cms);

    S3Client client = manager.client();
    try {
      assertEquals(Region.US_EAST_1, client.serviceClientConfiguration().region());
    } finally {
      client.close();
    }
  }

  @Test
  public void uploadsStaticAndProductStreamsLargerThanBufferIntact() throws Exception {
    byte[] content = new byte[16387];
    for (int i = 0; i < content.length; i++) {
      content[i] = (byte) (i % 251);
    }
    S3Client s3 = mock(S3Client.class);
    InputContentFile staticFile = new InputContentFile();
    staticFile.setFileName("large.png");
    staticFile.setMimeType("image/png");
    staticFile.setFileContentType(FileContentType.IMAGE);
    staticFile.setFile(new java.io.ByteArrayInputStream(content));
    staticManager(s3, "us-east-1").addFile("store-a", Optional.<String>empty(), staticFile);
    assertUploadedBytes(s3, content, "files/store-a/large.png");

    ProductImage image = productImage();
    ImageContentFile productFile = new ImageContentFile();
    productFile.setMimeType("image/png");
    productFile.setFileContentType(FileContentType.PRODUCT);
    productFile.setFile(new java.io.ByteArrayInputStream(content));
    productManager(s3, "us-east-1").addProductImage(image, productFile);
    assertUploadedBytes(s3, content, "products/store-a/sku-123/SMALL/front.png");
  }

  private void assertUploadedBytes(S3Client s3, byte[] expected, String expectedKey) throws Exception {
    ArgumentCaptor<PutObjectRequest> requests = ArgumentCaptor.forClass(PutObjectRequest.class);
    ArgumentCaptor<RequestBody> bodies = ArgumentCaptor.forClass(RequestBody.class);
    verify(s3, times(1)).putObject(requests.capture(), bodies.capture());
    assertEquals(expectedKey, requests.getValue().key());
    try (InputStream stream = bodies.getValue().contentStreamProvider().newStream();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[1024];
      int count;
      while ((count = stream.read(buffer)) != -1) {
        output.write(buffer, 0, count);
      }
      assertArrayEquals(expected, output.toByteArray());
    }
    org.mockito.Mockito.clearInvocations(s3);
  }

  private S3StaticContentAssetsManagerImpl staticManager(S3Client s3, String region) {
    CMSManager cms = cms(region);
    S3StaticContentAssetsManagerImpl manager = new S3StaticContentAssetsManagerImpl() {
      @Override
      protected S3Client s3Client() {
        return s3;
      }
    };
    manager.setCmsManager(cms);
    return manager;
  }

  private S3ProductContentFileManager productManager(S3Client s3, String region) {
    S3ProductContentFileManager manager = new S3ProductContentFileManager() {
      @Override
      protected S3Client s3Client() {
        return s3;
      }
    };
    manager.setCmsManager(cms(region));
    return manager;
  }

  private CMSManager cms(String region) {
    CMSManager cms = mock(CMSManager.class);
    when(cms.getRootName()).thenReturn("media-bucket");
    when(cms.getLocation()).thenReturn(region);
    return cms;
  }

  private Product product() {
    MerchantStore store = new MerchantStore();
    store.setCode("store-a");
    Product product = new Product();
    product.setMerchantStore(store);
    product.setSku("sku-123");
    return product;
  }

  private ProductImage productImage() {
    ProductImage image = mock(ProductImage.class);
    when(image.getProduct()).thenReturn(product());
    when(image.getProductImage()).thenReturn("front.png");
    return image;
  }

  private void assertDeleteKey(S3Client s3, String expected) {
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Consumer<DeleteObjectRequest.Builder>> requests =
        (ArgumentCaptor<Consumer<DeleteObjectRequest.Builder>>) (ArgumentCaptor<?>) ArgumentCaptor
            .forClass(Consumer.class);
    verify(s3).deleteObject(requests.capture());
    assertEquals(expected, deleteKey(requests.getValue()));
  }

  private String deleteKey(Consumer<DeleteObjectRequest.Builder> consumer) {
    DeleteObjectRequest.Builder builder = DeleteObjectRequest.builder();
    consumer.accept(builder);
    return builder.build().key();
  }

  private static class ExposedStaticManager extends S3StaticContentAssetsManagerImpl {
    private S3Client client() {
      return super.s3Client();
    }
  }
}
