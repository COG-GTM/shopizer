/**
 * 
 */
package com.salesmanager.core.business.modules.cms.content.infinispan;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.FileNameMap;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import org.apache.commons.io.IOUtils;
import org.infinispan.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.modules.cms.content.ContentAssetsManager;
import com.salesmanager.core.business.modules.cms.impl.CMSManager;
import com.salesmanager.core.business.modules.cms.impl.CacheManager;
import com.salesmanager.core.model.content.FileContentType;
import com.salesmanager.core.model.content.InputContentFile;
import com.salesmanager.core.model.content.OutputContentFile;

/**
 * Manages - Images - Files (js, pdf, css...) on infinispan
 * 
 * @author Umesh Awasthi
 * @since 1.2
 *
 */
public class CmsStaticContentFileManagerImpl
		implements ContentAssetsManager {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static final Logger LOGGER = LoggerFactory.getLogger(CmsStaticContentFileManagerImpl.class);
	private static CmsStaticContentFileManagerImpl fileManager = null;
	private static final String ROOT_NAME = "static-merchant-";

	private String rootName = ROOT_NAME;

	private CacheManager cacheManager;

	public void stopFileManager() {

		try {
			cacheManager.getManager().stop();
			LOGGER.info("Stopping CMS");
		} catch (final Exception e) {
			LOGGER.error("Error while stopping CmsStaticContentFileManager", e);
		}
	}

	@PostConstruct
	void init() {

		this.rootName = cacheManager.getRootName();
		LOGGER.info("init " + getClass().getName() + " setting root" + this.rootName);

	}

	public static CmsStaticContentFileManagerImpl getInstance() {

		if (fileManager == null) {
			fileManager = new CmsStaticContentFileManagerImpl();
		}

		return fileManager;

	}

	@Override
	public void addFile(final String merchantStoreCode, Optional<String>path, final InputContentFile inputStaticContentData)
			throws ServiceException {
		if (cacheManager.getCache() == null) {
			LOGGER.error("Unable to find cacheManager.getCache() in Infinispan..");
			throw new ServiceException(
					"CmsStaticContentFileManagerInfinispanImpl has a null cacheManager.getCache()");
		}
		try {

			String nodePath = this.getNodePath(merchantStoreCode, inputStaticContentData.getFileContentType());
			String key = buildKey(nodePath, inputStaticContentData.getFileName());

			cacheManager.getCache().put(key, IOUtils.toByteArray(inputStaticContentData.getFile()));

			LOGGER.info("Content data added successfully.");
		} catch (final Exception e) {
			LOGGER.error("Error while saving static content data", e);
			throw new ServiceException(e);

		}

	}

	@Override
	public void addFiles(final String merchantStoreCode, Optional<String> path, final List<InputContentFile> inputStaticContentDataList)
			throws ServiceException {
		if (cacheManager.getCache() == null) {
			LOGGER.error("Unable to find cacheManager.getCache() in Infinispan..");
			throw new ServiceException(
					"CmsStaticContentFileManagerInfinispanImpl has a null cacheManager.getCache()");
		}
		try {

			for (final InputContentFile inputStaticContentData : inputStaticContentDataList) {

				String nodePath = this.getNodePath(merchantStoreCode, inputStaticContentData.getFileContentType());
				String key = buildKey(nodePath, inputStaticContentData.getFileName());
				cacheManager.getCache().put(key, IOUtils.toByteArray(inputStaticContentData.getFile()));

			}

			LOGGER.info("Total {} files added successfully.", inputStaticContentDataList.size());

		} catch (final Exception e) {
			LOGGER.error("Error while saving content image", e);
			throw new ServiceException(e);

		}
	}

	@Override
	public OutputContentFile getFile(final String merchantStoreCode, Optional<String> path, final FileContentType fileContentType,
			final String contentFileName) throws ServiceException {

		if (cacheManager.getCache() == null) {
			throw new ServiceException("CmsStaticContentFileManagerInfinispan has a null cacheManager.getCache()");
		}
		OutputContentFile outputStaticContentData = new OutputContentFile();
		InputStream input = null;
		try {

			String nodePath = this.getNodePath(merchantStoreCode, fileContentType);
			String key = buildKey(nodePath, contentFileName);

			final byte[] fileBytes = (byte[]) cacheManager.getCache().get(key);

			if (fileBytes == null) {
				LOGGER.warn("file byte is null, no file found");
				return null;
			}

			input = new ByteArrayInputStream(fileBytes);

			final ByteArrayOutputStream output = new ByteArrayOutputStream();
			IOUtils.copy(input, output);

			outputStaticContentData.setFile(output);
			outputStaticContentData.setMimeType(URLConnection.getFileNameMap().getContentTypeFor(contentFileName));
			outputStaticContentData.setFileName(contentFileName);
			outputStaticContentData.setFileContentType(fileContentType);

		} catch (final Exception e) {
			LOGGER.error("Error while fetching file for {} merchant ", merchantStoreCode);
			throw new ServiceException(e);
		}
		return outputStaticContentData;
	}

	@Override
	public List<OutputContentFile> getFiles(final String merchantStoreCode, Optional<String> path, final FileContentType staticContentType)
			throws ServiceException {

		if (cacheManager.getCache() == null) {
			throw new ServiceException("CmsStaticContentFileManagerInfinispan has a null cacheManager.getCache()");
		}
		List<OutputContentFile> images = new ArrayList<OutputContentFile>();
		try {

			FileNameMap fileNameMap = URLConnection.getFileNameMap();
			String nodePath = this.getNodePath(merchantStoreCode, staticContentType);
			String prefix = getRootName() + nodePath + "/";

			Cache<String, Object> cache = cacheManager.getCache();

			for (String cacheKey : cache.keySet()) {
				if (cacheKey.startsWith(prefix)) {
					byte[] imageBytes = (byte[]) cache.get(cacheKey);

					OutputContentFile contentImage = new OutputContentFile();

					InputStream input = new ByteArrayInputStream(imageBytes);
					ByteArrayOutputStream output = new ByteArrayOutputStream();
					IOUtils.copy(input, output);

					String fileName = cacheKey.substring(cacheKey.lastIndexOf("/") + 1);
					String contentType = fileNameMap.getContentTypeFor(fileName);

					contentImage.setFile(output);
					contentImage.setMimeType(contentType);
					contentImage.setFileName(fileName);

					images.add(contentImage);
				}
			}

		} catch (final Exception e) {
			LOGGER.error("Error while fetching file for {} merchant ", merchantStoreCode);
			throw new ServiceException(e);
		}

		return images;

	}

	@Override
	public void removeFile(final String merchantStoreCode, final FileContentType staticContentType,
			final String fileName, Optional<String> path) throws ServiceException {

		if (cacheManager.getCache() == null) {
			throw new ServiceException("CmsStaticContentFileManagerInfinispan has a null cacheManager.getCache()");
		}

		try {

			String nodePath = this.getNodePath(merchantStoreCode, staticContentType);
			String key = buildKey(nodePath, fileName);
			cacheManager.getCache().remove(key);

		} catch (final Exception e) {
			LOGGER.error("Error while fetching file for {} merchant ", merchantStoreCode);
			throw new ServiceException(e);
		}

	}

	@Override
	public void removeFiles(final String merchantStoreCode, Optional<String> path) throws ServiceException {

		LOGGER.info("Removing all images for {} merchant ", merchantStoreCode);
		if (cacheManager.getCache() == null) {
			LOGGER.error("Unable to find cacheManager.getCache() in Infinispan..");
			throw new ServiceException("CmsImageFileManagerInfinispan has a null cacheManager.getCache()");
		}

		try {

			String prefix = getRootName() + merchantStoreCode;
			Cache<String, Object> cache = cacheManager.getCache();
			List<String> keysToRemove = cache.keySet().stream()
					.filter(k -> k.startsWith(prefix))
					.collect(Collectors.toList());
			keysToRemove.forEach(cache::remove);

		} catch (final Exception e) {
			LOGGER.error("Error while deleting content image for {} merchant ", merchantStoreCode);
			throw new ServiceException(e);
		}

	}

	private String buildKey(String nodePath, String fileName) {
		return getRootName() + nodePath + "/" + fileName;
	}

	private String getNodePath(final String storeCode, final FileContentType contentType) {

		StringBuilder nodePath = new StringBuilder();
		nodePath.append(storeCode).append("/").append(contentType.name());

		return nodePath.toString();

	}
	
	
	private String getFolder(final String storeCode, String folder) {

		return null;

	}

	public CacheManager getCacheManager() {
		return cacheManager;
	}

	public void setCacheManager(CacheManager cacheManager) {
		this.cacheManager = cacheManager;
	}

	@Override
	public List<String> getFileNames(final String merchantStoreCode, Optional<String> path, final FileContentType staticContentType)
			throws ServiceException {

		if (cacheManager.getCache() == null) {
			throw new ServiceException("CmsStaticContentFileManagerInfinispan has a null cacheManager.getCache()");
		}

		try {

			String nodePath = this.getNodePath(merchantStoreCode, staticContentType);
			String prefix = getRootName() + nodePath + "/";

			Cache<String, Object> cache = cacheManager.getCache();
			List<String> fileNames = cache.keySet().stream()
					.filter(k -> k.startsWith(prefix))
					.map(k -> k.substring(k.lastIndexOf("/") + 1))
					.collect(Collectors.toList());

			if (fileNames.isEmpty()) {
				LOGGER.warn("Unable to find content attribute for given merchant");
				return Collections.emptyList();
			}
			return fileNames;

		} catch (final Exception e) {
			LOGGER.error("Error while fetching file for {} merchant ", merchantStoreCode);
			throw new ServiceException(e);
		}

	}

	public void setRootName(String rootName) {
		this.rootName = rootName;
	}

	public String getRootName() {
		return rootName;
	}

	@Override
	public void addFolder(String merchantStoreCode, String folderName, Optional<String> path) throws ServiceException {
		// Folders are implicit in the flat cache key structure (prefix-based)
		// No explicit folder creation needed with flat cache approach
	}

	@Override
	public void removeFolder(String merchantStoreCode, String folderName, Optional<String> path) throws ServiceException {
		// TODO Auto-generated method stub

	}

	@Override
	public List<String> listFolders(String merchantStoreCode, Optional<String> path) throws ServiceException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CMSManager getCmsManager() {
    	return null;
  	}

}
