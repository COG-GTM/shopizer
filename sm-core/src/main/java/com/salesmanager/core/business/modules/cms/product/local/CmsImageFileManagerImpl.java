package com.salesmanager.core.business.modules.cms.product.local;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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
import com.salesmanager.core.business.utils.FileNameValidator;
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
      Path root = rootPath();
      this.createDirectories(root);

      String merchantCode = productImage.getProduct().getMerchantStore().getCode();
      String sku = productImage.getProduct().getSku();

      // small large
      String size;
      if (contentImage.getFileContentType().name().equals(FileContentType.PRODUCT.name())) {
        size = SMALL;
      } else if (contentImage.getFileContentType().name()
          .equals(FileContentType.PRODUCTLG.name())) {
        size = LARGE;
      } else {
        throw new ServiceException("Unsupported product image content type "
            + contentImage.getFileContentType());
      }

      Path sizePath = resolveWithin(root, merchantCode, sku, size);
      this.createDirectories(sizePath);

      // file creation
      Path path = resolveWithin(sizePath, contentImage.getFileName());
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


      Path path = resolveWithin(rootPath(), merchantStoreCode);

      Files.deleteIfExists(path);


    } catch (Exception e) {
      throw new ServiceException(e);
    }


  }


  @Override
  public void removeProductImage(ProductImage productImage) throws ServiceException {


    try {

      if (!FileNameValidator.isSafeFileName(productImage.getProductImage())) {
        LOGGER.warn("Not deleting product image file with unsafe name [{}]",
            productImage.getProductImage());
        return;
      }

      Path productPath = resolveWithin(rootPath(),
          productImage.getProduct().getMerchantStore().getCode(),
          productImage.getProduct().getSku());

      // delete small
      Files.deleteIfExists(resolveWithin(productPath, SMALL, productImage.getProductImage()));

      // delete large
      Files.deleteIfExists(resolveWithin(productPath, LARGE, productImage.getProductImage()));

    } catch (Exception e) {
      throw new ServiceException(e);
    }


  }

  @Override
  public void removeProductImages(Product product) throws ServiceException {

    try {

      Path path = resolveWithin(rootPath(), product.getMerchantStore().getCode(), product.getSku());

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


  private String buildRootPath() {
    return new StringBuilder().append(getRootName()).append(Constants.SLASH).append(ROOT_CONTAINER)
        .append(Constants.SLASH).toString();

  }


  private Path rootPath() {
    return Paths.get(buildRootPath()).toAbsolutePath().normalize();
  }

  /**
   * Resolves each segment as a single file name under base and rejects any result that is not
   * contained in base.
   */
  private static Path resolveWithin(Path base, String... segments) throws ServiceException {
    Path normalizedBase = base.toAbsolutePath().normalize();
    Path resolved = normalizedBase;
    for (String segment : segments) {
      if (!FileNameValidator.isSafeFileName(segment)) {
        throw new ServiceException("Invalid path segment [" + segment + "]");
      }
      resolved = resolved.resolve(segment);
    }
    resolved = resolved.normalize();
    if (!resolved.startsWith(normalizedBase) || resolved.equals(normalizedBase)) {
      throw new ServiceException("Path escapes the product image folder");
    }
    ensureRealPathWithin(normalizedBase, resolved);
    return resolved;
  }

  /**
   * Rejects paths whose closest existing ancestor resolves (following symbolic links) outside base.
   */
  private static void ensureRealPathWithin(Path base, Path path) throws ServiceException {
    if (Files.notExists(base)) {
      return;
    }
    Path existing = path;
    while (existing != null && Files.notExists(existing, LinkOption.NOFOLLOW_LINKS)) {
      existing = existing.getParent();
    }
    try {
      if (existing == null || !existing.toRealPath().startsWith(base.toRealPath())) {
        throw new ServiceException("Path escapes the product image folder");
      }
    } catch (IOException e) {
      throw new ServiceException("Cannot resolve product image path", e);
    }
  }

  private void createDirectories(Path path) throws IOException {

    if (Files.notExists(path)) {
      Files.createDirectories(path);
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
