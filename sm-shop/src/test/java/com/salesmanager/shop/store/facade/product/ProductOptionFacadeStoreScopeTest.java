package com.salesmanager.shop.store.facade.product;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.catalog.product.attribute.ProductAttributeService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.attribute.ProductAttribute;
import com.salesmanager.core.model.catalog.product.attribute.ProductOption;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.mapper.catalog.PersistableProductAttributeMapper;
import com.salesmanager.shop.mapper.catalog.ReadableProductAttributeMapper;
import com.salesmanager.shop.model.catalog.product.attribute.PersistableProductAttribute;
import com.salesmanager.shop.model.catalog.product.attribute.PersistableProductOptionValue;
import com.salesmanager.shop.model.catalog.product.attribute.ProductPropertyOption;
import com.salesmanager.shop.model.catalog.product.attribute.api.ReadableProductAttributeEntity;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class ProductOptionFacadeStoreScopeTest {
    @Mock private ProductService productService;
    @Mock private ProductAttributeService productAttributeService;
    @Mock private PersistableProductAttributeMapper persistableProductAttributeMapper;
    @Mock private ReadableProductAttributeMapper readableProductAttributeMapper;
    @InjectMocks private ProductOptionFacadeImpl facade;
    private MerchantStore store;
    private Product product;

    @BeforeEach
    void setUp() {
        store = new MerchantStore();
        store.setId(1);
        product = new Product();
        product.setId(10L);
        product.setMerchantStore(store);
        when(productService.getById(10L)).thenReturn(product);
    }

    @Test
    void rejectsAttributeOfAnotherProductInSameStore() throws Exception {
        Product other = new Product();
        other.setId(11L);
        other.setMerchantStore(store);
        ProductAttribute existing = new ProductAttribute();
        existing.setProduct(other);
        when(productAttributeService.getById(99L)).thenReturn(existing);
        PersistableProductAttribute source = attribute();
        source.setId(99L);
        assertThrows(ResourceNotFoundException.class, () -> facade.saveAttribute(10L, source, store, null));
        verifyNoInteractions(persistableProductAttributeMapper);
        verify(productAttributeService, never()).saveOrUpdate(any());
    }

    @Test
    void createsSameStoreAttributeAndBindsUrlProduct() throws Exception {
        save(false);
    }

    @Test
    void updatesSameStoreAttributeAndBindsUrlProduct() throws Exception {
        save(true);
    }

    private void save(boolean update) throws Exception {
        PersistableProductAttribute source = attribute();
        source.setProductId(999L);
        ProductAttribute existing = new ProductAttribute();
        existing.setId(99L);
        existing.setProduct(product);
        if (update) {
            source.setId(99L);
        }
        when(productAttributeService.getById(99L)).thenReturn(existing);
        when(persistableProductAttributeMapper.merge(eq(source), any(), eq(store), isNull())).thenAnswer(call -> {
            assertEquals(10L, source.getProductId());
            ProductAttribute destination = call.getArgument(1);
            if (update) assertSame(existing, destination);
            destination.setId(99L);
            destination.setProduct(product);
            return destination;
        });
        ReadableProductAttributeEntity readable = new ReadableProductAttributeEntity();
        when(readableProductAttributeMapper.convert(existing, store, null)).thenReturn(readable);
        assertSame(readable, facade.saveAttribute(10L, source, store, null));
        verify(productAttributeService).saveOrUpdate(argThat(a -> a.getProduct() == product && a.getId() == 99L));
    }

    @Test
    void bulkOverwritesBodyProductAndBindsEveryAttribute() throws Exception {
        List<PersistableProductAttribute> sources = Arrays.asList(attribute(), attribute());
        sources.forEach(a -> a.setProductId(999L));
        when(persistableProductAttributeMapper.convert(any(), eq(store), isNull())).thenAnswer(call -> {
            PersistableProductAttribute source = call.getArgument(0);
            assertEquals(10L, source.getProductId());
            ProductAttribute result = new ProductAttribute();
            ProductOption option = new ProductOption();
            option.setCode("color");
            result.setProductOption(option);
            return result;
        });
        assertEquals(2, facade.createAttributes(sources, 10L, store).size());
        verify(productAttributeService).saveAll(argThat(items -> StreamSupport.stream(items.spliterator(), false)
                .filter(a -> a.getProduct() == product).count() == 2));
        assertEquals(2, product.getAttributes().size());
        verify(productService).save(product);
    }

    @Test
    void emptyBulkSavesNoAttributes() throws Exception {
        assertTrue(facade.createAttributes(Collections.emptyList(), 10L, store).isEmpty());
        verifyNoInteractions(persistableProductAttributeMapper);
        verify(productAttributeService).saveAll(Collections.emptyList());
    }

    private PersistableProductAttribute attribute() {
        PersistableProductAttribute result = new PersistableProductAttribute();
        result.setOption(new ProductPropertyOption());
        result.setOptionValue(new PersistableProductOptionValue());
        return result;
    }
}
