package com.salesmanager.shop.mapper.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

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
import com.salesmanager.shop.model.catalog.product.attribute.ProductOptionValueDescription;
import com.salesmanager.shop.model.catalog.product.attribute.ProductPropertyOption;
import com.salesmanager.shop.store.api.exception.ConversionRuntimeException;

@ExtendWith(MockitoExtension.class)
class PersistableProductAttributeMapperStoreScopeTest {

	@Mock
	private ProductOptionService productOptionService;
	@Mock
	private ProductOptionValueService productOptionValueService;
	@Mock
	private ProductService productService;
	@Mock
	private PersistableProductOptionValueMapper persistableProductOptionValueMapper;

	@InjectMocks
	private PersistableProductAttributeMapper mapper;

	private final MerchantStore acme = store(1);
	private final MerchantStore globex = store(2);

	@Test
	void rejectsOptionIdFromAnotherStoreBeforeLoadingValuesOrProduct() {
		ProductOption option = option(globex);
		option.setId(4L);
		PersistableProductAttribute source = attribute(null);
		source.getOption().setCode(null);
		source.getOption().setId(4L);
		when(productOptionService.getById(4L)).thenReturn(option);

		assertThrows(ConversionRuntimeException.class,
				() -> mapper.merge(source, new ProductAttribute(), acme, null));

		verify(productOptionValueService, never()).getByCode(acme, "red");
		verify(persistableProductOptionValueMapper, never()).merge(source.getOptionValue(), null, acme, null);
		verify(productService, never()).getById(10L);
	}

	@Test
	void rejectsOptionWithoutStore() {
		when(productOptionService.getByCode(acme, "color")).thenReturn(option(null));

		assertThrows(ConversionRuntimeException.class,
				() -> mapper.merge(attribute(null), new ProductAttribute(), acme, null));

		verify(productOptionValueService, never()).getByCode(acme, "red");
	}

	@Test
	void rejectsOptionValueFromAnotherStoreWithoutDescriptions() {
		when(productOptionService.getByCode(acme, "color")).thenReturn(option(acme));
		when(productOptionValueService.getByCode(acme, "red")).thenReturn(optionValue(globex));

		assertThrows(ConversionRuntimeException.class,
				() -> mapper.merge(attribute(null), new ProductAttribute(), acme, null));

		verify(persistableProductOptionValueMapper, never()).merge(sourceValue(), null, acme, null);
		verify(productService, never()).getById(10L);
	}

	@Test
	void rejectsOptionValueFromAnotherStoreBeforeMergingDescriptions() {
		when(productOptionService.getByCode(acme, "color")).thenReturn(option(acme));
		when(productOptionValueService.getByCode(acme, "red")).thenReturn(optionValue(globex));
		PersistableProductAttribute source = attribute(null);
		source.getOptionValue().getDescriptions().add(new ProductOptionValueDescription());

		assertThrows(ConversionRuntimeException.class,
				() -> mapper.merge(source, new ProductAttribute(), acme, null));

		verify(persistableProductOptionValueMapper, never())
				.merge(source.getOptionValue(), null, acme, null);
	}

	@Test
	void rejectsMissingBodyProduct() {
		stubOwnOptionAndValue();
		when(productService.getById(10L)).thenReturn(null);

		assertThrows(ConversionRuntimeException.class,
				() -> mapper.merge(attribute(10L), new ProductAttribute(), acme, null));
	}

	@Test
	void rejectsBodyProductWithoutStore() {
		stubOwnOptionAndValue();
		when(productService.getById(10L)).thenReturn(product(10L, null));

		assertThrows(ConversionRuntimeException.class,
				() -> mapper.merge(attribute(10L), new ProductAttribute(), acme, null));
	}

	@Test
	void copiesAttributeFieldsAndKeepsExistingId() {
		Product scopedProduct = product(10L, acme);
		when(productService.getById(10L)).thenReturn(scopedProduct);
		ProductOption option = option(acme);
		ProductOptionValue value = optionValue(acme);
		when(productOptionService.getByCode(acme, "color")).thenReturn(option);
		when(productOptionValueService.getByCode(acme, "red")).thenReturn(value);
		PersistableProductAttribute source = attribute(10L);
		source.setProductAttributePrice(new BigDecimal("12.50"));
		source.setProductAttributeWeight(new BigDecimal("0.75"));
		source.setAttributeDisplayOnly(true);
		ProductAttribute destination = new ProductAttribute();
		destination.setId(88L);

		ProductAttribute result = mapper.merge(source, destination, acme, null);

		assertSame(scopedProduct, result.getProduct());
		assertSame(option, result.getProductOption());
		assertSame(value, result.getProductOptionValue());
		assertEquals(new BigDecimal("12.50"), result.getProductAttributePrice());
		assertEquals(new BigDecimal("0.75"), result.getProductAttributeWeight());
		assertEquals(true, result.getAttributeDisplayOnly());
		assertEquals(Long.valueOf(88L), result.getId());
		ProductAttribute newDestination = mapper.merge(source, new ProductAttribute(), acme, null);
		assertNull(newDestination.getId());
	}

	@Test
	void textValueCreatesDisplayOnlyValueForCurrentStore() {
		when(productOptionService.getByCode(acme, "color")).thenReturn(option(acme));
		PersistableProductAttribute source = attribute(null);
		source.getOptionValue().setCode(null);
		source.getOptionValue().setId(null);
		source.setAttributeDisplayOnly(true);

		ProductAttribute result = mapper.merge(source, new ProductAttribute(), acme, null);

		assertSame(acme, result.getProductOptionValue().getMerchantStore());
		assertEquals(true, result.getProductOptionValue().isProductOptionDisplayOnly());
		assertFalse(result.getProductOptionValue().getCode().isEmpty());
		verify(productService, never()).getById(10L);
	}

	private void stubOwnOptionAndValue() {
		when(productOptionService.getByCode(acme, "color")).thenReturn(option(acme));
		when(productOptionValueService.getByCode(acme, "red")).thenReturn(optionValue(acme));
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

	private static ProductOption option(MerchantStore store) {
		ProductOption option = new ProductOption();
		option.setCode("color");
		option.setMerchantStore(store);
		return option;
	}

	private static ProductOptionValue optionValue(MerchantStore store) {
		ProductOptionValue value = new ProductOptionValue();
		value.setCode("red");
		value.setMerchantStore(store);
		return value;
	}

	private static PersistableProductAttribute attribute(Long productId) {
		PersistableProductAttribute attribute = new PersistableProductAttribute();
		attribute.setProductId(productId);
		ProductPropertyOption option = new ProductPropertyOption();
		option.setCode("color");
		attribute.setOption(option);
		attribute.setOptionValue(sourceValue());
		return attribute;
	}

	private static PersistableProductOptionValue sourceValue() {
		PersistableProductOptionValue value = new PersistableProductOptionValue();
		value.setCode("red");
		return value;
	}
}
