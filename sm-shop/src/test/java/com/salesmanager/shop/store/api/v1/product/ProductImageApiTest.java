package com.salesmanager.shop.store.api.v1.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.catalog.product.image.ProductImageService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.image.ProductImage;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ProductImageApiTest {

    @Mock
    private ProductImageService productImageService;

    @Mock
    private ProductService productService;

    @InjectMocks
    private ProductImageApi productImageApi;

    @Test
    public void rejectsTraversalFileName() throws Exception {
        MerchantStore store = store(2, "GLOBEX");
        when(productService.getById(10L)).thenReturn(product(10L, store));

        String[] names = {"../../../ACME/SKU1/LARGE/main.jpg", "..\\..\\evil.png", "/tmp/evil.png", ".."};
        for (String name : names) {
            MultipartFile file = new MockMultipartFile("file", name, "image/png", new byte[] {1, 2, 3});
            try {
                productImageApi.uploadImage(10L, new MultipartFile[] {file}, 0, false, store, null);
                fail("Expected rejection of " + name);
            } catch (ServiceRuntimeException expected) {
                // expected
            }
        }

        verify(productImageService, never()).addProductImages(any(), anyList());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void acceptsPlainFileName() throws Exception {
        MerchantStore store = store(2, "GLOBEX");
        Product product = product(10L, store);
        when(productService.getById(10L)).thenReturn(product);

        MultipartFile file = new MockMultipartFile("file", "main.jpg", "image/jpeg", new byte[] {1, 2, 3});
        productImageApi.uploadImage(10L, new MultipartFile[] {file}, 0, false, store, null);

        ArgumentCaptor<List<ProductImage>> captor = ArgumentCaptor.forClass(List.class);
        verify(productImageService).addProductImages(eq(product), captor.capture());
        assertEquals("main.jpg", captor.getValue().get(0).getProductImage());
    }

    private static MerchantStore store(int id, String code) {
        MerchantStore store = new MerchantStore();
        store.setId(id);
        store.setCode(code);
        return store;
    }

    private static Product product(Long id, MerchantStore store) {
        Product product = new Product();
        product.setId(id);
        product.setSku("SKU1");
        product.setMerchantStore(store);
        product.setImages(new HashSet<>());
        return product;
    }
}
