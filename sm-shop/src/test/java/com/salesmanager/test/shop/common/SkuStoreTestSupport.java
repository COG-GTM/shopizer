package com.salesmanager.test.shop.common;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;

import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.catalog.product.type.ProductTypeService;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.catalog.product.description.ProductDescription;
import com.salesmanager.core.model.catalog.product.price.ProductPrice;
import com.salesmanager.core.model.catalog.product.type.ProductType;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;

public abstract class SkuStoreTestSupport extends ServicesTestSupport {

	@Autowired protected MerchantStoreService merchantService;
	@Autowired protected ProductService productService;
	@Autowired protected LanguageService languageService;
	@Autowired private ProductTypeService productTypeService;

	protected MerchantStore defaultStore;
	protected MerchantStore otherStore;
	protected Language language;
	protected String sharedSku;
	protected String otherOnlySku;
	protected Product defaultProduct;
	protected Product otherProduct;

	@Before
	public void createSkuFixture() throws Exception {
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		sharedSku = "shared_" + suffix;
		otherOnlySku = "other_" + suffix;
		defaultStore = merchantService.getByCode(MerchantStore.DEFAULT_STORE);
		language = languageService.getByCode("en");
		otherStore = new MerchantStore();
		otherStore.setCode("sku_" + suffix);
		otherStore.setStorename("SKU isolation fixture");
		otherStore.setCountry(defaultStore.getCountry());
		otherStore.setCurrency(defaultStore.getCurrency());
		otherStore.setDefaultLanguage(language);
		otherStore.setLanguages(Collections.singletonList(language));
		otherStore.setStoreEmailAddress("sku@example.test");
		otherStore.setStorephone("5555555555");
		otherStore.setStoreaddress("1 Test Street");
		otherStore.setStorecity("Montreal");
		otherStore.setStorepostalcode("H2H2H2");
		merchantService.create(otherStore);

		// Insert the other store first so an unscoped lookup cannot accidentally pass.
		otherProduct = createProduct(otherStore, sharedSku, new BigDecimal("73.00"));
		defaultProduct = createProduct(defaultStore, sharedSku, new BigDecimal("19.00"));
		createProduct(otherStore, otherOnlySku, new BigDecimal("31.00"));
	}

	protected Product createProduct(MerchantStore store, String sku, BigDecimal price) throws Exception {
		Product product = new Product();
		product.setSku(sku);
		product.setMerchantStore(store);
		product.setAvailable(true);
		product.setType(productTypeService.getProductType(ProductType.GENERAL_TYPE));
		ProductDescription description = new ProductDescription();
		description.setName(sku);
		description.setLanguage(language);
		description.setProduct(product);
		product.getDescriptions().add(description);
		ProductAvailability availability = new ProductAvailability();
		availability.setProduct(product);
		availability.setProductQuantity(20);
		availability.setRegion("*");
		ProductPrice productPrice = new ProductPrice();
		productPrice.setDefaultPrice(true);
		productPrice.setProductPriceAmount(price);
		productPrice.setProductAvailability(availability);
		availability.getPrices().add(productPrice);
		product.getAvailabilities().add(availability);
		productService.saveProduct(product);
		return product;
	}
}
