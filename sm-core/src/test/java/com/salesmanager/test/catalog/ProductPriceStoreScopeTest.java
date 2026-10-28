package com.salesmanager.test.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.Test;

import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.catalog.product.description.ProductDescription;
import com.salesmanager.core.model.catalog.product.price.ProductPrice;
import com.salesmanager.core.model.catalog.product.type.ProductType;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.test.common.AbstractSalesManagerCoreTestCase;

/**
 * Price and inventory lookups must only return records owned by the requesting store
 * (the inventory's store when the product belongs to it or its parent, otherwise the product's store).
 */
public class ProductPriceStoreScopeTest extends AbstractSalesManagerCoreTestCase {

	@Test
	public void priceAndInventoryLookupsAreStoreScoped() throws Exception {
		Language en = languageService.getByCode("en");
		MerchantStore acme = merchantService.getByCode(MerchantStore.DEFAULT_STORE);
		MerchantStore globex = createStore("GLOBEX", acme, null, en);

		Product acmeProduct = createProduct(acme, en, "ACME-PRICE-SKU", new BigDecimal("29.99"));
		createProduct(globex, en, "GLOBEX-PRICE-SKU", new BigDecimal("9.99"));

		ProductAvailability acmeInventory = productAvailabilityService.getBySku("ACME-PRICE-SKU", acme).get(0);
		Long acmeInventoryId = acmeInventory.getId();
		Long acmePriceId = acmeInventory.getPrices().iterator().next().getId();

		// owner store still sees its own records
		assertEquals(1, productPriceService.findByProductSku("ACME-PRICE-SKU", acme).size());
		assertNotNull(productPriceService.findById(acmePriceId, "ACME-PRICE-SKU", acme));
		assertEquals(1, productPriceService.findByInventoryId(acmeInventoryId, "ACME-PRICE-SKU", acme).size());
		assertTrue(productAvailabilityService.getById(acmeInventoryId, acme).isPresent());
		assertEquals(acmeProduct.getId(), productAvailabilityService.getById(acmeInventoryId, acme).get().getProduct().getId());

		// another store cannot reach them by sku, price id or inventory id
		assertTrue(productPriceService.findByProductSku("ACME-PRICE-SKU", globex).isEmpty());
		assertNull(productPriceService.findById(acmePriceId, "ACME-PRICE-SKU", globex));
		assertTrue(productPriceService.findByInventoryId(acmeInventoryId, "ACME-PRICE-SKU", globex).isEmpty());
		assertFalse(productAvailabilityService.getById(acmeInventoryId, globex).isPresent());
		assertTrue(productAvailabilityService.getBySku("ACME-PRICE-SKU", globex).isEmpty());
		assertEquals(0, productAvailabilityService.getBySku("ACME-PRICE-SKU", globex, 0, 10).getTotalElements());

		// a price id from another store does not match even with the caller's own sku
		assertNull(productPriceService.findById(acmePriceId, "GLOBEX-PRICE-SKU", globex));
	}

	@Test
	public void childStoreInventoryOnParentProductIsScopedToChild() throws Exception {
		Language en = languageService.getByCode("en");
		MerchantStore acme = merchantService.getByCode(MerchantStore.DEFAULT_STORE);
		MerchantStore acmeChild = createStore("ACMECHILD", acme, acme, en);
		MerchantStore globex = createStore("GLOBEX", acme, null, en);

		Product acmeProduct = createProduct(acme, en, "ACME-SHARED-SKU", new BigDecimal("19.99"));

		ProductAvailability childInventory = createInventory(acmeProduct, acmeChild, new BigDecimal("17.99"));
		Long childInventoryId = childInventory.getId();
		Long childPriceId = childInventory.getPrices().iterator().next().getId();

		// the child store owning the inventory can reach it and its prices
		assertTrue(productAvailabilityService.getById(childInventoryId, acmeChild).isPresent());
		assertEquals(1, productAvailabilityService.getBySku("ACME-SHARED-SKU", acmeChild).size());
		assertEquals(1, productPriceService.findByInventoryId(childInventoryId, "ACME-SHARED-SKU", acmeChild).size());
		assertNotNull(productPriceService.findById(childPriceId, "ACME-SHARED-SKU", acmeChild));

		// the parent (product owner) cannot reach inventory or prices owned by the child
		assertFalse(productAvailabilityService.getById(childInventoryId, acme).isPresent());
		assertEquals(1, productAvailabilityService.getBySku("ACME-SHARED-SKU", acme).size());
		assertTrue(productPriceService.findByInventoryId(childInventoryId, "ACME-SHARED-SKU", acme).isEmpty());
		assertNull(productPriceService.findById(childPriceId, "ACME-SHARED-SKU", acme));

		// an unrelated store holding inventory on the product still cannot reach it
		ProductAvailability globexInventory = createInventory(acmeProduct, globex, new BigDecimal("1.00"));
		Long globexPriceId = globexInventory.getPrices().iterator().next().getId();
		assertFalse(productAvailabilityService.getById(globexInventory.getId(), globex).isPresent());
		assertTrue(productAvailabilityService.getBySku("ACME-SHARED-SKU", globex).isEmpty());
		assertTrue(productPriceService.findByProductSku("ACME-SHARED-SKU", globex).isEmpty());
		assertNull(productPriceService.findById(globexPriceId, "ACME-SHARED-SKU", globex));
	}

	private ProductAvailability createInventory(Product product, MerchantStore store, BigDecimal amount) throws Exception {
		ProductAvailability inventory = new ProductAvailability();
		inventory.setProductDateAvailable(new Date());
		inventory.setProductQuantity(5);
		inventory.setRegion("*");
		inventory.setProduct(product);
		inventory.setMerchantStore(store);
		ProductPrice price = new ProductPrice();
		price.setDefaultPrice(true);
		price.setProductPriceAmount(amount);
		price.setProductAvailability(inventory);
		inventory.getPrices().add(price);
		productAvailabilityService.create(inventory);
		return inventory;
	}

	private MerchantStore createStore(String code, MerchantStore template, MerchantStore parent, Language en) throws Exception {
		MerchantStore existing = merchantService.getByCode(code);
		if (existing != null) {
			return existing;
		}
		List<Language> languages = new ArrayList<>();
		languages.add(en);
		MerchantStore store = new MerchantStore();
		store.setCode(code);
		store.setStorename(code);
		store.setCountry(template.getCountry());
		store.setCurrency(template.getCurrency());
		store.setZone(template.getZone());
		store.setDefaultLanguage(en);
		store.setLanguages(languages);
		store.setInBusinessSince(new Date());
		store.setStorephone("888-888-8888");
		store.setStorecity("My city");
		store.setStoreaddress("1234 Street address");
		store.setStorepostalcode("H2H-2H2");
		store.setStoreEmailAddress("contact@" + code.toLowerCase() + ".com");
		store.setDomainName(code.toLowerCase() + ".localhost");
		store.setStoreTemplate("december");
		store.setParent(parent);
		merchantService.create(store);
		return store;
	}

	private Product createProduct(MerchantStore store, Language en, String sku, BigDecimal amount) throws Exception {
		Product product = new Product();
		product.setSku(sku);
		product.setMerchantStore(store);
		product.setType(productTypeService.getProductType(ProductType.GENERAL_TYPE));

		ProductDescription description = new ProductDescription();
		description.setName(sku);
		description.setLanguage(en);
		description.setProduct(product);
		product.getDescriptions().add(description);

		ProductAvailability availability = new ProductAvailability();
		availability.setProductDateAvailable(new Date());
		availability.setProductQuantity(10);
		availability.setRegion("*");
		availability.setProduct(product);
		availability.setMerchantStore(store);
		product.getAvailabilities().add(availability);

		ProductPrice price = new ProductPrice();
		price.setDefaultPrice(true);
		price.setProductPriceAmount(amount);
		price.setProductAvailability(availability);
		availability.getPrices().add(price);

		productService.saveProduct(product);
		return product;
	}
}
