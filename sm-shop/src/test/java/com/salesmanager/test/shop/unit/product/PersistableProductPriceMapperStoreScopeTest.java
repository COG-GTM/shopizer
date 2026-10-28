package com.salesmanager.test.shop.unit.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.catalog.product.availability.ProductAvailabilityService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.catalog.product.price.ProductPrice;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.mapper.inventory.PersistableProductPriceMapper;
import com.salesmanager.shop.model.catalog.product.PersistableProductPrice;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;

@RunWith(MockitoJUnitRunner.class)
public class PersistableProductPriceMapperStoreScopeTest {

	@Mock
	private ProductAvailabilityService productAvailabilityService;

	@Mock
	private ProductService productService;

	@Mock
	private LanguageService languageService;

	@InjectMocks
	private PersistableProductPriceMapper mapper;

	private MerchantStore globex;
	private Language en;

	@Before
	public void setUp() {
		en = new Language("en");
		globex = new MerchantStore();
		globex.setId(2);
		globex.setCode("GLOBEX");
		globex.setDefaultLanguage(en);
	}

	private PersistableProductPrice price(Long inventoryId) {
		PersistableProductPrice price = new PersistableProductPrice();
		price.setSku("ACME-SKU");
		price.setProductAvailabilityId(inventoryId);
		price.setPrice(new BigDecimal("12.50"));
		price.setDefaultPrice(false);
		return price;
	}

	@Test
	public void inventoryOfAnotherStoreIsReportedAsNotFound() {
		when(productAvailabilityService.getById(5L, globex)).thenReturn(Optional.empty());

		assertThrows(ResourceNotFoundException.class, () -> mapper.convert(price(5L), globex, en));
	}

	@Test
	public void inventoryOfOwnStoreIsAttachedToThePrice() {
		ProductAvailability inventory = new ProductAvailability();
		inventory.setId(5L);
		when(productAvailabilityService.getById(5L, globex)).thenReturn(Optional.of(inventory));

		ProductPrice mapped = mapper.convert(price(5L), globex, en);

		assertSame(inventory, mapped.getProductAvailability());
		assertEquals(new BigDecimal("12.50"), mapped.getProductPriceAmount());
		assertEquals(true, inventory.getPrices().contains(mapped));
	}
}
