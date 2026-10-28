package com.salesmanager.shop.store.api.v1.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.controller.product.facade.ProductCommonFacade;

@RunWith(MockitoJUnitRunner.class)
public class ProductApiUpdateTest {

    @Mock
    private ProductCommonFacade facade;
    @InjectMocks
    private ProductApi api;

    private MerchantStore store;
    private PersistableProduct body;
    private MockHttpServletResponse response;

    @Before
    public void setUp() {
        store = new MerchantStore();
        store.setDefaultLanguage(new Language());
        body = new PersistableProduct();
        body.setId(10L);
        response = new MockHttpServletResponse();
    }

    @Test
    public void mismatchedIdsReturn400WithoutCallingSave() {
        api.update(11L, body, store, new MockHttpServletRequest(), response);
        assertEquals(400, response.getStatus());
        verifyNoInteractions(facade);
    }

    @Test
    public void missingBodyIdReturns400WithoutCallingSave() {
        body.setId(null);
        api.update(10L, body, store, new MockHttpServletRequest(), response);
        assertEquals(400, response.getStatus());
        verifyNoInteractions(facade);
    }

    @Test
    public void notFoundIsPropagatedInsteadOfBecoming503() {
        ResourceNotFoundException missing = new ResourceNotFoundException("missing");
        when(facade.saveProduct(store, body, store.getDefaultLanguage())).thenThrow(missing);
        assertEquals(missing, assertThrows(ResourceNotFoundException.class,
                () -> api.update(10L, body, store, new MockHttpServletRequest(), response)));
    }

    @Test
    public void matchingIdsStillSaveAndReturn200() {
        api.update(10L, body, store, new MockHttpServletRequest(), response);
        verify(facade).saveProduct(store, body, store.getDefaultLanguage());
        assertEquals(200, response.getStatus());
    }

    @Test
    public void unexpectedSaveErrorsKeepThe503Contract() {
        when(facade.saveProduct(store, body, store.getDefaultLanguage()))
                .thenThrow(new IllegalStateException("save failed"));
        api.update(10L, body, store, new MockHttpServletRequest(), response);
        assertEquals(503, response.getStatus());
    }
}
