package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import com.salesmanager.core.business.repositories.catalog.product.ProductRepository;
import com.salesmanager.core.business.repositories.catalog.product.variant.ProductVariantRepository;
import com.salesmanager.core.model.catalog.product.variant.ProductVariant;
import com.salesmanager.test.shop.common.SkuStoreTestSupport;

@RunWith(SpringRunner.class)
@Transactional
public class ProductSkuQueryIntegrationTest extends SkuStoreTestSupport {

	@Autowired private ProductRepository products;
	@Autowired private ProductVariantRepository variants;

	@Test
	public void nativeQueryReturnsOnlyTheRequestedStoresProduct() {
		assertEquals(Collections.singletonList(defaultProduct.getId()), ids(sharedSku, defaultStore.getId()));
		assertEquals(Collections.singletonList(otherProduct.getId()), ids(sharedSku, otherStore.getId()));
		assertTrue(ids(otherOnlySku, defaultStore.getId()).isEmpty());
		assertTrue(ids(sharedSku, Integer.MAX_VALUE).isEmpty());
	}

	@Test
	public void jpqlExistenceDoesNotRevealAnotherStoresSku() {
		assertTrue(products.existsBySku(sharedSku, defaultStore.getId()));
		assertTrue(products.existsBySku(otherOnlySku, otherStore.getId()));
		assertFalse(products.existsBySku(otherOnlySku, defaultStore.getId()));
		assertFalse(products.existsBySku(sharedSku, Integer.MAX_VALUE));
	}

	@Test
	public void variantSkuResolvesItsParentOnlyWithinItsOwnStore() throws Exception {
		String sku = "variant_" + otherStore.getCode();
		ProductVariant variant = new ProductVariant();
		variant.setProduct(otherProduct);
		variant.setSku(sku);
		variants.saveAndFlush(variant);
		assertTrue(products.existsBySku(sku, otherStore.getId()));
		assertFalse(products.existsBySku(sku, defaultStore.getId()));
		assertEquals(Collections.singletonList(otherProduct.getId()), ids(sku, otherStore.getId()));
		assertTrue(ids(sku, defaultStore.getId()).isEmpty());
		assertEquals(otherProduct.getId(), productService.getBySku(sku, otherStore, language).getId());
		assertEquals(otherProduct.getId(), productService.getBySku(sku, otherStore).getId());
	}

	private List<Long> ids(String sku, Integer store) {
		return products.findBySku(sku, store).stream().map(id -> ((Number) id).longValue()).collect(Collectors.toList());
	}
}
