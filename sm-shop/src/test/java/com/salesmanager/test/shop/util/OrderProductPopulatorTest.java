package com.salesmanager.test.shop.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.catalog.product.attribute.ProductAttributeService;
import com.salesmanager.core.business.services.catalog.product.file.DigitalProductService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.description.ProductDescription;
import com.salesmanager.core.model.catalog.product.price.FinalPrice;
import com.salesmanager.core.model.catalog.product.price.ProductPrice;
import com.salesmanager.core.model.catalog.product.variant.ProductVariant;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.orderproduct.OrderProduct;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;
import com.salesmanager.shop.populator.order.OrderProductPopulator;

class OrderProductPopulatorTest {

	private OrderProductPopulator populator;
	private ShoppingCartItem item;
	private ProductVariant variant;
	private MerchantStore store;
	private Language language;

	@BeforeEach
	void setUp() throws Exception {
		ProductService productService = mock(ProductService.class);
		populator = new OrderProductPopulator();
		populator.setProductService(productService);
		populator.setDigitalProductService(mock(DigitalProductService.class));
		populator.setProductAttributeService(mock(ProductAttributeService.class));
		store = new MerchantStore();
		store.setId(1);
		language = new Language();
		Product product = new Product();
		product.setId(10L);
		product.setSku("PARENT");
		product.setMerchantStore(store);
		ProductDescription description = new ProductDescription();
		description.setName("Parent product");
		product.setDescriptions(Collections.singleton(description));
		variant = new ProductVariant();
		variant.setId(100L);
		variant.setSku("VARIANT");
		product.setVariants(Collections.singleton(variant));
		item = new ShoppingCartItem(product);
		item.setItemPrice(BigDecimal.ONE);
		FinalPrice finalPrice = new FinalPrice();
		finalPrice.setFinalPrice(BigDecimal.ONE);
		finalPrice.setProductPrice(new ProductPrice());
		item.setFinalPrice(finalPrice);
		when(productService.getBySku("PARENT", store, language)).thenReturn(product);
	}

	@Test
	void selectedVariantUsesMatchingVariantSku() throws Exception {
		item.setVariant(100L);

		assertEquals("VARIANT", populate().getSku());
	}

	@Test
	void noSelectedVariantUsesParentSku() throws Exception {
		assertEquals("PARENT", populate().getSku());
	}

	@Test
	void selectedVariantWithNullSkuUsesParentSku() throws Exception {
		item.setVariant(100L);
		variant.setSku(null);

		assertEquals("PARENT", populate().getSku());
	}

	@Test
	void unmatchedVariantIdUsesParentSku() throws Exception {
		item.setVariant(999L);

		assertEquals("PARENT", populate().getSku());
	}

	private OrderProduct populate() throws Exception {
		return populator.populate(item, new OrderProduct(), store, language);
	}
}
