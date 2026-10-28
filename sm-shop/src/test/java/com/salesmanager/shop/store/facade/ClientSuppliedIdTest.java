package com.salesmanager.shop.store.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.catalog.product.price.ProductPriceService;
import com.salesmanager.core.business.services.catalog.product.variant.ProductVariantGroupService;
import com.salesmanager.core.business.services.catalog.product.variant.ProductVariantImageService;
import com.salesmanager.core.business.services.catalog.product.variant.ProductVariantService;
import com.salesmanager.core.business.services.catalog.product.variation.ProductVariationService;
import com.salesmanager.core.business.services.tax.TaxRateService;
import com.salesmanager.core.model.catalog.product.variant.ProductVariantGroup;
import com.salesmanager.core.model.catalog.product.variation.ProductVariation;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.tax.taxrate.TaxRate;
import com.salesmanager.shop.mapper.catalog.PersistableProductVariationMapper;
import com.salesmanager.shop.mapper.catalog.product.PersistableProductMapper;
import com.salesmanager.shop.mapper.catalog.product.PersistableProductVariantGroupMapper;
import com.salesmanager.shop.mapper.inventory.PersistableProductPriceMapper;
import com.salesmanager.shop.mapper.tax.PersistableTaxRateMapper;
import com.salesmanager.shop.model.catalog.product.PersistableProductPrice;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.variantGroup.PersistableProductVariantGroup;
import com.salesmanager.shop.model.catalog.product.variation.PersistableProductVariation;
import com.salesmanager.shop.model.tax.PersistableTaxRate;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.facade.product.ProductCommonFacadeImpl;
import com.salesmanager.shop.store.facade.product.ProductPriceFacadeImpl;
import com.salesmanager.shop.store.facade.product.ProductVariantGroupFacadeImpl;
import com.salesmanager.shop.store.facade.product.ProductVariationFacadeImpl;
import com.salesmanager.shop.store.facade.tax.TaxFacadeImpl;

/**
 * Create endpoints must never let a request body id turn an insert into an
 * update of another store's row.
 */
class ClientSuppliedIdTest {

	private static final Long OTHER_STORE_ROW_ID = 42L;

	private MerchantStore store;
	private Language language;

	@BeforeEach
	void setUp() {
		store = new MerchantStore();
		store.setId(2);
		store.setCode("GLOBEX");
		language = new Language();
	}

	@Test
	void variantGroupCreateIgnoresClientSuppliedId() throws Exception {
		ProductVariantService variantService = mock(ProductVariantService.class);
		ProductVariantImageService imageService = mock(ProductVariantImageService.class);
		ProductVariantGroupService groupService = mock(ProductVariantGroupService.class);
		when(variantService.getByIds(anyList(), eq(store))).thenReturn(Collections.emptyList());
		when(groupService.saveOrUpdate(any(ProductVariantGroup.class))).thenAnswer(i -> i.getArgument(0));

		PersistableProductVariantGroupMapper mapper = new PersistableProductVariantGroupMapper();
		ReflectionTestUtils.setField(mapper, "productVariantService", variantService);
		ReflectionTestUtils.setField(mapper, "productVariantImageService", imageService);

		ProductVariantGroupFacadeImpl facade = new ProductVariantGroupFacadeImpl();
		ReflectionTestUtils.setField(facade, "persistableProductIntanceGroupMapper", mapper);
		ReflectionTestUtils.setField(facade, "productVariantGroupService", groupService);

		PersistableProductVariantGroup body = new PersistableProductVariantGroup();
		body.setId(OTHER_STORE_ROW_ID);
		body.setproductVariants(Collections.singletonList(7L));

		facade.create(body, store, language);

		ArgumentCaptor<ProductVariantGroup> saved = ArgumentCaptor.forClass(ProductVariantGroup.class);
		verify(groupService).saveOrUpdate(saved.capture());
		assertNull(saved.getValue().getId());
		assertSame(store, saved.getValue().getMerchantStore());
		verify(imageService, never()).listByProductVariantGroup(anyLong(), any());
	}

	@Test
	void variationCreateIgnoresClientSuppliedId() throws Exception {
		PersistableProductVariationMapper mapper = mock(PersistableProductVariationMapper.class);
		ProductVariationService service = mock(ProductVariationService.class);
		when(mapper.convert(any(), eq(store), eq(language))).thenReturn(new ProductVariation());

		ProductVariationFacadeImpl facade = new ProductVariationFacadeImpl();
		ReflectionTestUtils.setField(facade, "persistableProductVariationMapper", mapper);
		ReflectionTestUtils.setField(facade, "productVariationService", service);

		PersistableProductVariation body = new PersistableProductVariation();
		body.setId(OTHER_STORE_ROW_ID);
		body.setCode("size-xl");

		facade.create(body, store, language);

		ArgumentCaptor<PersistableProductVariation> converted = ArgumentCaptor.forClass(PersistableProductVariation.class);
		verify(mapper).convert(converted.capture(), eq(store), eq(language));
		assertNull(converted.getValue().getId());
	}

	@Test
	void variationUpdateKeepsPathId() throws Exception {
		PersistableProductVariationMapper mapper = mock(PersistableProductVariationMapper.class);
		ProductVariationService service = mock(ProductVariationService.class);
		ProductVariation existing = new ProductVariation();
		existing.setId(5L);
		when(service.getById(store, 5L, language)).thenReturn(Optional.of(existing));
		when(mapper.merge(any(), eq(existing), eq(store), eq(language))).thenReturn(existing);

		ProductVariationFacadeImpl facade = new ProductVariationFacadeImpl();
		ReflectionTestUtils.setField(facade, "persistableProductVariationMapper", mapper);
		ReflectionTestUtils.setField(facade, "productVariationService", service);

		PersistableProductVariation body = new PersistableProductVariation();
		body.setId(OTHER_STORE_ROW_ID);
		body.setCode("size-xl");

		facade.update(5L, body, store, language);

		ArgumentCaptor<PersistableProductVariation> merged = ArgumentCaptor.forClass(PersistableProductVariation.class);
		verify(mapper).merge(merged.capture(), eq(existing), eq(store), eq(language));
		assertEquals(Long.valueOf(5L), merged.getValue().getId());
	}

	@Test
	void taxRateCreateIgnoresClientSuppliedId() throws Exception {
		PersistableTaxRateMapper mapper = mock(PersistableTaxRateMapper.class);
		TaxRateService service = mock(TaxRateService.class);
		when(mapper.convert(any(), eq(store), eq(language))).thenReturn(new TaxRate());
		when(service.saveOrUpdate(any(TaxRate.class))).thenAnswer(i -> i.getArgument(0));

		TaxFacadeImpl facade = new TaxFacadeImpl();
		ReflectionTestUtils.setField(facade, "persistableTaxRateMapper", mapper);
		ReflectionTestUtils.setField(facade, "taxRateService", service);

		PersistableTaxRate body = new PersistableTaxRate();
		body.setId(OTHER_STORE_ROW_ID);
		body.setCode("QC");

		facade.createTaxRate(body, store, language);

		ArgumentCaptor<PersistableTaxRate> converted = ArgumentCaptor.forClass(PersistableTaxRate.class);
		verify(mapper).convert(converted.capture(), eq(store), eq(language));
		assertNull(converted.getValue().getId());
	}

	@Test
	void taxRateUpdateKeepsPathId() throws Exception {
		PersistableTaxRateMapper mapper = mock(PersistableTaxRateMapper.class);
		TaxRateService service = mock(TaxRateService.class);
		TaxRate existing = new TaxRate();
		existing.setId(5L);
		when(service.getById(5L, store)).thenReturn(existing);
		when(mapper.merge(any(), eq(existing), eq(store), eq(language))).thenReturn(existing);

		TaxFacadeImpl facade = new TaxFacadeImpl();
		ReflectionTestUtils.setField(facade, "persistableTaxRateMapper", mapper);
		ReflectionTestUtils.setField(facade, "taxRateService", service);

		PersistableTaxRate body = new PersistableTaxRate();
		body.setId(OTHER_STORE_ROW_ID);
		body.setCode("QC");

		facade.updateTaxRate(5L, body, store, language);

		ArgumentCaptor<PersistableTaxRate> merged = ArgumentCaptor.forClass(PersistableTaxRate.class);
		verify(mapper).merge(merged.capture(), eq(existing), eq(store), eq(language));
		assertEquals(Long.valueOf(5L), merged.getValue().getId());
	}

	@Test
	void priceSaveRejectsPriceOfAnotherStore() throws Exception {
		PersistableProductPriceMapper mapper = mock(PersistableProductPriceMapper.class);
		ProductPriceService service = mock(ProductPriceService.class);
		when(service.findById(OTHER_STORE_ROW_ID, "SKU-1", store)).thenReturn(null);

		ProductPriceFacadeImpl facade = new ProductPriceFacadeImpl();
		ReflectionTestUtils.setField(facade, "persistableProductPriceMapper", mapper);
		ReflectionTestUtils.setField(facade, "productPriceService", service);

		PersistableProductPrice body = new PersistableProductPrice();
		body.setId(OTHER_STORE_ROW_ID);
		body.setSku("SKU-1");

		assertThrows(ResourceNotFoundException.class, () -> facade.save(body, store));
		verify(mapper, never()).convert(any(), any(), any());
	}

	@Test
	void productSaveRejectsProductOfAnotherStore() throws Exception {
		PersistableProductMapper mapper = mock(PersistableProductMapper.class);
		ProductService service = mock(ProductService.class);
		when(service.retrieveById(OTHER_STORE_ROW_ID, store)).thenReturn(Optional.empty());

		ProductCommonFacadeImpl facade = new ProductCommonFacadeImpl();
		ReflectionTestUtils.setField(facade, "persistableProductMapper", mapper);
		ReflectionTestUtils.setField(facade, "productService", service);

		PersistableProduct body = new PersistableProduct();
		body.setId(OTHER_STORE_ROW_ID);

		assertThrows(ResourceNotFoundException.class, () -> facade.saveProduct(store, body, language));
		verify(mapper, never()).merge(any(), any(), any(), any());
		verify(service, never()).getById(anyLong());
	}
}
