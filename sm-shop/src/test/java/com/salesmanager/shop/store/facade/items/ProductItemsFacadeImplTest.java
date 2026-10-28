package com.salesmanager.shop.store.facade.items;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.services.catalog.pricing.PricingService;
import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.catalog.product.relationship.ProductRelationshipService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.relationship.ProductRelationship;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.utils.ImageFilePath;

@RunWith(MockitoJUnitRunner.class)
public class ProductItemsFacadeImplTest {

	@Mock
	private ProductService productService;

	@Mock
	private PricingService pricingService;

	@Mock
	private ImageFilePath imageUtils;

	@Mock
	private ProductRelationshipService productRelationshipService;

	@InjectMocks
	private ProductItemsFacadeImpl facade;

	private final Language language = new Language();

	@Test
	public void addItemToGroupRejectsProductFromAnotherStore() {
		MerchantStore acme = store(1, "ACME");
		Product otherStoresProduct = product(10L, store(2, "OTHER"));
		try {
			facade.addItemToGroup(otherStoresProduct, "FEATURED_ITEM", acme, language);
			fail("Expected ResourceNotFoundException");
		} catch (ResourceNotFoundException expected) {
			verifyNoInteractions(productRelationshipService);
		}
	}

	@Test
	public void removeItemFromGroupRejectsProductFromAnotherStore() throws Exception {
		MerchantStore acme = store(1, "ACME");
		Product otherStoresProduct = product(10L, store(2, "OTHER"));
		try {
			facade.removeItemFromGroup(otherStoresProduct, "FEATURED_ITEM", acme, language);
			fail("Expected ResourceNotFoundException");
		} catch (ResourceNotFoundException expected) {
			verifyNoInteractions(productRelationshipService);
		}
	}

	@Test
	public void addItemToGroupAcceptsProductFromSameStore() throws Exception {
		MerchantStore acme = store(1, "ACME");
		facade.addItemToGroup(product(10L, acme), "FEATURED_ITEM", acme, language);
		verify(productRelationshipService).saveOrUpdate(any(ProductRelationship.class));
	}

	@Test
	public void addItemToGroupAcceptsProductFromParentStore() throws Exception {
		MerchantStore parent = store(1, "PARENT");
		MerchantStore child = store(3, "CHILD");
		child.setParent(parent);
		facade.addItemToGroup(product(10L, parent), "FEATURED_ITEM", child, language);
		verify(productRelationshipService).saveOrUpdate(any(ProductRelationship.class));
	}

	private static MerchantStore store(int id, String code) {
		MerchantStore store = new MerchantStore();
		store.setId(id);
		store.setCode(code);
		return store;
	}

	private static Product product(Long id, MerchantStore owner) {
		Product product = new Product();
		product.setId(id);
		product.setMerchantStore(owner);
		return product;
	}
}
