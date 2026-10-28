package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.controller.product.facade.ProductCommonFacade;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
public class ProductSavePersistenceIntegrationTest extends ServicesTestSupport {
    @Autowired
    private ProductService productService;
    @Autowired
    private MerchantStoreService storeService;
    @Autowired
    private ProductCommonFacade facade;

    @Test
    public void ownershipQueryReturnsNullRatherThanAMissingEntityProxy() {
        assertNull(productService.getProductWithOnlyMerchantStoreById(Long.MAX_VALUE));
    }

    @Test
    public void missingProductProducesNotFoundThroughTheRealPersistenceService() throws Exception {
        MerchantStore store = storeService.getByCode("DEFAULT");
        PersistableProduct body = new PersistableProduct();
        body.setId(Long.MAX_VALUE);

        assertThrows(ResourceNotFoundException.class,
                () -> facade.saveProduct(store, body, store.getDefaultLanguage()));
        assertNull(productService.getProductWithOnlyMerchantStoreById(Long.MAX_VALUE));
    }
}
