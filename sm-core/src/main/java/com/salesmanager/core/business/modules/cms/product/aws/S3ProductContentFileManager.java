package com.salesmanager.core.business.modules.cms.product.aws;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
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
  private volatile S3Client s3Client;
  private volatile String s3ClientRegion;

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
      // get buckets
      String bucketName = bucketName();



      ListObjectsV2Request listObjectsRequest = ListObjectsV2Request.builder()
          .bucket(bucketName).prefix(nodePath(merchantStoreCode)).build();

      List<OutputContentFile> files = null;
      final S3Client s3 = s3Client();
      ListObjectsV2Response results = s3.listObjectsV2(listObjectsRequest);
      List<S3Object> objects = results.contents();
      for (S3Object os : objects) {
        if (files == null) {
          files = new ArrayList<OutputContentFile>();
        }
        String mimetype = URLConnection.guessContentTypeFromName(os.key());
        if (!StringUtils.isBlank(mimetype)) {
          byte[] byteArray = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucketName)
              .key(os.key()).build()).asByteArray();
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
      // get buckets
      String bucketName = bucketName();

      final S3Client s3 = s3Client();
      s3.deleteObject(builder -> builder.bucket(bucketName).key(nodePath(merchantStoreCode)));

      LOGGER.info("Remove folder");
    } catch (final Exception e) {
      LOGGER.error("Error while removing folder", e);
      throw new ServiceException(e);

    }

  }

  @Override
  public void removeProductImage(ProductImage productImage) throws ServiceException {
    try {
      // get buckets
      String bucketName = bucketName();

      final S3Client s3 = s3Client();
      s3.deleteObject(builder -> builder.bucket(bucketName)
          .key(nodePath(productImage.getProduct().getMerchantStore().getCode(),
              productImage.getProduct().getSku()) + productImage.getProductImage()));

      LOGGER.info("Remove file");
    } catch (final Exception e) {
      LOGGER.error("Error while removing file", e);
      throw new ServiceException(e);

    }

  }

  @Override
  public void removeProductImages(Product product) throws ServiceException {
    try {
      // get buckets
      String bucketName = bucketName();

      final S3Client s3 = s3Client();
      s3.deleteObject(builder -> builder.bucket(bucketName)
          .key(nodePath(product.getMerchantStore().getCode(), product.getSku())));

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


    try {
      // get buckets
      String bucketName = bucketName();
      final S3Client s3 = s3Client();

      String nodePath = this.nodePath(productImage.getProduct().getMerchantStore().getCode(),
          productImage.getProduct().getSku(), contentImage);


      PutObjectRequest request = PutObjectRequest.builder().bucket(bucketName)
          .key(nodePath + productImage.getProductImage()).contentType(contentImage.getMimeType())
          .acl(software.amazon.awssdk.services.s3.model.ObjectCannedACL.PUBLIC_READ).build();

      s3.putObject(request, RequestBody.fromBytes(readBytes(contentImage.getFile())));


      LOGGER.info("Product add file");

    } catch (final Exception e) {
      LOGGER.error("Error while removing file", e);
      throw new ServiceException(e);

    }


  }


  private Bucket getBucket(String bucket_name) {
    final S3Client s3 = s3Client();
    Bucket named_bucket = null;
    List<Bucket> buckets = s3.listBuckets().buckets();
    for (Bucket b : buckets) {
      if (b.name().equals(bucket_name)) {
        named_bucket = b;
      }
    }

    if (named_bucket == null) {
      named_bucket = createBucket(bucket_name);
    }

    return named_bucket;
  }

  private Bucket createBucket(String bucket_name) {
    final S3Client s3 = s3Client();
    Bucket b = null;
    try {
      s3.headBucket(HeadBucketRequest.builder().bucket(bucket_name).build());
      System.out.format("Bucket %s already exists.\n", bucket_name);
      b = getBucket(bucket_name);
    } catch (S3Exception e) {
      if (e.statusCode() != 404) {
        throw e;
      }
      try {
        s3.createBucket(CreateBucketRequest.builder().bucket(bucket_name).build());
        b = Bucket.builder().name(bucket_name).build();
      } catch (S3Exception createException) {
        System.err.println(createException.awsErrorDetails() == null
            ? createException.getMessage() : createException.awsErrorDetails().errorMessage());
      }
    }
    return b;
  }

  /**
   * Builds an amazon S3 client
   * 
   * @return
   */
  protected S3Client s3Client() {
    String region = regionName();
    if (s3Client == null || !region.equals(s3ClientRegion)) {
      synchronized (this) {
        if (s3Client == null || !region.equals(s3ClientRegion)) {
          s3Client = S3Client.builder().region(Region.of(region))
              .credentialsProvider(DefaultCredentialsProvider.create()).build();
          s3ClientRegion = region;
        }
      }
    }
    return s3Client;
  }

  void setS3Client(S3Client s3Client) {
    this.s3Client = s3Client;
    this.s3ClientRegion = regionName();
  }

  private byte[] readBytes(java.io.InputStream inputStream) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int read;
    while ((read = inputStream.read(buffer)) != -1) {
      outputStream.write(buffer, 0, read);
    }
    return outputStream.toByteArray();
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
