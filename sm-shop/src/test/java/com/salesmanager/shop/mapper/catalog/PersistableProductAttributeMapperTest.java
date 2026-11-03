package com.salesmanager.shop.mapper.catalog;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.salesmanager.shop.model.catalog.product.attribute.ProductPropertyOption;
import com.salesmanager.shop.store.api.exception.ConversionRuntimeException;

@ExtendWith(MockitoExtension.class)
class PersistableProductAttributeMapperTest {

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

	@Test
	void mergeRejectsProductFromAnotherStore() {
		MerchantStore callerStore = store(1);

		ProductOption option = new ProductOption();
		option.setMerchantStore(callerStore);
		when(productOptionService.getByCode(callerStore, "color")).thenReturn(option);

		ProductOptionValue optionValue = new ProductOptionValue();
		optionValue.setMerchantStore(callerStore);
		when(productOptionValueService.getByCode(callerStore, "red")).thenReturn(optionValue);

		Product foreignProduct = new Product();
		foreignProduct.setId(20L);
		foreignProduct.setMerchantStore(store(2));
		when(productService.getById(20L)).thenReturn(foreignProduct);

		ProductPropertyOption propertyOption = new ProductPropertyOption();
		propertyOption.setCode("color");
		PersistableProductOptionValue propertyValue = new PersistableProductOptionValue();
		propertyValue.setCode("red");

		PersistableProductAttribute source = new PersistableProductAttribute();
		source.setProductId(20L);
		source.setOption(propertyOption);
		source.setOptionValue(propertyValue);

		assertThrows(ConversionRuntimeException.class,
				() -> mapper.merge(source, new ProductAttribute(), callerStore, null));
	}

	@Test
	void mergeCreatesTextValueWhenOptionValueIdIsMissing() {
		MerchantStore callerStore = store(1);

		ProductOption option = new ProductOption();
		option.setMerchantStore(callerStore);
		when(productOptionService.getByCode(callerStore, "engraving")).thenReturn(option);

		Product ownProduct = new Product();
		ownProduct.setId(10L);
		ownProduct.setMerchantStore(callerStore);
		when(productService.getById(10L)).thenReturn(ownProduct);

		ProductPropertyOption propertyOption = new ProductPropertyOption();
		propertyOption.setCode("engraving");

		PersistableProductAttribute source = new PersistableProductAttribute();
		source.setProductId(10L);
		source.setOption(propertyOption);
		source.setOptionValue(new PersistableProductOptionValue());

		ProductAttribute attribute = mapper.merge(source, new ProductAttribute(), callerStore, null);

		assertSame(ownProduct, attribute.getProduct());
		assertTrue(attribute.getProductOptionValue().isProductOptionDisplayOnly());
	}

	private static MerchantStore store(int id) {
		MerchantStore store = new MerchantStore();
		store.setId(id);
		return store;
	}
}
