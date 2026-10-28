package com.salesmanager.shop.store.facade.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class ProductOptionFacadeImplAttributeStoreTest {

	private static final long ACME_PRODUCT = 10L;
	private static final long GLOBEX_PRODUCT = 20L;
	private static final long GLOBEX_ATTRIBUTE = 200L;

	@Mock
	private ProductService productService;
	@Mock
	private ProductAttributeService productAttributeService;
	@Mock
	private PersistableProductAttributeMapper persistableProductAttributeMapper;
	@Mock
	private ReadableProductAttributeMapper readableProductAttributeMapper;

	@InjectMocks
	private ProductOptionFacadeImpl facade;

	private final MerchantStore acme = store(1, "ACME");
	private final MerchantStore globex = store(2, "GLOBEX");

	@Test
	void saveAttributeRejectsProductOfAnotherStore() throws Exception {
		when(productService.getById(GLOBEX_PRODUCT)).thenReturn(product(GLOBEX_PRODUCT, globex));

		assertThrows(ResourceNotFoundException.class,
				() -> facade.saveAttribute(GLOBEX_PRODUCT, attribute(null), acme, null));

		verifyNoInteractions(persistableProductAttributeMapper);
		verify(productAttributeService, never()).saveOrUpdate(any());
	}

	@Test
	void updateAttributeRejectsAttributeOfAnotherStoresProduct() throws Exception {
		Product acmeProduct = product(ACME_PRODUCT, acme);
		when(productService.getById(ACME_PRODUCT)).thenReturn(acmeProduct);
		ProductAttribute globexAttribute = new ProductAttribute();
		globexAttribute.setId(GLOBEX_ATTRIBUTE);
		globexAttribute.setProduct(product(GLOBEX_PRODUCT, globex));
		when(productAttributeService.getById(GLOBEX_ATTRIBUTE)).thenReturn(globexAttribute);

		assertThrows(ResourceNotFoundException.class,
				() -> facade.saveAttribute(ACME_PRODUCT, attribute(GLOBEX_ATTRIBUTE), acme, null));

		verifyNoInteractions(persistableProductAttributeMapper);
		verify(productAttributeService, never()).saveOrUpdate(any());
	}

	@Test
	void updateAttributeViaOtherStoresProductUrlIsRejected() throws Exception {
		when(productService.getById(GLOBEX_PRODUCT)).thenReturn(product(GLOBEX_PRODUCT, globex));

		assertThrows(ResourceNotFoundException.class,
				() -> facade.saveAttribute(GLOBEX_PRODUCT, attribute(GLOBEX_ATTRIBUTE), acme, null));

		verify(productAttributeService, never()).getById(any());
		verify(productAttributeService, never()).saveOrUpdate(any());
	}

	@Test
	void saveAttributeAttachesToOwnStoresProduct() throws Exception {
		Product acmeProduct = product(ACME_PRODUCT, acme);
		when(productService.getById(ACME_PRODUCT)).thenReturn(acmeProduct);
		ProductAttribute merged = new ProductAttribute();
		merged.setId(100L);
		when(persistableProductAttributeMapper.merge(any(), any(), any(), any())).thenReturn(merged);
		when(productAttributeService.getById(100L)).thenReturn(merged);

		facade.saveAttribute(ACME_PRODUCT, attribute(null), acme, null);

		ArgumentCaptor<ProductAttribute> saved = ArgumentCaptor.forClass(ProductAttribute.class);
		verify(productAttributeService).saveOrUpdate(saved.capture());
		assertSame(acmeProduct, saved.getValue().getProduct());
	}

	@Test
	void createAttributesRejectsProductOfAnotherStoreBeforeSaving() throws Exception {
		when(productService.getById(GLOBEX_PRODUCT)).thenReturn(product(GLOBEX_PRODUCT, globex));

		assertThrows(ResourceNotFoundException.class,
				() -> facade.createAttributes(Collections.singletonList(attribute(null)), GLOBEX_PRODUCT, acme));

		verifyNoInteractions(persistableProductAttributeMapper);
		verify(productAttributeService, never()).saveAll(anyIterable());
	}

	@Test
	void createAttributesIgnoresBodyProductId() throws Exception {
		Product acmeProduct = product(ACME_PRODUCT, acme);
		when(productService.getById(ACME_PRODUCT)).thenReturn(acmeProduct);
		PersistableProductAttribute body = attribute(null);
		body.setProductId(GLOBEX_PRODUCT);
		ProductAttribute converted = new ProductAttribute();
		ProductOption option = new ProductOption();
		option.setCode("color");
		converted.setProductOption(option);
		when(persistableProductAttributeMapper.convert(any(), any(), any())).thenReturn(converted);

		facade.createAttributes(Collections.singletonList(body), ACME_PRODUCT, acme);

		ArgumentCaptor<PersistableProductAttribute> mapped = ArgumentCaptor.forClass(PersistableProductAttribute.class);
		verify(persistableProductAttributeMapper).convert(mapped.capture(), any(), any());
		assertEquals(Long.valueOf(ACME_PRODUCT), mapped.getValue().getProductId());
		assertSame(acmeProduct, converted.getProduct());
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<ProductAttribute>> saved = ArgumentCaptor.forClass(List.class);
		verify(productAttributeService).saveAll(saved.capture());
		assertSame(acmeProduct, saved.getValue().get(0).getProduct());
	}

	private static MerchantStore store(int id, String code) {
		MerchantStore store = new MerchantStore();
		store.setId(id);
		store.setCode(code);
		return store;
	}

	private static Product product(long id, MerchantStore store) {
		Product product = new Product();
		product.setId(id);
		product.setMerchantStore(store);
		return product;
	}

	private static PersistableProductAttribute attribute(Long id) {
		PersistableProductAttribute attribute = new PersistableProductAttribute();
		attribute.setId(id);
		ProductPropertyOption option = new ProductPropertyOption();
		option.setCode("color");
		attribute.setOption(option);
		PersistableProductOptionValue value = new PersistableProductOptionValue();
		value.setCode("red");
		attribute.setOptionValue(value);
		return attribute;
	}
}
