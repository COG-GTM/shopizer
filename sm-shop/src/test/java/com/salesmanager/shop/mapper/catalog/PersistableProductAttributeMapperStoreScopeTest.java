package com.salesmanager.shop.mapper.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.catalog.product.attribute.ProductOptionService;
import com.salesmanager.core.business.services.catalog.product.attribute.ProductOptionValueService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.attribute.ProductAttribute;
import com.salesmanager.core.model.catalog.product.attribute.ProductOption;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionValue;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.model.catalog.product.attribute.PersistableProductAttribute;
import com.salesmanager.shop.model.catalog.product.attribute.PersistableProductOptionValue;
import com.salesmanager.shop.model.catalog.product.attribute.ProductPropertyOption;
import com.salesmanager.shop.store.api.exception.ConversionRuntimeException;

@ExtendWith(MockitoExtension.class)
class PersistableProductAttributeMapperStoreScopeTest {
    @Mock private ProductService productService;
    @Mock private ProductOptionService productOptionService;
    @Mock private ProductOptionValueService productOptionValueService;
    @InjectMocks private PersistableProductAttributeMapper mapper;
    private MerchantStore store;
    private PersistableProductAttribute source;
    private ProductOptionValue value;

    @BeforeEach
    void setUp() {
        store = new MerchantStore();
        store.setId(1);
        ProductOption option = new ProductOption();
        option.setMerchantStore(store);
        value = new ProductOptionValue();
        value.setMerchantStore(store);
        source = new PersistableProductAttribute();
        source.setProductId(10L);
        ProductPropertyOption property = new ProductPropertyOption();
        property.setCode("color");
        source.setOption(property);
        PersistableProductOptionValue propertyValue = new PersistableProductOptionValue();
        propertyValue.setCode("red");
        source.setOptionValue(propertyValue);
        when(productOptionService.getByCode(store, "color")).thenReturn(option);
        when(productOptionValueService.getByCode(store, "red")).thenReturn(value);
    }

    @Test
    void acceptsSameStoreProductAndResolvesValueByCode() {
        Product product = new Product();
        product.setId(10L);
        product.setMerchantStore(store);
        when(productService.getById(10L)).thenReturn(product);
        ProductAttribute result = mapper.convert(source, store, null);
        assertSame(product, result.getProduct());
        assertSame(value, result.getProductOptionValue());
        verify(productOptionValueService).getByCode(store, "red");
        verify(productOptionValueService, never()).getById(anyLong());
    }

    @Test
    void rejectsMissingProduct() {
        assertThrows(ConversionRuntimeException.class, () -> mapper.convert(source, store, null));
    }
}
