package com.salesmanager.test.shop.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.services.catalog.pricing.PricingService;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.catalog.product.price.FinalPrice;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.application.config.LocationImageConfig;
import com.salesmanager.shop.mapper.inventory.ReadableInventoryMapper;
import com.salesmanager.shop.populator.store.ReadableMerchantStorePopulator;
import com.salesmanager.shop.store.controller.store.facade.StoreFacadeImpl;
import com.salesmanager.shop.utils.CloudFilePathUtils;
import com.salesmanager.shop.utils.LocalImageFilePathUtils;

class DroolsStringReplacementTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"default", "cloud"})
    void imageMethodOnlyTreatsNullAndEmptyAsAbsent(String method) {
        LocationImageConfig config = new LocationImageConfig();
        ReflectionTestUtils.setField(config, "method", method);
        ReflectionTestUtils.setField(config, "contentUrl", "https://images.example");
        ReflectionTestUtils.setField(config, "staticPath", "/static");
        Class<?> expected = method == null || method.isEmpty() || method.equals("default")
                ? LocalImageFilePathUtils.class : CloudFilePathUtils.class;
        assertInstanceOf(expected, config.img());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"inventory-sku"})
    void inventorySkuFallsBackOnlyForNullOrEmpty(String sku) throws Exception {
        ReadableInventoryMapper mapper = new ReadableInventoryMapper();
        PricingService pricing = mock(PricingService.class);
        FinalPrice price = new FinalPrice();
        price.setStringPrice("10.00");
        when(pricing.calculateProductPrice(any(ProductAvailability.class))).thenReturn(price);
        ReflectionTestUtils.setField(mapper, "pricingService", pricing);
        ReflectionTestUtils.setField(mapper, "readableMerchantStorePopulator", mock(ReadableMerchantStorePopulator.class));
        ProductAvailability source = new ProductAvailability();
        Product product = new Product();
        product.setSku("product-sku");
        source.setProduct(product);
        source.setSku(sku);
        var result = mapper.convert(source, new MerchantStore(), new Language());
        assertEquals(sku == null || sku.isEmpty() ? "product-sku" : sku, result.getSku());
        assertEquals("10.00", result.getPrice());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"OTHER"})
    void storeCodeDefaultsOnlyForNullOrEmpty(String code) throws Exception {
        StoreFacadeImpl facade = new StoreFacadeImpl();
        MerchantStoreService service = mock(MerchantStoreService.class);
        MerchantStore expected = new MerchantStore();
        String lookup = code == null || code.isEmpty() ? "DEFAULT" : code;
        when(service.getByCode(lookup)).thenReturn(expected);
        ReflectionTestUtils.setField(facade, "merchantStoreService", service);
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (code != null) request.setParameter("store", code);
        assertSame(expected, facade.getByCode(request));
        verify(service).getByCode(lookup);
    }
}
