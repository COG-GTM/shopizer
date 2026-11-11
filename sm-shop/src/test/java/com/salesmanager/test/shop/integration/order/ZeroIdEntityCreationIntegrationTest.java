package com.salesmanager.test.shop.integration.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.salesmanager.core.business.services.catalog.category.CategoryService;
import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.order.OrderService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.model.catalog.category.Category;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.catalog.product.description.ProductDescription;
import com.salesmanager.core.model.common.Billing;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@Transactional
public class ZeroIdEntityCreationIntegrationTest extends ServicesTestSupport {

    @Autowired private ProductService productService;
    @Autowired private CustomerService customerService;
    @Autowired private OrderService orderService;
    @Autowired private CategoryService categoryService;
    @Autowired private MerchantStoreService merchantStoreService;
    @Autowired private LanguageService languageService;

    private MerchantStore store() throws Exception {
        return merchantStoreService.getByCode(MerchantStore.DEFAULT_STORE);
    }

    private Billing billing() throws Exception {
        Billing billing = new Billing();
        billing.setFirstName("Test");
        billing.setLastName("Customer");
        billing.setCountry(store().getCountry());
        return billing;
    }

    @Test
    void createsProductWithZeroId() throws Exception {
        long count = productService.count();
        Product product = new Product();
        product.setId(0L);
        product.setSku("zero_id_product");
        product.setMerchantStore(store());
        ProductAvailability availability = new ProductAvailability();
        availability.setProduct(product);
        availability.setMerchantStore(store());
        product.getAvailabilities().add(availability);
        ProductDescription description = new ProductDescription();
        description.setProduct(product);
        description.setLanguage(languageService.getByCode("en"));
        description.setName("Zero ID product");
        product.getDescriptions().add(description);
        productService.create(product);
        assertTrue(product.getId() > 0);
        assertEquals(count + 1, productService.count().longValue());
        assertEquals(product.getId(), productService.getBySku(product.getSku(), store()).getId());
        assertEquals(product.getId(), productService.getBySku(product.getSku(), store(), languageService.getByCode("en")).getId());
    }

    @Test
    void createsCustomerWithZeroId() throws Exception {
        long count = customerService.count();
        Customer customer = new Customer();
        customer.setId(0L);
        customer.setEmailAddress("zero-id@example.com");
        customer.setMerchantStore(store());
        customer.setDefaultLanguage(languageService.getByCode("en"));
        customer.setBilling(billing());
        customerService.saveOrUpdate(customer);
        assertTrue(customer.getId() > 0);
        assertEquals(count + 1, customerService.count().longValue());
    }

    @Test
    void createsOrderWithZeroId() throws Exception {
        long count = orderService.count();
        Order order = new Order();
        order.setId(0L);
        order.setMerchant(store());
        order.setCustomerEmailAddress("zero-id@example.com");
        order.setBilling(billing());
        orderService.create(order);
        assertTrue(order.getId() > 0);
        assertEquals(count + 1, orderService.count().longValue());
    }

    @Test
    void createsCategoryWithZeroId() throws Exception {
        long count = categoryService.count();
        Category category = new Category(store());
        category.setId(0L);
        category.setCode("zero-id-category");
        categoryService.create(category);
        assertTrue(category.getId() > 0);
        assertEquals(count + 1, categoryService.count().longValue());
        assertEquals("/" + category.getId() + "/", category.getLineage());
    }
}
