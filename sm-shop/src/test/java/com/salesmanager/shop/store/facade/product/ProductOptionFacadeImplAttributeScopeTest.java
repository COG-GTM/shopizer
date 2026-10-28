package com.salesmanager.shop.store.facade.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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
import com.salesmanager.shop.model.catalog.product.attribute.api.ReadableProductAttributeEntity;
import com.salesmanager.shop.model.entity.CodeEntity;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class ProductOptionFacadeImplAttributeScopeTest {

	private static final long PRODUCT_ID = 10L;
	private static final long OTHER_PRODUCT_ID = 20L;
	private static final long ATTRIBUTE_ID = 100L;

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

	private final MerchantStore acme = store(1);
	private final MerchantStore globex = store(2);

	@Test
	void saveAttributeRejectsMissingProductBeforeMappingOrSaving() throws Exception {
		when(productService.getById(PRODUCT_ID)).thenReturn(null);

		assertThrows(ResourceNotFoundException.class,
				() -> facade.saveAttribute(PRODUCT_ID, attribute(null, null), acme, null));

		verifyNoInteractions(persistableProductAttributeMapper);
		verify(productAttributeService, never()).saveOrUpdate(any());
	}

	@Test
	void saveAttributeRejectsProductWithoutStore() throws Exception {
		Product product = product(PRODUCT_ID, null);
		when(productService.getById(PRODUCT_ID)).thenReturn(product);

		assertThrows(ResourceNotFoundException.class,
				() -> facade.saveAttribute(PRODUCT_ID, attribute(null, null), acme, null));

		verifyNoInteractions(persistableProductAttributeMapper);
		verify(productAttributeService, never()).saveOrUpdate(any());
	}

	@Test
	void updateRejectsMissingAttribute() throws Exception {
		when(productService.getById(PRODUCT_ID)).thenReturn(product(PRODUCT_ID, acme));
		when(productAttributeService.getById(ATTRIBUTE_ID)).thenReturn(null);

		assertThrows(ResourceNotFoundException.class,
				() -> facade.saveAttribute(PRODUCT_ID, attribute(ATTRIBUTE_ID, null), acme, null));

		verifyNoInteractions(persistableProductAttributeMapper);
		verify(productAttributeService, never()).saveOrUpdate(any());
	}

	@Test
	void updateRejectsAttributeWithoutProduct() throws Exception {
		when(productService.getById(PRODUCT_ID)).thenReturn(product(PRODUCT_ID, acme));
		ProductAttribute existing = new ProductAttribute();
		when(productAttributeService.getById(ATTRIBUTE_ID)).thenReturn(existing);

		assertThrows(ResourceNotFoundException.class,
				() -> facade.saveAttribute(PRODUCT_ID, attribute(ATTRIBUTE_ID, null), acme, null));

		verifyNoInteractions(persistableProductAttributeMapper);
		verify(productAttributeService, never()).saveOrUpdate(any());
	}

	@Test
	void updateRejectsAttributeForAnotherProductInSameStore() throws Exception {
		when(productService.getById(PRODUCT_ID)).thenReturn(product(PRODUCT_ID, acme));
		ProductAttribute existing = new ProductAttribute();
		existing.setProduct(product(OTHER_PRODUCT_ID, acme));
		when(productAttributeService.getById(ATTRIBUTE_ID)).thenReturn(existing);

		assertThrows(ResourceNotFoundException.class,
				() -> facade.saveAttribute(PRODUCT_ID, attribute(ATTRIBUTE_ID, null), acme, null));

		verifyNoInteractions(persistableProductAttributeMapper);
		verify(productAttributeService, never()).saveOrUpdate(any());
	}

	@Test
	void updateUsesFetchedAttributeAndKeepsUrlProductAfterMerge() throws Exception {
		Product scopedProduct = product(PRODUCT_ID, acme);
		Product otherProduct = product(OTHER_PRODUCT_ID, acme);
		ProductAttribute existing = new ProductAttribute();
		existing.setId(ATTRIBUTE_ID);
		existing.setProduct(scopedProduct);
		ProductAttribute mapped = new ProductAttribute();
		mapped.setId(ATTRIBUTE_ID);
		mapped.setProduct(otherProduct);
		PersistableProductAttribute body = attribute(ATTRIBUTE_ID, null);
		when(productService.getById(PRODUCT_ID)).thenReturn(scopedProduct);
		when(productAttributeService.getById(ATTRIBUTE_ID)).thenReturn(existing, mapped);
		when(persistableProductAttributeMapper.merge(body, existing, acme, null)).thenReturn(mapped);
		when(readableProductAttributeMapper.convert(mapped, acme, null))
				.thenReturn(new ReadableProductAttributeEntity());

		facade.saveAttribute(PRODUCT_ID, body, acme, null);

		verify(persistableProductAttributeMapper).merge(body, existing, acme, null);
		ArgumentCaptor<ProductAttribute> saved = ArgumentCaptor.forClass(ProductAttribute.class);
		verify(productAttributeService).saveOrUpdate(saved.capture());
		assertSame(scopedProduct, saved.getValue().getProduct());
	}

	@Test
	void createAttributesRejectsNullAttributesWithJsoupValidateException() {
		assertThrows(IllegalArgumentException.class,
				() -> facade.createAttributes(null, PRODUCT_ID, acme));
	}

	@Test
	void createAttributesRejectsMissingProductBeforeConvertingOrSaving() throws Exception {
		when(productService.getById(PRODUCT_ID)).thenReturn(null);

		assertThrows(ResourceNotFoundException.class,
				() -> facade.createAttributes(Collections.singletonList(attribute(null, null)), PRODUCT_ID, acme));

		verifyNoInteractions(persistableProductAttributeMapper);
		verify(productAttributeService, never()).saveAll(anyIterable());
	}

	@Test
	void createAttributesScopesEveryBodyAndSavesThemOnTheUrlProduct() throws Exception {
		Product scopedProduct = product(PRODUCT_ID, acme);
		scopedProduct.setAttributes(new HashSet<>());
		PersistableProductAttribute bodyWithOtherProduct = attribute(null, OTHER_PRODUCT_ID);
		PersistableProductAttribute bodyWithoutProduct = attribute(null, null);
		ProductAttribute first = converted("color");
		ProductAttribute second = converted("size");
		when(productService.getById(PRODUCT_ID)).thenReturn(scopedProduct);
		when(persistableProductAttributeMapper.convert(bodyWithOtherProduct, acme, null)).thenReturn(first);
		when(persistableProductAttributeMapper.convert(bodyWithoutProduct, acme, null)).thenReturn(second);

		List<CodeEntity> result = facade.createAttributes(
				Arrays.asList(bodyWithOtherProduct, bodyWithoutProduct), PRODUCT_ID, acme);

		assertEquals(Long.valueOf(PRODUCT_ID), bodyWithOtherProduct.getProductId());
		assertEquals(Long.valueOf(PRODUCT_ID), bodyWithoutProduct.getProductId());
		verify(persistableProductAttributeMapper).convert(bodyWithOtherProduct, acme, null);
		verify(persistableProductAttributeMapper).convert(bodyWithoutProduct, acme, null);
		ArgumentCaptor<List<ProductAttribute>> saved = ArgumentCaptor.forClass(List.class);
		verify(productAttributeService).saveAll(saved.capture());
		assertEquals(2, saved.getValue().size());
		assertSame(scopedProduct, saved.getValue().get(0).getProduct());
		assertSame(scopedProduct, saved.getValue().get(1).getProduct());
		verify(productService).save(scopedProduct);
		assertEquals(2, scopedProduct.getAttributes().size());
		assertEquals(Arrays.asList("color", "size"),
				Arrays.asList(result.get(0).getCode(), result.get(1).getCode()));
	}

	@Test
	void getAttributeRejectsAnotherStoresProduct() {
		ProductAttribute existing = new ProductAttribute();
		existing.setProduct(product(PRODUCT_ID, globex));
		when(productAttributeService.getById(ATTRIBUTE_ID)).thenReturn(existing);

		assertThrows(ResourceNotFoundException.class,
				() -> facade.getAttribute(PRODUCT_ID, ATTRIBUTE_ID, acme, null));
	}

	@Test
	void deleteAttributeRejectsAnotherStoresProduct() throws Exception {
		ProductAttribute existing = new ProductAttribute();
		existing.setProduct(product(PRODUCT_ID, globex));
		when(productAttributeService.getById(ATTRIBUTE_ID)).thenReturn(existing);

		assertThrows(ResourceNotFoundException.class,
				() -> facade.deleteAttribute(PRODUCT_ID, ATTRIBUTE_ID, acme));

		verify(productAttributeService, never()).delete(existing);
	}

	private static ProductAttribute converted(String optionCode) {
		ProductAttribute attribute = new ProductAttribute();
		ProductOption option = new ProductOption();
		option.setCode(optionCode);
		attribute.setProductOption(option);
		return attribute;
	}

	private static MerchantStore store(int id) {
		MerchantStore store = new MerchantStore();
		store.setId(id);
		return store;
	}

	private static Product product(long id, MerchantStore store) {
		Product product = new Product();
		product.setId(id);
		product.setMerchantStore(store);
		return product;
	}

	private static PersistableProductAttribute attribute(Long attributeId, Long productId) {
		PersistableProductAttribute attribute = new PersistableProductAttribute();
		attribute.setId(attributeId);
		attribute.setProductId(productId);
		ProductPropertyOption option = new ProductPropertyOption();
		option.setCode("color");
		attribute.setOption(option);
		PersistableProductOptionValue value = new PersistableProductOptionValue();
		value.setCode("red");
		attribute.setOptionValue(value);
		return attribute;
	}
}
