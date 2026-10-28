package com.salesmanager.shop.store.facade.items;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.salesmanager.core.business.services.catalog.product.relationship.ProductRelationshipService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.relationship.ProductRelationship;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;

@ExtendWith(MockitoExtension.class)
class ProductItemsFacadeImplTest {

	@Mock
	private ProductRelationshipService productRelationshipService;

	@InjectMocks
	private ProductItemsFacadeImpl facade;

	@Test
	void addItemToGroupRejectsProductFromAnotherStore() throws Exception {
		MerchantStore callerStore = store(2, "GLOBEX");
		Product foreignProduct = product(10L, store(1, "ACME"));

		assertThrows(UnauthorizedException.class,
				() -> facade.addItemToGroup(foreignProduct, "FEATURED_ITEM", callerStore, new Language()));

		verifyNoInteractions(productRelationshipService);
	}

	@Test
	void addItemToGroupRejectsProductWithoutStore() throws Exception {
		MerchantStore callerStore = store(2, "GLOBEX");
		Product orphanProduct = product(10L, null);

		assertThrows(UnauthorizedException.class,
				() -> facade.addItemToGroup(orphanProduct, "FEATURED_ITEM", callerStore, new Language()));

		verify(productRelationshipService, never()).saveOrUpdate(any(ProductRelationship.class));
	}

	private static MerchantStore store(int id, String code) {
		MerchantStore store = new MerchantStore();
		store.setId(id);
		store.setCode(code);
		return store;
	}

	private static Product product(Long id, MerchantStore store) {
		Product product = new Product();
		product.setId(id);
		product.setMerchantStore(store);
		return product;
	}
}
