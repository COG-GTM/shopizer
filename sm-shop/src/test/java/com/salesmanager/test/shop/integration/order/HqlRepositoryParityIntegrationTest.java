package com.salesmanager.test.shop.integration.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.salesmanager.core.business.repositories.catalog.category.CategoryRepository;
import com.salesmanager.core.business.repositories.catalog.product.ProductRepository;
import com.salesmanager.core.business.repositories.catalog.product.ProductRepositoryCustom;
import com.salesmanager.core.business.repositories.catalog.product.ProductRepositoryImpl;
import com.salesmanager.core.business.repositories.catalog.product.availability.PageableProductAvailabilityRepository;
import com.salesmanager.core.business.repositories.catalog.product.availability.ProductAvailabilityRepository;
import com.salesmanager.core.business.repositories.catalog.product.manufacturer.ManufacturerRepository;
import com.salesmanager.core.business.repositories.catalog.product.relationship.ProductRelationshipRepository;
import com.salesmanager.core.business.repositories.catalog.product.review.ProductReviewRepository;
import com.salesmanager.core.business.repositories.catalog.product.variant.PageableProductVariantRepositoty;
import com.salesmanager.core.business.repositories.catalog.product.variant.ProductVariantRepository;
import com.salesmanager.core.business.repositories.customer.CustomerRepository;
import com.salesmanager.core.business.repositories.merchant.MerchantRepository;
import com.salesmanager.core.business.repositories.merchant.PageableMerchantRepository;
import com.salesmanager.core.business.repositories.order.OrderRepository;
import com.salesmanager.core.business.repositories.user.PermissionRepository;
import com.salesmanager.core.model.catalog.category.Category;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.ProductCriteria;
import com.salesmanager.core.model.catalog.product.ProductList;
import com.salesmanager.core.model.catalog.product.attribute.ProductAttribute;
import com.salesmanager.core.model.catalog.product.attribute.ProductOption;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionDescription;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionValue;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionValueDescription;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.catalog.product.description.ProductDescription;
import com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer;
import com.salesmanager.core.model.catalog.product.manufacturer.ManufacturerDescription;
import com.salesmanager.core.model.catalog.product.price.ProductPrice;
import com.salesmanager.core.model.catalog.product.price.ProductPriceDescription;
import com.salesmanager.core.model.catalog.product.relationship.ProductRelationship;
import com.salesmanager.core.model.catalog.product.review.ProductReview;
import com.salesmanager.core.model.catalog.product.review.ProductReviewDescription;
import com.salesmanager.core.model.catalog.product.variant.ProductVariant;
import com.salesmanager.core.model.catalog.product.variation.ProductVariation;
import com.salesmanager.core.model.common.Billing;
import com.salesmanager.core.model.common.CredentialsReset;
import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.generic.SalesManagerEntity;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.merchant.MerchantStoreCriteria;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.customer.CustomerCriteria;
import com.salesmanager.core.model.customer.CustomerList;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.order.OrderTotal;
import com.salesmanager.core.model.order.orderproduct.OrderProduct;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.tax.taxclass.TaxClass;
import com.salesmanager.core.model.user.Group;
import com.salesmanager.core.model.user.Permission;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@Transactional
public class HqlRepositoryParityIntegrationTest extends ServicesTestSupport {
    @PersistenceContext private EntityManager em;
    @Autowired private ApplicationContext context;
    @Autowired private ProductRepository products;
    @Autowired private CategoryRepository categories;
    @Autowired private CustomerRepository customers;
    @Autowired private MerchantRepository merchants;
    @Autowired private ProductRelationshipRepository relationships;
    @Autowired private PageableProductAvailabilityRepository pageableAvailability;

    private final Properties originals = new Properties();

    /** Hibernate 5 originals whose text Hibernate 6 refuses to interpret (fixed by AB-542). */
    private static final String[] HIBERNATE6_REJECTED_ORIGINALS = {
            "MerchantImpl.listByCriteria",
            "MerchantRepository.findByCode(String)",
            "MerchantRepository.getById(int)",
            "MerchantRepository.getByParent(String)",
            "PageableMerchantRepository.listAll(String,Pageable)",
            "PageableMerchantRepository.listAllRetailers(String,Pageable)",
            "PageableMerchantRepository.listByStore(String,Pageable)",
            "PageableProductAvailabilityRepository.listByStore(Long,Integer,String,Pageable).count",
            "PageableProductVariantRepositoty.findByProductId(Integer,Long,Pageable)",
            "ProductAvailabilityRepository.getById(Long)",
            "ProductAvailabilityRepository.getById(Long,int)",
            "ProductImpl.criteria.shop",
            "ProductImpl.getById",
            "ProductImpl.getProductsListForLocale",
            "ProductImpl.getProductsListForLocale.count",
            "ProductImpl.productQueryV2",
            "ProductReviewRepository.findByProduct(Long)",
            "ProductReviewRepository.findByProduct(Long,Integer)",
            "ProductReviewRepository.findByProductAndCustomer(Long,Long)",
            "ProductVariantRepository.findById(Long,Long,Integer)",
            "ProductVariantRepository.findByIds(List<Long>,Integer)",
            "ProductVariantRepository.findByProductId(Integer,Long)",
            "ProductVariantRepository.findBySku(String,Long,Integer,Integer)",
            "ProductVariantRepository.findOne(Long,Integer)",
    };
    private MerchantStore store;
    private MerchantStore parentStore;
    private Language language;
    private Category parent;
    private Category child;
    private Product product;
    private ProductAvailability availability;
    private ProductVariant variant;
    private Customer customer;
    private Order order;
    private Group group;
    private TaxClass taxClass;

    @BeforeEach
    void fixtures() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/hibernate5-original-hql.properties")) {
            assertNotNull(input);
            originals.load(input);
        }
        parentStore = em.createQuery("from MerchantStore where code='DEFAULT'", MerchantStore.class).getSingleResult();
        language = em.createQuery("from Language where code='en'", Language.class).getSingleResult();
        store = new MerchantStore();
        store.setCode("hql_parity_store");
        store.setStorename("HQL parity store");
        store.setStorephone("1234567890");
        store.setStorecity("Montreal");
        store.setStorepostalcode("H1H1H1");
        store.setStoreEmailAddress("hql-store@example.com");
        store.setCountry(parentStore.getCountry());
        store.setCurrency(parentStore.getCurrency());
        store.setDefaultLanguage(language);
        store.getLanguages().add(language);
        store.setParent(parentStore);
        store.setRetailer(true);
        em.persist(store);
        parent = new Category(store);
        parent.setId(null);
        parent.setCode("hql_parent");
        parent.setLineage("/hql/");
        em.persist(parent);
        child = new Category(store);
        child.setId(null);
        child.setCode("hql_child");
        child.setParent(parent);
        child.setLineage("/hql/child/");
        em.persist(child);
        Manufacturer manufacturer = new Manufacturer();
        manufacturer.setCode("hql_manufacturer");
        manufacturer.setMerchantStore(store);
        ManufacturerDescription manufacturerDescription = new ManufacturerDescription();
        manufacturerDescription.setManufacturer(manufacturer);
        manufacturerDescription.setLanguage(language);
        manufacturerDescription.setName("HQL manufacturer");
        manufacturer.getDescriptions().add(manufacturerDescription);
        em.persist(manufacturer);
        taxClass = new TaxClass("hql_tax");
        taxClass.setMerchantStore(store);
        em.persist(taxClass);
        product = new Product();
        product.setSku("hql_parity_product");
        product.setMerchantStore(store);
        product.setManufacturer(manufacturer);
        product.setTaxClass(taxClass);
        product.setDateAvailable(new Date(0));
        product.getCategories().add(child);
        ProductDescription description = new ProductDescription();
        description.setProduct(product);
        description.setLanguage(language);
        description.setName("HQL product");
        description.setSeUrl("hql-product");
        product.getDescriptions().add(description);
        availability = new ProductAvailability();
        availability.setProduct(product);
        availability.setMerchantStore(store);
        availability.setRegion("*");
        ProductPrice price = new ProductPrice();
        price.setProductAvailability(availability);
        ProductPriceDescription priceDescription = new ProductPriceDescription();
        priceDescription.setProductPrice(price);
        priceDescription.setLanguage(language);
        priceDescription.setName("HQL price");
        price.getDescriptions().add(priceDescription);
        availability.getPrices().add(price);
        product.getAvailabilities().add(availability);
        em.persist(product);
        ProductOption option = new ProductOption();
        option.setCode("hql_option");
        option.setMerchantStore(store);
        ProductOptionDescription optionDescription = new ProductOptionDescription();
        optionDescription.setProductOption(option);
        optionDescription.setLanguage(language);
        optionDescription.setName("HQL option");
        option.getDescriptions().add(optionDescription);
        em.persist(option);
        ProductOptionValue value = optionValue("hql_value", language);
        Language french = em.createQuery("from Language where code='fr'", Language.class).getSingleResult();
        ProductOptionValue otherValue = optionValue("hql_other_value", french);
        ProductVariation variation = variation("hql_variation", option, value);
        ProductVariation otherVariation = variation("hql_other_variation", option, otherValue);
        variant = new ProductVariant();
        variant.setSku("hql_variant");
        variant.setCode("hql_variant");
        variant.setProduct(product);
        variant.setVariation(variation);
        variant.setVariationValue(otherVariation);
        em.persist(variant);
        product.getVariants().add(variant);
        ProductAttribute attribute = new ProductAttribute();
        attribute.setProduct(product);
        attribute.setProductOption(option);
        attribute.setProductOptionValue(value);
        em.persist(attribute);
        product.getAttributes().add(attribute);
        customer = new Customer();
        customer.setEmailAddress("hql@example.com");
        customer.setNick("hql_customer");
        customer.setMerchantStore(store);
        customer.setDefaultLanguage(language);
        customer.setBilling(billing());
        Delivery delivery = new Delivery();
        delivery.setFirstName("Hql");
        delivery.setLastName("Customer");
        delivery.setCountry(store.getCountry());
        customer.setDelivery(delivery);
        CredentialsReset reset = new CredentialsReset();
        reset.setCredentialsRequest("hql_reset");
        customer.setCredentialsResetRequest(reset);
        em.persist(customer);
        ProductReview review = new ProductReview();
        review.setProduct(product);
        review.setCustomer(customer);
        ProductReviewDescription reviewDescription = new ProductReviewDescription();
        reviewDescription.setProductReview(review);
        reviewDescription.setLanguage(language);
        reviewDescription.setName("HQL review");
        review.getDescriptions().add(reviewDescription);
        em.persist(review);
        order = new Order();
        order.setMerchant(store);
        order.setCustomerEmailAddress(customer.getEmailAddress());
        order.setBilling(billing());
        OrderProduct orderProduct = new OrderProduct();
        orderProduct.setOrder(order);
        orderProduct.setProductName("HQL product");
        orderProduct.setOneTimeCharge(BigDecimal.ONE);
        order.getOrderProducts().add(orderProduct);
        OrderTotal total = new OrderTotal();
        total.setOrder(order);
        total.setOrderTotalCode("hql_total");
        total.setValue(BigDecimal.ONE);
        order.getOrderTotal().add(total);
        em.persist(order);
        Permission permission = new Permission();
        permission.setPermissionName("HQL_PERMISSION");
        em.persist(permission);
        group = new Group("HQL_GROUP");
        group.getPermissions().add(permission);
        permission.getGroups().add(group);
        em.persist(group);
        ProductRelationship relationship = new ProductRelationship();
        relationship.setProduct(product);
        relationship.setRelatedProduct(product);
        relationship.setStore(store);
        relationship.setCode("hql_related");
        em.persist(relationship);
        em.flush();
    }

    private Billing billing() {
        Billing billing = new Billing();
        billing.setFirstName("Hql");
        billing.setLastName("Customer");
        billing.setCountry(store.getCountry());
        return billing;
    }

    private ProductOptionValue optionValue(String code, Language descriptionLanguage) {
        ProductOptionValue value = new ProductOptionValue();
        value.setCode(code);
        value.setMerchantStore(store);
        ProductOptionValueDescription description = new ProductOptionValueDescription();
        description.setProductOptionValue(value);
        description.setLanguage(descriptionLanguage);
        description.setName(code);
        value.getDescriptions().add(description);
        em.persist(value);
        return value;
    }

    private ProductVariation variation(String code, ProductOption option, ProductOptionValue value) {
        ProductVariation variation = new ProductVariation();
        variation.setCode(code);
        variation.setMerchantStore(store);
        variation.setProductOption(option);
        variation.setProductOptionValue(value);
        em.persist(variation);
        return variation;
    }

    private String original(String key) {
        String hql = originals.getProperty(key);
        assertNotNull(hql, key);
        return hql;
    }

    private List<?> rows(Object result) {
        if (result instanceof Page) return ((Page<?>) result).getContent();
        if (result instanceof Optional) return ((Optional<?>) result).map(Collections::singletonList).orElse(Collections.emptyList());
        if (result instanceof Collection) return new ArrayList<>((Collection<?>) result);
        return result == null ? Collections.emptyList() : Collections.singletonList(result);
    }

    private Object id(Object value) {
        if (value instanceof SalesManagerEntity) return ((SalesManagerEntity<?, ?>) value).getId();
        if (value instanceof Object[]) return Arrays.stream((Object[]) value).map(this::id).collect(Collectors.toList());
        return value;
    }

    /**
     * Hibernate 6 (SQM) rejects some of the original Hibernate 5 HQL strings at parse time
     * (duplicate aliases, fetching the same association twice, boolean/integer comparisons);
     * those are exactly the defects AB-542 repaired. Such originals cannot be replayed any more,
     * so the parity checks below fall back to asserting the repaired query alone and
     * {@link #hibernate6RejectsExactlyTheOriginalsAb542Repaired()} pins the rejected set.
     */
    private List<?> original(String hql, Supplier<List<?>> rows) {
        try {
            em.createQuery(hql.replaceAll("%?(\\?\\d+)%?", "$1"));
        } catch (IllegalArgumentException | org.hibernate.query.sqm.InterpretationException e) {
            return null; // only a Hibernate 6 parse rejection skips the comparison
        }
        return rows.get(); // binding or execution failures still fail the test
    }

    private void same(String hql, List<?> expected, Object actual) {
        if (expected == null) return;
        List<?> before = expected.stream().map(this::id).collect(Collectors.toList());
        List<?> after = rows(actual).stream().map(this::id).collect(Collectors.toList());
        if (hql.toLowerCase(Locale.ROOT).contains("order by")) assertEquals(before, after, hql);
        else assertEquals(new HashSet<>(before), new HashSet<>(after), hql);
    }

    private Query positional(String hql, Object[] args) {
        Query query = em.createQuery(hql.replaceAll("%?(\\?\\d+)%?", "$1"));
        for (jakarta.persistence.Parameter<?> parameter : query.getParameters()) {
            int position = parameter.getPosition();
            Object value = args[position - 1];
            if (value != null && hql.contains("%?" + position + "%")) value = "%" + value + "%";
            query.setParameter(position, value);
        }
        return query;
    }

    private Map<String, Class<?>> annotatedRepositories() {
        Map<String, Class<?>> repositories = new HashMap<>();
        for (Class<?> type : Arrays.asList(ProductAvailabilityRepository.class, ManufacturerRepository.class,
                ProductReviewRepository.class, PageableProductVariantRepositoty.class, ProductVariantRepository.class,
                CustomerRepository.class, MerchantRepository.class, PageableMerchantRepository.class,
                OrderRepository.class, PermissionRepository.class)) repositories.put(type.getSimpleName(), type);
        return repositories;
    }

    private Object[] arguments(String key) {
        if (key.startsWith("ProductAvailabilityRepository")) return key.contains(",int")
                ? new Object[] {availability.getId(), store.getId()} : new Object[] {availability.getId()};
        if (key.contains("findByCategoriesAndLanguage")) return new Object[] {Collections.singletonList(child.getId()), language.getId()};
        if (key.contains("findByProductInCategoryId")) return new Object[] {store.getId(), "/hql/", language.getId()};
        if (key.contains("findByProductAndCustomer")) return new Object[] {product.getId(), customer.getId()};
        if (key.startsWith("ProductReviewRepository")) return key.contains(",Integer")
                ? new Object[] {product.getId(), language.getId()} : new Object[] {product.getId()};
        if (key.startsWith("PageableProductVariant")) return new Object[] {store.getId(), product.getId(), PageRequest.of(0, 100)};
        if (key.startsWith("ProductVariantRepository")) {
            if (key.contains("findBySku")) return new Object[] {variant.getCode(), product.getId(), store.getId(), language.getId()};
            if (key.contains("findByProductId")) return new Object[] {store.getId(), product.getId()};
            if (key.contains("findByIds")) return new Object[] {Collections.singletonList(variant.getId()), store.getId()};
            if (key.contains("findById(")) return new Object[] {variant.getId(), product.getId(), store.getId()};
            return new Object[] {variant.getId(), store.getId()};
        }
        if (key.startsWith("CustomerRepository")) {
            String nick = key.contains("Reset") ? "hql_reset" : customer.getNick();
            return key.contains(",int") ? new Object[] {nick, store.getId()} : new Object[] {nick, store.getCode()};
        }
        if (key.startsWith("MerchantRepository")) {
            if (key.contains("getById")) return new Object[] {store.getId()};
            return new Object[] {key.contains("getByParent") ? parentStore.getCode() : store.getCode()};
        }
        if (key.startsWith("PageableMerchantRepository")) return new Object[] {
                key.contains("listByStore") ? parentStore.getCode() : "HQL parity", PageRequest.of(0, 100)};
        if (key.startsWith("OrderRepository")) return new Object[] {order.getId(), store.getId()};
        if (key.startsWith("PermissionRepository")) return new Object[] {Collections.singleton(group.getId())};
        throw new AssertionError(key);
    }

    private Object missing(Object value) {
        if (value instanceof String) return "__missing_hql__";
        if (value instanceof Long) return -1L;
        if (value instanceof Integer) return -1;
        if (value instanceof Set) return Collections.singleton(-1);
        if (value instanceof List) return Collections.singletonList(-1L);
        throw new AssertionError(value);
    }

    @Test
    void everyChangedAnnotatedQueryMatchesOriginalForNonEmptyAndEmptyResults() throws Exception {
        int checked = 0;
        Map<String, Class<?>> repositories = annotatedRepositories();
        for (String key : new java.util.TreeSet<>(originals.stringPropertyNames())) {
            String typeName = key.substring(0, key.indexOf('.'));
            if (!repositories.containsKey(typeName) || key.endsWith(".count")) continue;
            Class<?> type = repositories.get(typeName);
            Method method = Arrays.stream(type.getDeclaredMethods()).filter(candidate -> {
                String signature = candidate.getName() + "(" + Arrays.stream(candidate.getParameterTypes())
                        .map(Class::getSimpleName).collect(Collectors.joining(",")) + ")";
                return key.substring(key.indexOf('.') + 1).replaceAll("<[^>]+>", "").equals(signature);
            }).findFirst().orElseThrow(() -> new AssertionError(key));
            Object repository = context.getBean(type);
            Object[] args = arguments(key);
            String hql = original(key);
            List<?> before = original(hql, () -> positional(hql, args).getResultList());
            if (before != null) assertFalse(before.isEmpty(), key);
            Object repaired = method.invoke(repository, args);
            assertFalse(rows(repaired).isEmpty(), key);
            same(hql, before, repaired);
            Object[] emptyArgs = args.clone();
            emptyArgs[0] = missing(args[0]);
            List<?> empty = original(hql, () -> positional(hql, emptyArgs).getResultList());
            if (empty != null) assertTrue(empty.isEmpty(), key);
            Object repairedEmpty = method.invoke(repository, emptyArgs);
            assertTrue(rows(repairedEmpty).isEmpty(), key);
            same(hql, empty, repairedEmpty);
            checked++;
        }
        assertEquals(24, checked);
    }

    @Test
    void changedAvailabilityCountMatchesOriginalIncludingEmptyPage() {
        String hql = original("PageableProductAvailabilityRepository.listByStore(Long,Integer,String,Pageable).count");
        for (Long productId : Arrays.asList(product.getId(), -1L)) {
            Object[] args = {productId, store.getId(), "hql_parity"};
            List<?> before = original(hql, () -> positional(hql, args).getResultList());
            Page<ProductAvailability> page = pageableAvailability.listByStore(productId, store.getId(), "hql_parity", PageRequest.of(0, 1));
            long expected = productId.equals(product.getId()) ? 1L : 0L;
            if (before != null) assertEquals(expected, ((Number) before.get(0)).longValue());
            assertEquals(expected, page.getTotalElements());
        }
    }

    private Map<String, Object> parameters(Object... pairs) {
        Map<String, Object> parameters = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) parameters.put((String) pairs[i], pairs[i + 1]);
        return parameters;
    }

    private List<?> named(String hql, Map<String, Object> parameters) {
        Query query = em.createQuery(hql);
        parameters.forEach(query::setParameter);
        return query.getResultList();
    }

    private void parity(String key, Map<String, Object> parameters, Supplier<?> repaired, boolean nonEmpty) {
        String hql = original(key);
        List<?> before = original(hql, () -> named(hql, parameters));
        if (before != null) assertEquals(nonEmpty, !before.isEmpty(), key);
        Object actual = repaired.get();
        assertEquals(nonEmpty, !rows(actual).isEmpty(), key);
        same(hql, before, actual);
    }

    @Test
    void customProductQueriesMatchOriginals() {
        for (boolean found : Arrays.asList(true, false)) {
            String sku = found ? product.getSku() : "__missing_hql__";
            Long id = found ? product.getId() : -1L;
            Set<Long> categoryIds = Collections.singleton(found ? child.getId() : -1L);
            Map<String, Object> locale = parameters("lid", Arrays.asList("*", "CA"), "dt", new Date(), "lang", language.getId());
            parity("ProductImpl.getByCodeLanguage", parameters("code", sku, "lang", language.getId()), () -> products.getByCode(sku, language), found);
            parity("ProductImpl.getByCodeStore", parameters("code", sku, "id", store.getId()), () -> products.getByCode(sku, store), found);
            Map<String, Object> urlParams = parameters("lid", Arrays.asList("*", "CA"), "dt", new Date(), "seUrl", found ? "hql-product" : "missing");
            if (found) {
                parity("ProductImpl.getByFriendlyUrl", urlParams, () -> products.getByFriendlyUrl(store, "hql-product", Locale.CANADA), true);
            } else {
                List<?> before = named(original("ProductImpl.getByFriendlyUrl"), urlParams);
                assertTrue(before.isEmpty());
                assertThrows(IndexOutOfBoundsException.class, () -> before.get(0));
                assertThrows(IndexOutOfBoundsException.class, () -> products.getByFriendlyUrl(store, "missing", Locale.CANADA));
            }
            locale.put("pid", id);
            parity("ProductImpl.getProductForLocale", locale, () -> products.getProductForLocale(id, language, Locale.CANADA), found);
            parity("ProductImpl.getProductsListByCategories", parameters("cid", categoryIds), () -> products.getProductsListByCategories(categoryIds), found);
            parity("ProductImpl.getProductsListByCategoriesLanguage", parameters("cid", categoryIds, "lang", language.getId(), "dt", new Date()),
                    () -> products.getProductsListByCategories(categoryIds, language), found);
            MerchantStore selectedStore = found ? store : new MerchantStore(-1, "missing", "missing");
            parity("ProductImpl.listByStore", parameters("mid", selectedStore.getId()), () -> products.listByStore(selectedStore), found);
            TaxClass selectedTax = found ? taxClass : new TaxClass();
            if (!found) selectedTax.setId(-1L);
            parity("ProductImpl.listByTaxClass", parameters("tid", selectedTax.getId()), () -> products.listByTaxClass(selectedTax), found);
            parity("ProductImpl.getById", parameters("productId", id, "id", store.getId()), () -> products.getById(id, store, language), found);
            String v2 = original("ProductImpl.productQueryV2") + "where p.id=:pid";
            List<?> unscoped = original(v2, () -> named(v2, parameters("pid", id)));
            if (unscoped != null) assertEquals(found, !unscoped.isEmpty());
            Product byId = ((ProductRepositoryCustom) products).getById(id);
            assertEquals(found, byId != null);
            same(v2, unscoped, byId);
            String scoped = v2 + " and merch.id in (:mid)";
            Product scopedById = products.getById(id, store);
            assertEquals(found, scopedById != null);
            same(scoped, original(scoped, () -> named(scoped, parameters("pid", id, "mid", Arrays.asList(store.getId(), parentStore.getId())))), scopedById);
        }
    }

    @Test
    void productCriteriaBranchesAndPrivateGraphsMatchOriginals() {
        ProductRepositoryImpl implementation = new ProductRepositoryImpl();
        ReflectionTestUtils.setField(implementation, "em", em);
        for (String name : Arrays.asList("productQueryV1", "productQueryV2")) {
            String before = original("ProductImpl." + name);
            String after = ReflectionTestUtils.invokeMethod(implementation, name);
            assertCleanHql(after);
            for (Long id : Arrays.asList(product.getId(), -1L)) {
                String tail = " where p.id=:productId";
                Map<String, Object> params = parameters("productId", id);
                List<?> oldRows = original(before, () -> named(before + tail, params));
                if (oldRows != null) assertEquals(id.equals(product.getId()), !oldRows.isEmpty());
                List<?> newRows = named(after + tail, params);
                assertEquals(id.equals(product.getId()), !newRows.isEmpty());
                same(before, oldRows, newRows);
            }
        }
        for (String origin : Arrays.asList(ProductCriteria.ORIGIN_SHOP, ProductCriteria.ORIGIN_ADMIN)) {
            for (boolean found : Arrays.asList(true, false)) {
                ProductCriteria criteria = new ProductCriteria();
                criteria.setOrigin(origin);
                criteria.setLanguage(language.getCode());
                criteria.setProductIds(Collections.singletonList(found ? product.getId() : -1L));
                criteria.setAvailable(true);
                String key = "ProductImpl.criteria." + origin;
                Map<String, Object> params = parameters("mId", store.getId(), "lang", language.getCode(), "pId", criteria.getProductIds(), "dt", new Date());
                parity(key, params, () -> products.listByStore(store, language, criteria).getProducts(), found);
            }
        }
    }

    @Test
    void categoryAndRelationshipQueriesMatchOriginals() {
        for (Long id : Arrays.asList(product.getId(), -1L)) {
            parity("CategoryImpl.listByProduct", parameters("id", id, "mid", store.getId()), () -> categories.listByProduct(store, id), id.equals(product.getId()));
            List<Long> ids = Collections.singletonList(id.equals(product.getId()) ? child.getId() : -1L);
            parity("CategoryImpl.countProductsByCategories", parameters("cid", ids, "dt", new Date()),
                    () -> categories.countProductsByCategories(store, ids), id.equals(product.getId()));
        }
        for (String code : Arrays.asList("hql_related", "missing")) {
            parity("RelationshipImpl.HQL_GET_BY_PRODUCT_ID_AND_CODE_AVAILABLE", parameters("code", code, "available", true, "pId", product.getId()),
                    () -> relationships.getByType(store, code, product), code.equals("hql_related"));
            parity("RelationshipImpl.HQL_GET_GROUP_BY_CODE_AND_STORE_ID", parameters("code", code, "storeId", store.getId()),
                    () -> relationships.getByGroup(store, code), code.equals("hql_related"));
        }
    }

    @Test
    void merchantCriteriaMatchesOriginal() throws Exception {
        for (String code : Arrays.asList(store.getCode(), "missing")) {
            MerchantStoreCriteria criteria = new MerchantStoreCriteria();
            criteria.setCode(code);
            String hql = original("MerchantImpl.listByCriteria") + " where lower(m.code) like:code";
            List<?> before = original(hql, () -> named(hql, parameters("code", "%" + code + "%")));
            if (before != null) assertEquals(code.equals(store.getCode()), !before.isEmpty());
            List<?> repaired = merchants.listByCriteria(criteria).getList();
            assertEquals(code.equals(store.getCode()), !repaired.isEmpty());
            same(hql, before, repaired);
        }
    }

    @Test
    void originalBrokenCustomerBranchFailsAndRepairedBranchReturnsFixtureIds() {
        String predicate = " where c.merchantStore.id=:mId and c..billing.firstName like:fn";
        assertThrows(IllegalArgumentException.class, () -> em.createQuery("select count(c) from Customer as c" + predicate));
        String result = "select c from Customer as c left join fetch c.delivery.country left join fetch c.delivery.zone "
                + "left join fetch c.billing.country left join fetch c.billing.zone left join fetch c.attributes ca "
                + "left join fetch ca.customerOption cao left join fetch ca.customerOptionValue cav "
                + "left join fetch cao.descriptions caod left join fetch cav.descriptions left join fetch c.groups";
        assertThrows(IllegalArgumentException.class, () -> em.createQuery(result + predicate));
        CustomerCriteria criteria = new CustomerCriteria();
        criteria.setFirstName("Hql");
        CustomerList resultList = customers.listByStore(store, criteria);
        assertEquals(1, resultList.getTotalCount());
        assertEquals(Collections.singletonList(customer.getId()), resultList.getCustomers().stream().map(Customer::getId).collect(Collectors.toList()));
        criteria.setFirstName("missing");
        assertEquals(0, customers.listByStore(store, criteria).getTotalCount());
    }

    @Test
    void originalBrokenCategoryBranchesFailAndRepairedBranchesReturnFixtureIds() {
        String base = "select c from Category c join fetch c.merchantStore cm ";
        assertThrows(IllegalArgumentException.class, () -> em.createQuery(base + " where c.parent IsNull order by c.sortOrder asc"));
        assertThrows(IllegalArgumentException.class, () -> em.createQuery(base + " where c.parent IsNull and cm.id=:mid order by c.sortOrder asc"));
        assertThrows(IllegalArgumentException.class, () -> em.createQuery(base + " join fetch c.parent cp where cp.id =:cId and cm.id=:mid order by c.sortOrder asc")
                .setParameter("cid", parent.getId()));
        assertEquals(Collections.singletonList(parent.getId()), categories.listByStoreAndParent(store, null).stream().map(Category::getId).collect(Collectors.toList()));
        assertEquals(Collections.singletonList(child.getId()), categories.listByStoreAndParent(store, parent).stream().map(Category::getId).collect(Collectors.toList()));
        List<Category> globalRoots = categories.listByStoreAndParent(null, null);
        assertTrue(globalRoots.stream().anyMatch(category -> parent.getId().equals(category.getId())));
        assertTrue(globalRoots.stream().allMatch(category -> category.getParent() == null));
        assertEquals(Collections.singletonList(parent.getId()), globalRoots.stream()
                .filter(category -> store.getId().equals(category.getMerchantStore().getId()))
                .map(Category::getId).collect(Collectors.toList()));
        String executes = base + " join fetch c.parent cp where cp.id =:cid order by c.sortOrder asc";
        same(executes, named(executes, parameters("cid", parent.getId())), categories.listByStoreAndParent(null, parent));
        Category missing = new Category();
        missing.setId(-1L);
        assertTrue(categories.listByStoreAndParent(store, missing).isEmpty());
        assertTrue(categories.listByStoreAndParent(null, missing).isEmpty());
    }

    @Test
    void productLocaleCountHasParityButOriginalResultBindingFails() {
        Map<String, Object> params = parameters("mId", store.getId(), "cid", Collections.singleton(child.getId()),
                "lid", Arrays.asList("*", "CA"), "dt", new Date());
        String count = original("ProductImpl.getProductsListForLocale.count");
        List<?> counted = original(count, () -> named(count, params));
        long before = counted == null ? 1L : ((Number) counted.get(0)).longValue();
        assertEquals(1L, before);
        // Hibernate 5 only failed when binding the parameters; Hibernate 6 already rejects the text
        assertThrows(IllegalArgumentException.class, () -> em.createQuery(original("ProductImpl.getProductsListForLocale")).setParameter("mId", store.getId()));
        ProductRepositoryImpl implementation = new ProductRepositoryImpl();
        ReflectionTestUtils.setField(implementation, "em", em);
        ProductList result = ReflectionTestUtils.invokeMethod(implementation, "getProductsListForLocale", store, Collections.singleton(child.getId()), language, Locale.CANADA, 0, -1);
        assertEquals(before, result.getTotalCount());
        assertEquals(Collections.singletonList(product.getId()), result.getProducts().stream().map(Product::getId).collect(Collectors.toList()));
        ProductList empty = ReflectionTestUtils.invokeMethod(implementation, "getProductsListForLocale", store, Collections.singleton(-1L), language, Locale.CANADA, 0, -1);
        assertEquals(0, empty.getTotalCount());
    }

    @Test
    void hibernate6RejectsExactlyTheOriginalsAb542Repaired() {
        Set<String> rejected = new java.util.TreeSet<>();
        for (String key : new java.util.TreeSet<>(originals.stringPropertyNames())) {
            try {
                em.createQuery(original(key).replaceAll("%?(\\?\\d+)%?", "$1"));
            } catch (IllegalArgumentException | org.hibernate.query.sqm.InterpretationException e) {
                rejected.add(key);
            }
        }
        assertEquals(new java.util.TreeSet<>(Arrays.asList(HIBERNATE6_REJECTED_ORIGINALS)), rejected);
    }

    @Test
    void auditEveryActiveAnnotatedHqlAndExplicitCountQueryOnHibernate5() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        CachingMetadataReaderFactory metadata = new CachingMetadataReaderFactory(resolver);
        Resource[] resources = resolver.getResources("classpath*:com/salesmanager/core/business/repositories/**/*.class");
        int hqlCount = 0;
        int nativeCount = 0;
        for (Resource resource : resources) {
            Class<?> type = Class.forName(metadata.getMetadataReader(resource).getClassMetadata().getClassName());
            for (Method method : type.getDeclaredMethods()) {
                if (method.isBridge() || method.isSynthetic()) continue;
                org.springframework.data.jpa.repository.Query annotation = method.getAnnotation(org.springframework.data.jpa.repository.Query.class);
                if (annotation == null) continue;
                if (annotation.nativeQuery()) {
                    nativeCount++;
                    continue;
                }
                assertCleanHql(annotation.value());
                em.createQuery(annotation.value().replaceAll("%?(\\?\\d+)%?", "$1"));
                if (!annotation.countQuery().isEmpty()) {
                    assertCleanHql(annotation.countQuery());
                    em.createQuery(annotation.countQuery().replaceAll("%?(\\?\\d+)%?", "$1"));
                }
                hqlCount++;
            }
        }
        assertEquals(225, hqlCount);
        assertEquals(3, nativeCount);
    }

    private void assertCleanHql(String hql) {
        Set<String> aliases = new HashSet<>();
        Set<String> entities = em.getMetamodel().getEntities().stream()
                .map(entity -> entity.getName().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        Matcher declarations = Pattern.compile("(?:\\bfrom\\s+|\\bjoin\\s+(?:fetch\\s+)?+)([\\w.]+)\\s+(?:as\\s+)?(\\w+)", Pattern.CASE_INSENSITIVE).matcher(hql);
        while (declarations.find()) {
            String path = declarations.group(1);
            String alias = declarations.group(2).toLowerCase(Locale.ROOT);
            if (Arrays.asList("where", "left", "inner", "join", "on", "order", "group").contains(alias)) continue;
            assertTrue(aliases.add(alias), "Repeated alias " + alias + ": " + hql);
            assertFalse(entities.contains(alias), "Entity-name alias " + alias + ": " + hql);
            assertFalse(path.substring(path.lastIndexOf('.') + 1).equalsIgnoreCase(alias), "Attribute-name alias " + alias + ": " + hql);
        }
        Set<String> paths = new HashSet<>();
        Matcher fetches = Pattern.compile("\\bjoin\\s+fetch\\s+([\\w.]+)", Pattern.CASE_INSENSITIVE).matcher(hql);
        while (fetches.find()) assertTrue(paths.add(fetches.group(1)), "Repeated fetch " + fetches.group(1) + ": " + hql);
    }
}
