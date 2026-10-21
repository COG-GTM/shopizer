package com.salesmanager.core.business.modules.cms.content.infinispan;

import java.io.ByteArrayOutputStream;
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

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.modules.cms.content.ContentAssetsManager;
import com.salesmanager.core.business.modules.cms.impl.CMSManager;
import com.salesmanager.core.business.modules.cms.impl.CacheManager;
import com.salesmanager.core.business.modules.cms.impl.CmsCacheKeys;
import com.salesmanager.core.model.content.FileContentType;
import com.salesmanager.core.model.content.InputContentFile;
import com.salesmanager.core.model.content.OutputContentFile;

public class CmsStaticContentFileManagerImpl implements ContentAssetsManager {
	private static final long serialVersionUID = 1L;
	private static final Logger LOGGER = LoggerFactory.getLogger(CmsStaticContentFileManagerImpl.class);
	private static CmsStaticContentFileManagerImpl fileManager;
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
		rootName = cacheManager.getRootName();
		LOGGER.info("init " + getClass().getName() + " setting root" + rootName);
	}

	public static CmsStaticContentFileManagerImpl getInstance() {
		if (fileManager == null) {
			fileManager = new CmsStaticContentFileManagerImpl();
		}
		return fileManager;
	}

	@Override
	public void addFile(String storeCode, Optional<String> path, InputContentFile input) throws ServiceException {
		Cache<String, byte[]> cache = requireCache();
		try {
			cache.put(fileKey(storeCode, input.getFileContentType(), input.getFileName()),
					IOUtils.toByteArray(input.getFile()));
			LOGGER.info("Content data added successfully.");
		} catch (Exception e) {
			LOGGER.error("Error while saving static content data", e);
			throw new ServiceException(e);
		}
	}

	@Override
	public void addFiles(String storeCode, Optional<String> path, List<InputContentFile> inputs)
			throws ServiceException {
		Cache<String, byte[]> cache = requireCache();
		try {
			for (InputContentFile input : inputs) {
				cache.put(fileKey(storeCode, input.getFileContentType(), input.getFileName()),
						IOUtils.toByteArray(input.getFile()));
			}
			LOGGER.info("Total {} files added successfully.", inputs.size());
		} catch (Exception e) {
			LOGGER.error("Error while saving content image", e);
			throw new ServiceException(e);
		}
	}

	@Override
	public OutputContentFile getFile(String storeCode, Optional<String> path, FileContentType type, String name)
			throws ServiceException {
		Cache<String, byte[]> cache = requireCache();
		try {
			byte[] bytes = cache.get(fileKey(storeCode, type, name));
			if (bytes == null) {
				LOGGER.warn("file byte is null, no file found");
				return null;
			}
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			IOUtils.write(bytes, output);
			OutputContentFile file = new OutputContentFile();
			file.setFile(output);
			file.setMimeType(URLConnection.getFileNameMap().getContentTypeFor(name));
			file.setFileName(name);
			file.setFileContentType(type);
			return file;
		} catch (Exception e) {
			LOGGER.error("Error while fetching file for {} merchant ", storeCode);
			throw new ServiceException(e);
		}
	}

	@Override
	public List<OutputContentFile> getFiles(String storeCode, Optional<String> path, FileContentType type)
			throws ServiceException {
		Cache<String, byte[]> cache = requireCache();
		try {
			List<OutputContentFile> files = new ArrayList<>();
			for (String key : CmsCacheKeys.directChildren(cache, nodePath(storeCode, type))) {
				byte[] bytes = cache.get(key);
				if (bytes == null) {
					continue;
				}
				String name = key.substring(key.lastIndexOf('/') + 1);
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				IOUtils.write(bytes, output);
				OutputContentFile file = new OutputContentFile();
				file.setFile(output);
				file.setMimeType(URLConnection.getFileNameMap().getContentTypeFor(name));
				file.setFileName(name);
				files.add(file);
			}
			return files;
		} catch (Exception e) {
			LOGGER.error("Error while fetching file for {} merchant ", storeCode);
			throw new ServiceException(e);
		}
	}

	@Override
	public void removeFile(String storeCode, FileContentType type, String name, Optional<String> path)
			throws ServiceException {
		requireCache().remove(fileKey(storeCode, type, name));
	}

	@Override
	public void removeFiles(String storeCode, Optional<String> path) throws ServiceException {
		LOGGER.info("Removing all images for {} merchant ", storeCode);
		CmsCacheKeys.removePrefix(requireCache(), CmsCacheKeys.join(rootName, storeCode) + "/");
	}

	@Override
	public List<String> getFileNames(String storeCode, Optional<String> path, FileContentType type)
			throws ServiceException {
		List<String> names = CmsCacheKeys.directChildren(requireCache(), nodePath(storeCode, type)).stream()
				.map(key -> key.substring(key.lastIndexOf('/') + 1))
				.collect(Collectors.toList());
		if (names.isEmpty()) {
			LOGGER.warn("Unable to find content attribute for given merchant");
			return Collections.emptyList();
		}
		return names;
	}

	@Override
	public void addFolder(String storeCode, String folderName, Optional<String> path) throws ServiceException {
		String marker = CmsCacheKeys.join(rootName, storeCode, FileContentType.IMAGE.name(),
				path.orElse(null), folderName) + "/";
		requireCache().put(marker, new byte[0]);
	}

	@Override
	public void removeFolder(String storeCode, String folderName, Optional<String> path) throws ServiceException {
	}

	@Override
	public List<String> listFolders(String storeCode, Optional<String> path) throws ServiceException {
		return null;
	}

	@Override
	public String nodePath(String storeCode, FileContentType type) {
		return CmsCacheKeys.join(rootName, storeCode, type.name());
	}

	private String fileKey(String storeCode, FileContentType type, String name) {
		return CmsCacheKeys.join(nodePath(storeCode, type), name);
	}

	private Cache<String, byte[]> requireCache() throws ServiceException {
		Cache<String, byte[]> cache = cacheManager.getCache();
		if (cache == null) {
			throw new ServiceException("CmsStaticContentFileManagerInfinispan has a null cacheManager.getCache()");
		}
		return cache;
	}

	public void setCacheManager(CacheManager cacheManager) {
		this.cacheManager = cacheManager;
	}

	@Override
	public CMSManager getCmsManager() {
		return null;
	}

	public String getRootName() {
		return rootName;
	}

	public void setRootName(String rootName) {
		this.rootName = rootName;
	}
}
