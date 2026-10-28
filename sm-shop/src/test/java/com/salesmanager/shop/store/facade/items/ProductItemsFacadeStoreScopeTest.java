package com.salesmanager.shop.store.facade.items;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.services.catalog.product.relationship.ProductRelationshipService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.relationship.ProductRelationship;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.store.api.exception.OperationNotAllowedException;

@RunWith(MockitoJUnitRunner.class)
public class ProductItemsFacadeStoreScopeTest {

    @Mock private ProductRelationshipService relationships;
    @Spy @InjectMocks private ProductItemsFacadeImpl facade;
    @Captor private ArgumentCaptor<ProductRelationship> savedRelationship;

    @Test
    public void equalStoreIdsAllowRelationshipToBeSavedWithCallerStore() throws Exception {
        MerchantStore caller = store(1000);
        Product product = product(store(1000));
        Language language = new Language();
        ReadableProductList expected = new ReadableProductList();
        when(relationships.getByGroup(caller, "FEATURED_ITEM")).thenReturn(Collections.emptyList());
        doReturn(expected).when(facade).listItemsByGroup("FEATURED_ITEM", caller, language);

        assertSame(expected, facade.addItemToGroup(product, "FEATURED_ITEM", caller, language));
        verify(relationships).saveOrUpdate(savedRelationship.capture());
        assertSame(caller, savedRelationship.getValue().getStore());
        assertSame(product, savedRelationship.getValue().getRelatedProduct());
        org.junit.Assert.assertEquals("FEATURED_ITEM", savedRelationship.getValue().getCode());
        org.junit.Assert.assertTrue(savedRelationship.getValue().isActive());
    }

    @Test
    public void sameStoreDuplicateStillRejectsWithoutSaving() throws Exception {
        MerchantStore caller = store(1000);
        Product product = product(caller);
        ProductRelationship existing = new ProductRelationship();
        existing.setRelatedProduct(product);
        when(relationships.getByGroup(caller, "FEATURED_ITEM"))
                .thenReturn(Collections.singletonList(existing));

        assertThrows(OperationNotAllowedException.class,
                () -> facade.addItemToGroup(product, "FEATURED_ITEM", caller, new Language()));
        verify(relationships, never()).saveOrUpdate(any(ProductRelationship.class));
    }

    @Test
    public void nullCallerStoreRejectsBeforeRelationshipLookup() {
        assertThrows(NullPointerException.class,
                () -> facade.addItemToGroup(product(store(1000)), "FEATURED_ITEM", null, new Language()));
        verifyNoInteractions(relationships);
    }

    private static Product product(MerchantStore store) {
        Product product = new Product();
        product.setId(42L);
        product.setMerchantStore(store);
        return product;
    }

    private static MerchantStore store(int id) {
        MerchantStore store = new MerchantStore();
        store.setId(id);
        store.setCode("DEFAULT");
        return store;
    }
}
