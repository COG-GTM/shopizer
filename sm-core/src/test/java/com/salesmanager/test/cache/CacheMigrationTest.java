package com.salesmanager.test.cache;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.jcache.JCacheCacheManager;

import com.salesmanager.core.business.modules.cms.content.ContentAssetsManager;
import com.salesmanager.core.business.modules.cms.impl.CmsCacheKeys;
import com.salesmanager.core.business.modules.cms.product.ProductAssetsManager;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.business.utils.CacheUtils;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.file.ProductImageSize;
import com.salesmanager.core.model.catalog.product.image.ProductImage;
import com.salesmanager.core.model.content.FileContentType;
import com.salesmanager.core.model.content.ImageContentFile;
import com.salesmanager.core.model.content.InputContentFile;
import com.salesmanager.core.model.content.OutputContentFile;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.test.common.AbstractSalesManagerCoreTestCase;

public class CacheMigrationTest extends AbstractSalesManagerCoreTestCase {
	@Inject
	@Qualifier("defaultContentAssetsManager")
	private ContentAssetsManager contentAssetsManager;

	@Inject
	@Qualifier("defaultProductAssetsManager")
	private ProductAssetsManager productAssetsManager;

	@PersistenceUnit
	private EntityManagerFactory entityManagerFactory;

	@Inject
	private javax.cache.CacheManager jCacheManager;

	@Inject
	@Qualifier("serviceCacheManager")
	private JCacheCacheManager serviceCacheManager;

	@Inject
	private CacheUtils cacheUtils;

	@Test
	public void contentFilesUseFlatKeysAndFoldersAreNotListed() throws Exception {
		String store = "cms-" + UUID.randomUUID();
		byte[] image = { 1, 2, 3 };
		byte[] staticFile = { 4, 5, 6 };
		contentAssetsManager.addFile(store, Optional.empty(), input("b.png", FileContentType.IMAGE, image));
		contentAssetsManager.addFile(store, Optional.empty(), input("a.png", FileContentType.IMAGE, image));
		contentAssetsManager.addFile(store, Optional.empty(), input("app.js", FileContentType.STATIC_FILE, staticFile));
		contentAssetsManager.addFolder(store, "empty-folder", Optional.empty());

		assertArrayEquals(image, contentAssetsManager.getFile(store, Optional.empty(), FileContentType.IMAGE, "a.png")
				.getFile().toByteArray());
		assertArrayEquals(staticFile, contentAssetsManager.getFile(store, Optional.empty(), FileContentType.STATIC_FILE,
				"app.js").getFile().toByteArray());
		assertEquals(Arrays.asList("a.png", "b.png"),
				contentAssetsManager.getFileNames(store, Optional.empty(), FileContentType.IMAGE));
		assertEquals(Arrays.asList("a.png", "b.png"), fileNames(
				contentAssetsManager.getFiles(store, Optional.empty(), FileContentType.IMAGE)));
		assertNull(contentAssetsManager.getFile(store, Optional.empty(), FileContentType.IMAGE, "empty-folder"));

		contentAssetsManager.removeFile(store, FileContentType.IMAGE, "a.png", Optional.empty());
		assertNull(contentAssetsManager.getFile(store, Optional.empty(), FileContentType.IMAGE, "a.png"));
		contentAssetsManager.removeFiles(store, Optional.empty());
		assertTrue(contentAssetsManager.getFileNames(store, Optional.empty(), FileContentType.IMAGE).isEmpty());
		assertTrue(contentAssetsManager.getFileNames(store, Optional.empty(), FileContentType.STATIC_FILE).isEmpty());
	}

	@Test
	public void productImagesUseSizePathsAndRemoveAllSkuVariants() throws Exception {
		String storeCode = "cms-" + UUID.randomUUID();
		String sku = "sku-" + UUID.randomUUID();
		String name = "image.png";
		MerchantStore store = new MerchantStore();
		store.setCode(storeCode);
		Product product = new Product();
		product.setMerchantStore(store);
		product.setSku(sku);
		ProductImage productImage = new ProductImage();
		productImage.setProduct(product);
		productImage.setProductImage(name);

		byte[] small = { 7, 8, 9 };
		byte[] large = { 10, 11, 12 };
		byte[] direct = { 13, 14, 15 };
		productAssetsManager.addProductImage(productImage, image(name, FileContentType.PRODUCT, small));
		productAssetsManager.addProductImage(productImage, image(name, FileContentType.PRODUCTLG, large));
		productAssetsManager.addProductImage(productImage, image(name, FileContentType.IMAGE, direct));

		assertArrayEquals(small, productAssetsManager.getProductImage(storeCode, sku, name, ProductImageSize.SMALL)
				.getFile().toByteArray());
		assertArrayEquals(large, productAssetsManager.getProductImage(storeCode, sku, name, ProductImageSize.LARGE)
				.getFile().toByteArray());
		List<OutputContentFile> storeImages = productAssetsManager.getImages(storeCode, FileContentType.IMAGE);
		assertEquals(Collections.singletonList(name), fileNames(storeImages));
		assertArrayEquals(direct, storeImages.get(0).getFile().toByteArray());
		assertTrue(productAssetsManager.getImages(product).isEmpty());
		productAssetsManager.removeProductImage(productImage);
		assertNull(productAssetsManager.getProductImage(storeCode, sku, name, ProductImageSize.SMALL));
		assertNull(productAssetsManager.getProductImage(storeCode, sku, name, ProductImageSize.LARGE));
		assertTrue(productAssetsManager.getImages(storeCode, FileContentType.IMAGE).isEmpty());

		productAssetsManager.addProductImage(productImage, image(name, FileContentType.PRODUCT, small));
		productAssetsManager.removeProductImages(product);
		assertNull(productAssetsManager.getProductImage(storeCode, sku, name, ProductImageSize.SMALL));
		productAssetsManager.addProductImage(productImage, image(name, FileContentType.PRODUCTLG, large));
		productAssetsManager.removeImages(storeCode);
		assertNull(productAssetsManager.getProductImage(storeCode, sku, name, ProductImageSize.LARGE));
	}

	@Test
	public void cacheKeyHelperSeparatesPrefixesAndExcludesDeepAndFolderKeys() {
		DefaultCacheManager manager = new DefaultCacheManager(new ConfigurationBuilder().build());
		try {
			Cache<String, byte[]> cache = manager.getCache();
			cache.put("root/DEFAULT/a", new byte[0]);
			cache.put("root/DEFAULT/deep/b", new byte[0]);
			cache.put("root/DEFAULT/folder/", new byte[0]);
			cache.put("root/DEFAULT2/c", new byte[0]);
			assertEquals(Collections.singletonList("root/DEFAULT/a"),
					CmsCacheKeys.directChildren(cache, "root/DEFAULT"));
			assertEquals("root/DEFAULT/a", CmsCacheKeys.join("root", "DEFAULT", "a"));
			CmsCacheKeys.removePrefix(cache, "root/DEFAULT/");
			assertFalse(cache.containsKey("root/DEFAULT/a"));
			assertTrue(cache.containsKey("root/DEFAULT2/c"));
		} finally {
			manager.stop();
		}
	}

	@Test
	public void hibernateAndSpringShareJCacheManagerAndCacheRegions() throws Exception {
		assertTrue(serviceCacheManager.getCacheManager() == jCacheManager);
		List<String> cacheNames = new ArrayList<>();
		jCacheManager.getCacheNames().forEach(cacheNames::add);
		assertTrue(cacheNames.containsAll(Arrays.asList("store", "countriesMap", "countrByCode", "zoneByCode",
				"languageByCode", "com.shopizer.OBJECT_CACHE")));

		Language language = languageService.getByCode("en");
		assertNotNull(language);
		org.hibernate.SessionFactory sessionFactory = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class);
		org.hibernate.stat.Statistics statistics = sessionFactory.getStatistics();
		statistics.setStatisticsEnabled(true);
		sessionFactory.getCache().evictEntityData(Language.class);
		statistics.clear();
		loadLanguage(sessionFactory, language.getId());
		long puts = statistics.getSecondLevelCachePutCount();
		boolean cached = sessionFactory.getCache().containsEntity(Language.class, language.getId());
		loadLanguage(sessionFactory, language.getId());
		long regionHits = statistics.getSecondLevelCacheStatistics(
				"com.salesmanager.core.model.reference.language.Language").getHitCount();
		assertTrue("Expected a second-level cache hit, got " + statistics.getSecondLevelCacheHitCount()
				+ " (puts=" + puts + ", containsEntity=" + cached + ", regionHits=" + regionHits + ")",
				statistics.getSecondLevelCacheHitCount() > 0);
		assertTrue(regionHits > 0);
		javax.cache.Cache<?, ?> regionCache =
				jCacheManager.getCache("com.salesmanager.core.model.reference.language.Language");
		assertNotNull(regionCache);
		assertTrue(regionCache.iterator().hasNext());

		languageService.getByCode("en");
		languageService.getByCode("en");
		assertTrue(jCacheManager.getCache("languageByCode").iterator().hasNext());

		try {
			cacheUtils.putInCache("value", "1_FOO");
			cacheUtils.putInCache("value2", "not-a-store-key");
			assertEquals("value", cacheUtils.getFromCache("1_FOO"));
			assertTrue(cacheUtils.getCacheKeys(new MerchantStore()).contains("FOO"));
			cacheUtils.removeAllFromCache(new MerchantStore());
			assertNull(cacheUtils.getFromCache("1_FOO"));
			assertEquals("value2", cacheUtils.getFromCache("not-a-store-key"));
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}

	private static void loadLanguage(org.hibernate.SessionFactory sessionFactory, Object id) {
		EntityManager entityManager = sessionFactory.createEntityManager();
		try {
			assertNotNull(entityManager.find(Language.class, id));
		} finally {
			entityManager.close();
		}
	}

	private static InputContentFile input(String name, FileContentType type, byte[] bytes) {
		InputContentFile file = new InputContentFile();
		file.setFileName(name);
		file.setFileContentType(type);
		file.setFile(new ByteArrayInputStream(bytes));
		return file;
	}

	private static ImageContentFile image(String name, FileContentType type, byte[] bytes) {
		ImageContentFile file = new ImageContentFile();
		file.setFileName(name);
		file.setFileContentType(type);
		file.setFile(new ByteArrayInputStream(bytes));
		return file;
	}

	private static List<String> fileNames(List<OutputContentFile> files) {
		List<String> names = new ArrayList<>();
		files.forEach(file -> names.add(file.getFileName()));
		return names;
	}
}
