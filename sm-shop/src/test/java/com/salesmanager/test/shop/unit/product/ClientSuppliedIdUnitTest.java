package com.salesmanager.test.shop.unit.product;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.services.catalog.product.attribute.ProductOptionService;
import com.salesmanager.core.business.services.catalog.product.attribute.ProductOptionSetService;
import com.salesmanager.core.business.services.catalog.product.type.ProductTypeService;
import com.salesmanager.core.business.services.catalog.product.variant.ProductVariantGroupService;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionSet;
import com.salesmanager.core.model.catalog.product.type.ProductType;
import com.salesmanager.core.model.catalog.product.variant.ProductVariantGroup;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.mapper.catalog.PersistableProductOptionSetMapper;
import com.salesmanager.shop.mapper.catalog.PersistableProductTypeMapper;
import com.salesmanager.shop.mapper.catalog.product.PersistableProductVariantGroupMapper;
import com.salesmanager.shop.model.catalog.product.attribute.optionset.PersistableProductOptionSet;
import com.salesmanager.shop.model.catalog.product.type.PersistableProductType;
import com.salesmanager.shop.model.catalog.product.product.variantGroup.PersistableProductVariantGroup;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.facade.product.ProductOptionSetFacadeImpl;
import com.salesmanager.shop.store.facade.product.ProductTypeFacadeImpl;
import com.salesmanager.shop.store.facade.product.ProductVariantGroupFacadeImpl;

public class ClientSuppliedIdUnitTest {
    private final MerchantStore store = new MerchantStore();
    private final Language language = new Language();

    private PersistableProductOptionSet body(Long id) {
        PersistableProductOptionSet body = new PersistableProductOptionSet();
        body.setId(id);
        body.setCode("IDTEST");
        body.setOption(3L);
        return body;
    }

    private PersistableProductOptionSetMapper mapper() {
        PersistableProductOptionSetMapper mapper = new PersistableProductOptionSetMapper();
        ReflectionTestUtils.setField(mapper, "productOptionService", mock(ProductOptionService.class));
        return mapper;
    }

    @Test
    public void optionSetConvertIgnoresBodyId() {
        assertNull(mapper().convert(body(91L), store, language).getId());
    }

    @Test
    public void optionSetMergePreservesDestinationId() {
        ProductOptionSet destination = new ProductOptionSet();
        destination.setId(42L);
        assertEquals(Long.valueOf(42L), mapper().merge(body(91L), destination, store, language).getId());
    }

    @Test
    public void optionSetConvertAcceptsAbsentId() {
        assertNull(mapper().convert(body(null), store, language).getId());
    }

    private ProductOptionSetFacadeImpl optionFacade(ProductOptionSetService service,
            PersistableProductOptionSetMapper mapper) {
        ProductOptionSetFacadeImpl facade = new ProductOptionSetFacadeImpl();
        ReflectionTestUtils.setField(facade, "productOptionSetService", service);
        ReflectionTestUtils.setField(facade, "persistableProductOptionSetMapper", mapper);
        return facade;
    }

    @Test
    public void optionSetCreateClearsBodyIdBeforeSaving() throws Exception {
        ProductOptionSetService service = mock(ProductOptionSetService.class);
        PersistableProductOptionSetMapper mapper = mock(PersistableProductOptionSetMapper.class);
        when(mapper.convert(any(), eq(store), eq(language))).thenAnswer(invocation -> {
            ProductOptionSet model = new ProductOptionSet();
            model.setId(((PersistableProductOptionSet) invocation.getArgument(0)).getId());
            return model;
        });
        optionFacade(service, mapper).create(body(91L), store, language);
        org.mockito.ArgumentCaptor<ProductOptionSet> saved = org.mockito.ArgumentCaptor.forClass(ProductOptionSet.class);
        verify(service).create(saved.capture());
        assertNull(saved.getValue().getId());
        assertSame(store, saved.getValue().getStore());
    }

    @Test
    public void optionSetUpdateUsesStoreScopedRowId() throws Exception {
        ProductOptionSetService service = mock(ProductOptionSetService.class);
        ProductOptionSet existing = new ProductOptionSet();
        existing.setId(42L);
        existing.setCode("ORIGINAL");
        when(service.getById(store, 42L, language)).thenReturn(existing);
        ProductOptionSetFacadeImpl facade = optionFacade(service, mapper());
        facade.update(42L, body(91L), store, language);
        org.mockito.ArgumentCaptor<ProductOptionSet> saved = org.mockito.ArgumentCaptor.forClass(ProductOptionSet.class);
        verify(service).save(saved.capture());
        assertEquals(Long.valueOf(42L), saved.getValue().getId());
        assertEquals("ORIGINAL", saved.getValue().getCode());
        assertSame(store, saved.getValue().getStore());
    }

    @Test
    public void optionSetUpdateRejectsMissingStoreScopedRow() throws Exception {
        ProductOptionSetService service = mock(ProductOptionSetService.class);
        try {
            optionFacade(service, mapper()).update(42L, body(91L), store, language);
            fail("Expected a store-scoped not-found error");
        } catch (ResourceNotFoundException expected) {
            verify(service, never()).save(any(ProductOptionSet.class));
            verify(service).getById(store, 42L, language);
        }
    }

    @Test
    public void productTypeCreateClearsBodyIdBeforeSaving() throws Exception {
        ProductTypeService service = mock(ProductTypeService.class);
        ProductTypeFacadeImpl facade = new ProductTypeFacadeImpl();
        ReflectionTestUtils.setField(facade, "productTypeService", service);
        ReflectionTestUtils.setField(facade, "persistableProductTypeMapper", new PersistableProductTypeMapper());
        when(service.saveOrUpdate(any(ProductType.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PersistableProductType body = new PersistableProductType();
        body.setId(91L);
        body.setCode("TYPEIDTEST");
        facade.save(body, store, language);
        org.mockito.ArgumentCaptor<ProductType> saved = org.mockito.ArgumentCaptor.forClass(ProductType.class);
        verify(service).saveOrUpdate(saved.capture());
        assertNull(saved.getValue().getId());
    }

    @Test
    public void variantGroupCreateClearsBodyIdBeforeSaving() throws Exception {
        ProductVariantGroupService service = mock(ProductVariantGroupService.class);
        PersistableProductVariantGroupMapper mapper = mock(PersistableProductVariantGroupMapper.class);
        when(mapper.convert(any(), eq(store), eq(language))).thenAnswer(invocation -> {
            ProductVariantGroup model = new ProductVariantGroup();
            model.setId(((PersistableProductVariantGroup) invocation.getArgument(0)).getId());
            return model;
        });
        when(service.saveOrUpdate(any(ProductVariantGroup.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ProductVariantGroupFacadeImpl facade = new ProductVariantGroupFacadeImpl();
        ReflectionTestUtils.setField(facade, "productVariantGroupService", service);
        ReflectionTestUtils.setField(facade, "persistableProductIntanceGroupMapper", mapper);
        PersistableProductVariantGroup body = new PersistableProductVariantGroup();
        body.setId(91L);
        facade.create(body, store, language);
        org.mockito.ArgumentCaptor<ProductVariantGroup> saved = org.mockito.ArgumentCaptor.forClass(ProductVariantGroup.class);
        verify(service).saveOrUpdate(saved.capture());
        assertNull(saved.getValue().getId());
    }
}
