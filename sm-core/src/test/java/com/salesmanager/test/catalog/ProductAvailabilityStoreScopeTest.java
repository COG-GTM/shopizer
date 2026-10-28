package com.salesmanager.test.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;

import org.junit.Test;
import org.springframework.data.domain.Page;

import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.catalog.product.description.ProductDescription;
import com.salesmanager.core.model.catalog.product.price.ProductPrice;
import com.salesmanager.core.model.catalog.product.price.ProductPriceDescription;
import com.salesmanager.core.model.catalog.product.type.ProductType;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.test.common.AbstractSalesManagerCoreTestCase;

public class ProductAvailabilityStoreScopeTest extends AbstractSalesManagerCoreTestCase {

	private static final String SKU = "SCOPED-INV-01";
	private static final String OTHER_STORE = "SCOPEB";

	@Test
	public void getBySkuIsRestrictedToStore() throws Exception {

		Language en = languageService.getByCode("en");
		MerchantStore storeA = merchantService.getByCode(MerchantStore.DEFAULT_STORE);
		MerchantStore storeB = merchantService.getByCode(OTHER_STORE);
		if (storeB == null) {
			storeB = createStore(storeA, OTHER_STORE);
		}

		createProduct(storeA, en, 42, new BigDecimal("19.99"));

		Page<ProductAvailability> own = productAvailabilityService.getBySku(SKU, storeA, 0, 10);
		assertEquals(1, own.getTotalElements());
		assertEquals(1, own.getContent().size());
		assertEquals(Integer.valueOf(42), own.getContent().get(0).getProductQuantity());

		Page<ProductAvailability> foreign = productAvailabilityService.getBySku(SKU, storeB, 0, 10);
		assertEquals(0, foreign.getTotalElements());
		assertTrue(foreign.getContent().isEmpty());

		List<ProductAvailability> ownList = productAvailabilityService.getBySku(SKU, storeA);
		assertEquals(1, ownList.size());

		List<ProductAvailability> foreignList = productAvailabilityService.getBySku(SKU, storeB);
		assertTrue(foreignList.isEmpty());
	}

	private MerchantStore createStore(MerchantStore template, String code) throws Exception {
		MerchantStore store = new MerchantStore();
		store.setCountry(template.getCountry());
		store.setCurrency(template.getCurrency());
		store.setDefaultLanguage(template.getDefaultLanguage());
		store.setInBusinessSince(new Date(System.currentTimeMillis()));
		store.setZone(template.getZone());
		store.setStorename("Scope test store");
		store.setStorephone("888-888-8888");
		store.setCode(code);
		store.setStorecity("My city");
		store.setStoreaddress("1234 Street address");
		store.setStorepostalcode("H2H-2H2");
		store.setStoreEmailAddress("scope@shopizer.com");
		store.setDomainName("localhost:8080");
		store.setStoreTemplate("december");
		merchantService.create(store);
		return merchantService.getByCode(code);
	}

	private void createProduct(MerchantStore store, Language en, int quantity, BigDecimal amount) throws Exception {
		Product product = new Product();
		product.setSku(SKU);
		product.setType(productTypeService.getProductType(ProductType.GENERAL_TYPE));
		product.setMerchantStore(store);

		ProductDescription description = new ProductDescription();
		description.setName("Scoped inventory product");
		description.setLanguage(en);
		description.setProduct(product);
		product.getDescriptions().add(description);

		ProductAvailability availability = new ProductAvailability();
		availability.setProductDateAvailable(new Date(System.currentTimeMillis()));
		availability.setProductQuantity(quantity);
		availability.setRegion("*");
		availability.setAvailable(true);
		availability.setProduct(product);
		availability.setMerchantStore(store);

		ProductPrice price = new ProductPrice();
		price.setDefaultPrice(true);
		price.setProductPriceAmount(amount);
		price.setProductAvailability(availability);

		ProductPriceDescription priceDescription = new ProductPriceDescription();
		priceDescription.setName("Base price");
		priceDescription.setProductPrice(price);
		priceDescription.setLanguage(en);
		price.getDescriptions().add(priceDescription);
		availability.getPrices().add(price);

		product.getAvailabilities().add(availability);
		productService.saveProduct(product);
	}
}
