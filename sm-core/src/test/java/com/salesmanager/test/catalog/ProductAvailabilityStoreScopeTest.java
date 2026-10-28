package com.salesmanager.test.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.sql.Date;
import java.util.ArrayList;

import javax.inject.Inject;

import org.junit.Test;

import com.salesmanager.core.business.repositories.catalog.product.availability.ProductAvailabilityRepository;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.catalog.product.description.ProductDescription;
import com.salesmanager.core.model.catalog.product.price.ProductPrice;
import com.salesmanager.core.model.catalog.product.price.ProductPriceDescription;
import com.salesmanager.core.model.catalog.product.type.ProductType;
import com.salesmanager.core.model.merchant.MerchantStore;

public class ProductAvailabilityStoreScopeTest extends
		com.salesmanager.test.common.AbstractSalesManagerCoreTestCase {

	@Inject
	private ProductAvailabilityRepository productAvailabilityRepository;

	@Test
	public void getByIdOnlyReturnsAvailabilityForRequestedStore() throws Exception {
		MerchantStore defaultStore = merchantService.getByCode(MerchantStore.DEFAULT_STORE);
		MerchantStore otherStore = new MerchantStore();
		otherStore.setCode("SCOPE_" + System.nanoTime());
		otherStore.setStorename("Scope test store");
		otherStore.setStorephone(defaultStore.getStorephone());
		otherStore.setStorecity(defaultStore.getStorecity());
		otherStore.setStorepostalcode(defaultStore.getStorepostalcode());
		otherStore.setStoreaddress(defaultStore.getStoreaddress());
		otherStore.setStoreEmailAddress("scope-" + System.nanoTime() + "@example.com");
		otherStore.setCountry(defaultStore.getCountry());
		otherStore.setCurrency(defaultStore.getCurrency());
		otherStore.setDefaultLanguage(defaultStore.getDefaultLanguage());
		otherStore.setLanguages(new ArrayList<>(defaultStore.getLanguages()));
		Product product = new Product();
		product.setSku("scope-" + System.nanoTime());
		product.setMerchantStore(defaultStore);
		product.setType(productTypeService.getProductType(ProductType.GENERAL_TYPE));

		ProductDescription description = new ProductDescription();
		description.setName("Store scoped availability");
		description.setLanguage(languageService.getByCode("en"));
		description.setProduct(product);
		product.getDescriptions().add(description);

		ProductAvailability availability = new ProductAvailability();
		availability.setProductDateAvailable(new Date(System.currentTimeMillis()));
		availability.setProductQuantity(3);
		availability.setRegion("*");
		availability.setProduct(product);
		product.getAvailabilities().add(availability);

		ProductPrice price = new ProductPrice();
		price.setDefaultPrice(true);
		price.setProductPriceAmount(java.math.BigDecimal.TEN);
		price.setProductAvailability(availability);
		ProductPriceDescription priceDescription = new ProductPriceDescription();
		priceDescription.setName("Base price");
		priceDescription.setProductPrice(price);
		priceDescription.setLanguage(languageService.getByCode("en"));
		price.getDescriptions().add(priceDescription);
		availability.getPrices().add(price);

		try {
			merchantService.create(otherStore);
			productService.saveProduct(product);

			Long availabilityId = availability.getId();
			assertNotNull(availabilityId);
			assertTrue(productAvailabilityService.getById(availabilityId, defaultStore).isPresent());
			assertFalse(productAvailabilityService.getById(availabilityId, otherStore).isPresent());

			ProductAvailability repositoryResult = productAvailabilityRepository.getById(availabilityId,
					defaultStore.getId());
			assertNotNull(repositoryResult);
			assertEquals(defaultStore.getId(), repositoryResult.getProduct().getMerchantStore().getId());
		} finally {
			try {
				if (product.getId() != null) {
					productService.delete(product);
				}
			} finally {
				if (otherStore.getId() != null) {
					merchantService.delete(otherStore);
				}
			}
		}
	}
}
