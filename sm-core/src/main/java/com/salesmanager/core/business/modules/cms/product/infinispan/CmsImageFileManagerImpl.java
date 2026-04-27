package com.salesmanager.core.business.modules.cms.product.infinispan;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.FileNameMap;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.apache.commons.io.IOUtils;
import org.infinispan.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.modules.cms.impl.CMSManager;
import com.salesmanager.core.business.modules.cms.impl.CacheManager;
import com.salesmanager.core.business.modules.cms.product.ProductAssetsManager;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.file.ProductImageSize;
import com.salesmanager.core.model.catalog.product.image.ProductImage;
import com.salesmanager.core.model.content.FileContentType;
import com.salesmanager.core.model.content.ImageContentFile;
import com.salesmanager.core.model.content.OutputContentFile;
import com.salesmanager.core.model.merchant.MerchantStore;

/**
 * Manager for storing in retrieving image files from the CMS This is a layer on top of Infinispan
 * 
 * Manages - Product images
 * 
 * @author Carl Samson
 */
public class CmsImageFileManagerImpl implements ProductAssetsManager {

  /**
   * 
   */
  private static final long serialVersionUID = 1L;

  private static final Logger LOGGER = LoggerFactory.getLogger(CmsImageFileManagerImpl.class);

  private static CmsImageFileManagerImpl fileManager = null;

  private final static String ROOT_NAME = "product-merchant";

  private final static String SMALL = "SMALL";
  private final static String LARGE = "LARGE";

  private String rootName = ROOT_NAME;

  private CacheManager cacheManager;


  /**
   * Requires to stop the engine when image servlet un-deploys
   */
  public void stopFileManager() {

    try {
      LOGGER.info("Stopping CMS");
      cacheManager.getManager().stop();
    } catch (Exception e) {
      LOGGER.error("Error while stopping CmsImageFileManager", e);
    }
  }

  @PostConstruct
  void init() {

    this.rootName = cacheManager.getRootName();
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

  @Override
  public void addProductImage(ProductImage productImage, ImageContentFile contentImage)
      throws ServiceException {

    if (cacheManager.getCache() == null) {
      throw new ServiceException(
          "CmsImageFileManagerInfinispan has a null cacheManager.getCache()");
    }

    try {

      StringBuilder nodePath = new StringBuilder();
      nodePath.append(productImage.getProduct().getMerchantStore().getCode())
          .append(Constants.SLASH).append(productImage.getProduct().getSku())
          .append(Constants.SLASH);


      if (contentImage.getFileContentType().name().equals(FileContentType.PRODUCT.name())) {
        nodePath.append(SMALL);
      } else if (contentImage.getFileContentType().name()
          .equals(FileContentType.PRODUCTLG.name())) {
        nodePath.append(LARGE);
      }

      String key = buildKey(nodePath.toString(), contentImage.getFileName());

      InputStream isFile = contentImage.getFile();

      ByteArrayOutputStream output = new ByteArrayOutputStream();
      IOUtils.copy(isFile, output);

      cacheManager.getCache().put(key, output.toByteArray());

    } catch (Exception e) {

      throw new ServiceException(e);

    }

  }

  @Override
  public OutputContentFile getProductImage(ProductImage productImage) throws ServiceException {

    return getProductImage(productImage.getProduct().getMerchantStore().getCode(),
        productImage.getProduct().getSku(), productImage.getProductImage());

  }


  public List<OutputContentFile> getImages(MerchantStore store, FileContentType imageContentType)
      throws ServiceException {

    return getImages(store.getCode(), imageContentType);

  }

  @Override
  public List<OutputContentFile> getImages(Product product) throws ServiceException {

    if (cacheManager.getCache() == null) {
      throw new ServiceException(
          "CmsImageFileManagerInfinispan has a null cacheManager.getCache()");
    }

    List<OutputContentFile> images = new ArrayList<OutputContentFile>();


    try {

      FileNameMap fileNameMap = URLConnection.getFileNameMap();
      String prefix = getRootName() + product.getMerchantStore().getCode() + Constants.SLASH;

      Cache<String, Object> cache = cacheManager.getCache();

      for (String cacheKey : cache.keySet()) {
        if (cacheKey.startsWith(prefix)) {
          byte[] imageBytes = (byte[]) cache.get(cacheKey);

          OutputContentFile contentImage = new OutputContentFile();

          InputStream input = new ByteArrayInputStream(imageBytes);
          ByteArrayOutputStream output = new ByteArrayOutputStream();
          IOUtils.copy(input, output);

          String fileName = cacheKey.substring(cacheKey.lastIndexOf(Constants.SLASH) + 1);
          String contentType = fileNameMap.getContentTypeFor(fileName);

          contentImage.setFile(output);
          contentImage.setMimeType(contentType);
          contentImage.setFileName(fileName);

          images.add(contentImage);
        }
      }


    }

    catch (Exception e) {
      throw new ServiceException(e);
    }

    return images;
  }



  @Override
  public void removeImages(final String merchantStoreCode) throws ServiceException {
    if (cacheManager.getCache() == null) {
      throw new ServiceException(
          "CmsImageFileManagerInfinispan has a null cacheManager.getCache()");
    }

    try {

      String prefix = getRootName() + merchantStoreCode;
      Cache<String, Object> cache = cacheManager.getCache();
      List<String> keysToRemove = cache.keySet().stream()
          .filter(k -> k.startsWith(prefix))
          .collect(Collectors.toList());
      keysToRemove.forEach(cache::remove);

    } catch (Exception e) {
      throw new ServiceException(e);
    }

  }


  @Override
  public void removeProductImage(ProductImage productImage) throws ServiceException {

    if (cacheManager.getCache() == null) {
      throw new ServiceException(
          "CmsImageFileManagerInfinispan has a null cacheManager.getCache()");
    }

    try {

      StringBuilder nodePath = new StringBuilder();
      nodePath.append(productImage.getProduct().getMerchantStore().getCode())
          .append(Constants.SLASH).append(productImage.getProduct().getSku());

      String prefix = getRootName() + nodePath.toString() + Constants.SLASH;
      String keyToRemove = prefix + productImage.getProductImage();
      // Try removing from both SMALL and LARGE subdirectories
      cacheManager.getCache().remove(keyToRemove);
      // Also try with size subdirectories
      cacheManager.getCache().remove(prefix + SMALL + Constants.SLASH + productImage.getProductImage());
      cacheManager.getCache().remove(prefix + LARGE + Constants.SLASH + productImage.getProductImage());

    } catch (Exception e) {
      throw new ServiceException(e);
    }

  }

  @Override
  public void removeProductImages(Product product) throws ServiceException {

    if (cacheManager.getCache() == null) {
      throw new ServiceException(
          "CmsImageFileManagerInfinispan has a null cacheManager.getCache()");
    }

    try {

      String prefix = getRootName() + product.getMerchantStore().getCode()
          + Constants.SLASH + product.getSku();

      Cache<String, Object> cache = cacheManager.getCache();
      List<String> keysToRemove = cache.keySet().stream()
          .filter(k -> k.startsWith(prefix))
          .collect(Collectors.toList());
      keysToRemove.forEach(cache::remove);

    } catch (Exception e) {
      throw new ServiceException(e);
    }

  }


  @Override
  public List<OutputContentFile> getImages(final String merchantStoreCode,
      FileContentType imageContentType) throws ServiceException {
    if (cacheManager.getCache() == null) {
      throw new ServiceException(
          "CmsImageFileManagerInfinispan has a null cacheManager.getCache()");
    }
    List<OutputContentFile> images = new ArrayList<OutputContentFile>();
    FileNameMap fileNameMap = URLConnection.getFileNameMap();

    try {

      String prefix = getRootName() + merchantStoreCode + Constants.SLASH;
      Cache<String, Object> cache = cacheManager.getCache();

      for (String cacheKey : cache.keySet()) {
        if (cacheKey.startsWith(prefix)) {
          byte[] imageBytes = (byte[]) cache.get(cacheKey);

          OutputContentFile contentImage = new OutputContentFile();

          InputStream input = new ByteArrayInputStream(imageBytes);
          ByteArrayOutputStream output = new ByteArrayOutputStream();
          IOUtils.copy(input, output);

          String fileName = cacheKey.substring(cacheKey.lastIndexOf(Constants.SLASH) + 1);
          String contentType = fileNameMap.getContentTypeFor(fileName);

          contentImage.setFile(output);
          contentImage.setMimeType(contentType);
          contentImage.setFileName(fileName);

          images.add(contentImage);
        }
      }

    } catch (Exception e) {
      throw new ServiceException(e);
    }

    return images;
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

    if (cacheManager.getCache() == null) {
      throw new ServiceException(
          "CmsImageFileManagerInfinispan has a null cacheManager.getCache()");
    }
    InputStream input = null;
    OutputContentFile contentImage = new OutputContentFile();
    try {

      FileNameMap fileNameMap = URLConnection.getFileNameMap();

      StringBuilder nodePath = new StringBuilder();
      nodePath.append(merchantStoreCode).append(Constants.SLASH).append(productCode)
          .append(Constants.SLASH).append(size);

      String key = buildKey(nodePath.toString(), imageName);

      byte[] imageBytes = (byte[]) cacheManager.getCache().get(key);

      if (imageBytes == null) {
        LOGGER.warn("Image " + imageName + " does not exist");
        return null;
      }

      input = new ByteArrayInputStream(imageBytes);
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      IOUtils.copy(input, output);

      String contentType = fileNameMap.getContentTypeFor(imageName);

      contentImage.setFile(output);
      contentImage.setMimeType(contentType);
      contentImage.setFileName(imageName);



    } catch (Exception e) {
      throw new ServiceException(e);
    } finally {
      if (input != null) {
        try {
          input.close();
        } catch (Exception ignore) {
        }
      }
    }

    return contentImage;

  }

  private String buildKey(String nodePath, String fileName) {
    return getRootName() + nodePath + Constants.SLASH + fileName;
  }

  public CacheManager getCacheManager() {
    return cacheManager;
  }

  public void setCacheManager(CacheManager cacheManager) {
    this.cacheManager = cacheManager;
  }

  public void setRootName(String rootName) {
    this.rootName = rootName;
  }

  public String getRootName() {
    return rootName;
  }



}
