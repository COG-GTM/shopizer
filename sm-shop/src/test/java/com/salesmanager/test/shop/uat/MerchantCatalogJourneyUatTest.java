package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.salesmanager.core.business.services.catalog.product.availability.ProductAvailabilityService;
import com.salesmanager.core.business.services.catalog.product.price.ProductPriceService;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.catalog.product.price.ProductPrice;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.PersistableProductPrice;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
class MerchantCatalogJourneyUatTest extends ServicesTestSupport {

	@Autowired
	private ProductAvailabilityService productAvailabilityService;

	@Autowired
	private MerchantStoreService merchantStoreService;

	@Autowired
	private ProductPriceService productPriceService;

	@Test
	void merchantCreatesUpdatesAndReadsProductAndPrice() throws Exception {
		String sku = "MERCHANTUAT" + Long.toString(System.nanoTime(), 36).toUpperCase();
		PersistableProduct body = product(sku);
		body.setSku(sku);
		ResponseEntity<Entity> created = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=DEFAULT", new HttpEntity<>(body, getHeader()), Entity.class);
		assertEquals(HttpStatus.CREATED, created.getStatusCode());

		List<ProductAvailability> availabilities = productAvailabilityService.getBySku(sku, 0, 100)
				.getContent();
		Long inventoryId = availabilities.get(0).getId();
		PersistableProductPrice price = new PersistableProductPrice();
		price.setPrice(new BigDecimal("310.00"));
		price.setDiscountedPrice(new BigDecimal("300.00"));
		price.setDefaultPrice(true);
		ResponseEntity<Entity> addedPrice = testRestTemplate.postForEntity(
				"/api/v1/private/product/" + sku + "/price?store=DEFAULT",
				new HttpEntity<>(price, getHeader()), Entity.class);
		assertEquals(HttpStatus.CREATED, addedPrice.getStatusCode());

		price.setId(addedPrice.getBody().getId());
		price.setPrice(new BigDecimal("320.00"));
		ResponseEntity<Void> updatedPrice = testRestTemplate.exchange(
				"/api/v1/private/product/" + sku + "/inventory/" + inventoryId + "/price/"
						+ addedPrice.getBody().getId() + "?store=DEFAULT",
				HttpMethod.PUT, new HttpEntity<>(price, getHeader()), Void.class);
		assertEquals(HttpStatus.OK, updatedPrice.getStatusCode());
		ProductPrice priceAfter = productPriceService.findById(addedPrice.getBody().getId(), sku,
				merchantStoreService.getByCode("DEFAULT"));
		assertNotNull(priceAfter);
		assertEquals(new BigDecimal("320.00"), priceAfter.getProductPriceAmount());

		body.setId(created.getBody().getId());
		body.getDescriptions().get(0).setName(sku + " updated");
		ResponseEntity<Void> updatedProduct = testRestTemplate.exchange(
				"/api/v1/private/product/" + created.getBody().getId() + "?store=DEFAULT",
				HttpMethod.PUT, new HttpEntity<>(body, getHeader()), Void.class);
		assertEquals(HttpStatus.OK, updatedProduct.getStatusCode());
		assertTrue(testRestTemplate.getForEntity("/api/v2/product/" + sku + "?store=DEFAULT",
				String.class).getStatusCode().is2xxSuccessful());
	}
}
