package com.salesmanager.core.business.services.order.ordertotal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.description.ProductDescription;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.OrderSummary;
import com.salesmanager.core.model.order.OrderSummaryType;
import com.salesmanager.core.model.order.OrderTotal;
import com.salesmanager.core.model.order.OrderTotalVariation;
import com.salesmanager.core.model.order.RebatesOrderTotalVariation;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;
import com.salesmanager.core.modules.order.total.OrderTotalPostProcessorModule;

@ExtendWith(MockitoExtension.class)
class OrderTotalServiceImplTest {

	@Mock
	private ProductService productService;
	@Mock
	private OrderTotalPostProcessorModule discountModule;
	@Mock
	private OrderTotalPostProcessorModule rebateModule;

	private OrderTotalServiceImpl service;
	private MerchantStore store;
	private Customer customer;
	private Language language;

	@BeforeEach
	void setUp() {
		service = new OrderTotalServiceImpl();
		ReflectionTestUtils.setField(service, "productService", productService);
		store = new MerchantStore();
		customer = new Customer();
		language = new Language("en");
	}

	@Test
	void noRegisteredModulesYieldsEmptyVariation() throws Exception {
		ReflectionTestUtils.setField(service, "orderTotalPostProcessors", null);

		OrderTotalVariation variation = service.findOrderTotalVariation(summary(item("SKU-1")), customer, store, language);

		assertThat(variation).isInstanceOf(RebatesOrderTotalVariation.class);
		assertThat(variation.getVariations()).isNull();
		verifyNoInteractions(productService);
	}

	@Test
	void emptyModuleListYieldsEmptyVariation() throws Exception {
		registerModules();

		OrderTotalVariation variation = service.findOrderTotalVariation(summary(item("SKU-1")), customer, store, language);

		assertThat(variation.getVariations()).isNull();
		verifyNoInteractions(productService);
	}

	@Test
	void modulesReturningNullProduceNoVariations() throws Exception {
		registerModules(discountModule);
		ShoppingCartItem item = item("SKU-1");
		OrderSummary summary = summary(item);
		Product product = product("Hat");
		when(productService.getBySku("SKU-1", store, language)).thenReturn(product);
		when(discountModule.caculateProductPiceVariation(summary, item, product, customer, store)).thenReturn(null);

		OrderTotalVariation variation = service.findOrderTotalVariation(summary, customer, store, language);

		assertThat(variation.getVariations()).isNull();
	}

	@Test
	void emptyCartProducesNoVariationsAndNoLookups() throws Exception {
		registerModules(discountModule);

		OrderTotalVariation variation = service.findOrderTotalVariation(summary(), customer, store, language);

		assertThat(variation.getVariations()).isNull();
		verifyNoInteractions(productService, discountModule);
	}

	@Test
	void variationWithoutTextIsLabelledWithProductName() throws Exception {
		registerModules(discountModule);
		ShoppingCartItem item = item("SKU-1");
		OrderSummary summary = summary(item);
		Product product = product("Summer hat");
		OrderTotal discount = orderTotal("5.00", null);
		when(productService.getBySku("SKU-1", store, language)).thenReturn(product);
		when(discountModule.caculateProductPiceVariation(summary, item, product, customer, store)).thenReturn(discount);

		OrderTotalVariation variation = service.findOrderTotalVariation(summary, customer, store, language);

		assertThat(variation.getVariations()).containsExactly(discount);
		assertThat(discount.getText()).isEqualTo("Summer hat");
		assertThat(discount.getValue()).isEqualByComparingTo("5.00");
	}

	@Test
	void variationWithTextKeepsModuleText() throws Exception {
		registerModules(discountModule);
		ShoppingCartItem item = item("SKU-1");
		OrderSummary summary = summary(item);
		Product product = product("Summer hat");
		OrderTotal discount = orderTotal("5.00", "10% off");
		when(productService.getBySku("SKU-1", store, language)).thenReturn(product);
		when(discountModule.caculateProductPiceVariation(summary, item, product, customer, store)).thenReturn(discount);

		OrderTotalVariation variation = service.findOrderTotalVariation(summary, customer, store, language);

		assertThat(variation.getVariations()).extracting(OrderTotal::getText).containsExactly("10% off");
	}

	@Test
	void everyModuleIsAppliedToEveryItemInModuleOrder() throws Exception {
		registerModules(discountModule, rebateModule);
		ShoppingCartItem hat = item("HAT");
		ShoppingCartItem shoe = item("SHOE");
		OrderSummary summary = summary(hat, shoe);
		Product hatProduct = product("Hat");
		Product shoeProduct = product("Shoe");
		when(productService.getBySku("HAT", store, language)).thenReturn(hatProduct);
		when(productService.getBySku("SHOE", store, language)).thenReturn(shoeProduct);
		OrderTotal hatDiscount = orderTotal("1.00", "d-hat");
		OrderTotal shoeDiscount = orderTotal("2.00", "d-shoe");
		OrderTotal hatRebate = orderTotal("0.50", "r-hat");
		when(discountModule.caculateProductPiceVariation(summary, hat, hatProduct, customer, store)).thenReturn(hatDiscount);
		when(discountModule.caculateProductPiceVariation(summary, shoe, shoeProduct, customer, store)).thenReturn(shoeDiscount);
		when(rebateModule.caculateProductPiceVariation(summary, hat, hatProduct, customer, store)).thenReturn(hatRebate);
		when(rebateModule.caculateProductPiceVariation(summary, shoe, shoeProduct, customer, store)).thenReturn(null);

		OrderTotalVariation variation = service.findOrderTotalVariation(summary, customer, store, language);

		assertThat(variation.getVariations()).containsExactly(hatDiscount, shoeDiscount, hatRebate);
	}

	@Test
	void nullCustomerIsPassedThroughToModules() throws Exception {
		registerModules(discountModule);
		ShoppingCartItem item = item("SKU-1");
		OrderSummary summary = summary(item);
		Product product = product("Hat");
		OrderTotal discount = orderTotal("1.00", "anon");
		when(productService.getBySku("SKU-1", store, language)).thenReturn(product);
		when(discountModule.caculateProductPiceVariation(summary, item, product, null, store)).thenReturn(discount);

		OrderTotalVariation variation = service.findOrderTotalVariation(summary, null, store, language);

		assertThat(variation.getVariations()).containsExactly(discount);
	}

	@Test
	void moduleFailurePropagates() throws Exception {
		registerModules(discountModule);
		ShoppingCartItem item = item("SKU-1");
		when(productService.getBySku("SKU-1", store, language)).thenReturn(product("Hat"));
		when(discountModule.caculateProductPiceVariation(any(), any(), any(), any(), any()))
				.thenThrow(new IllegalStateException("rule engine down"));

		assertThatThrownBy(() -> service.findOrderTotalVariation(summary(item), customer, store, language))
				.isInstanceOf(IllegalStateException.class).hasMessage("rule engine down");
	}

	private void registerModules(OrderTotalPostProcessorModule... modules) {
		ReflectionTestUtils.setField(service, "orderTotalPostProcessors", Arrays.asList(modules));
	}

	private static OrderSummary summary(ShoppingCartItem... items) {
		OrderSummary summary = new OrderSummary();
		summary.setOrderSummaryType(OrderSummaryType.SHOPPINGCART);
		summary.setProducts(items.length == 0 ? Collections.emptyList() : Arrays.asList(items));
		return summary;
	}

	private static ShoppingCartItem item(String sku) {
		ShoppingCartItem item = new ShoppingCartItem();
		item.setSku(sku);
		item.setQuantity(1);
		item.setItemPrice(BigDecimal.TEN);
		return item;
	}

	private static Product product(String name) {
		ProductDescription description = new ProductDescription();
		description.setName(name);
		Product product = new Product();
		product.setDescriptions(new HashSet<>(Collections.singleton(description)));
		return product;
	}

	private static OrderTotal orderTotal(String value, String text) {
		OrderTotal total = new OrderTotal();
		total.setValue(new BigDecimal(value));
		total.setText(text);
		return total;
	}
}
