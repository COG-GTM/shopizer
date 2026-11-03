package com.salesmanager.shop.store.facade.product;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.catalog.product.attribute.ProductAttributeService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.attribute.ProductAttribute;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.mapper.catalog.PersistableProductAttributeMapper;
import com.salesmanager.shop.model.catalog.product.attribute.PersistableProductAttribute;
import com.salesmanager.shop.model.catalog.product.attribute.PersistableProductOptionValue;
import com.salesmanager.shop.model.catalog.product.attribute.ProductPropertyOption;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class ProductOptionFacadeImplTest {

	private static final long FOREIGN_PRODUCT_ID = 20L;

	@Mock
	private ProductService productService;

	@Mock
	private ProductAttributeService productAttributeService;

	@Mock
	private PersistableProductAttributeMapper persistableProductAttributeMapper;

	@InjectMocks
	private ProductOptionFacadeImpl facade;

	private MerchantStore callerStore;
	private Product foreignProduct;

	@BeforeEach
	void setUp() {
		callerStore = store(1, "ACME");
		foreignProduct = new Product();
		foreignProduct.setId(FOREIGN_PRODUCT_ID);
		foreignProduct.setMerchantStore(store(2, "GLOBEX"));
	}

	@Test
	void saveAttributeRejectsProductFromAnotherStore() throws Exception {
		when(productService.getById(FOREIGN_PRODUCT_ID)).thenReturn(foreignProduct);

		assertThrows(ResourceNotFoundException.class,
				() -> facade.saveAttribute(FOREIGN_PRODUCT_ID, attribute(null), callerStore, null));

		verifyNoInteractions(persistableProductAttributeMapper);
		verify(productAttributeService, never()).saveOrUpdate(any());
	}

	@Test
	void saveAttributeRejectsExistingAttributeFromAnotherStore() throws Exception {
		Product ownProduct = new Product();
		ownProduct.setId(10L);
		ownProduct.setMerchantStore(callerStore);
		when(productService.getById(10L)).thenReturn(ownProduct);

		ProductAttribute foreignAttribute = new ProductAttribute();
		foreignAttribute.setId(99L);
		foreignAttribute.setProduct(foreignProduct);
		when(productAttributeService.getById(99L)).thenReturn(foreignAttribute);

		assertThrows(ResourceNotFoundException.class,
				() -> facade.saveAttribute(10L, attribute(99L), callerStore, null));

		verifyNoInteractions(persistableProductAttributeMapper);
		verify(productAttributeService, never()).saveOrUpdate(any());
	}

	@Test
	void createAttributesRejectsProductFromAnotherStore() throws Exception {
		when(productService.getById(FOREIGN_PRODUCT_ID)).thenReturn(foreignProduct);

		assertThrows(ResourceNotFoundException.class, () -> facade
				.createAttributes(Collections.singletonList(attribute(null)), FOREIGN_PRODUCT_ID, callerStore));

		verifyNoInteractions(persistableProductAttributeMapper);
		verify(productAttributeService, never()).saveAll(any());
	}

	private static MerchantStore store(int id, String code) {
		MerchantStore store = new MerchantStore();
		store.setId(id);
		store.setCode(code);
		return store;
	}

	private static PersistableProductAttribute attribute(Long id) {
		PersistableProductAttribute attribute = new PersistableProductAttribute();
		attribute.setId(id);
		attribute.setOption(new ProductPropertyOption());
		attribute.setOptionValue(new PersistableProductOptionValue());
		return attribute;
	}
}
