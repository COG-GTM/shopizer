package com.salesmanager.test.cache;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.util.UUID;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.jcache.JCacheCacheManager;

import com.salesmanager.core.business.modules.cms.content.ContentAssetsManager;
import com.salesmanager.core.business.modules.cms.product.ProductAssetsManager;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.file.ProductImageSize;
import com.salesmanager.core.model.catalog.product.image.ProductImage;
import com.salesmanager.core.model.content.FileContentType;
import com.salesmanager.core.model.content.ImageContentFile;
import com.salesmanager.core.model.content.InputContentFile;
import com.salesmanager.core.model.content.OutputContentFile;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.country.Country;
import com.salesmanager.core.model.reference.currency.Currency;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.reference.zone.Zone;
import com.salesmanager.test.common.AbstractSalesManagerCoreTestCase;

public class CacheBehaviourIntegrationTest extends AbstractSalesManagerCoreTestCase {

	@Inject
	private javax.cache.CacheManager jCacheManager;

	@Inject
	@Qualifier("serviceCacheManager")
	private JCacheCacheManager serviceCacheManager;

	@PersistenceUnit
	private EntityManagerFactory entityManagerFactory;

	@Inject
	@Qualifier("defaultContentAssetsManager")
	private ContentAssetsManager contentAssetsManager;

	@Inject
	@Qualifier("defaultProductAssetsManager")
	private ProductAssetsManager productAssetsManager;

	@Test
	public void cacheableReferenceLookupsPopulateSharedJCacheRegions() throws Exception {
		serviceCacheManager.getCache("languageByCode").clear();
		serviceCacheManager.getCache("countrByCode").clear();
		serviceCacheManager.getCache("zoneByCode").clear();

		assertNotNull(languageService.getByCode("en"));
		assertNotNull(countryService.getByCode("CA"));
		assertNotNull(zoneService.getByCode("QC"));
		assertTrue(jCacheManager.getCache("languageByCode").containsKey("en"));
		assertTrue(jCacheManager.getCache("countrByCode").containsKey("CA"));
		assertTrue(jCacheManager.getCache("zoneByCode").containsKey("QC"));
	}

	@Test
	public void languageServiceReadsEntriesFromTheSharedJCacheRegion() throws Exception {
		String code = "zz-" + UUID.randomUUID();
		Language sentinel = new Language();
		sentinel.setCode(code);
		jCacheManager.getCache("languageByCode").put(code, sentinel);

		try {
			assertSame(sentinel, languageService.getByCode(code));
		} finally {
			jCacheManager.getCache("languageByCode").remove(code);
		}
	}

	@Test
	public void countryAndCurrencyEntitiesAreRetrievedFromHibernateSecondLevelCache() throws Exception {
		SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
		Country country = countryService.getByCode("CA");
		Currency currency = currencyService.getByCode("CAD");
		assertNotNull(country);
		assertNotNull(currency);

		assertSecondLevelCacheHit(sessionFactory, Country.class, country.getId(),
				"com.salesmanager.core.model.reference.country.Country");
		assertSecondLevelCacheHit(sessionFactory, Currency.class, currency.getId(),
				"com.salesmanager.core.model.reference.currency.Currency");
	}

	@Test
	public void merchantStoreIsNotStoredInSelectiveSecondLevelCache() {
		SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
		Serializable id = Integer.valueOf(1);
		sessionFactory.getCache().evictEntityData(MerchantStore.class, id);
		EntityManager entityManager = sessionFactory.createEntityManager();
		try {
			assertNotNull(entityManager.find(MerchantStore.class, id));
		} finally {
			entityManager.close();
		}

		assertFalse(sessionFactory.getCache().containsEntity(MerchantStore.class, id));
	}

	@Test
	public void hibernateUsesTheSpringJCacheManagerAndJCacheRegionFactory() {
		assertSame(jCacheManager, entityManagerFactory.getProperties().get("hibernate.javax.cache.cache_manager"));
		assertEquals("org.hibernate.cache.jcache.internal.JCacheRegionFactory",
				entityManagerFactory.getProperties().get("hibernate.cache.region.factory_class"));
	}

	@Test
	public void contentAssetsManagerHandlesMissingFilesOverwriteAndStoreIsolation() throws Exception {
		String store = "cms-" + UUID.randomUUID();
		String siblingStore = store + "2";
		String fileName = "same.png";
		byte[] original = { 1, 2, 3 };
		byte[] overwritten = { 4, 5, 6 };
		byte[] siblingBytes = { 7, 8, 9 };

		try {
			assertNull(contentAssetsManager.getFile(store, java.util.Optional.empty(), FileContentType.IMAGE,
					"missing.png"));
			assertTrue(contentAssetsManager.getFileNames(store, java.util.Optional.empty(),
					FileContentType.IMAGE).isEmpty());
			assertTrue(contentAssetsManager.getFiles(store, java.util.Optional.empty(),
					FileContentType.IMAGE).isEmpty());

			contentAssetsManager.addFile(store, java.util.Optional.empty(),
					input(fileName, FileContentType.IMAGE, original));
			contentAssetsManager.addFile(store, java.util.Optional.empty(),
					input(fileName, FileContentType.IMAGE, overwritten));
			contentAssetsManager.addFile(siblingStore, java.util.Optional.empty(),
					input(fileName, FileContentType.IMAGE, siblingBytes));

			assertEquals(java.util.Collections.singletonList(fileName),
					contentAssetsManager.getFileNames(store, java.util.Optional.empty(), FileContentType.IMAGE));
			assertEquals(java.util.Collections.singletonList(fileName),
					contentAssetsManager.getFileNames(siblingStore, java.util.Optional.empty(), FileContentType.IMAGE));
			assertArrayEquals(overwritten, contentAssetsManager.getFile(store, java.util.Optional.empty(),
					FileContentType.IMAGE, fileName).getFile().toByteArray());

			contentAssetsManager.removeFiles(store, java.util.Optional.empty());
			assertTrue(contentAssetsManager.getFileNames(store, java.util.Optional.empty(),
					FileContentType.IMAGE).isEmpty());
			assertArrayEquals(siblingBytes, contentAssetsManager.getFile(siblingStore,
					java.util.Optional.empty(), FileContentType.IMAGE, fileName).getFile().toByteArray());
		} finally {
			contentAssetsManager.removeFiles(store, java.util.Optional.empty());
			contentAssetsManager.removeFiles(siblingStore, java.util.Optional.empty());
		}
	}

	@Test
	public void removingProductImagesDoesNotRemoveAnotherSkuWithTheSamePrefix() throws Exception {
		String storeCode = "cms-" + UUID.randomUUID();
		String sku = "sku-" + UUID.randomUUID();
		String siblingSku = sku + "2";
		String fileName = "image.png";
		byte[] productBytes = { 10, 11, 12 };
		byte[] siblingBytes = { 13, 14, 15 };
		MerchantStore store = new MerchantStore();
		store.setCode(storeCode);
		Product product = product(store, sku);
		Product sibling = product(store, siblingSku);
		ProductImage productImage = imageReference(product, fileName);
		ProductImage siblingImage = imageReference(sibling, fileName);

		try {
			assertNull(productAssetsManager.getProductImage(storeCode, "missing", fileName,
					ProductImageSize.SMALL));
			productAssetsManager.addProductImage(productImage,
					image(fileName, FileContentType.PRODUCT, productBytes));
			productAssetsManager.addProductImage(siblingImage,
					image(fileName, FileContentType.PRODUCT, siblingBytes));

			productAssetsManager.removeProductImages(product);

			assertNull(productAssetsManager.getProductImage(storeCode, sku, fileName, ProductImageSize.SMALL));
			assertArrayEquals(siblingBytes, productAssetsManager.getProductImage(storeCode, siblingSku, fileName,
					ProductImageSize.SMALL).getFile().toByteArray());
		} finally {
			productAssetsManager.removeProductImages(product);
			productAssetsManager.removeProductImages(sibling);
		}
	}

	private static void assertSecondLevelCacheHit(SessionFactory sessionFactory, Class<?> entityClass,
			Serializable id, String regionName) {
		Statistics statistics = sessionFactory.getStatistics();
		statistics.setStatisticsEnabled(true);
		sessionFactory.getCache().evictEntityData(entityClass, id);
		statistics.clear();
		loadEntity(sessionFactory, entityClass, id);
		loadEntity(sessionFactory, entityClass, id);

		assertTrue(regionName, statistics.getSecondLevelCacheStatistics(regionName).getHitCount() > 0);
		assertTrue(regionName, sessionFactory.getCache().containsEntity(entityClass, id));
	}

	private static void loadEntity(SessionFactory sessionFactory, Class<?> entityClass, Serializable id) {
		EntityManager entityManager = sessionFactory.createEntityManager();
		try {
			assertNotNull(entityManager.find(entityClass, id));
		} finally {
			entityManager.close();
		}
	}

	private static Product product(MerchantStore store, String sku) {
		Product product = new Product();
		product.setMerchantStore(store);
		product.setSku(sku);
		return product;
	}

	private static ProductImage imageReference(Product product, String fileName) {
		ProductImage image = new ProductImage();
		image.setProduct(product);
		image.setProductImage(fileName);
		return image;
	}

	private static InputContentFile input(String fileName, FileContentType type, byte[] bytes) {
		InputContentFile file = new InputContentFile();
		file.setFileName(fileName);
		file.setFileContentType(type);
		file.setFile(new java.io.ByteArrayInputStream(bytes));
		return file;
	}

	private static ImageContentFile image(String fileName, FileContentType type, byte[] bytes) {
		ImageContentFile file = new ImageContentFile();
		file.setFileName(fileName);
		file.setFileContentType(type);
		file.setFile(new java.io.ByteArrayInputStream(bytes));
		return file;
	}
}
