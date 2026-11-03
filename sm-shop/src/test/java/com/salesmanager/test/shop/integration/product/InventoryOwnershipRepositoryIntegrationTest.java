package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import com.salesmanager.core.business.repositories.catalog.product.availability.PageableProductAvailabilityRepository;
import com.salesmanager.core.business.repositories.catalog.product.availability.ProductAvailabilityRepository;
import com.salesmanager.core.business.repositories.catalog.product.price.ProductPriceRepository;
import com.salesmanager.core.business.services.catalog.product.availability.ProductAvailabilityService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.catalog.product.price.ProductPrice;
import com.salesmanager.core.model.catalog.product.variant.ProductVariant;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.application.ShopApplication;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@Transactional
public class InventoryOwnershipRepositoryIntegrationTest {

	@PersistenceContext private EntityManager em;
	@Autowired private ProductAvailabilityRepository inventories;
	@Autowired private PageableProductAvailabilityRepository pages;
	@Autowired private ProductAvailabilityService service;
	@Autowired private ProductPriceRepository prices;

	private MerchantStore owner;
	private MerchantStore child;
	private MerchantStore stranger;
	private Product product;

	@Before
	public void setUp() {
		owner = em.createQuery("from MerchantStore where code = 'DEFAULT'", MerchantStore.class).getSingleResult();
		child = store("ownershipChild");
		child.setParent(owner);
		stranger = store("ownershipStranger");
		product = new Product();
		product.setSku("ownershipProduct");
		product.setMerchantStore(owner);
		em.persist(product);
	}

	@Test
	public void inventoryStoreOverridesProductOwnerForSkuAndIdQueries() {
		ProductAvailability inventory = inventory(child, null);
		assertVisible(inventory, product.getSku(), child);
		assertHidden(inventory, product.getSku(), owner);
		assertHidden(inventory, product.getSku(), stranger);
	}

	@Test
	public void legacyNullInventoryStoreFallsBackOnlyToProductOwner() {
		ProductAvailability inventory = inventory(null, null);
		assertVisible(inventory, product.getSku(), owner);
		assertHidden(inventory, product.getSku(), child);
		assertHidden(inventory, product.getSku(), stranger);
	}

	@Test
	public void variantSkuUsesSameOwnershipPredicate() {
		ProductVariant variant = new ProductVariant();
		variant.setProduct(product);
		variant.setSku("ownershipVariant");
		em.persist(variant);
		ProductAvailability inventory = inventory(child, variant);
		assertVisible(inventory, variant.getSku(), child);
		assertHidden(inventory, variant.getSku(), owner);
		assertHidden(inventory, variant.getSku(), stranger);
	}

	@Test
	public void priceAndInventoryIdsCannotBeIgnoredWhenProductSkuMatches() {
		ProductAvailability first = inventory(owner, null);
		ProductAvailability second = inventory(child, null);
		Long priceId = first.defaultPrice().getId();
		assertNull(prices.findByProduct(product.getSku(), Long.MAX_VALUE, owner.getCode()));
		assertNull(prices.findByProduct(product.getSku(), priceId, child.getCode()));
		assertTrue(prices.findByProductInventoty(product.getSku(), second.getId(), owner.getCode()).isEmpty());
		assertTrue(prices.findByProductInventoty(product.getSku(), Long.MAX_VALUE, owner.getCode()).isEmpty());
	}

	@Test
	public void paginationCountDoesNotIncludeOtherStoresInventory() {
		ProductAvailability first = inventory(owner, null);
		inventory(child, null);
		Page<ProductAvailability> page = pages.getBySku(product.getSku(), owner.getCode(), PageRequest.of(0, 1));
		assertEquals(1L, page.getTotalElements());
		assertEquals(first.getId(), page.getContent().get(0).getId());
		assertTrue(pages.getBySku(product.getSku(), owner.getCode(), PageRequest.of(1, 1)).isEmpty());
	}

	private void assertVisible(ProductAvailability inventory, String sku, MerchantStore store) {
		assertEquals(Collections.singletonList(inventory.getId()), ids(inventories.getBySku(sku, store.getCode())));
		assertEquals(inventory.getId(), service.getById(inventory.getId(), store).get().getId());
		assertEquals(Collections.singletonList(inventory.getId()), ids(pages.getBySku(sku, store.getCode(), PageRequest.of(0, 10)).getContent()));
		assertEquals(inventory.defaultPrice().getId(), prices.findByProduct(sku, store.getCode()).get(0).getId());
		assertEquals(inventory.defaultPrice().getId(), prices.findByProduct(sku, inventory.defaultPrice().getId(), store.getCode()).getId());
		assertEquals(1, prices.findByProductInventoty(sku, inventory.getId(), store.getCode()).size());
	}

	private void assertHidden(ProductAvailability inventory, String sku, MerchantStore store) {
		assertTrue(inventories.getBySku(sku, store.getCode()).isEmpty());
		assertFalse(service.getById(inventory.getId(), store).isPresent());
		assertEquals(0L, pages.getBySku(sku, store.getCode(), PageRequest.of(0, 1)).getTotalElements());
		assertTrue(prices.findByProduct(sku, store.getCode()).isEmpty());
		assertNull(prices.findByProduct(sku, inventory.defaultPrice().getId(), store.getCode()));
		assertTrue(prices.findByProductInventoty(sku, inventory.getId(), store.getCode()).isEmpty());
	}

	private List<Long> ids(List<ProductAvailability> rows) {
		return rows.stream().map(ProductAvailability::getId).collect(Collectors.toList());
	}

	private ProductAvailability inventory(MerchantStore store, ProductVariant variant) {
		ProductAvailability inventory = new ProductAvailability(product, store);
		inventory.setProductVariant(variant);
		ProductPrice price = new ProductPrice();
		price.setProductAvailability(inventory);
		price.setDefaultPrice(true);
		price.setProductPriceAmount(new BigDecimal("19.95"));
		inventory.getPrices().add(price);
		em.persist(inventory);
		em.flush();
		return inventory;
	}

	private MerchantStore store(String code) {
		MerchantStore store = new MerchantStore();
		store.setCode(code);
		store.setStorename(code);
		store.setStorephone("1234567890");
		store.setStorecity("Montreal");
		store.setStorepostalcode("H1H1H1");
		store.setStoreEmailAddress(code + "@example.com");
		store.setCountry(owner.getCountry());
		store.setCurrency(owner.getCurrency());
		store.setDefaultLanguage(owner.getDefaultLanguage());
		store.setLanguages(Collections.singletonList(owner.getDefaultLanguage()));
		em.persist(store);
		return store;
	}
}
