package com.salesmanager.shop.store.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.services.catalog.catalog.CatalogEntryService;
import com.salesmanager.core.business.services.catalog.catalog.CatalogService;
import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.catalog.product.attribute.ProductOptionSetService;
import com.salesmanager.core.business.services.catalog.product.price.ProductPriceService;
import com.salesmanager.core.business.services.catalog.product.type.ProductTypeService;
import com.salesmanager.core.business.services.tax.TaxClassService;
import com.salesmanager.core.model.catalog.catalog.Catalog;
import com.salesmanager.core.model.catalog.catalog.CatalogCategoryEntry;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionSet;
import com.salesmanager.core.model.catalog.product.price.ProductPrice;
import com.salesmanager.core.model.catalog.product.type.ProductType;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.tax.taxclass.TaxClass;
import com.salesmanager.shop.mapper.Mapper;
import com.salesmanager.shop.mapper.catalog.PersistableProductOptionSetMapper;
import com.salesmanager.shop.mapper.catalog.PersistableProductTypeMapper;
import com.salesmanager.shop.mapper.catalog.ReadableCatalogCategoryEntryMapper;
import com.salesmanager.shop.mapper.catalog.ReadableCatalogMapper;
import com.salesmanager.shop.mapper.catalog.PersistableCatalogMapper;
import com.salesmanager.shop.mapper.inventory.PersistableProductPriceMapper;
import com.salesmanager.shop.mapper.tax.PersistableTaxClassMapper;
import com.salesmanager.shop.model.catalog.catalog.PersistableCatalog;
import com.salesmanager.shop.model.catalog.catalog.PersistableCatalogCategoryEntry;
import com.salesmanager.shop.model.catalog.catalog.ReadableCatalog;
import com.salesmanager.shop.model.catalog.catalog.ReadableCatalogCategoryEntry;
import com.salesmanager.shop.model.catalog.product.PersistableProductPrice;
import com.salesmanager.shop.model.catalog.product.attribute.optionset.PersistableProductOptionSet;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.type.PersistableProductType;
import com.salesmanager.shop.model.tax.PersistableTaxClass;
import com.salesmanager.shop.store.facade.catalog.CatalogFacadeImpl;
import com.salesmanager.shop.store.facade.product.ProductCommonFacadeImpl;
import com.salesmanager.shop.store.facade.product.ProductOptionSetFacadeImpl;
import com.salesmanager.shop.store.facade.product.ProductPriceFacadeImpl;
import com.salesmanager.shop.store.facade.product.ProductTypeFacadeImpl;
import com.salesmanager.shop.store.facade.tax.TaxFacadeImpl;

class ClientSuppliedIdMoreTest {

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
	void optionSetCreateClearsBodyIdBeforeMapping() throws Exception {
		ProductOptionSetService service = mock(ProductOptionSetService.class);
		PersistableProductOptionSetMapper mapper = mock(PersistableProductOptionSetMapper.class);
		when(service.getCode(store, "OPTIONS")).thenReturn(null);
		when(mapper.convert(any(), eq(store), eq(language))).thenReturn(new ProductOptionSet());

		ProductOptionSetFacadeImpl facade = new ProductOptionSetFacadeImpl();
		ReflectionTestUtils.setField(facade, "productOptionSetService", service);
		ReflectionTestUtils.setField(facade, "persistableProductOptionSetMapper", mapper);
		PersistableProductOptionSet body = new PersistableProductOptionSet();
		body.setCode("OPTIONS");
		body.setId(42L);

		facade.create(body, store, language);

		ArgumentCaptor<PersistableProductOptionSet> converted = ArgumentCaptor.forClass(PersistableProductOptionSet.class);
		verify(mapper).convert(converted.capture(), eq(store), eq(language));
		assertNull(converted.getValue().getId());
	}

	@Test
	void productTypeSaveClearsBodyIdBeforeMapping() throws Exception {
		ProductTypeService service = mock(ProductTypeService.class);
		PersistableProductTypeMapper mapper = mock(PersistableProductTypeMapper.class);
		ProductType mapped = new ProductType();
		mapped.setId(90L);
		when(service.getByCode("TYPE", store, language)).thenReturn(null);
		when(mapper.convert(any(), eq(store), eq(language))).thenReturn(new ProductType());
		when(service.saveOrUpdate(any(ProductType.class))).thenReturn(mapped);

		ProductTypeFacadeImpl facade = new ProductTypeFacadeImpl();
		ReflectionTestUtils.setField(facade, "productTypeService", service);
		ReflectionTestUtils.setField(facade, "persistableProductTypeMapper", mapper);
		PersistableProductType body = new PersistableProductType();
		body.setCode("TYPE");
		body.setId(42L);

		facade.save(body, store, language);

		ArgumentCaptor<PersistableProductType> converted = ArgumentCaptor.forClass(PersistableProductType.class);
		verify(mapper).convert(converted.capture(), eq(store), eq(language));
		assertNull(converted.getValue().getId());
	}

	@Test
	void catalogCreateClearsBodyIdBeforeMapping() {
		CatalogService service = mock(CatalogService.class);
		PersistableCatalogMapper mapper = mock(PersistableCatalogMapper.class);
		ReadableCatalogMapper readableMapper = mock(ReadableCatalogMapper.class);
		Catalog mapped = new Catalog();
		mapped.setCode("CATALOG");
		when(service.existByCode("CATALOG", store)).thenReturn(false);
		when(mapper.convert(any(), eq(store), eq(language))).thenReturn(mapped);
		when(service.getByCode("CATALOG", store)).thenReturn(Optional.of(mapped));
		when(readableMapper.convert(mapped, store, language)).thenReturn(new ReadableCatalog());

		CatalogFacadeImpl facade = new CatalogFacadeImpl();
		ReflectionTestUtils.setField(facade, "catalogService", service);
		ReflectionTestUtils.setField(facade, "persistableCatalogMapper", mapper);
		ReflectionTestUtils.setField(facade, "readableCatalogMapper", readableMapper);
		PersistableCatalog body = new PersistableCatalog();
		body.setCode("CATALOG");
		body.setId(42L);

		facade.saveCatalog(body, store, language);

		ArgumentCaptor<PersistableCatalog> converted = ArgumentCaptor.forClass(PersistableCatalog.class);
		verify(mapper).convert(converted.capture(), eq(store), eq(language));
		assertNull(converted.getValue().getId());
	}

	@Test
	void catalogEntryCreateClearsBodyIdBeforeMapping() {
		CatalogService catalogService = mock(CatalogService.class);
		CatalogEntryService entryService = mock(CatalogEntryService.class);
		@SuppressWarnings("unchecked")
		Mapper<PersistableCatalogCategoryEntry, CatalogCategoryEntry> mapper = mock(Mapper.class);
		ReadableCatalogCategoryEntryMapper readableMapper = mock(ReadableCatalogCategoryEntryMapper.class);
		Catalog catalog = new Catalog();
		CatalogCategoryEntry mapped = new CatalogCategoryEntry();
		when(catalogService.getByCode("CATALOG", store)).thenReturn(Optional.of(catalog));
		when(mapper.convert(any(), eq(store), eq(language))).thenReturn(mapped);
		when(readableMapper.convert(mapped, store, language)).thenReturn(new ReadableCatalogCategoryEntry());

		CatalogFacadeImpl facade = new CatalogFacadeImpl();
		ReflectionTestUtils.setField(facade, "catalogService", catalogService);
		ReflectionTestUtils.setField(facade, "catalogEntryService", entryService);
		ReflectionTestUtils.setField(facade, "persistableCatalogEntryMapper", mapper);
		ReflectionTestUtils.setField(facade, "readableCatalogEntryMapper", readableMapper);
		PersistableCatalogCategoryEntry body = new PersistableCatalogCategoryEntry();
		body.setCatalog("CATALOG");
		body.setId(42L);

		facade.addCatalogEntry(body, store, language);

		ArgumentCaptor<PersistableCatalogCategoryEntry> converted = ArgumentCaptor.forClass(PersistableCatalogCategoryEntry.class);
		verify(mapper).convert(converted.capture(), eq(store), eq(language));
		assertNull(converted.getValue().getId());
	}

	@Test
	void taxClassCreateClearsBodyIdBeforeMapping() throws Exception {
		TaxClassService service = mock(TaxClassService.class);
		PersistableTaxClassMapper mapper = mock(PersistableTaxClassMapper.class);
		when(service.exists("TAX", store)).thenReturn(false);
		when(mapper.convert(any(), eq(store), eq(language))).thenReturn(new TaxClass());
		when(service.saveOrUpdate(any(TaxClass.class))).thenReturn(new TaxClass());

		TaxFacadeImpl facade = new TaxFacadeImpl();
		ReflectionTestUtils.setField(facade, "taxClassService", service);
		ReflectionTestUtils.setField(facade, "persistableTaxClassMapper", mapper);
		PersistableTaxClass body = new PersistableTaxClass();
		body.setCode("TAX");
		body.setId(42L);

		facade.createTaxClass(body, store, language);

		ArgumentCaptor<PersistableTaxClass> converted = ArgumentCaptor.forClass(PersistableTaxClass.class);
		verify(mapper).convert(converted.capture(), eq(store), eq(language));
		assertNull(converted.getValue().getId());
	}

	@Test
	void taxClassUpdateUsesPathIdForMapping() throws Exception {
		TaxClassService service = mock(TaxClassService.class);
		PersistableTaxClassMapper mapper = mock(PersistableTaxClassMapper.class);
		TaxClass existing = new TaxClass();
		existing.setMerchantStore(store);
		when(service.getById(5L)).thenReturn(existing);
		when(mapper.convert(any(), eq(store), eq(language))).thenReturn(existing);

		TaxFacadeImpl facade = new TaxFacadeImpl();
		ReflectionTestUtils.setField(facade, "taxClassService", service);
		ReflectionTestUtils.setField(facade, "persistableTaxClassMapper", mapper);
		PersistableTaxClass body = new PersistableTaxClass();
		body.setId(42L);
		body.setCode("TAX");

		facade.updateTaxClass(5L, body, store, language);

		ArgumentCaptor<PersistableTaxClass> converted = ArgumentCaptor.forClass(PersistableTaxClass.class);
		verify(mapper).convert(converted.capture(), eq(store), eq(language));
		assertEquals(5L, converted.getValue().getId());
	}

	@Test
	void priceCreateWithoutIdSkipsLookupAndSaves() throws Exception {
		ProductPriceService service = mock(ProductPriceService.class);
		PersistableProductPriceMapper mapper = mock(PersistableProductPriceMapper.class);
		ProductPrice mapped = priceModel(null);
		mapped.setId(70L);
		when(mapper.convert(any(), eq(store), any())).thenReturn(mapped);
		when(service.saveOrUpdate(mapped)).thenReturn(mapped);
		ProductPriceFacadeImpl facade = priceFacade(service, mapper);
		PersistableProductPrice body = new PersistableProductPrice();
		body.setSku("SKU-NULL");

		assertEquals(70L, facade.save(body, store));

		verify(service, never()).findById(anyLong(), anyString(), any());
		verify(service).saveOrUpdate(mapped);
	}

	@Test
	void priceUpdateForSameStoreLoadsAndSaves() throws Exception {
		ProductPriceService service = mock(ProductPriceService.class);
		PersistableProductPriceMapper mapper = mock(PersistableProductPriceMapper.class);
		ProductPrice existing = priceModel(7L);
		when(service.findById(7L, "SKU-7", store)).thenReturn(existing);
		when(mapper.convert(any(), eq(store), any())).thenReturn(existing);
		when(service.saveOrUpdate(existing)).thenReturn(existing);
		ProductPriceFacadeImpl facade = priceFacade(service, mapper);
		PersistableProductPrice body = new PersistableProductPrice();
		body.setId(7L);
		body.setSku("SKU-7");

		assertEquals(7L, facade.save(body, store));

		verify(service).findById(7L, "SKU-7", store);
		verify(service).saveOrUpdate(existing);
	}

	@Test
	void zeroPriceIdIsTreatedAsCreate() throws Exception {
		ProductPriceService service = mock(ProductPriceService.class);
		PersistableProductPriceMapper mapper = mock(PersistableProductPriceMapper.class);
		ProductPrice mapped = priceModel(null);
		mapped.setId(71L);
		when(mapper.convert(any(), eq(store), any())).thenReturn(mapped);
		when(service.saveOrUpdate(mapped)).thenReturn(mapped);
		ProductPriceFacadeImpl facade = priceFacade(service, mapper);
		PersistableProductPrice body = new PersistableProductPrice();
		body.setId(0L);
		body.setSku("SKU-ZERO");

		assertEquals(71L, facade.save(body, store));

		verify(service, never()).findById(anyLong(), anyString(), any());
		verify(service).saveOrUpdate(mapped);
	}

	@Test
	void sameStoreProductUsesScopedLookupAndMergesRetrievedProduct() throws Exception {
		ProductService service = mock(ProductService.class);
		com.salesmanager.shop.mapper.catalog.product.PersistableProductMapper mapper =
				mock(com.salesmanager.shop.mapper.catalog.product.PersistableProductMapper.class);
		Product retrieved = new Product();
		retrieved.setId(17L);
		when(service.retrieveById(17L, store)).thenReturn(Optional.of(retrieved));
		when(mapper.merge(any(), eq(retrieved), eq(store), eq(language))).thenReturn(retrieved);
		when(service.saveProduct(retrieved)).thenReturn(retrieved);

		ProductCommonFacadeImpl facade = new ProductCommonFacadeImpl();
		ReflectionTestUtils.setField(facade, "productService", service);
		ReflectionTestUtils.setField(facade, "persistableProductMapper", mapper);
		PersistableProduct body = new PersistableProduct();
		body.setId(17L);

		assertEquals(17L, facade.saveProduct(store, body, language));

		verify(service).retrieveById(17L, store);
		verify(service, never()).getById(anyLong());
		verify(mapper).merge(body, retrieved, store, language);
	}

	private ProductPriceFacadeImpl priceFacade(ProductPriceService service, PersistableProductPriceMapper mapper) {
		ProductPriceFacadeImpl facade = new ProductPriceFacadeImpl();
		ReflectionTestUtils.setField(facade, "productPriceService", service);
		ReflectionTestUtils.setField(facade, "persistableProductPriceMapper", mapper);
		return facade;
	}

	private ProductPrice priceModel(Long id) {
		ProductPrice price = new ProductPrice();
		price.setId(id);
		price.setProductAvailability(new ProductAvailability());
		return price;
	}
}
