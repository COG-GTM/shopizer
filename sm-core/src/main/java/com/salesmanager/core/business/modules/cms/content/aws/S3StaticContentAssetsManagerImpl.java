package com.salesmanager.core.business.modules.cms.content.aws;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.collections4.CollectionUtils;
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
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.modules.cms.content.ContentAssetsManager;
import com.salesmanager.core.business.modules.cms.impl.CMSManager;
import com.salesmanager.core.model.content.FileContentType;
import com.salesmanager.core.model.content.InputContentFile;
import com.salesmanager.core.model.content.OutputContentFile;

/**
 * Static content management with S3
 * 
 * @author carlsamson
 *
 */
public class S3StaticContentAssetsManagerImpl implements ContentAssetsManager {

	private static final long serialVersionUID = 1L;

	private static final Logger LOGGER = LoggerFactory.getLogger(S3StaticContentAssetsManagerImpl.class);

	private static S3StaticContentAssetsManagerImpl fileManager = null;

	private CMSManager cmsManager;
	private volatile S3Client s3Client;
	private volatile String s3ClientRegion;

	public static S3StaticContentAssetsManagerImpl getInstance() {

		if (fileManager == null) {
			fileManager = new S3StaticContentAssetsManagerImpl();
		}

		return fileManager;

	}

	@Override
	public OutputContentFile getFile(String merchantStoreCode, Optional<String> folderPath, FileContentType fileContentType, String contentName)
			throws ServiceException {
		try {
			// get buckets
			String bucketName = bucketName();

			final S3Client s3 = s3Client();
			byte[] contents = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucketName)
					.key(nodePath(merchantStoreCode, fileContentType) + contentName).build()).asByteArray();

			LOGGER.info("Content getFile");
			return getOutputContentFile(contents);
		} catch (final Exception e) {
			LOGGER.error("Error while getting file", e);
			throw new ServiceException(e);

		}
	}

	@Override
	public List<String> getFileNames(String merchantStoreCode, Optional<String> folderPath, FileContentType fileContentType)
			throws ServiceException {
		try {
			// get buckets
			String bucketName = bucketName();

			ListObjectsV2Request listObjectsRequest = ListObjectsV2Request.builder().bucket(bucketName)
					.prefix(nodePath(merchantStoreCode, fileContentType)).build();

			List<String> fileNames = null;

			final S3Client s3 = s3Client();
			ListObjectsV2Response results = s3.listObjectsV2(listObjectsRequest);
			List<S3Object> objects = results.contents();
			for (S3Object os : objects) {
				if (isInsideSubFolder(os.key())) {
					continue;
				}
				if (fileNames == null) {
					fileNames = new ArrayList<String>();
				}
				String mimetype = URLConnection.guessContentTypeFromName(os.key());
				if (!StringUtils.isBlank(mimetype)) {
					fileNames.add(getName(os.key()));
				}
			}

			LOGGER.info("Content get file names");
			return fileNames;
		} catch (final Exception e) {
			LOGGER.error("Error while getting file names", e);
			throw new ServiceException(e);

		}
	}

	@Override
	public List<OutputContentFile> getFiles(String merchantStoreCode, Optional<String> folderPath, FileContentType fileContentType)
			throws ServiceException {
		try {
			// get buckets
			String bucketName = bucketName();

			ListObjectsV2Request listObjectsRequest = ListObjectsV2Request.builder().bucket(bucketName)
					.prefix(nodePath(merchantStoreCode, fileContentType)).build();

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

			LOGGER.info("Content getFiles");
			return files;
		} catch (final Exception e) {
			LOGGER.error("Error while getting files", e);
			throw new ServiceException(e);

		}
	}

	@Override
	public void addFile(String merchantStoreCode, Optional<String> folderPath, InputContentFile inputStaticContentData) throws ServiceException {

		try {
			// get buckets
			String bucketName = bucketName();

			String nodePath = nodePath(merchantStoreCode, inputStaticContentData.getFileContentType());

			final S3Client s3 = s3Client();
			PutObjectRequest request = PutObjectRequest.builder().bucket(bucketName)
					.key(nodePath + inputStaticContentData.getFileName())
					.contentType(inputStaticContentData.getMimeType())
					.acl(software.amazon.awssdk.services.s3.model.ObjectCannedACL.PUBLIC_READ).build();
			s3.putObject(request, RequestBody.fromBytes(readBytes(inputStaticContentData.getFile())));

			LOGGER.info("Content add file");
		} catch (final Exception e) {
			LOGGER.error("Error while adding file", e);
			throw new ServiceException(e);

		}

	}

	@Override
	public void addFiles(String merchantStoreCode, Optional<String> folderPath, List<InputContentFile> inputStaticContentDataList)
			throws ServiceException {

		if (CollectionUtils.isNotEmpty(inputStaticContentDataList)) {
			for (InputContentFile inputFile : inputStaticContentDataList) {
				this.addFile(merchantStoreCode, folderPath, inputFile);
			}

		}

	}

	@Override
	public void removeFile(String merchantStoreCode, FileContentType staticContentType, String fileName, Optional<String> folderPath)
			throws ServiceException {

		try {
			// get buckets
			String bucketName = bucketName();

			final S3Client s3 = s3Client();
			s3.deleteObject(builder -> builder.bucket(bucketName)
					.key(nodePath(merchantStoreCode, staticContentType) + fileName));

			LOGGER.info("Remove file");
		} catch (final Exception e) {
			LOGGER.error("Error while removing file", e);
			throw new ServiceException(e);

		}

	}

	@Override
	public void removeFiles(String merchantStoreCode, Optional<String> folderPath) throws ServiceException {

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
					LOGGER.debug("AWS CMS Using region " + region);
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

	private String regionName() {
		String regionName = getCmsManager().getLocation();
		if (StringUtils.isBlank(regionName)) {
			regionName = DEFAULT_REGION_NAME;
		}
		return regionName;
	}

	public CMSManager getCmsManager() {
		return cmsManager;
	}

	public void setCmsManager(CMSManager cmsManager) {
		this.cmsManager = cmsManager;
	}

	@Override
	public void addFolder(String merchantStoreCode, String folderName, Optional<String> folderPath) throws ServiceException {
		// TODO Auto-generated method stub

	}

	@Override
	public void removeFolder(String merchantStoreCode, String folderName, Optional<String> folderPath) throws ServiceException {
		// TODO Auto-generated method stub

	}


	@Override
	public List<String> listFolders(String merchantStoreCode, Optional<String> path) throws ServiceException {
		// TODO Auto-generated method stub
		return null;
	}

}
