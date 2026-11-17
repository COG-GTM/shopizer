package com.salesmanager.test.catalog;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import javax.inject.Inject;
import com.salesmanager.core.business.repositories.catalog.product.ProductRepository;
import com.salesmanager.core.model.catalog.category.Category;
import com.salesmanager.core.model.catalog.category.CategoryDescription;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.ProductCriteria;
import com.salesmanager.core.model.catalog.product.ProductList;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.catalog.product.description.ProductDescription;
import com.salesmanager.core.model.catalog.product.price.ProductPrice;
import com.salesmanager.core.model.catalog.product.price.ProductPriceDescription;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.test.common.AbstractSalesManagerCoreTestCase;

public class ProductPagingTest extends AbstractSalesManagerCoreTestCase {
    @Inject
    private ProductRepository repository;
    private MerchantStore store;
    private Language en;
    private Category category;
    private final List<Long> ids = new ArrayList<>();
    private String prefix;

    @Before
    public void createCatalog() throws Exception {
        store = merchantService.getByCode(MerchantStore.DEFAULT_STORE);
        en = languageService.getByCode("en");
        prefix = "paging-" + java.util.UUID.randomUUID().toString();
        category = new Category();
        category.setCode(prefix);
        category.setMerchantStore(store);
        CategoryDescription cd = new CategoryDescription();
        cd.setCategory(category);
        cd.setLanguage(en);
        cd.setName(prefix);
        category.getDescriptions().add(cd);
        categoryService.create(category);
        for (int i = 0; i < 8; i++) {
            Product p = new Product();
            p.setMerchantStore(store);
            p.setSku(prefix + "-" + i);
            p.setSortOrder(i / 2);
            p.setAvailable(true);
            ProductDescription d = new ProductDescription();
            d.setProduct(p);
            d.setLanguage(en);
            d.setName(prefix + " name " + i);
            p.getDescriptions().add(d);
            if (i % 2 == 0) p.getCategories().add(category);
            if (i < 7) {
                ProductAvailability a = new ProductAvailability();
                a.setProduct(p);
                a.setRegion("*");
                a.setAvailable(true);
                a.setProductQuantity(10);
                ProductPrice price = new ProductPrice();
                price.setProductAvailability(a);
                price.setDefaultPrice(true);
                price.setProductPriceAmount(BigDecimal.TEN);
                ProductPriceDescription pd = new ProductPriceDescription();
                pd.setProductPrice(price);
                pd.setLanguage(en);
                pd.setName("Base price");
                price.getDescriptions().add(pd);
                a.getPrices().add(price);
                p.getAvailabilities().add(a);
            }
            if (i < 7) productService.create(p);
            else repository.saveAndFlush(p);
            ids.add(p.getId());
        }
    }

    private ProductCriteria criteria(int offset, int size) {
        ProductCriteria c = new ProductCriteria();
        c.setProductIds(ids.subList(0, 7));
        c.setLanguage("en");
        c.setStartIndex(offset);
        c.setMaxCount(size);
        return c;
    }

    private ProductList list(ProductCriteria c) {
        return productService.listByStore(store, en, c);
    }

    @Test
    public void pagesPartitionHydratedSortedCatalog() {
        ProductList full = list(criteria(0, 0));
        assertEquals(7, full.getProducts().size());
        Set<Long> seen = new HashSet<>();
        int previous = Integer.MIN_VALUE;
        for (int offset = 0; offset < 9; offset += 3) {
            ProductList page = list(criteria(offset, 3));
            assertEquals(full.getTotalCount(), page.getTotalCount());
            assertEquals(Math.min(3, 7 - offset), page.getProducts().size());
            for (Product p : page.getProducts()) {
                assertTrue(seen.add(p.getId()));
                assertTrue(p.getSortOrder() >= previous);
                previous = p.getSortOrder();
                assertFalse(p.getDescriptions().isEmpty());
                assertFalse(p.getAvailabilities().isEmpty());
                p.getAvailabilities().forEach(a -> {
                    assertFalse(a.getPrices().isEmpty());
                    a.getPrices().forEach(price -> assertEquals(0, BigDecimal.TEN.compareTo(price.getProductPriceAmount())));
                });
                if (ids.indexOf(p.getId()) % 2 == 0) assertEquals(category.getId(), p.getCategories().iterator().next().getId());
            }
        }
        assertEquals(new HashSet<>(ids.subList(0, 7)), seen);
        ProductList beyond = list(criteria(99, 3));
        assertTrue(beyond.getProducts().isEmpty());
        assertEquals(7, beyond.getTotalCount());
    }

    @Test
    public void pagedListingDoesNotFallBackToInMemoryPagination() {
        Logger logger = (Logger) LoggerFactory.getLogger("org.hibernate.hql.internal.ast.QueryTranslatorImpl");
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.WARN);
        try {
            assertEquals(3, list(criteria(0, 3)).getProducts().size());
            assertFalse("Paged listing must not log HHH000104 (in-memory collection-fetch pagination)",
                    appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("HHH000104")));
        } finally {
            logger.setLevel(previousLevel);
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    public void filtersApplyBeforePaging() {
        ProductCriteria c = criteria(0, 2);
        c.setCategoryIds(Collections.singletonList(category.getId()));
        assertEquals(4, list(c).getTotalCount());
        assertEquals(Arrays.asList(ids.get(0), ids.get(2)), productIds(list(c)));
        c.setStartIndex(2);
        assertEquals(Arrays.asList(ids.get(4), ids.get(6)), productIds(list(c)));
        c = criteria(0, 1);
        c.setCode(prefix + "-3");
        assertEquals(Collections.singletonList(ids.get(3)), productIds(list(c)));
        c = criteria(0, 1);
        c.setProductName(prefix + " name 5");
        assertEquals(Collections.singletonList(ids.get(5)), productIds(list(c)));
        c.setProductName("does-not-exist");
        assertEquals(0, list(c).getTotalCount());
        assertTrue(list(c).getProducts().isEmpty());
    }

    @Test
    public void missingAvailabilityAndLanguageDoNotLeakIntoResults() throws Exception {
        ProductCriteria c = criteria(0, 3);
        c.setProductIds(Collections.singletonList(ids.get(7)));
        assertTrue(list(c).getProducts().isEmpty());
        c = criteria(0, 3);
        c.setLanguage("fr");
        assertTrue(productService.listByStore(store, languageService.getByCode("fr"), c).getProducts().isEmpty());
    }

    private List<Long> productIds(ProductList list) {
        List<Long> result = new ArrayList<>();
        list.getProducts().forEach(p -> result.add(p.getId()));
        return result;
    }
}
