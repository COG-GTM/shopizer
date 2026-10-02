package com.salesmanager.core.business.modules.cms.product.aws;

import java.io.ByteArrayOutputStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.modules.aws.AwsRegions;
import com.salesmanager.core.business.modules.cms.impl.CMSManager;
import com.salesmanager.core.business.modules.cms.product.ProductAssetsManager;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.file.ProductImageSize;
import com.salesmanager.core.model.catalog.product.image.ProductImage;
import com.salesmanager.core.model.content.FileContentType;
import com.salesmanager.core.model.content.ImageContentFile;
import com.salesmanager.core.model.content.OutputContentFile;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Product content file manager with AWS S3
 * 
 * @author carlsamson
 *
 */
public class S3ProductContentFileManager
    implements ProductAssetsManager {

  /**
   * 
   */
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
    String bucketName = bucketName();
    try (S3Client s3 = s3Client()) {
      List<OutputContentFile> files = null;
      ListObjectsV2Request listObjectsRequest = ListObjectsV2Request.builder()
          .bucket(bucketName).prefix(nodePath(merchantStoreCode)).build();
      for (S3Object os : s3.listObjectsV2Paginator(listObjectsRequest).contents()) {
        if (files == null) {
          files = new ArrayList<OutputContentFile>();
        }
        String mimetype = URLConnection.guessContentTypeFromName(os.key());
        if (!StringUtils.isBlank(mimetype)) {
          byte[] byteArray = s3.getObjectAsBytes(
              GetObjectRequest.builder().bucket(bucketName).key(os.key()).build()).asByteArray();
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
    String bucketName = bucketName();
    try (S3Client s3 = s3Client()) {
      deleteObject(s3, bucketName, nodePath(merchantStoreCode));

      LOGGER.info("Remove folder");
    } catch (final Exception e) {
      LOGGER.error("Error while removing folder", e);
      throw new ServiceException(e);

    }

  }

  @Override
  public void removeProductImage(ProductImage productImage) throws ServiceException {
    String bucketName = bucketName();
    try (S3Client s3 = s3Client()) {
      deleteObject(s3, bucketName, nodePath(productImage.getProduct().getMerchantStore().getCode(),
          productImage.getProduct().getSku()) + productImage.getProductImage());

      LOGGER.info("Remove file");
    } catch (final Exception e) {
      LOGGER.error("Error while removing file", e);
      throw new ServiceException(e);

    }

  }

  @Override
  public void removeProductImages(Product product) throws ServiceException {
    String bucketName = bucketName();
    try (S3Client s3 = s3Client()) {
      deleteObject(s3, bucketName, nodePath(product.getMerchantStore().getCode(), product.getSku()));

      LOGGER.info("Remove file");
    } catch (final Exception e) {
      LOGGER.error("Error while removing file", e);
      throw new ServiceException(e);

    }

  }

  @Override
  public OutputContentFile getProductImage(String merchantStoreCode, String productCode,
      String imageName) throws ServiceException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public OutputContentFile getProductImage(String merchantStoreCode, String productCode,
      String imageName, ProductImageSize size) throws ServiceException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public OutputContentFile getProductImage(ProductImage productImage) throws ServiceException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<OutputContentFile> getImages(Product product) throws ServiceException {
    return null;
  }

  @Override
  public void addProductImage(ProductImage productImage, ImageContentFile contentImage)
      throws ServiceException {


    String bucketName = bucketName();
    try (S3Client s3 = s3Client()) {
      String nodePath = this.nodePath(productImage.getProduct().getMerchantStore().getCode(),
          productImage.getProduct().getSku(), contentImage);

      PutObjectRequest request = PutObjectRequest.builder()
          .bucket(bucketName)
          .key(nodePath + productImage.getProductImage())
          .contentType(contentImage.getMimeType())
          .acl(ObjectCannedACL.PUBLIC_READ)
          .build();

      s3.putObject(request, RequestBody.fromBytes(IOUtils.toByteArray(contentImage.getFile())));

      LOGGER.info("Product add file");

    } catch (final Exception e) {
      LOGGER.error("Error while removing file", e);
      throw new ServiceException(e);

    }


  }


  private static void deleteObject(S3Client s3, String bucketName, String key) {
    s3.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
  }

  /**
   * Builds an amazon S3 client
   * 
   * @return
   */
  private S3Client s3Client() {
    return S3Client.builder().region(AwsRegions.of(regionName(), DEFAULT_REGION_NAME)).build();
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
    // node path
    String nodePath = nodePath(store);
    sb.append(nodePath);

    // product path
    sb.append(product).append(Constants.SLASH);
    return sb.toString();

  }

  private String nodePath(String store, String product, ImageContentFile contentImage) {

    StringBuilder sb = new StringBuilder();
    // node path
    String nodePath = nodePath(store, product);
    sb.append(nodePath);

    // small large
    if (contentImage.getFileContentType().name().equals(FileContentType.PRODUCT.name())) {
      sb.append(SMALL);
    } else if (contentImage.getFileContentType().name().equals(FileContentType.PRODUCTLG.name())) {
      sb.append(LARGE);
    }

    return sb.append(Constants.SLASH).toString();


  }

  public static String getName(String filename) {
    if (filename == null) {
      return null;
    }
    int index = indexOfLastSeparator(filename);
    return filename.substring(index + 1);
  }

  public static int indexOfLastSeparator(String filename) {
    if (filename == null) {
      return -1;
    }
    int lastUnixPos = filename.lastIndexOf(UNIX_SEPARATOR);
    int lastWindowsPos = filename.lastIndexOf(WINDOWS_SEPARATOR);
    return Math.max(lastUnixPos, lastWindowsPos);
  }



  public CMSManager getCmsManager() {
    return cmsManager;
  }

  public void setCmsManager(CMSManager cmsManager) {
    this.cmsManager = cmsManager;
  }


}
