package com.salesmanager.core.business.modules.cms.product.local;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.modules.cms.impl.CMSManager;
import com.salesmanager.core.business.modules.cms.impl.LocalCacheManagerImpl;
import com.salesmanager.core.business.modules.cms.product.ProductAssetsManager;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.file.ProductImageSize;
import com.salesmanager.core.model.catalog.product.image.ProductImage;
import com.salesmanager.core.model.content.FileContentType;
import com.salesmanager.core.model.content.ImageContentFile;
import com.salesmanager.core.model.content.OutputContentFile;
import com.salesmanager.core.model.merchant.MerchantStore;

/**
 * Manager for storing and deleting image files from the CMS which is a web server
 * 
 * Manages - Product images
 * 
 * @author Carl Samson
 */
public class CmsImageFileManagerImpl
    implements ProductAssetsManager {

  /**
   * 
   */
  private static final long serialVersionUID = 1L;

  private static final Logger LOGGER = LoggerFactory.getLogger(CmsImageFileManagerImpl.class);

  private static CmsImageFileManagerImpl fileManager = null;

  private final static String ROOT_NAME = "";

  private final static String SMALL = "SMALL";
  private final static String LARGE = "LARGE";

  private static final String ROOT_CONTAINER = "products";

  private String rootName = ROOT_NAME;

  private LocalCacheManagerImpl cacheManager;

  @PostConstruct
  void init() {

    this.rootName = ((CMSManager) cacheManager).getRootName();
    LOGGER.info("init " + getClass().getName() + " setting root" + this.rootName);

  }

  public static CmsImageFileManagerImpl getInstance() {

    if (fileManager == null) {
      fileManager = new CmsImageFileManagerImpl();
    }

    return fileManager;

  }

  private CmsImageFileManagerImpl() {

  }

  /**
   * root/products/<merchant id>/<product id>/1.jpg
   */

  @Override
  public void addProductImage(ProductImage productImage, ImageContentFile contentImage)
      throws ServiceException {


    try {

      // base path
      Path root = Paths.get(buildRootPath()).toAbsolutePath().normalize();
      this.createDirectoryIfNorExist(root);

      // node path
      String merchantCode = productImage.getProduct().getMerchantStore().getCode();
      Path merchantPath = resolveWithinRoot(root, merchantCode);
      this.createDirectoryIfNorExist(merchantPath);

      // product path
      String sku = productImage.getProduct().getSku();
      Path dirPath = resolveWithinRoot(root, merchantCode, sku);
      this.createDirectoryIfNorExist(dirPath);

      // small large
      String sizeFolder;
      if (contentImage.getFileContentType().name().equals(FileContentType.PRODUCT.name())) {
        sizeFolder = SMALL;
      } else if (contentImage.getFileContentType().name()
          .equals(FileContentType.PRODUCTLG.name())) {
        sizeFolder = LARGE;
      } else {
        throw new IOException("Unsupported content type");
      }
      Path sizePath = resolveWithinRoot(root, merchantCode, sku, sizeFolder);
      this.createDirectoryIfNorExist(sizePath);


      // file creation
      Path path = resolveWithinRoot(root, merchantCode, sku, sizeFolder, contentImage.getFileName());
      InputStream isFile = contentImage.getFile();

      Files.copy(isFile, path, StandardCopyOption.REPLACE_EXISTING);


    } catch (Exception e) {

      throw new ServiceException(e);

    }

  }

  @Override
  public OutputContentFile getProductImage(ProductImage productImage) throws ServiceException {

    // the web server takes care of the images
    return null;

  }


  public List<OutputContentFile> getImages(MerchantStore store, FileContentType imageContentType)
      throws ServiceException {

    // the web server takes care of the images

    return null;

  }

  @Override
  public List<OutputContentFile> getImages(Product product) throws ServiceException {

    // the web server takes care of the images

    return null;
  }



  @Override
  public void removeImages(final String merchantStoreCode) throws ServiceException {

    try {

      Path root = Paths.get(buildRootPath()).toAbsolutePath().normalize();
      Path path = resolveWithinRoot(root, merchantStoreCode);
      Files.deleteIfExists(path);


    } catch (Exception e) {
      throw new ServiceException(e);
    }


  }


  @Override
  public void removeProductImage(ProductImage productImage) throws ServiceException {


    try {

      // delete small
      Path root = Paths.get(buildRootPath()).toAbsolutePath().normalize();
      String merchantCode = productImage.getProduct().getMerchantStore().getCode();
      String sku = productImage.getProduct().getSku();
      String imageName = productImage.getProductImage();
      Path smallPath = resolveWithinRoot(root, merchantCode, sku, SMALL, imageName);
      Files.deleteIfExists(smallPath);

      // delete large
      Path largePath = resolveWithinRoot(root, merchantCode, sku, LARGE, imageName);
      Files.deleteIfExists(largePath);

    } catch (Exception e) {
      throw new ServiceException(e);
    }


  }

  @Override
  public void removeProductImages(Product product) throws ServiceException {

    try {

      Path root = Paths.get(buildRootPath()).toAbsolutePath().normalize();
      Path path = resolveWithinRoot(root, product.getMerchantStore().getCode(), product.getSku());
      Files.deleteIfExists(path);

    } catch (Exception e) {
      throw new ServiceException(e);
    }

  }


  @Override
  public List<OutputContentFile> getImages(final String merchantStoreCode,
      FileContentType imageContentType) throws ServiceException {

    // the web server taks care of the images

    return null;
  }

  @Override
  public OutputContentFile getProductImage(String merchantStoreCode, String productCode,
      String imageName) throws ServiceException {
    return getProductImage(merchantStoreCode, productCode, imageName,
        ProductImageSize.SMALL.name());
  }

  @Override
  public OutputContentFile getProductImage(String merchantStoreCode, String productCode,
      String imageName, ProductImageSize size) throws ServiceException {
    return getProductImage(merchantStoreCode, productCode, imageName, size.name());
  }

  private OutputContentFile getProductImage(String merchantStoreCode, String productCode,
      String imageName, String size) throws ServiceException {

    return null;

  }

  private static String safeSegment(String value) throws IOException {
    if (value == null || value.trim().isEmpty()) {
      throw new IOException("Empty path segment");
    }
    if (value.contains("/") || value.contains("\\") || value.contains("\0")
        || ".".equals(value) || "..".equals(value)) {
      throw new IOException("Invalid path segment: " + value);
    }
    Path p = Paths.get(value);
    if (p.isAbsolute() || p.getNameCount() != 1) {
      throw new IOException("Invalid path segment: " + value);
    }
    return value;
  }

  private Path resolveWithinRoot(Path root, String... segments) throws IOException {
    Path normalizedRoot = root.toAbsolutePath().normalize();
    Path resolved = normalizedRoot;
    for (String segment : segments) {
      resolved = resolved.resolve(safeSegment(segment));
    }
    Path normalized = resolved.toAbsolutePath().normalize();
    if (!normalized.startsWith(normalizedRoot)) {
      throw new IOException("Path escapes content root: " + normalized);
    }
    return normalized;
  }


  private String buildRootPath() {
    return new StringBuilder().append(getRootName()).append(Constants.SLASH).append(ROOT_CONTAINER)
        .append(Constants.SLASH).toString();

  }


  private void createDirectoryIfNorExist(Path path) throws IOException {

    if (Files.notExists(path)) {
      Files.createDirectory(path);
    }
  }

  public void setRootName(String rootName) {
    this.rootName = rootName;
  }

  public String getRootName() {
    return rootName;
  }

  public LocalCacheManagerImpl getCacheManager() {
    return cacheManager;
  }

  public void setCacheManager(LocalCacheManagerImpl cacheManager) {
    this.cacheManager = cacheManager;
  }



}
