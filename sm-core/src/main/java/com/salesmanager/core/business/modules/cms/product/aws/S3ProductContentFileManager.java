package com.salesmanager.core.business.modules.cms.product.aws;

import java.io.ByteArrayOutputStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.modules.cms.impl.CMSManager;
import com.salesmanager.core.business.modules.cms.product.ProductAssetsManager;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.file.ProductImageSize;
import com.salesmanager.core.model.catalog.product.image.ProductImage;
import com.salesmanager.core.model.content.FileContentType;
import com.salesmanager.core.model.content.ImageContentFile;
import com.salesmanager.core.model.content.OutputContentFile;

public class S3ProductContentFileManager implements ProductAssetsManager {

  private static final long serialVersionUID = 1L;

  private static final Logger LOGGER = LoggerFactory.getLogger(S3ProductContentFileManager.class);

  private static S3ProductContentFileManager fileManager = null;

  private static String DEFAULT_BUCKET_NAME = "shopizer-content";
  private static String DEFAULT_REGION_NAME = "us-east-1";
  private static final String ROOT_NAME = "products";

  private static final char UNIX_SEPARATOR = '/';
  private static final char WINDOWS_SEPARATOR = '\\';

  private final static String SMALL = "SMALL";
  private final static String LARGE = "LARGE";

  private CMSManager cmsManager;

  public static S3ProductContentFileManager getInstance() {
    if (fileManager == null) {
      fileManager = new S3ProductContentFileManager();
    }
    return fileManager;
  }

  @Override
  public List<OutputContentFile> getImages(String merchantStoreCode,
      FileContentType imageContentType) throws ServiceException {
    try {
      String bucketName = bucketName();

      ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
          .bucket(bucketName).prefix(nodePath(merchantStoreCode)).build();

      List<OutputContentFile> files = null;
      final S3Client s3 = s3Client();
      ListObjectsV2Response result = s3.listObjectsV2(listRequest);
      List<S3Object> objects = result.contents();
      for (S3Object os : objects) {
        if (files == null) {
          files = new ArrayList<>();
        }
        String mimetype = URLConnection.guessContentTypeFromName(os.key());
        if (!StringUtils.isBlank(mimetype)) {
          byte[] byteArray = s3.getObjectAsBytes(GetObjectRequest.builder()
              .bucket(bucketName).key(os.key()).build()).asByteArray();
          ByteArrayOutputStream baos = new ByteArrayOutputStream(byteArray.length);
          baos.write(byteArray, 0, byteArray.length);
          OutputContentFile ct = new OutputContentFile();
          ct.setFile(baos);
          files.add(ct);
        }
      }
      return files;
    } catch (final Exception e) {
      LOGGER.error("Error while getting files", e);
      throw new ServiceException(e);
    }
  }

  @Override
  public void removeImages(String merchantStoreCode) throws ServiceException {
    try {
      String bucketName = bucketName();
      final S3Client s3 = s3Client();
      s3.deleteObject(DeleteObjectRequest.builder()
          .bucket(bucketName).key(nodePath(merchantStoreCode)).build());
      LOGGER.info("Remove folder");
    } catch (final Exception e) {
      LOGGER.error("Error while removing folder", e);
      throw new ServiceException(e);
    }
  }

  @Override
  public void removeProductImage(ProductImage productImage) throws ServiceException {
    try {
      String bucketName = bucketName();
      final S3Client s3 = s3Client();
      s3.deleteObject(DeleteObjectRequest.builder()
          .bucket(bucketName)
          .key(nodePath(productImage.getProduct().getMerchantStore().getCode(),
              productImage.getProduct().getSku()) + productImage.getProductImage())
          .build());
      LOGGER.info("Remove file");
    } catch (final Exception e) {
      LOGGER.error("Error while removing file", e);
      throw new ServiceException(e);
    }
  }

  @Override
  public void removeProductImages(Product product) throws ServiceException {
    try {
      String bucketName = bucketName();
      final S3Client s3 = s3Client();
      s3.deleteObject(DeleteObjectRequest.builder()
          .bucket(bucketName)
          .key(nodePath(product.getMerchantStore().getCode(), product.getSku()))
          .build());
      LOGGER.info("Remove file");
    } catch (final Exception e) {
      LOGGER.error("Error while removing file", e);
      throw new ServiceException(e);
    }
  }

  @Override
  public OutputContentFile getProductImage(String merchantStoreCode, String productCode,
      String imageName) throws ServiceException {
    return null;
  }

  @Override
  public OutputContentFile getProductImage(String merchantStoreCode, String productCode,
      String imageName, ProductImageSize size) throws ServiceException {
    return null;
  }

  @Override
  public OutputContentFile getProductImage(ProductImage productImage) throws ServiceException {
    return null;
  }

  @Override
  public List<OutputContentFile> getImages(Product product) throws ServiceException {
    return null;
  }

  @Override
  public void addProductImage(ProductImage productImage, ImageContentFile contentImage)
      throws ServiceException {
    try {
      String bucketName = bucketName();
      final S3Client s3 = s3Client();

      String nodePath = this.nodePath(productImage.getProduct().getMerchantStore().getCode(),
          productImage.getProduct().getSku(), contentImage);

      String key = nodePath + productImage.getProductImage();

      PutObjectRequest putRequest = PutObjectRequest.builder()
          .bucket(bucketName)
          .key(key)
          .contentType(contentImage.getMimeType())
          .acl("public-read")
          .build();

      byte[] bytes = IOUtils.toByteArray(contentImage.getFile());
      s3.putObject(putRequest, RequestBody.fromBytes(bytes));

      LOGGER.info("Product add file");
    } catch (final Exception e) {
      LOGGER.error("Error while adding file", e);
      throw new ServiceException(e);
    }
  }

  private S3Client s3Client() {
    return S3Client.builder().region(Region.of(regionName())).build();
  }

  private String bucketName() {
    String bucketName = getCmsManager().getRootName();
    if (StringUtils.isBlank(bucketName)) {
      bucketName = DEFAULT_BUCKET_NAME;
    }
    return bucketName;
  }

  private String regionName() {
    String regionName = getCmsManager().getLocation();
    if (StringUtils.isBlank(regionName)) {
      regionName = DEFAULT_REGION_NAME;
    }
    return regionName;
  }

  private String nodePath(String store) {
    return new StringBuilder().append(ROOT_NAME).append(Constants.SLASH).append(store)
        .append(Constants.SLASH).toString();
  }

  private String nodePath(String store, String product) {
    StringBuilder sb = new StringBuilder();
    String nodePath = nodePath(store);
    sb.append(nodePath);
    sb.append(product).append(Constants.SLASH);
    return sb.toString();
  }

  private String nodePath(String store, String product, ImageContentFile contentImage) {
    StringBuilder sb = new StringBuilder();
    String nodePath = nodePath(store, product);
    sb.append(nodePath);
    if (contentImage.getFileContentType().name().equals(FileContentType.PRODUCT.name())) {
      sb.append(SMALL);
    } else if (contentImage.getFileContentType().name().equals(FileContentType.PRODUCTLG.name())) {
      sb.append(LARGE);
    }
    return sb.append(Constants.SLASH).toString();
  }

  public CMSManager getCmsManager() {
    return cmsManager;
  }

  public void setCmsManager(CMSManager cmsManager) {
    this.cmsManager = cmsManager;
  }
}
