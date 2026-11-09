package com.salesmanager.test.aws;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.modules.cms.content.aws.S3StaticContentAssetsManagerImpl;
import com.salesmanager.core.business.modules.cms.impl.CMSManager;
import com.salesmanager.core.business.modules.cms.product.aws.S3ProductContentFileManager;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.image.ProductImage;
import com.salesmanager.core.model.content.FileContentType;
import com.salesmanager.core.model.content.ImageContentFile;
import com.salesmanager.core.model.content.InputContentFile;
import com.salesmanager.core.model.content.OutputContentFile;
import com.salesmanager.core.model.merchant.MerchantStore;

/**
 * Exercises the AWS SDK v2 (S3Client) call sites of the S3 CMS managers against a local fake S3
 * endpoint: PutObject (+ public-read ACL and content type), ListObjectsV2, GetObject, DeleteObject.
 */
public class S3ContentManagersSdkV2Test {

	private static final String BUCKET = "shopizer-test-bucket";
	private static final byte[] PNG = new byte[] { (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2, 3, 4 };

	private static FakeAwsServer server;

	private static final CMSManager CMS = new CMSManager() {
		@Override
		public String getRootName() {
			return BUCKET;
		}

		@Override
		public String getLocation() {
			return "ca-central-1";
		}
	};

	@BeforeAll
	static void startServer() throws Exception {
		server = new FakeAwsServer();
		server.configureSdk();
	}

	@AfterAll
	static void stopServer() {
		FakeAwsServer.clearSdk();
		server.close();
	}

	@BeforeEach
	void reset() {
		server.objects().clear();
		server.requests().clear();
	}

	private S3StaticContentAssetsManagerImpl staticManager() {
		S3StaticContentAssetsManagerImpl m = new S3StaticContentAssetsManagerImpl();
		m.setCmsManager(CMS);
		return m;
	}

	private static InputContentFile image(String name, byte[] bytes) {
		InputContentFile f = new InputContentFile();
		f.setFileName(name);
		f.setMimeType("image/png");
		f.setFileContentType(FileContentType.IMAGE);
		f.setFile(new ByteArrayInputStream(bytes));
		return f;
	}

	@Test
	void staticContentPutListGetDeleteRoundTrip() throws Exception {
		S3StaticContentAssetsManagerImpl manager = staticManager();

		manager.addFile("DEFAULT", Optional.empty(), image("logo.png", PNG));

		assertArrayEquals(PNG, server.objects().get(BUCKET + "/files/DEFAULT/logo.png"),
				"object body stored under files/<store>/<name>");
		FakeAwsServer.RecordedRequest put = server.requests().stream().filter(r -> r.method.equals("PUT"))
				.findFirst().orElseThrow();
		assertEquals("/" + BUCKET + "/files/DEFAULT/logo.png", put.path);
		assertEquals("public-read", put.header("x-amz-acl"), "canned ACL PUBLIC_READ is kept");
		assertEquals("image/png", put.header("content-type"));
		assertTrue(put.header("authorization").contains("/ca-central-1/s3/aws4_request"),
				"request is signed for the CMS location region: " + put.header("authorization"));

		List<String> names = manager.getFileNames("DEFAULT", Optional.empty(), FileContentType.IMAGE);
		assertEquals(List.of("logo.png"), names);

		OutputContentFile out = manager.getFile("DEFAULT", Optional.empty(), FileContentType.IMAGE, "logo.png");
		assertNotNull(out);
		assertArrayEquals(PNG, out.getFile().toByteArray());

		List<OutputContentFile> files = manager.getFiles("DEFAULT", Optional.empty(), FileContentType.IMAGE);
		assertEquals(1, files.size());
		assertArrayEquals(PNG, files.get(0).getFile().toByteArray());

		manager.removeFile("DEFAULT", FileContentType.IMAGE, "logo.png", Optional.empty());
		assertFalse(server.objects().containsKey(BUCKET + "/files/DEFAULT/logo.png"));
	}

	@Test
	void staticContentFileNamesSkipSubFoldersAndUnknownMimeTypes() throws Exception {
		S3StaticContentAssetsManagerImpl manager = staticManager();
		manager.addFile("DEFAULT", Optional.empty(), image("a.png", PNG));
		manager.addFile("DEFAULT", Optional.empty(), image("b.jpg", PNG));
		server.objects().put(BUCKET + "/files/DEFAULT/sub/c.png", PNG);
		server.objects().put(BUCKET + "/files/DEFAULT/noextension", PNG);
		server.objects().put(BUCKET + "/files/OTHER/d.png", PNG);

		List<String> names = manager.getFileNames("DEFAULT", Optional.empty(), FileContentType.IMAGE);
		assertEquals(List.of("a.png", "b.jpg"), names.stream().sorted().collect(Collectors.toList()));
	}

	@Test
	void staticContentAddFilesUploadsEveryFile() throws Exception {
		S3StaticContentAssetsManagerImpl manager = staticManager();
		manager.addFiles("DEFAULT", Optional.empty(),
				List.of(image("one.png", PNG), image("two.png", "two".getBytes(StandardCharsets.UTF_8))));
		assertEquals(2, server.objects().size());
		assertArrayEquals("two".getBytes(StandardCharsets.UTF_8), server.objects().get(BUCKET + "/files/DEFAULT/two.png"));
	}

	@Test
	void staticContentMissingObjectIsWrappedInServiceException() {
		S3StaticContentAssetsManagerImpl manager = staticManager();
		assertThrows(ServiceException.class,
				() -> manager.getFile("DEFAULT", Optional.empty(), FileContentType.IMAGE, "missing.png"));
	}

	@Test
	void staticContentDefaultsBucketWhenRootNameBlank() throws Exception {
		S3StaticContentAssetsManagerImpl manager = new S3StaticContentAssetsManagerImpl();
		manager.setCmsManager(new CMSManager() {
			@Override
			public String getRootName() {
				return "";
			}

			@Override
			public String getLocation() {
				return null;
			}
		});
		manager.addFile("DEFAULT", Optional.empty(), image("x.png", PNG));
		FakeAwsServer.RecordedRequest put = server.requests().get(server.requests().size() - 1);
		assertEquals("/shopizer/files/DEFAULT/x.png", put.path, "DEFAULT_BUCKET_NAME is used");
		assertTrue(put.header("authorization").contains("/us-east-1/s3/aws4_request"),
				"DEFAULT_REGION_NAME is used: " + put.header("authorization"));
	}

	@Test
	void productImageUploadAndRemove() throws Exception {
		S3ProductContentFileManager manager = new S3ProductContentFileManager();
		manager.setCmsManager(CMS);

		MerchantStore store = new MerchantStore();
		store.setCode("DEFAULT");
		Product product = new Product();
		product.setSku("SKU-1");
		product.setMerchantStore(store);
		ProductImage productImage = new ProductImage();
		productImage.setProduct(product);
		productImage.setProductImage("front.png");

		ImageContentFile content = new ImageContentFile();
		content.setFileName("front.png");
		content.setMimeType("image/png");
		content.setFileContentType(FileContentType.PRODUCT);
		content.setFile(new ByteArrayInputStream(PNG));

		manager.addProductImage(productImage, content);

		String key = BUCKET + "/products/DEFAULT/SKU-1/SMALL/front.png";
		assertArrayEquals(PNG, server.objects().get(key));
		FakeAwsServer.RecordedRequest put = server.requests().stream().filter(r -> r.method.equals("PUT"))
				.findFirst().orElseThrow();
		assertEquals("public-read", put.header("x-amz-acl"));
		assertEquals("image/png", put.header("content-type"));

		List<OutputContentFile> images = manager.getImages("DEFAULT", FileContentType.PRODUCT);
		assertEquals(1, images.size());
		assertArrayEquals(PNG, images.get(0).getFile().toByteArray());

		manager.removeProductImages(product);
		FakeAwsServer.RecordedRequest delete = server.requests().get(server.requests().size() - 1);
		assertEquals("DELETE", delete.method);
		assertEquals("/" + BUCKET + "/products/DEFAULT/SKU-1/", delete.path);
	}
}
