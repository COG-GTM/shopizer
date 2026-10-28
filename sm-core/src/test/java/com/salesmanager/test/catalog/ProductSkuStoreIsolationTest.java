package com.salesmanager.test.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.description.ProductDescription;
import com.salesmanager.core.model.catalog.product.type.ProductType;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;

public class ProductSkuStoreIsolationTest extends com.salesmanager.test.common.AbstractSalesManagerCoreTestCase {

	private static final String SHARED_SKU = "ISO-SHARED-SKU";
	private static final String DEFAULT_ONLY_SKU = "ISO-DEFAULT-ONLY";

	@Test
	public void skuLookupsAreScopedToTheRequestedStore() throws Exception {

		Language en = languageService.getByCode("en");
		MerchantStore defaultStore = merchantService.getByCode(MerchantStore.DEFAULT_STORE);
		MerchantStore otherStore = createStore("ISOSTORE", en);

		Product otherProduct = createProduct(otherStore, SHARED_SKU, en);
		Product defaultProduct = createProduct(defaultStore, SHARED_SKU, en);
		createProduct(defaultStore, DEFAULT_ONLY_SKU, en);

		assertTrue(productService.exists(DEFAULT_ONLY_SKU, defaultStore));
		assertFalse(productService.exists(DEFAULT_ONLY_SKU, otherStore));
		assertTrue(productService.exists(SHARED_SKU, otherStore));
		assertFalse(productService.exists("ISO-UNKNOWN", defaultStore));

		Product found = productService.getBySku(SHARED_SKU, defaultStore, en);
		assertNotNull(found);
		assertEquals(defaultProduct.getId(), found.getId());

		found = productService.getBySku(SHARED_SKU, otherStore, en);
		assertNotNull(found);
		assertEquals(otherProduct.getId(), found.getId());

		found = productService.getBySku(SHARED_SKU, defaultStore);
		assertNotNull(found);
		assertEquals(defaultProduct.getId(), found.getId());

		try {
			productService.getBySku(DEFAULT_ONLY_SKU, otherStore, en);
			throw new AssertionError("SKU of another store must not resolve");
		} catch (ServiceException expected) {
			// expected
		}
	}

	private MerchantStore createStore(String code, Language en) throws ServiceException {
		List<Language> languages = new ArrayList<Language>();
		languages.add(en);

		MerchantStore store = new MerchantStore();
		store.setCountry(countryService.getByCode("CA"));
		store.setCurrency(currencyService.getByCode("CAD"));
		store.setDefaultLanguage(en);
		store.setInBusinessSince(new Date(System.currentTimeMillis()));
		store.setZone(zoneService.getByCode("QC"));
		store.setStorename("Isolation store");
		store.setStorephone("888-888-8888");
		store.setCode(code);
		store.setStorecity("My city");
		store.setStoreaddress("1234 Street address");
		store.setStorepostalcode("H2H-2H2");
		store.setStoreEmailAddress("isolation@shopizer.com");
		store.setDomainName("isolation.localhost");
		store.setStoreTemplate("december");
		store.setRetailer(true);
		store.setLanguages(languages);
		merchantService.create(store);
		return merchantService.getByCode(code);
	}

	private Product createProduct(MerchantStore store, String sku, Language en) throws ServiceException {
		Product product = new Product();
		product.setSku(sku);
		product.setMerchantStore(store);
		product.setType(productTypeService.getProductType(ProductType.GENERAL_TYPE));
		product.setAvailable(true);

		ProductDescription description = new ProductDescription();
		description.setName(sku + " " + store.getCode());
		description.setLanguage(en);
		description.setProduct(product);
		product.getDescriptions().add(description);

		productService.saveProduct(product);
		return product;
	}
}
