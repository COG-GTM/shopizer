package com.salesmanager.shop.mapper.catalog;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
class PersistableProductAttributeMapperStoreTest {

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
	void rejectsProductOfAnotherStore() {
		stubOwnOptionAndValue();
		when(productService.getById(20L)).thenReturn(product(20L, globex));

		assertThrows(ConversionRuntimeException.class,
				() -> mapper.merge(attribute(20L), new ProductAttribute(), acme, null));
	}

	@Test
	void attachesProductOfOwnStore() {
		stubOwnOptionAndValue();
		Product acmeProduct = product(10L, acme);
		when(productService.getById(10L)).thenReturn(acmeProduct);

		ProductAttribute result = mapper.merge(attribute(10L), new ProductAttribute(), acme, null);

		assertSame(acmeProduct, result.getProduct());
	}

	@Test
	void doesNotModifyAnotherStoresOptionValue() throws Exception {
		when(productOptionService.getByCode(acme, "color")).thenReturn(option(acme));
		when(productOptionValueService.getByCode(acme, "red")).thenReturn(optionValue(globex));
		PersistableProductAttribute source = attribute(10L);
		source.getOptionValue().getDescriptions().add(new ProductOptionValueDescription());

		assertThrows(ConversionRuntimeException.class,
				() -> mapper.merge(source, new ProductAttribute(), acme, null));

		verify(productOptionValueService, never()).saveOrUpdate(any());
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
		PersistableProductOptionValue value = new PersistableProductOptionValue();
		value.setCode("red");
		attribute.setOptionValue(value);
		return attribute;
	}
}
