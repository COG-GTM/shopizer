package com.salesmanager.shop.store.facade.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.mapper.catalog.product.PersistableProductMapper;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;

@RunWith(MockitoJUnitRunner.class)
public class ProductSaveBoundaryTest {
    @Mock
    private ProductService service;
    @Mock
    private PersistableProductMapper mapper;
    @InjectMocks
    private ProductCommonFacadeImpl facade;

    private MerchantStore store;
    private Language language;

    @Before
    public void setUp() {
        store = new MerchantStore();
        store.setId(1000);
        store.setCode("TEST");
        language = new Language();
    }

    @Test
    public void productWithoutAnOwnerIsRejectedBeforeMapping() throws Exception {
        Product existing = new Product();
        existing.setId(10L);
        when(service.getProductWithOnlyMerchantStoreById(10L)).thenReturn(existing);
        PersistableProduct body = new PersistableProduct();
        body.setId(10L);

        assertThrows(ResourceNotFoundException.class, () -> facade.saveProduct(store, body, language));
        verifyNoInteractions(mapper);
        verify(service, never()).getById(any());
        verify(service, never()).saveProduct(any());
    }

    @Test
    public void equalStoreIdsOutsideIntegerCacheAllowAnUpdate() throws Exception {
        MerchantStore owner = new MerchantStore();
        owner.setId(Integer.valueOf("1000"));
        Product existing = new Product();
        existing.setId(10L);
        existing.setMerchantStore(owner);
        PersistableProduct body = new PersistableProduct();
        body.setId(10L);
        when(service.getProductWithOnlyMerchantStoreById(10L)).thenReturn(existing);
        when(service.getById(10L)).thenReturn(existing);
        when(mapper.merge(body, existing, store, language)).thenReturn(existing);
        when(service.saveProduct(existing)).thenReturn(existing);

        assertEquals(Long.valueOf(10), facade.saveProduct(store, body, language));
        verify(mapper).merge(body, existing, store, language);
    }

    @Test
    public void nullIdStillCreatesWithoutAnOwnershipLookup() throws Exception {
        assertCreate(null);
    }

    @Test
    public void zeroIdStillCreatesWithoutAnOwnershipLookup() throws Exception {
        assertCreate(0L);
    }

    private void assertCreate(Long id) throws Exception {
        PersistableProduct body = new PersistableProduct();
        body.setId(id);
        Product saved = new Product();
        saved.setId(20L);
        when(mapper.merge(eq(body), any(Product.class), eq(store), eq(language))).thenReturn(saved);
        when(service.saveProduct(saved)).thenReturn(saved);

        assertEquals(Long.valueOf(20), facade.saveProduct(store, body, language));
        verify(service, never()).getProductWithOnlyMerchantStoreById(any());
        verify(service, never()).getById(any());
    }
}
