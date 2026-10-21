package com.salesmanager.test.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.junit.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.salesmanager.core.business.repositories.catalog.product.ProductRepository;
import com.salesmanager.core.business.repositories.catalog.product.ProductRepositoryCustom;
import com.salesmanager.core.business.repositories.catalog.product.availability.PageableProductAvailabilityRepository;
import com.salesmanager.core.business.repositories.catalog.product.availability.ProductAvailabilityRepository;
import com.salesmanager.core.business.repositories.catalog.product.review.ProductReviewRepository;
import com.salesmanager.core.business.repositories.catalog.product.variant.PageableProductVariantRepositoty;
import com.salesmanager.core.business.repositories.catalog.product.variant.ProductVariantRepository;
import com.salesmanager.core.business.repositories.merchant.MerchantRepository;
import com.salesmanager.core.business.repositories.merchant.PageableMerchantRepository;
import com.salesmanager.core.business.services.catalog.product.variant.ProductVariantService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.ProductCriteria;
import com.salesmanager.core.model.catalog.product.ProductList;
import com.salesmanager.core.model.catalog.product.attribute.ProductOption;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionDescription;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionType;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionValue;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionValueDescription;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.catalog.product.description.ProductDescription;
import com.salesmanager.core.model.catalog.product.price.ProductPrice;
import com.salesmanager.core.model.catalog.product.price.ProductPriceDescription;
import com.salesmanager.core.model.catalog.product.review.ProductReview;
import com.salesmanager.core.model.catalog.product.review.ProductReviewDescription;
import com.salesmanager.core.model.catalog.product.variant.ProductVariant;
import com.salesmanager.core.model.catalog.product.variation.ProductVariation;
import com.salesmanager.core.model.common.Billing;
import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.core.model.generic.SalesManagerEntity;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.merchant.MerchantStoreCriteria;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.test.common.AbstractSalesManagerCoreTestCase;

public class HqlParityTest extends AbstractSalesManagerCoreTestCase {

	// H5 parsed mpleft as an alias followed by an inner country fetch; country is non-nullable, so inner vs left is result-equivalent.
	private static final String OLD_MERCHANT_BY_CODE = "select m from MerchantStore m "
			+ "left join fetch m.parent mpleft join fetch m.country mc "
			+ "left join fetch m.currency mc left join fetch m.zone mz "
			+ "left join fetch m.defaultLanguage md left join fetch m.languages mls where m.code = ?1";
	private static final String OLD_MERCHANT_BY_ID = "select m from MerchantStore m "
			+ "left join fetch m.parent mp left join fetch m.country mc left join fetch m.currency mc "
			+ "left join fetch m.zone mz left join fetch m.defaultLanguage md "
			+ "left join fetch m.languages mls where m.id = ?1";
	private static final String OLD_MERCHANT_BY_PARENT = "select distinct m from MerchantStore m "
			+ "left join fetch m.parent mp left join fetch m.country mc left join fetch m.currency mc "
			+ "left join fetch m.zone mz left join fetch m.defaultLanguage md "
			+ "left join fetch m.languages mls where mp.code = ?1";
	private static final String OLD_MERCHANT_PAGE_BY_STORE = "select distinct m from MerchantStore m "
			+ "left join fetch m.parent mp left join fetch m.country mc left join fetch m.currency mc "
			+ "left join fetch m.zone mz left join fetch m.defaultLanguage md "
			+ "left join fetch m.languages mls where mp.code = ?1";
	private static final String OLD_MERCHANT_PAGE_BY_STORE_COUNT =
			"select count(distinct m) from MerchantStore m join m.parent mp where mp.code = ?1";
	private static final String OLD_MERCHANT_PAGE_ALL = "select distinct m from MerchantStore m "
			+ "left join fetch m.parent mp left join fetch m.country mc left join fetch m.currency mc "
			+ "left join fetch m.zone mz left join fetch m.defaultLanguage md "
			+ "left join fetch m.languages mls where (?1 is null or m.storename like ?1)";
	private static final String OLD_MERCHANT_PAGE_ALL_COUNT =
			"select count(distinct m) from MerchantStore m where (?1 is null or m.storename like ?1)";
	private static final String OLD_MERCHANT_PAGE_RETAILERS = "select distinct m from MerchantStore m "
			+ "left join fetch m.parent mp left join fetch m.country mc left join fetch m.currency mc "
			+ "left join fetch m.zone mz left join fetch m.defaultLanguage md "
			+ "left join fetch m.languages mls where m.retailer = true and "
			+ "(?1 is null or m.storename like ?1)";
	private static final String OLD_MERCHANT_PAGE_RETAILERS_COUNT =
			"select count(distinct m) from MerchantStore m join m.parent "
					+ "where m.retailer = true and (?1 is null or m.storename like ?1)";
	private static final String OLD_MERCHANT_CRITERIA =
			"select distinct m from MerchantStore m left join fetch m.country mc "
					+ "left join fetch m.parent cp left join fetch m.currency mc "
					+ "left join fetch m.zone mz left join fetch m.defaultLanguage md "
					+ "left join fetch m.languages mls where lower(m.code) like:code";
	private static final String OLD_MERCHANT_CRITERIA_COUNT =
			"select count(distinct m) from MerchantStore m where lower(m.code) like:code";

	private static final String OLD_REVIEW_BY_PRODUCT =
			"select p from ProductReview p left join fetch p.descriptions pd "
					+ "join fetch p.customer pc join fetch pc.merchantStore pcm "
					+ "left join fetch pc.defaultLanguage pcl left join fetch pc.attributes pca "
					+ "left join fetch pca.customerOption pcao left join fetch pca.customerOptionValue pcav "
					+ "left join fetch pcao.descriptions pcaod left join fetch pcav.descriptions pcavd "
					+ "join fetch p.product pp join fetch pp.merchantStore ppm "
					+ "join fetch p.product pp join fetch pp.merchantStore ppm "
					+ "left join fetch p.descriptions pd where pp.id = ?1";
	private static final String OLD_REVIEW_BY_PRODUCT_LANGUAGE =
			"select p from ProductReview p left join fetch p.descriptions pd "
					+ "join fetch p.customer pc join fetch pc.merchantStore pcm "
					+ "left join fetch pc.defaultLanguage pcl left join fetch pc.attributes pca "
					+ "left join fetch pca.customerOption pcao left join fetch pca.customerOptionValue pcav "
					+ "left join fetch pcao.descriptions pcaod left join fetch pcav.descriptions pcavd "
					+ "join fetch p.product pp join fetch pp.merchantStore ppm "
					+ "join fetch p.product pp join fetch pp.merchantStore ppm "
					+ "left join fetch p.descriptions pd where pp.id = ?1 and pd.language.id =?2";
	private static final String OLD_REVIEW_BY_PRODUCT_CUSTOMER =
			"select p from ProductReview p left join fetch p.descriptions pd "
					+ "join fetch p.customer pc join fetch pc.merchantStore pcm "
					+ "left join fetch pc.defaultLanguage pcl left join fetch pc.attributes pca "
					+ "left join fetch pca.customerOption pcao left join fetch pca.customerOptionValue pcav "
					+ "left join fetch pcao.descriptions pcaod left join fetch pcav.descriptions pcavd "
					+ "join fetch p.product pp join fetch pp.merchantStore ppm "
					+ "join fetch p.product pp join fetch pp.merchantStore ppm "
					+ "left join fetch p.descriptions pd where pp.id = ?1 and pc.id = ?2";

	private static final String OLD_AVAILABILITY_BY_ID = "select distinct p from ProductAvailability p "
			+ "left join fetch p.merchantStore pm left join fetch p.prices pp "
			+ "left join fetch pp.descriptions ppd left join fetch p.merchantStore pm "
			+ "join fetch p.product ppr join fetch ppr.merchantStore pprm where p.id=?1";
	private static final String OLD_AVAILABILITY_BY_ID_AND_STORE = "select distinct p from ProductAvailability p "
			+ "left join fetch p.merchantStore pm left join fetch p.prices pp "
			+ "left join fetch pp.descriptions ppd left join fetch p.merchantStore pm "
			+ "join fetch p.product ppr join fetch ppr.merchantStore pprm where p.id=?1 and pprm.id=?2";
	private static final String OLD_AVAILABILITY_PAGE_COUNT =
			"select count(p) from ProductAvailability p join p.merchantStore pm "
					+ "join p.prices pp join pp.descriptions ppd join p.merchantStore pm "
					+ "join p.product ppr join ppr.merchantStore pprm "
					+ "where ppr.id=?1 and pprm.id=?2 and (?3 is null or pm.code like ?3)";

	private static final String OLD_VARIANT_QUERY =
			"select p from ProductVariant p join fetch p.product pr "
					+ "left join fetch p.variation pv left join fetch pv.productOption pvpo "
					+ "left join fetch pv.productOptionValue pvpov left join fetch pvpo.descriptions pvpod "
					+ "left join fetch pvpov.descriptions pvpovd left join fetch p.variationValue pvv "
					+ "left join fetch pvv.productOption pvvpo "
					+ "left join fetch pvv.productOptionValue pvvpov "
					+ "left join fetch pvvpo.descriptions povvpod "
					+ "left join fetch pvpov.descriptions povvpovd ";
	private static final String OLD_VARIANT_BY_ID =
			OLD_VARIANT_QUERY + "left join fetch pv.merchantStore pvm where p.id = ?1 and pvm.id = ?2";
	private static final String OLD_VARIANT_BY_IDS =
			OLD_VARIANT_QUERY + "left join fetch pv.merchantStore pvm where p.id in (?1) and pvm.id = ?2";
	private static final String OLD_VARIANT_BY_PRODUCT_ID =
			OLD_VARIANT_QUERY + "left join fetch pr.merchantStore prm "
					+ "where p.id = ?1 and pr.id = ?2 and prm.id = ?3";
	private static final String OLD_VARIANT_BY_SKU =
			OLD_VARIANT_QUERY + "left join fetch pr.merchantStore prm "
					+ "where pvpod.language.id = ?4 and pvpovd.language.id = ?4 "
					+ "and povvpod.language.id = ?4 and povvpovd.language.id = ?4 "
					+ "and pr.id = ?2 and p.code = ?1 and prm.id = ?3";
	private static final String OLD_VARIANTS_BY_PRODUCT =
			"select distinct p from ProductVariant as p join fetch p.product pr "
					+ "left join fetch p.variation pv left join fetch pv.productOption pvpo "
					+ "left join fetch pv.productOptionValue pvpov "
					+ "left join fetch pvpo.descriptions pvpod "
					+ "left join fetch pvpov.descriptions pvpovd "
					+ "left join fetch p.variationValue pvv "
					+ "left join fetch pvv.productOption pvvpo "
					+ "left join fetch pvv.productOptionValue pvvpov "
					+ "left join fetch pvvpo.descriptions povvpod "
					+ "left join fetch pvpov.descriptions pvpovd "
					+ "left join fetch p.productVariantGroup pig "
					+ "left join fetch pig.images pigi left join fetch pigi.descriptions pigid "
					+ "left join fetch pv.merchantStore pvm where pr.id = ?2 and pvm.id = ?1";
	private static final String OLD_VARIANTS_BY_PRODUCT_PAGE =
			"select p from ProductVariant p join fetch p.product pr "
					+ "left join fetch pr.merchantStore prm where pr.id = ?2 and prm.id = ?1";

	private static final String OLD_PRODUCT_QUERY_V2 =
			"select distinct p from Product as p join fetch p.descriptions pd "
					+ "join fetch p.merchantStore merch left join fetch p.availabilities pavail "
					+ "left join fetch p.type type left join fetch p.images images "
					+ "left join fetch pavail.prices pavailpr "
					+ "left join fetch pavailpr.descriptions pavailprdesc "
					+ "left join fetch p.categories categs left join fetch categs.descriptions categsd "
					+ "left join fetch p.attributes pattr left join fetch pattr.productOption po "
					+ "left join fetch po.descriptions pod "
					+ "left join fetch pattr.productOptionValue pov "
					+ "left join fetch pov.descriptions povd left join fetch p.relationships pr "
					+ "left join fetch p.manufacturer manuf left join fetch manuf.descriptions manufd "
					+ "left join fetch p.type type left join fetch p.variants pinst "
					+ "left join fetch pinst.variation pv left join fetch pv.productOption pvpo "
					+ "left join fetch pv.productOptionValue pvpov "
					+ "left join fetch pvpo.descriptions pvpod "
					+ "left join fetch pvpov.descriptions pvpovd "
					+ "left join fetch pinst.variationValue pvv "
					+ "left join fetch pvv.productOption pvvpo "
					+ "left join fetch pvv.productOptionValue pvvpov "
					+ "left join fetch pvvpo.descriptions povvpod "
					+ "left join fetch pvpov.descriptions povvpovd "
					+ "left join fetch pinst.availabilities pinsta "
					+ "left join fetch pinsta.prices pinstap "
					+ "left join fetch pinstap.descriptions pinstapdesc "
					+ "left join fetch pinst.productVariantGroup pinstg "
					+ "left join fetch pinstg.images pinstgimg "
					+ "left join fetch pinstgimg.descriptions ";

	private static final String OLD_PRODUCT_STORE_LIST =
			"select p from Product as p join fetch p.merchantStore merch "
					+ "join fetch p.availabilities pa left join fetch pa.prices pap "
					+ "join fetch p.descriptions pd left join fetch p.categories categs "
					+ "left join fetch pap.descriptions papd left join fetch p.images images "
					+ "left join fetch p.attributes pattr "
					+ "left join fetch pattr.productOption po left join fetch po.descriptions pod "
					+ "left join fetch pattr.productOptionValue pov "
					+ "left join fetch pov.descriptions povd "
					+ "left join fetch p.manufacturer manuf "
					+ "left join fetch manuf.descriptions manufd left join fetch p.type type "
					+ "left join fetch p.taxClass tx where merch.id=:mid";
	private static final String OLD_PRODUCT_CRITERIA_COUNT =
			"select count(distinct p) from Product as p inner join p.descriptions pd "
					+ "where p.merchantStore.id=:mId and pd.language.code=:lang";
	private static final String OLD_PRODUCT_CRITERIA_LIST =
			"select distinct p from Product as p join fetch p.merchantStore merch "
					+ "join fetch p.availabilities pa left join fetch pa.prices pap "
					+ "left join fetch pap.descriptions papd left join fetch p.descriptions pd "
					+ "left join fetch p.categories categs left join fetch categs.descriptions cd "
					+ "left join fetch p.images images left join fetch p.manufacturer manuf "
					+ "left join fetch manuf.descriptions manufd left join fetch p.type type "
					+ "left join fetch p.taxClass tx left join fetch p.attributes pattr "
					+ "left join fetch pattr.productOption po left join fetch po.descriptions pod "
					+ "left join fetch pattr.productOptionValue pov left join fetch pov.descriptions povd "
					+ "left join fetch p.variants pinst left join fetch pinst.variation pv "
					+ "left join fetch pv.productOption pvpo left join fetch pv.productOptionValue pvpov "
					+ "left join fetch pvpo.descriptions pvpod left join fetch pvpov.descriptions pvpovd "
					+ "left join fetch pinst.variationValue pvv left join fetch pvv.productOption pvvpo "
					+ "left join fetch pvv.productOptionValue pvvpov "
					+ "left join fetch pvvpo.descriptions povvpod "
					+ "left join fetch pvpov.descriptions povvpovd "
					+ "left join fetch pinst.availabilities pinsta left join fetch pinsta.prices pinstap "
					+ "left join fetch pinstap.descriptions pinstapdesc "
					+ "left join fetch pinst.productVariantGroup pinstg "
					+ "left join fetch pinstg.images pinstgimg left join fetch pinstgimg.descriptions "
					+ "where merch.id=:mId and pd.language.code=:lang";

	@PersistenceContext
	private EntityManager entityManager;
	@Inject
	private PlatformTransactionManager transactionManager;

	@Inject
	private MerchantRepository merchantRepository;
	@Inject
	private PageableMerchantRepository pageableMerchantRepository;
	@Inject
	private ProductRepository productRepository;
	@Inject
	private ProductReviewRepository productReviewRepository;
	@Inject
	private ProductAvailabilityRepository productAvailabilityRepository;
	@Inject
	private PageableProductAvailabilityRepository pageableProductAvailabilityRepository;
	@Inject
	private ProductVariantRepository productVariantRepository;
	@Inject
	private PageableProductVariantRepositoty pageableProductVariantRepository;
	@Inject
	private ProductVariantService productVariantService;

	private MerchantStore store;
	private MerchantStore childStore;
	private Language english;
	private Language french;
	private Product product;
	private Customer customer;
	private ProductAvailability availability;
	private ProductVariant variant;

	private void seedParityData() throws Exception {
		store = merchantService.getByCode(MerchantStore.DEFAULT_STORE);
		english = languageService.getByCode(ENGLISH_LANGUAGE_CODE);
		french = languageService.getByCode(FRENCH_LANGUAGE_CODE);
		String suffix = UUID.randomUUID().toString().replace("-", "");

		childStore = new MerchantStore();
		childStore.setCode("HQL_" + suffix);
		childStore.setStorename("HQL parity retailer " + suffix);
		childStore.setStoreEmailAddress("hql-" + suffix + "@example.com");
		childStore.setStorephone("555-0100");
		childStore.setStorecity("Test City");
		childStore.setStorepostalcode("00000");
		childStore.setCountry(store.getCountry());
		childStore.setCurrency(store.getCurrency());
		childStore.setZone(store.getZone());
		childStore.setDefaultLanguage(english);
		childStore.setLanguages(Arrays.asList(english, french));
		childStore.setParent(store);
		childStore.setRetailer(true);
		entityManager.persist(childStore);
		entityManager.flush();

		product = new Product();
		product.setSku("HQL_" + suffix);
		product.setMerchantStore(store);
		product.setType(productTypeService.getProductType("GENERAL"));
		addProductDescription(product, english, "HQL parity product");
		addProductDescription(product, french, "Produit de parité HQL");

		availability = new ProductAvailability();
		availability.setProduct(product);
		availability.setMerchantStore(store);
		availability.setProductDateAvailable(new Date());
		availability.setProductQuantity(10);
		availability.setRegion("*");
		ProductPrice price = new ProductPrice();
		price.setProductAvailability(availability);
		price.setProductPriceAmount(new BigDecimal("19.99"));
		ProductPriceDescription priceDescription = new ProductPriceDescription();
		priceDescription.setName("Base price");
		priceDescription.setLanguage(english);
		priceDescription.setProductPrice(price);
		price.getDescriptions().add(priceDescription);
		availability.getPrices().add(price);
		product.getAvailabilities().add(availability);
		productService.saveProduct(product);
		entityManager.flush();

		customer = new Customer();
		customer.setMerchantStore(store);
		customer.setDefaultLanguage(english);
		customer.setEmailAddress("hql-" + suffix + "@example.com");
		customer.setPassword("test-password");
		customer.setNick("hql-" + suffix);
		customer.setGender(CustomerGender.M);
		Billing billing = new Billing();
		billing.setFirstName("HQL");
		billing.setLastName("Parity");
		billing.setAddress("Test billing address");
		billing.setCountry(store.getCountry());
		billing.setZone(store.getZone());
		customer.setBilling(billing);
		Delivery delivery = new Delivery();
		delivery.setFirstName("HQL");
		delivery.setLastName("Parity");
		delivery.setAddress("Test delivery address");
		delivery.setCountry(store.getCountry());
		delivery.setZone(store.getZone());
		customer.setDelivery(delivery);
		customerService.create(customer);
		entityManager.flush();

		ProductReview review = new ProductReview();
		review.setProduct(product);
		review.setCustomer(customer);
		review.setReviewRating(4.0d);
		review.setReviewDate(new Date());
		ProductReviewDescription englishReview = new ProductReviewDescription(english, "English review");
		englishReview.setProductReview(review);
		ProductReviewDescription frenchReview = new ProductReviewDescription(french, "Avis en français");
		frenchReview.setProductReview(review);
		review.getDescriptions().add(englishReview);
		review.getDescriptions().add(frenchReview);
		productReviewService.create(review);
		entityManager.flush();

		ProductOption variationOption = createOption("hqlcolor" + suffix, english, french);
		ProductOptionValue variationValue = createOptionValue("hqlred" + suffix, english, french);
		ProductVariation variation = createVariation(variationOption, variationValue, "color-" + suffix);

		ProductOption variationValueOption = createOption("hqlsize" + suffix, english, french);
		ProductOptionValue variationValueOptionValue = createOptionValue("hqlsmall" + suffix, english, french);
		ProductVariation variationValueVariation =
				createVariation(variationValueOption, variationValueOptionValue, "size-" + suffix);
		entityManager.flush();

		variant = new ProductVariant();
		variant.setProduct(product);
		variant.setVariation(variation);
		variant.setVariationValue(variationValueVariation);
		variant.setSku("HQLVAR_" + suffix);
		variant.setCode("HQLVAR_" + suffix);
		productVariantService.saveProductVariant(variant);
		entityManager.flush();
		availability.setProductVariant(variant);
		entityManager.flush();
	}

	@Test
	public void merchantQueriesPreserveIdsAndPageCounts() {
		runWithParityFixture(() -> {
		assertParity(query(OLD_MERCHANT_BY_CODE, childStore.getCode()),
				Collections.singletonList(merchantRepository.findByCode(childStore.getCode())));
		assertParity(query(OLD_MERCHANT_BY_ID, childStore.getId()),
				Collections.singletonList(merchantRepository.getById(childStore.getId())));
		List<MerchantStore> currentChildren = merchantRepository.getByParent(store.getCode());
		assertFalse(currentChildren.isEmpty());
		assertParity(query(OLD_MERCHANT_BY_PARENT, store.getCode()), currentChildren);
		assertEquals(Collections.singletonList(String.valueOf(childStore.getId())), ids(currentChildren));

		assertPageParity(query(OLD_MERCHANT_PAGE_BY_STORE, store.getCode()),
				count(OLD_MERCHANT_PAGE_BY_STORE_COUNT, store.getCode()),
				pageableMerchantRepository.listByStore(store.getCode(), PageRequest.of(0, 50)));
		String retailerNamePattern = "%" + childStore.getStorename() + "%";
		assertPageParity(query(OLD_MERCHANT_PAGE_ALL, retailerNamePattern),
				count(OLD_MERCHANT_PAGE_ALL_COUNT, retailerNamePattern),
				pageableMerchantRepository.listAll(retailerNamePattern, PageRequest.of(0, 50)));
		assertPageParity(query(OLD_MERCHANT_PAGE_RETAILERS, retailerNamePattern),
				count(OLD_MERCHANT_PAGE_RETAILERS_COUNT, retailerNamePattern),
				pageableMerchantRepository.listAllRetailers(retailerNamePattern, PageRequest.of(0, 50)));

		MerchantStoreCriteria criteria = new MerchantStoreCriteria();
		criteria.setCode(childStore.getCode());
		criteria.setName(null);
		String codePattern = "%" + childStore.getCode().toLowerCase() + "%";
		List<MerchantStore> oldCriteria = query(OLD_MERCHANT_CRITERIA, "code", codePattern);
		List<MerchantStore> currentCriteria = merchantService.getByCriteria(criteria).getList();
		assertEquals(count(OLD_MERCHANT_CRITERIA_COUNT, "code", codePattern).intValue(), currentCriteria.size());
		assertParity(oldCriteria, currentCriteria);
		});
	}

	@Test
	public void productReviewQueriesPreserveIds() {
		runWithParityFixture(() -> {
		assertParity(query(OLD_REVIEW_BY_PRODUCT, product.getId()),
				productReviewRepository.findByProduct(product.getId()));
		assertParity(query(OLD_REVIEW_BY_PRODUCT_LANGUAGE, product.getId(), english.getId()),
				productReviewRepository.findByProduct(product.getId(), english.getId()));
		assertParity(query(OLD_REVIEW_BY_PRODUCT_CUSTOMER, product.getId(), customer.getId()),
				Collections.singletonList(productReviewRepository.findByProductAndCustomer(
						product.getId(), customer.getId())));
		});
	}

	@Test
	public void availabilityQueriesPreserveIdsAndCount() {
		runWithParityFixture(() -> {
		assertParity(query(OLD_AVAILABILITY_BY_ID, availability.getId()),
				Collections.singletonList(productAvailabilityRepository.getById(availability.getId())));
		assertParity(query(OLD_AVAILABILITY_BY_ID_AND_STORE, availability.getId(), store.getId()),
				Collections.singletonList(productAvailabilityRepository.getById(
						availability.getId(), store.getId())));
		Page<ProductAvailability> page = pageableProductAvailabilityRepository.listByStore(
				product.getId(), store.getId(), "DEFA", PageRequest.of(0, 20));
		Number expectedCount = count(OLD_AVAILABILITY_PAGE_COUNT,
				product.getId(), store.getId(), "%DEFA%");
		assertEquals(expectedCount.longValue(), page.getTotalElements());
		assertParity(query(OLD_AVAILABILITY_BY_ID, availability.getId()), page.getContent());
		});
	}

	@Test
	public void productVariantQueriesPreserveIdsAndPageCount() {
		runWithParityFixture(() -> {
		assertParity(query(OLD_VARIANT_BY_ID, variant.getId(), store.getId()),
				Collections.singletonList(productVariantRepository.findOne(variant.getId(), store.getId()).get()));
		assertParity(query(OLD_VARIANT_BY_IDS, Collections.singletonList(variant.getId()), store.getId()),
				productVariantRepository.findByIds(Collections.singletonList(variant.getId()), store.getId()));
		assertParity(query(OLD_VARIANT_BY_PRODUCT_ID, variant.getId(), product.getId(), store.getId()),
				Collections.singletonList(productVariantRepository.findById(
						variant.getId(), product.getId(), store.getId()).get()));
		assertParity(query(OLD_VARIANT_BY_SKU, variant.getCode(), product.getId(), store.getId(), english.getId()),
				Collections.singletonList(productVariantRepository.findBySku(
						variant.getCode(), product.getId(), store.getId(), english.getId()).get()));
		assertParity(query(OLD_VARIANTS_BY_PRODUCT, store.getId(), product.getId()),
				productVariantRepository.findByProductId(store.getId(), product.getId()));

		Page<ProductVariant> page = pageableProductVariantRepository.findByProductId(
				store.getId(), product.getId(), PageRequest.of(0, 20));
		assertParity(query(OLD_VARIANTS_BY_PRODUCT_PAGE, store.getId(), product.getId()), page.getContent());
		assertEquals(1L, page.getTotalElements());
		});
	}

	@Test
	public void productRepositoryQueriesPreserveIds() {
		runWithParityFixture(() -> {
		String oldById = OLD_PRODUCT_QUERY_V2 + "where p.id=:pid";
		assertParity(query(oldById, "pid", product.getId()),
				Collections.singletonList(((ProductRepositoryCustom) productRepository).getById(product.getId())));
		assertParity(query(OLD_PRODUCT_STORE_LIST, "mid", store.getId()),
				productRepository.listByStore(store));

		ProductCriteria criteria = new ProductCriteria();
		criteria.setLanguage(english.getCode());
		ProductList products = productRepository.listByStore(store, english, criteria);
		assertNotNull(products);
		assertFalse(products.getProducts().isEmpty());
		assertParity(queryNamed(OLD_PRODUCT_CRITERIA_LIST,
				"mId", store.getId(), "lang", english.getCode()),
				products.getProducts());
		assertEquals(countNamed(OLD_PRODUCT_CRITERIA_COUNT,
				"mId", store.getId(), "lang", english.getCode()).intValue(), products.getTotalCount());
		});
	}

	private ProductOption createOption(String code, Language en, Language fr) throws Exception {
		ProductOption option = new ProductOption();
		option.setMerchantStore(store);
		option.setCode(code);
		option.setProductOptionType(ProductOptionType.Radio.name());
		option.getDescriptions().add(optionDescription(option, en, code + " en"));
		option.getDescriptions().add(optionDescription(option, fr, code + " fr"));
		productOptionService.saveOrUpdate(option);
		return option;
	}

	private void runWithParityFixture(ParityTestAction action) {
		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
		transactionTemplate.execute(status -> {
			try {
				seedParityData();
				action.run();
				status.setRollbackOnly();
				return null;
			} catch (Exception exception) {
				throw new IllegalStateException(exception);
			}
		});
	}

	@FunctionalInterface
	private interface ParityTestAction {
		void run() throws Exception;
	}

	private ProductOptionDescription optionDescription(ProductOption option, Language language, String name) {
		ProductOptionDescription description = new ProductOptionDescription();
		description.setProductOption(option);
		description.setLanguage(language);
		description.setName(name);
		description.setDescription(name);
		return description;
	}

	private ProductOptionValue createOptionValue(String code, Language en, Language fr) throws Exception {
		ProductOptionValue optionValue = new ProductOptionValue();
		optionValue.setMerchantStore(store);
		optionValue.setCode(code);
		optionValue.getDescriptions().add(optionValueDescription(optionValue, en, code + " en"));
		optionValue.getDescriptions().add(optionValueDescription(optionValue, fr, code + " fr"));
		productOptionValueService.saveOrUpdate(optionValue);
		return optionValue;
	}

	private ProductOptionValueDescription optionValueDescription(
			ProductOptionValue optionValue, Language language, String name) {
		ProductOptionValueDescription description = new ProductOptionValueDescription();
		description.setProductOptionValue(optionValue);
		description.setLanguage(language);
		description.setName(name);
		description.setDescription(name);
		return description;
	}

	private ProductVariation createVariation(ProductOption option, ProductOptionValue value, String code) {
		ProductVariation variation = new ProductVariation();
		variation.setMerchantStore(store);
		variation.setProductOption(option);
		variation.setProductOptionValue(value);
		variation.setCode(code);
		entityManager.persist(variation);
		return variation;
	}

	private void addProductDescription(Product target, Language language, String name) {
		ProductDescription description = new ProductDescription();
		description.setProduct(target);
		description.setLanguage(language);
		description.setName(name);
		target.getDescriptions().add(description);
	}

	private <T> List<T> query(String hql, Object... parameters) {
		javax.persistence.Query query = entityManager.createQuery(hql);
		for (int index = 0; index < parameters.length; index++) {
			query.setParameter(index + 1, parameters[index]);
		}
		@SuppressWarnings("unchecked")
		List<T> result = query.getResultList();
		return result;
	}

	private <T> List<T> query(String hql, String parameter, Object value) {
		javax.persistence.Query query = entityManager.createQuery(hql);
		query.setParameter(parameter, value);
		@SuppressWarnings("unchecked")
		List<T> result = query.getResultList();
		return result;
	}

	private Number count(String hql, Object... parameters) {
		javax.persistence.Query query = entityManager.createQuery(hql);
		for (int index = 0; index < parameters.length; index++) {
			query.setParameter(index + 1, parameters[index]);
		}
		return (Number) query.getSingleResult();
	}

	private Number count(String hql, String parameter, Object value) {
		javax.persistence.Query query = entityManager.createQuery(hql);
		query.setParameter(parameter, value);
		return (Number) query.getSingleResult();
	}

	private <T> List<T> queryNamed(String hql, Object... parameters) {
		javax.persistence.Query query = entityManager.createQuery(hql);
		for (int index = 0; index < parameters.length; index += 2) {
			query.setParameter((String) parameters[index], parameters[index + 1]);
		}
		@SuppressWarnings("unchecked")
		List<T> result = query.getResultList();
		return result;
	}

	private Number countNamed(String hql, Object... parameters) {
		javax.persistence.Query query = entityManager.createQuery(hql);
		for (int index = 0; index < parameters.length; index += 2) {
			query.setParameter((String) parameters[index], parameters[index + 1]);
		}
		return (Number) query.getSingleResult();
	}

	private void assertPageParity(List<?> oldResults, Number oldCount, Page<?> currentPage) {
		assertFalse(oldResults.isEmpty());
		assertFalse(currentPage.getContent().isEmpty());
		assertParity(oldResults, currentPage.getContent());
		assertEquals(oldCount.longValue(), currentPage.getTotalElements());
	}

	private void assertParity(List<?> oldResults, List<?> currentResults) {
		assertFalse("pre-change query must return data", oldResults.isEmpty());
		assertFalse("current query must return data", currentResults.isEmpty());
		List<String> oldIds = ids(oldResults);
		List<String> currentIds = ids(currentResults);
		assertEquals("root result counts differ", oldIds.size(), currentIds.size());
		assertEquals(oldIds, currentIds);
	}

	private List<String> ids(List<?> values) {
		List<String> ids = new ArrayList<>();
		for (Object value : values) {
			String id = String.valueOf(((SalesManagerEntity<?, ?>) value).getId());
			if (!ids.contains(id)) {
				ids.add(id);
			}
		}
		Collections.sort(ids);
		return ids;
	}

}
