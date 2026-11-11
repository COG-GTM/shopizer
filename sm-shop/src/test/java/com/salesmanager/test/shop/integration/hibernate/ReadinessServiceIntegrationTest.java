package com.salesmanager.test.shop.integration.hibernate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.catalog.category.CategoryService;
import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.catalog.product.manufacturer.ManufacturerService;
import com.salesmanager.core.business.services.catalog.product.review.ProductReviewService;
import com.salesmanager.core.business.services.catalog.product.variant.ProductVariantService;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.model.catalog.category.Category;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.attribute.ProductOption;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionDescription;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionValue;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionValueDescription;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.catalog.product.description.ProductDescription;
import com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer;
import com.salesmanager.core.model.catalog.product.manufacturer.ManufacturerDescription;
import com.salesmanager.core.model.catalog.product.review.ProductReview;
import com.salesmanager.core.model.catalog.product.review.ProductReviewDescription;
import com.salesmanager.core.model.catalog.product.variant.ProductVariant;
import com.salesmanager.core.model.catalog.product.variation.ProductVariation;
import com.salesmanager.core.model.common.Billing;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.customer.CustomerCriteria;
import com.salesmanager.core.model.customer.CustomerList;
import com.salesmanager.core.model.generic.SalesManagerEntity;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@Transactional
public class ReadinessServiceIntegrationTest extends ServicesTestSupport {
    @PersistenceContext private EntityManager em;
    @Autowired private CategoryService categories;
    @Autowired private ProductService products;
    @Autowired private ManufacturerService manufacturers;
    @Autowired private ProductReviewService reviews;
    @Autowired private ProductVariantService variants;
    @Autowired private CustomerService customers;
    @Autowired private MerchantStoreService stores;
    @Autowired private LanguageService languages;

    private MerchantStore store;
    private Language language;
    private Category parent;
    private Category child;
    private Category empty;
    private Product product;
    private Manufacturer manufacturer;
    private Customer customer;

    @BeforeEach
    void fixtures() throws Exception {
        store = stores.getByCode(MerchantStore.DEFAULT_STORE);
        language = languages.getByCode("en");
        parent = categoryEntity("service_parent", null);
        child = categoryEntity("service_child", parent);
        empty = categoryEntity("service_empty", null);
        manufacturer = new Manufacturer();
        manufacturer.setCode("service_manufacturer");
        manufacturer.setMerchantStore(store);
        ManufacturerDescription manufacturerDescription = new ManufacturerDescription();
        manufacturerDescription.setManufacturer(manufacturer);
        manufacturerDescription.setLanguage(language);
        manufacturerDescription.setName("Service manufacturer");
        manufacturer.getDescriptions().add(manufacturerDescription);
        em.persist(manufacturer);

        product = new Product();
        product.setSku("service_product");
        product.setMerchantStore(store);
        product.setManufacturer(manufacturer);
        product.setAvailable(true);
        product.setDateAvailable(new Date(0));
        product.getCategories().add(child);
        ProductDescription description = new ProductDescription();
        description.setProduct(product);
        description.setLanguage(language);
        description.setName("Service product");
        product.getDescriptions().add(description);
        ProductAvailability availability = new ProductAvailability();
        availability.setProduct(product);
        availability.setMerchantStore(store);
        availability.setRegion("*");
        product.getAvailabilities().add(availability);
        em.persist(product);

        customer = new Customer();
        customer.setEmailAddress("service-search@example.com");
        customer.setNick("service_customer");
        customer.setMerchantStore(store);
        customer.setDefaultLanguage(language);
        Billing billing = new Billing();
        billing.setFirstName("ReadinessFirstName");
        billing.setLastName("Customer");
        billing.setCountry(store.getCountry());
        customer.setBilling(billing);
        em.persist(customer);
        em.flush();
        em.clear();
    }

    @Test
    void categoryRootServiceHandlesNullParent() throws Exception {
        Set<Object> roots = ids(categories.listByStoreAndParent(store, null));
        assertTrue(roots.contains(parent.getId()));
        assertTrue(roots.contains(empty.getId()));
        assertFalse(roots.contains(child.getId()));
    }

    @Test
    void categoryChildServiceReturnsOnlyChildrenAndHandlesEmptyParent() throws Exception {
        assertEquals(Collections.singleton(child.getId()), ids(categories.listByStoreAndParent(store, parent)));
        assertTrue(categories.listByStoreAndParent(store, empty).isEmpty());
    }

    @Test
    void localeProductServiceRunsRepairedResultQueryAndHandlesEmptyCategory() throws Exception {
        assertEquals(Collections.singleton(product.getId()),
                ids(products.getProductsForLocale(child, language, Locale.CANADA)));
        assertTrue(products.getProductsForLocale(empty, language, Locale.CANADA).isEmpty());
    }

    @Test
    void customerFirstNameSearchRunsCountAndResultBranches() {
        CustomerCriteria criteria = new CustomerCriteria();
        criteria.setFirstName("ReadinessFirstName");
        CustomerList found = customers.getListByStore(store, criteria);
        assertEquals(1, found.getTotalCount());
        assertEquals(Collections.singleton(customer.getId()), ids(found.getCustomers()));
        criteria.setFirstName("NoReadinessCustomer");
        CustomerList missing = customers.getListByStore(store, criteria);
        assertEquals(0, missing.getTotalCount());
        assertTrue(missing.getCustomers().isEmpty());
    }

    @Test
    void merchantServiceLoadsCountryAndCurrencyThroughRewrittenQuery() throws Exception {
        MerchantStore loaded = stores.getByCode(MerchantStore.DEFAULT_STORE);
        assertNotNull(loaded);
        assertEquals(store.getId(), loaded.getId());
        assertNotNull(loaded.getCountry());
        assertEquals(store.getCountry().getId(), loaded.getCountry().getId());
        assertNotNull(loaded.getCurrency());
        assertEquals(store.getCurrency().getId(), loaded.getCurrency().getId());
    }

    @Test
    void manufacturerServicesReturnFixtureFromCategoryQueries() throws Exception {
        assertEquals(Collections.singleton(manufacturer.getId()), ids(manufacturers.listByProductsByCategoriesId(
                store, Collections.singletonList(child.getId()), language)));
        assertEquals(Collections.singleton(manufacturer.getId()),
                ids(manufacturers.listByProductsInCategory(store, child, language)));
        assertTrue(manufacturers.listByProductsByCategoriesId(
                store, Collections.singletonList(empty.getId()), language).isEmpty());
    }

    @Test
    void reviewServicesReturnCustomerProductAndLocalizedDescription() {
        ProductReview review = new ProductReview();
        review.setProduct(em.find(Product.class, product.getId()));
        review.setCustomer(em.find(Customer.class, customer.getId()));
        ProductReviewDescription description = new ProductReviewDescription();
        description.setProductReview(review);
        description.setLanguage(em.find(Language.class, language.getId()));
        description.setName("Service review");
        review.getDescriptions().add(description);
        em.persist(review);
        em.flush();
        em.clear();

        assertEquals(Collections.singleton(review.getId()), ids(reviews.getByProduct(product)));
        assertEquals(Collections.singleton(review.getId()), ids(reviews.getByProduct(product, language)));
        ProductReview loaded = reviews.getByProductAndCustomer(product.getId(), customer.getId());
        assertEquals(review.getId(), loaded.getId());
        assertEquals(customer.getId(), loaded.getCustomer().getId());
        assertEquals(product.getId(), loaded.getProduct().getId());
        assertEquals("Service review", loaded.getDescriptions().iterator().next().getName());
        assertTrue(reviews.getByProductAndCustomer(product.getId(), -1L) == null);
    }

    @Test
    void variantServicesLoadGraphAndHandleMissingSku() {
        ProductOption option = new ProductOption();
        option.setCode("service_option");
        option.setMerchantStore(em.find(MerchantStore.class, store.getId()));
        ProductOptionDescription optionDescription = new ProductOptionDescription();
        optionDescription.setProductOption(option);
        optionDescription.setLanguage(em.find(Language.class, language.getId()));
        optionDescription.setName("Service option");
        option.getDescriptions().add(optionDescription);
        em.persist(option);
        ProductOptionValue value = new ProductOptionValue();
        value.setCode("service_value");
        value.setMerchantStore(option.getMerchantStore());
        ProductOptionValueDescription valueDescription = new ProductOptionValueDescription();
        valueDescription.setProductOptionValue(value);
        valueDescription.setLanguage(optionDescription.getLanguage());
        valueDescription.setName("Service value");
        value.getDescriptions().add(valueDescription);
        em.persist(value);
        ProductVariation variation = new ProductVariation();
        variation.setCode("service_variation");
        variation.setMerchantStore(option.getMerchantStore());
        variation.setProductOption(option);
        variation.setProductOptionValue(value);
        em.persist(variation);
        ProductVariant variant = new ProductVariant();
        variant.setSku("service_variant");
        variant.setCode("service_variant");
        variant.setProduct(em.find(Product.class, product.getId()));
        variant.setVariation(variation);
        variant.setVariationValue(variation);
        em.persist(variant);
        em.flush();
        em.clear();

        assertEquals(variant.getId(), variants.getBySku("service_variant", product.getId(), store, language)
                .orElseThrow(AssertionError::new).getId());
        assertEquals(Collections.singleton(variant.getId()), ids(variants.getByProductId(store, product, language)));
        assertEquals(variant.getId(), variants.getById(variant.getId(), product.getId(), store)
                .orElseThrow(AssertionError::new).getId());
        assertFalse(variants.getBySku("missing_service_variant", product.getId(), store, language).isPresent());
    }

    @Test
    void nativeSkuServiceReturnsFixtureAndUsesExistingMissingSkuExceptionContract() throws Exception {
        assertEquals(product.getId(), products.getBySku(product.getSku(), store).getId());
        assertEquals(product.getId(), products.getBySku(product.getSku(), store, language).getId());
        assertFalse(products.exists("missing_service_product", store));
        assertThrows(ServiceException.class, () -> products.getBySku("missing_service_product", store));
        assertThrows(ServiceException.class, () -> products.getBySku("missing_service_product", store, language));
    }

    private Category categoryEntity(String code, Category parentCategory) {
        Category category = new Category(store);
        category.setId(null);
        category.setCode(code);
        category.setParent(parentCategory);
        category.setLineage(parentCategory == null ? "/service/" : "/service/" + parentCategory.getId() + "/");
        em.persist(category);
        return category;
    }

    private Set<Object> ids(Collection<? extends SalesManagerEntity<?, ?>> entities) {
        return entities.stream().map(SalesManagerEntity::getId).collect(Collectors.toSet());
    }
}
