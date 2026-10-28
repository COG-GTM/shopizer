package com.salesmanager.test.shop.unit.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.services.catalog.product.price.ProductPriceService;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.catalog.product.price.ProductPrice;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.mapper.inventory.PersistableProductPriceMapper;
import com.salesmanager.shop.model.catalog.product.PersistableProductPrice;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.facade.product.ProductPriceFacadeImpl;

@RunWith(MockitoJUnitRunner.class)
public class ProductPriceFacadeStoreScopeTest {

	@Mock
	private ProductPriceService productPriceService;

	@Mock
	private PersistableProductPriceMapper persistableProductPriceMapper;

	@InjectMocks
	private ProductPriceFacadeImpl facade;

	private MerchantStore globex;

	@Before
	public void setUp() {
		globex = new MerchantStore();
		globex.setId(2);
		globex.setCode("GLOBEX");
		globex.setDefaultLanguage(new Language("en"));
	}

	private PersistableProductPrice price(Long id, Long inventoryId) {
		PersistableProductPrice price = new PersistableProductPrice();
		price.setId(id);
		price.setSku("ACME-SKU");
		price.setProductAvailabilityId(inventoryId);
		price.setPrice(new BigDecimal("0.01"));
		price.setDefaultPrice(true);
		return price;
	}

	@Test
	public void saveWithPriceIdFromAnotherStoreIsRejected() throws Exception {
		when(productPriceService.findById(10L, "ACME-SKU", globex)).thenReturn(null);

		assertThrows(ResourceNotFoundException.class, () -> facade.save(price(10L, 5L), globex));

		verify(persistableProductPriceMapper, never()).convert(any(), any(), any());
		verify(productPriceService, never()).saveOrUpdate(any());
	}

	@Test
	public void saveWithPriceIdOnDifferentInventoryIsRejected() throws Exception {
		ProductAvailability inventory = new ProductAvailability();
		inventory.setId(6L);
		ProductPrice existing = new ProductPrice();
		existing.setId(10L);
		existing.setProductAvailability(inventory);
		when(productPriceService.findById(10L, "ACME-SKU", globex)).thenReturn(existing);

		assertThrows(ResourceNotFoundException.class, () -> facade.save(price(10L, 5L), globex));

		verify(productPriceService, never()).saveOrUpdate(any());
	}

	@Test
	public void saveWithOwnPriceIdUpdates() throws Exception {
		ProductAvailability inventory = new ProductAvailability();
		inventory.setId(5L);
		ProductPrice existing = new ProductPrice();
		existing.setId(10L);
		existing.setProductAvailability(inventory);
		PersistableProductPrice request = price(10L, 5L);
		when(productPriceService.findById(10L, "ACME-SKU", globex)).thenReturn(existing);
		when(persistableProductPriceMapper.convert(request, globex, globex.getDefaultLanguage())).thenReturn(existing);
		when(productPriceService.saveOrUpdate(existing)).thenReturn(existing);

		assertEquals(Long.valueOf(10L), facade.save(request, globex));
	}

	@Test
	public void deleteOfPriceNotInStoreReturnsNotFound() throws Exception {
		when(productPriceService.findById(10L, "ACME-SKU", globex)).thenReturn(null);

		assertThrows(ResourceNotFoundException.class, () -> facade.delete(10L, "ACME-SKU", globex));

		verify(productPriceService, never()).delete(any(ProductPrice.class));
	}
}
