package com.salesmanager.shop.store.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.product.PersistableProductPrice;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.store.api.v1.product.ProductApi;
import com.salesmanager.shop.store.api.v1.product.ProductPriceApi;
import com.salesmanager.shop.store.api.v2.product.ProductApiV2;
import com.salesmanager.shop.store.controller.product.facade.ProductCommonFacade;
import com.salesmanager.shop.store.controller.product.facade.ProductPriceFacade;

class ClientSuppliedIdApiTest {

	@Test
	void productV1CreateClearsBodyIdBeforeCallingFacade() {
		ProductCommonFacade facade = mock(ProductCommonFacade.class);
		when(facade.saveProduct(any(), any(), any())).thenReturn(11L);
		ProductApi api = new ProductApi();
		ReflectionTestUtils.setField(api, "productCommonFacade", facade);
		PersistableProduct body = new PersistableProduct();
		body.setId(42L);

		api.create(body, new MerchantStore(), new Language());

		ArgumentCaptor<PersistableProduct> captured = ArgumentCaptor.forClass(PersistableProduct.class);
		verify(facade).saveProduct(any(), captured.capture(), any());
		assertNull(captured.getValue().getId());
	}

	@Test
	void productInventoryV2CreateClearsBodyIdBeforeCallingFacade() {
		ProductCommonFacade facade = mock(ProductCommonFacade.class);
		when(facade.saveProduct(any(), any(), any())).thenReturn(12L);
		ProductApiV2 api = new ProductApiV2();
		ReflectionTestUtils.setField(api, "productCommonFacade", facade);
		PersistableProduct body = new PersistableProduct();
		body.setId(42L);

		api.create(body, new MerchantStore(), new Language());

		ArgumentCaptor<PersistableProduct> captured = ArgumentCaptor.forClass(PersistableProduct.class);
		verify(facade).saveProduct(any(), captured.capture(), any());
		assertNull(captured.getValue().getId());
	}

	@Test
	void inventoryPricePostClearsBodyIdBeforeCallingFacade() {
		ProductPriceFacade facade = mock(ProductPriceFacade.class);
		when(facade.save(any(), any())).thenReturn(21L);
		ProductPriceApi api = new ProductPriceApi();
		ReflectionTestUtils.setField(api, "productPriceFacade", facade);
		PersistableProductPrice body = new PersistableProductPrice();
		body.setId(42L);

		api.save("SKU", 13L, body, new MerchantStore(), new Language());

		ArgumentCaptor<PersistableProductPrice> captured = ArgumentCaptor.forClass(PersistableProductPrice.class);
		verify(facade).save(captured.capture(), any());
		assertNull(captured.getValue().getId());
		assertEquals("SKU", captured.getValue().getSku());
		assertEquals(13L, captured.getValue().getProductAvailabilityId());
	}

	@Test
	void pricePostClearsBodyIdBeforeCallingFacade() {
		ProductPriceFacade facade = mock(ProductPriceFacade.class);
		when(facade.save(any(), any())).thenReturn(22L);
		ProductPriceApi api = new ProductPriceApi();
		ReflectionTestUtils.setField(api, "productPriceFacade", facade);
		PersistableProductPrice body = new PersistableProductPrice();
		body.setId(42L);

		api.save("SKU", body, new MerchantStore(), new Language());

		ArgumentCaptor<PersistableProductPrice> captured = ArgumentCaptor.forClass(PersistableProductPrice.class);
		verify(facade).save(captured.capture(), any());
		assertNull(captured.getValue().getId());
		assertEquals("SKU", captured.getValue().getSku());
	}

	@Test
	void pricePutUsesPathIdInsteadOfBodyId() {
		ProductPriceFacade facade = mock(ProductPriceFacade.class);
		when(facade.save(any(), any())).thenReturn(23L);
		ProductPriceApi api = new ProductPriceApi();
		ReflectionTestUtils.setField(api, "productPriceFacade", facade);
		PersistableProductPrice body = new PersistableProductPrice();
		body.setId(42L);

		api.edit("SKU", 13L, 5L, body, new MerchantStore(), new Language());

		ArgumentCaptor<PersistableProductPrice> captured = ArgumentCaptor.forClass(PersistableProductPrice.class);
		verify(facade).save(captured.capture(), any());
		assertEquals(5L, captured.getValue().getId());
		assertEquals(13L, captured.getValue().getProductAvailabilityId());
	}
}
