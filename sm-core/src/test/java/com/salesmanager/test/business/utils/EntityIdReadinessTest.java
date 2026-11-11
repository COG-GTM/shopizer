package com.salesmanager.test.business.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.repositories.catalog.product.ProductRepository;
import com.salesmanager.core.business.services.catalog.product.ProductServiceImpl;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;

public class EntityIdReadinessTest {

    @Test
    void normalizesZeroIdsBeforeSaveAndBatchSave() throws Exception {
        ProductRepository repository = mock(ProductRepository.class);
        ProductServiceImpl service = new ProductServiceImpl(repository);
        doAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            assertNull(product.getId());
            return product;
        }).when(repository).saveAndFlush(any(Product.class));
        Product product = new Product();
        product.setId(0L);
        service.save(product);
        assertNull(product.getId());
        Product second = new Product();
        second.setId(0L);
        service.saveAll(Arrays.asList(product, second));
        assertNull(second.getId());
        service.saveAll(Collections.emptyList());
    }

    @Test
    void preservesNullAndAssignedIdsAndRepositoryFailures() throws Exception {
        ProductRepository repository = mock(ProductRepository.class);
        ProductServiceImpl service = new ProductServiceImpl(repository);
        Product product = new Product();
        service.save(product);
        assertNull(product.getId());
        product.setId(23L);
        service.save(product);
        assertEquals(23L, product.getId().longValue());
        when(repository.saveAndFlush(product)).thenThrow(new IllegalStateException("failed"));
        assertThrows(IllegalStateException.class, () -> service.save(product));
    }

    @Test
    void acceptsNativeIdsFromDifferentNumberTypes() throws Exception {
        for (Number id : Arrays.asList(BigInteger.valueOf(23), Long.valueOf(23), Integer.valueOf(23))) {
            ProductRepository repository = mock(ProductRepository.class);
            ProductServiceImpl service = new ProductServiceImpl(repository);
            MerchantStore store = new MerchantStore();
            store.setId(1);
            Language language = new Language();
            Product product = new Product();
            when(repository.findBySku("sku", 1)).thenReturn(Collections.singletonList(id));
            when(repository.getById(23L, store)).thenReturn(product);
            when(repository.getById(23L, store, language)).thenReturn(product);
            assertEquals(product, service.getBySku("sku", store));
            assertEquals(product, service.getBySku("sku", store, language));
            verify(repository).getById(23L, store);
        }
    }

    @Test
    void wrapsMissingAndInvalidNativeIds() {
        ProductRepository repository = mock(ProductRepository.class);
        ProductServiceImpl service = new ProductServiceImpl(repository);
        MerchantStore store = new MerchantStore();
        store.setId(1);
        Language language = new Language();
        when(repository.findBySku("sku", 1)).thenReturn(Collections.emptyList());
        assertThrows(ServiceException.class, () -> service.getBySku("sku", store));
        assertThrows(ServiceException.class, () -> service.getBySku("sku", store, language));
        when(repository.findBySku("sku", 1)).thenReturn(Collections.singletonList("invalid"));
        assertThrows(ServiceException.class, () -> service.getBySku("sku", store));
        assertThrows(ServiceException.class, () -> service.getBySku("sku", store, language));
    }
}
