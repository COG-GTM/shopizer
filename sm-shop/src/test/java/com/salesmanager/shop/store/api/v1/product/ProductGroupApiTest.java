package com.salesmanager.shop.store.api.v1.product;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.controller.items.facade.ProductItemsFacade;

@RunWith(MockitoJUnitRunner.class)
public class ProductGroupApiTest {

    private static final Long PRODUCT_ID = 42L;
    private static final String GROUP = "FEATURED_ITEM";

    @Mock private ProductService productService;
    @Mock private ProductItemsFacade productItemsFacade;
    @InjectMocks private ProductGroupApi api;

    private MerchantStore caller;
    private Language language;

    @Before
    public void setUp() {
        caller = store(1000, "CALLER");
        language = new Language();
    }

    @Test
    public void addAcceptsDistinctStoreObjectsWithEqualIds() {
        Product product = product(store(1000, "RELOADED"));
        ReadableProductList expected = new ReadableProductList();
        when(productService.getProductWithOnlyMerchantStoreById(PRODUCT_ID)).thenReturn(product);
        when(productItemsFacade.addItemToGroup(product, GROUP, caller, language)).thenReturn(expected);

        assertSame(expected, add());
        verify(productService, never()).getById(PRODUCT_ID);
        verify(productItemsFacade).addItemToGroup(product, GROUP, caller, language);
    }

    @Test
    public void removeAcceptsDistinctStoreObjectsWithEqualIds() throws Exception {
        Product product = product(store(1000, "RELOADED"));
        ReadableProductList expected = new ReadableProductList();
        when(productService.getProductWithOnlyMerchantStoreById(PRODUCT_ID)).thenReturn(product);
        when(productItemsFacade.removeItemFromGroup(product, GROUP, caller, language)).thenReturn(expected);

        assertSame(expected, remove());
        verify(productService, never()).getById(PRODUCT_ID);
        verify(productItemsFacade).removeItemFromGroup(product, GROUP, caller, language);
    }

    @Test
    public void addRejectsForeignProductEvenWhenStoreCodesMatch() {
        rejectAdd(product(store(1001, "CALLER")), UnauthorizedException.class);
    }

    @Test
    public void removeRejectsForeignProductEvenWhenStoreCodesMatch() {
        rejectRemove(product(store(1001, "CALLER")), UnauthorizedException.class);
    }

    @Test
    public void addRejectsProductWithoutStore() {
        rejectAdd(product(null), UnauthorizedException.class);
    }

    @Test
    public void removeRejectsProductWithoutStore() {
        rejectRemove(product(null), UnauthorizedException.class);
    }

    @Test
    public void addReportsMissingProductBeforeCallingFacade() {
        rejectAdd(null, ResourceNotFoundException.class);
    }

    @Test
    public void removeReportsMissingProductBeforeCallingFacade() {
        rejectRemove(null, ResourceNotFoundException.class);
    }

    private void rejectAdd(Product product, Class<? extends Throwable> exception) {
        when(productService.getProductWithOnlyMerchantStoreById(PRODUCT_ID)).thenReturn(product);
        assertThrows(exception, this::add);
        verifyNoInteractions(productItemsFacade);
        verify(productService, never()).getById(PRODUCT_ID);
    }

    private void rejectRemove(Product product, Class<? extends Throwable> exception) {
        when(productService.getProductWithOnlyMerchantStoreById(PRODUCT_ID)).thenReturn(product);
        assertThrows(exception, this::remove);
        verifyNoInteractions(productItemsFacade);
        verify(productService, never()).getById(PRODUCT_ID);
    }

    private ReadableProductList add() {
        return api.addProductToGroup(PRODUCT_ID, GROUP, caller, language, new MockHttpServletResponse());
    }

    private ReadableProductList remove() {
        return api.removeProductFromGroup(PRODUCT_ID, GROUP, caller, language,
                new MockHttpServletRequest(), new MockHttpServletResponse());
    }

    private static Product product(MerchantStore store) {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setMerchantStore(store);
        return product;
    }

    private static MerchantStore store(int id, String code) {
        MerchantStore store = new MerchantStore();
        store.setId(id);
        store.setCode(code);
        return store;
    }
}
