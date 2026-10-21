package com.salesmanager.core.business.modules.cms.product.infinispan;

import java.io.ByteArrayOutputStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.PostConstruct;

import org.apache.commons.io.IOUtils;
import org.infinispan.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.modules.cms.impl.CacheManager;
import com.salesmanager.core.business.modules.cms.impl.CmsCacheKeys;
import com.salesmanager.core.business.modules.cms.product.ProductAssetsManager;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.file.ProductImageSize;
import com.salesmanager.core.model.catalog.product.image.ProductImage;
import com.salesmanager.core.model.content.FileContentType;
import com.salesmanager.core.model.content.ImageContentFile;
import com.salesmanager.core.model.content.OutputContentFile;
import com.salesmanager.core.model.merchant.MerchantStore;

public class CmsImageFileManagerImpl implements ProductAssetsManager {
	private static final long serialVersionUID = 1L;
	private static final Logger LOGGER = LoggerFactory.getLogger(CmsImageFileManagerImpl.class);
	private static CmsImageFileManagerImpl fileManager;
	private static final String ROOT_NAME = "product-merchant";
	private static final String SMALL = "SMALL";
	private static final String LARGE = "LARGE";

	private String rootName = ROOT_NAME;
	private CacheManager cacheManager;

	public void stopFileManager() {
		try {
			cacheManager.getManager().stop();
			LOGGER.info("Stopping CMS");
		} catch (Exception e) {
			LOGGER.error("Error while stopping CmsImageFileManager", e);
		}
	}

	@PostConstruct
	void init() {
		rootName = cacheManager.getRootName();
		LOGGER.info("init " + getClass().getName() + " setting root" + rootName);
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
	public void addProductImage(ProductImage productImage, ImageContentFile contentImage) throws ServiceException {
		Cache<String, byte[]> cache = requireCache();
		try {
			String store = productImage.getProduct().getMerchantStore().getCode();
			String sku = productImage.getProduct().getSku();
			FileContentType type = contentImage.getFileContentType();
			String key = type == FileContentType.PRODUCT
					? CmsCacheKeys.join(rootName, store, sku, SMALL, contentImage.getFileName())
					: type == FileContentType.PRODUCTLG
							? CmsCacheKeys.join(rootName, store, sku, LARGE, contentImage.getFileName())
							: CmsCacheKeys.join(rootName, store, sku, contentImage.getFileName());
			cache.put(key, IOUtils.toByteArray(contentImage.getFile()));
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
		Cache<String, byte[]> cache = requireCache();
		return outputFiles(cache, CmsCacheKeys.directChildren(cache,
				CmsCacheKeys.join(rootName, product.getMerchantStore().getCode())));
	}

	@Override
	public void removeImages(String merchantStoreCode) throws ServiceException {
		CmsCacheKeys.removePrefix(requireCache(), CmsCacheKeys.join(rootName, merchantStoreCode) + "/");
	}

	@Override
	public void removeProductImage(ProductImage productImage) throws ServiceException {
		String store = productImage.getProduct().getMerchantStore().getCode();
		String sku = productImage.getProduct().getSku();
		String name = productImage.getProductImage();
		Cache<String, byte[]> cache = requireCache();
		cache.remove(CmsCacheKeys.join(rootName, store, sku, name));
		cache.remove(CmsCacheKeys.join(rootName, store, sku, SMALL, name));
		cache.remove(CmsCacheKeys.join(rootName, store, sku, LARGE, name));
	}

	@Override
	public void removeProductImages(Product product) throws ServiceException {
		CmsCacheKeys.removePrefix(requireCache(),
				CmsCacheKeys.join(rootName, product.getMerchantStore().getCode(), product.getSku()) + "/");
	}

	@Override
	public List<OutputContentFile> getImages(String merchantStoreCode, FileContentType imageContentType)
			throws ServiceException {
		Cache<String, byte[]> cache = requireCache();
		String prefix = CmsCacheKeys.join(rootName, merchantStoreCode) + "/";
		List<String> keys = new ArrayList<>();
		try (java.util.stream.Stream<String> stream = cache.keySet().stream()) {
			stream.filter(key -> key.startsWith(prefix))
					.filter(key -> key.substring(prefix.length()).split("/", -1).length == 2)
					.forEach(keys::add);
		}
		keys.sort(Comparator.comparing(key -> key.substring(key.lastIndexOf('/') + 1)));
		return outputFiles(cache, keys);
	}

	@Override
	public OutputContentFile getProductImage(String merchantStoreCode, String productCode, String imageName)
			throws ServiceException {
		return getProductImage(merchantStoreCode, productCode, imageName, ProductImageSize.SMALL.name());
	}

	@Override
	public OutputContentFile getProductImage(String merchantStoreCode, String productCode, String imageName,
			ProductImageSize size) throws ServiceException {
		return getProductImage(merchantStoreCode, productCode, imageName, size.name());
	}

	private OutputContentFile getProductImage(String merchantStoreCode, String productCode, String imageName,
			String size) throws ServiceException {
		String key = CmsCacheKeys.join(rootName, merchantStoreCode, productCode, size, imageName);
		byte[] bytes = requireCache().get(key);
		if (bytes == null) {
			LOGGER.warn("Image {} does not exist", imageName);
			return null;
		}
		return outputFile(imageName, bytes);
	}

	private List<OutputContentFile> outputFiles(Cache<String, byte[]> cache, List<String> keys) {
		List<OutputContentFile> files = new ArrayList<>();
		for (String key : keys) {
			byte[] bytes = cache.get(key);
			if (bytes != null) {
				files.add(outputFile(key.substring(key.lastIndexOf('/') + 1), bytes));
			}
		}
		return files;
	}

	private OutputContentFile outputFile(String name, byte[] bytes) {
		try {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			IOUtils.write(bytes, output);
			OutputContentFile file = new OutputContentFile();
			file.setFile(output);
			file.setMimeType(URLConnection.getFileNameMap().getContentTypeFor(name));
			file.setFileName(name);
			return file;
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private Cache<String, byte[]> requireCache() throws ServiceException {
		Cache<String, byte[]> cache = cacheManager.getCache();
		if (cache == null) {
			throw new ServiceException("CmsImageFileManagerInfinispan has a null cacheManager.getCache()");
		}
		return cache;
	}

	public void setCacheManager(CacheManager cacheManager) {
		this.cacheManager = cacheManager;
	}

	public String getRootName() {
		return rootName;
	}
}
