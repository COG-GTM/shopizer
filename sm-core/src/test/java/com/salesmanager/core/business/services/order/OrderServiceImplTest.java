package com.salesmanager.core.business.services.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.modules.order.InvoiceModule;
import com.salesmanager.core.business.repositories.order.OrderRepository;
import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.order.ordertotal.OrderTotalService;
import com.salesmanager.core.business.services.payments.PaymentService;
import com.salesmanager.core.business.services.payments.TransactionService;
import com.salesmanager.core.business.services.shipping.ShippingService;
import com.salesmanager.core.business.services.shoppingcart.ShoppingCartService;
import com.salesmanager.core.business.services.tax.TaxService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.catalog.product.price.FinalPrice;
import com.salesmanager.core.model.catalog.product.price.ProductPrice;
import com.salesmanager.core.model.catalog.product.price.ProductPriceType;
import com.salesmanager.core.model.common.UserContext;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.order.OrderSummary;
import com.salesmanager.core.model.order.OrderSummaryType;
import com.salesmanager.core.model.order.OrderTotal;
import com.salesmanager.core.model.order.OrderTotalSummary;
import com.salesmanager.core.model.order.OrderTotalType;
import com.salesmanager.core.model.order.OrderTotalVariation;
import com.salesmanager.core.model.order.RebatesOrderTotalVariation;
import com.salesmanager.core.model.order.orderproduct.OrderProduct;
import com.salesmanager.core.model.order.orderproduct.OrderProductDownload;
import com.salesmanager.core.model.order.orderstatus.OrderStatus;
import com.salesmanager.core.model.order.orderstatus.OrderStatusHistory;
import com.salesmanager.core.model.payments.Payment;
import com.salesmanager.core.model.payments.Transaction;
import com.salesmanager.core.model.payments.TransactionType;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.shipping.ShippingConfiguration;
import com.salesmanager.core.model.shipping.ShippingSummary;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;
import com.salesmanager.core.model.tax.TaxItem;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

	@Mock
	private OrderRepository orderRepository;
	@Mock
	private InvoiceModule invoiceModule;
	@Mock
	private ShippingService shippingService;
	@Mock
	private PaymentService paymentService;
	@Mock
	private ProductService productService;
	@Mock
	private TaxService taxService;
	@Mock
	private CustomerService customerService;
	@Mock
	private ShoppingCartService shoppingCartService;
	@Mock
	private TransactionService transactionService;
	@Mock
	private OrderTotalService orderTotalService;

	private OrderServiceImpl service;
	private MerchantStore store;
	private Customer customer;
	private Language language;

	@BeforeEach
	void setUp() {
		service = new OrderServiceImpl(orderRepository);
		ReflectionTestUtils.setField(service, "invoiceModule", invoiceModule);
		ReflectionTestUtils.setField(service, "shippingService", shippingService);
		ReflectionTestUtils.setField(service, "paymentService", paymentService);
		ReflectionTestUtils.setField(service, "productService", productService);
		ReflectionTestUtils.setField(service, "taxService", taxService);
		ReflectionTestUtils.setField(service, "customerService", customerService);
		ReflectionTestUtils.setField(service, "shoppingCartService", shoppingCartService);
		ReflectionTestUtils.setField(service, "transactionService", transactionService);
		ReflectionTestUtils.setField(service, "orderTotalService", orderTotalService);

		store = new MerchantStore();
		store.setId(1);
		customer = new Customer();
		customer.setId(10L);
		language = new Language("en");
	}

	@Nested
	class OrderTotalCalculation {

		@ParameterizedTest(name = "qty {0} x {1} = {2}")
		@CsvSource({
			"1, 19.99, 19.99",
			"3, 19.99, 59.97",
			"0, 19.99, 0.00",
			"1000, 19.99, 19990.00",
			"2147483647, 19.99, 42928198103.53",
			"7, 0.00, 0.00",
			"3, 0.333, 0.999",
			"-2, 19.99, -39.98"
		})
		void subTotalAndTotalAreItemPriceTimesQuantity(int quantity, String unitPrice, String expected) throws Exception {
			givenNoVariations();
			ShoppingCartItem item = item("SKU-1", unitPrice, quantity);

			OrderTotalSummary result = service.caculateOrderTotal(summary(item), customer, store, language);

			assertThat(item.getSubTotal()).isEqualByComparingTo(expected);
			assertThat(result.getSubTotal()).isEqualByComparingTo(expected);
			assertThat(result.getTotal()).isEqualByComparingTo(expected);
		}

		@Test
		void subTotalSumsAllLineItems() throws Exception {
			givenNoVariations();
			ShoppingCartItem hats = item("HAT", "10.00", 2);
			ShoppingCartItem pens = item("PEN", "0.99", 3);
			ShoppingCartItem tv = item("TV", "1234.56", 1);

			OrderTotalSummary result = service.caculateOrderTotal(summary(hats, pens, tv), customer, store, language);

			assertThat(hats.getSubTotal()).isEqualByComparingTo("20.00");
			assertThat(pens.getSubTotal()).isEqualByComparingTo("2.97");
			assertThat(tv.getSubTotal()).isEqualByComparingTo("1234.56");
			assertThat(result.getSubTotal()).isEqualByComparingTo("1257.53");
			assertThat(result.getTotal()).isEqualByComparingTo("1257.53");
		}

		@Test
		void subTotalKeepsFullPrecisionWithoutRounding() throws Exception {
			givenNoVariations();
			ShoppingCartItem item = item("SKU-1", "0.3333", 3);

			OrderTotalSummary result = service.caculateOrderTotal(summary(item), customer, store, language);

			assertThat(result.getSubTotal()).isEqualTo(new BigDecimal("0.9999"));
		}

		@Test
		void emptyOrderHasZeroSubTotalAndTotalLines() throws Exception {
			givenNoVariations();

			OrderTotalSummary result = service.caculateOrderTotal(summary(), customer, store, language);

			assertThat(result.getSubTotal()).isEqualByComparingTo("0");
			assertThat(result.getTotal()).isEqualByComparingTo("0");
			assertThat(result.getTaxTotal()).isNull();
			assertThat(result.getTotals()).extracting(OrderTotal::getOrderTotalType)
					.containsExactly(OrderTotalType.SUBTOTAL, OrderTotalType.TOTAL);
		}

		@Test
		void subTotalAndTotalLinesHaveExpectedMetadata() throws Exception {
			givenNoVariations();

			OrderTotalSummary result = service.caculateOrderTotal(summary(item("A", "5.00", 2)), customer, store, language);

			OrderTotal subTotal = line(result, OrderTotalType.SUBTOTAL);
			assertThat(subTotal.getModule()).isEqualTo(Constants.OT_SUBTOTAL_MODULE_CODE);
			assertThat(subTotal.getOrderTotalCode()).isEqualTo("order.total.subtotal");
			assertThat(subTotal.getSortOrder()).isEqualTo(5);
			assertThat(subTotal.getValue()).isEqualByComparingTo("10.00");
			OrderTotal total = line(result, OrderTotalType.TOTAL);
			assertThat(total.getModule()).isEqualTo(Constants.OT_TOTAL_MODULE_CODE);
			assertThat(total.getOrderTotalCode()).isEqualTo("order.total.total");
			assertThat(total.getSortOrder()).isEqualTo(500);
			assertThat(total.getValue()).isEqualByComparingTo("10.00");
		}

		@Test
		void customerlessOverloadPassesNullCustomerToCollaborators() throws Exception {
			when(orderTotalService.findOrderTotalVariation(any(), isNull(), eq(store), eq(language)))
					.thenReturn(new RebatesOrderTotalVariation());

			OrderTotalSummary result = service.caculateOrderTotal(summary(item("A", "4.50", 2)), store, language);

			assertThat(result.getTotal()).isEqualByComparingTo("9.00");
			verify(taxService).calculateTax(any(OrderSummary.class), isNull(), eq(store), eq(language));
		}

		@ParameterizedTest
		@EnumSource(OrderSummaryType.class)
		void variationsAreAppliedForEverySummaryType(OrderSummaryType type) throws Exception {
			givenVariations(orderTotal("2.00"));
			OrderSummary summary = summary(item("A", "10.00", 1));
			summary.setOrderSummaryType(type);

			OrderTotalSummary result = service.caculateOrderTotal(summary, customer, store, language);

			assertThat(result.getSubTotal()).isEqualByComparingTo("8.00");
		}

		@Test
		void missingSummaryTypeFailsWithServiceException() {
			OrderSummary summary = summary(item("A", "10.00", 1));
			summary.setOrderSummaryType(null);

			assertThatThrownBy(() -> service.caculateOrderTotal(summary, customer, store, language))
					.isInstanceOf(ServiceException.class);
		}

		@Test
		void nullQuantityFailsWithServiceException() {
			ShoppingCartItem item = item("A", "10.00", 1);
			item.setQuantity(null);

			assertThatThrownBy(() -> service.caculateOrderTotal(summary(item), customer, store, language))
					.isInstanceOf(ServiceException.class);
		}

		@Test
		void nullItemPriceFailsWithServiceException() {
			ShoppingCartItem item = item("A", "10.00", 1);
			item.setItemPrice(null);

			assertThatThrownBy(() -> service.caculateOrderTotal(summary(item), customer, store, language))
					.isInstanceOf(ServiceException.class);
		}

		@Test
		void requiredArgumentsAreValidated() {
			OrderSummary noProducts = new OrderSummary();
			noProducts.setOrderSummaryType(OrderSummaryType.ORDERTOTAL);
			noProducts.setProducts(null);
			OrderSummary valid = summary();

			assertThatThrownBy(() -> service.caculateOrderTotal(null, customer, store, language))
					.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> service.caculateOrderTotal(noProducts, customer, store, language))
					.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> service.caculateOrderTotal(valid, customer, null, language))
					.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> service.caculateOrderTotal(valid, null, store, language))
					.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> service.caculateOrderTotal(null, store, language))
					.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> service.caculateOrderTotal(noProducts, store, language))
					.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> service.caculateOrderTotal(valid, (MerchantStore) null, language))
					.isInstanceOf(NullPointerException.class);
			verifyNoInteractions(orderTotalService, taxService, shippingService);
		}
	}

	@Nested
	class Variations {

		@Test
		void discountVariationReducesSubTotalAndTotal() throws Exception {
			OrderTotal discount = orderTotal("5.00");
			givenVariations(discount);

			OrderTotalSummary result = service.caculateOrderTotal(summary(item("A", "25.00", 2)), customer, store, language);

			assertThat(result.getSubTotal()).isEqualByComparingTo("45.00");
			assertThat(result.getTotal()).isEqualByComparingTo("45.00");
			assertThat(result.getTotals()).contains(discount);
			assertThat(discount.getSortOrder()).isEqualTo(10);
		}

		@Test
		void multipleVariationsAreCumulativeAndSortedFromTen() throws Exception {
			OrderTotal first = orderTotal("1.50");
			OrderTotal second = orderTotal("2.25");
			OrderTotal third = orderTotal("0.25");
			givenVariations(first, second, third);

			OrderTotalSummary result = service.caculateOrderTotal(summary(item("A", "10.00", 1)), customer, store, language);

			assertThat(result.getSubTotal()).isEqualByComparingTo("6.00");
			assertThat(Arrays.asList(first.getSortOrder(), second.getSortOrder(), third.getSortOrder()))
					.containsExactly(10, 11, 12);
		}

		@Test
		void variationLargerThanSubTotalIsNotFlooredAtZero() throws Exception {
			givenVariations(orderTotal("15.00"));

			OrderTotalSummary result = service.caculateOrderTotal(summary(item("A", "10.00", 1)), customer, store, language);

			assertThat(result.getSubTotal()).isEqualByComparingTo("-5.00");
			assertThat(result.getTotal()).isEqualByComparingTo("-5.00");
		}

		@Test
		void negativeVariationIncreasesSubTotal() throws Exception {
			givenVariations(orderTotal("-3.00"));

			OrderTotalSummary result = service.caculateOrderTotal(summary(item("A", "10.00", 1)), customer, store, language);

			assertThat(result.getSubTotal()).isEqualByComparingTo("13.00");
		}

		@Test
		void emptyVariationListLeavesSubTotalUnchanged() throws Exception {
			OrderTotalVariation variation = new RebatesOrderTotalVariation();
			variation.setVariations(new ArrayList<>());
			when(orderTotalService.findOrderTotalVariation(any(), any(), any(), any())).thenReturn(variation);

			OrderTotalSummary result = service.caculateOrderTotal(summary(item("A", "10.00", 1)), customer, store, language);

			assertThat(result.getSubTotal()).isEqualByComparingTo("10.00");
		}

		@Test
		void variationServiceFailureIsWrapped() throws Exception {
			when(orderTotalService.findOrderTotalVariation(any(), any(), any(), any()))
					.thenThrow(new IllegalStateException("rules down"));

			assertThatThrownBy(() -> service.caculateOrderTotal(summary(item("A", "10.00", 1)), customer, store, language))
					.isInstanceOf(ServiceException.class).hasRootCauseMessage("rules down");
		}
	}

	@Nested
	class AdditionalPrices {

		@ParameterizedTest
		@EnumSource(ProductPriceType.class)
		void nonDefaultAdditionalPricesDoNotChangeSubTotalOrTotal(ProductPriceType type) throws Exception {
			givenNoVariations();
			ShoppingCartItem first = item("A", "10.00", 2);
			first.setFinalPrice(finalPriceWithAdditional(additionalPrice("setup", "3.00", false, type)));
			ShoppingCartItem second = item("B", "5.00", 1);
			second.setFinalPrice(finalPriceWithAdditional(
					additionalPrice("setup", "1.50", false, type),
					additionalPrice("other", "9.00", false, type)));

			OrderTotalSummary result = service.caculateOrderTotal(summary(first, second), customer, store, language);

			assertThat(result.getSubTotal()).isEqualByComparingTo("25.00");
			assertThat(result.getTotal()).isEqualByComparingTo("25.00");
			assertThat(result.getTotals()).extracting(OrderTotal::getOrderTotalType)
					.containsExactly(OrderTotalType.SUBTOTAL, OrderTotalType.TOTAL);
		}

		@Test
		void defaultAdditionalPricesAreIgnored() throws Exception {
			givenNoVariations();
			ShoppingCartItem item = item("A", "10.00", 1);
			item.setFinalPrice(finalPriceWithAdditional(additionalPrice("base", "10.00", true, ProductPriceType.ONE_TIME)));

			OrderTotalSummary result = service.caculateOrderTotal(summary(item), customer, store, language);

			assertThat(result.getSubTotal()).isEqualByComparingTo("10.00");
		}

		@Test
		void finalPriceWithoutAdditionalPricesIsSupported() throws Exception {
			givenNoVariations();
			ShoppingCartItem item = item("A", "10.00", 3);
			item.setFinalPrice(new FinalPrice());

			OrderTotalSummary result = service.caculateOrderTotal(summary(item), customer, store, language);

			assertThat(result.getSubTotal()).isEqualByComparingTo("30.00");
		}

		@Test
		void nonDefaultAdditionalPriceWithoutProductPriceFailsWithServiceException() {
			ShoppingCartItem item = item("A", "10.00", 1);
			FinalPrice orphan = new FinalPrice();
			orphan.setFinalPrice(new BigDecimal("1.00"));
			item.setFinalPrice(finalPriceWithAdditional(orphan));

			assertThatThrownBy(() -> service.caculateOrderTotal(summary(item), customer, store, language))
					.isInstanceOf(ServiceException.class);
		}
	}

	@Nested
	class ShippingAndHandling {

		@Test
		void paidShippingIsAddedToTotalButNotSubTotal() throws Exception {
			givenNoVariations();
			givenShippingConfiguration(null);
			OrderSummary summary = summary(item("A", "25.00", 2));
			summary.setShippingSummary(shipping("7.50", null, false));

			OrderTotalSummary result = service.caculateOrderTotal(summary, customer, store, language);

			assertThat(result.getSubTotal()).isEqualByComparingTo("50.00");
			assertThat(result.getTotal()).isEqualByComparingTo("57.50");
			OrderTotal shippingLine = line(result, OrderTotalType.SHIPPING);
			assertThat(shippingLine.getValue()).isEqualByComparingTo("7.50");
			assertThat(shippingLine.getSortOrder()).isEqualTo(100);
			assertThat(shippingLine.getModule()).isEqualTo(Constants.OT_SHIPPING_MODULE_CODE);
		}

		@Test
		void freeShippingContributesZeroEvenWhenAQuoteExists() throws Exception {
			givenNoVariations();
			givenShippingConfiguration(null);
			OrderSummary summary = summary(item("A", "25.00", 2));
			summary.setShippingSummary(shipping("7.50", null, true));

			OrderTotalSummary result = service.caculateOrderTotal(summary, customer, store, language);

			assertThat(line(result, OrderTotalType.SHIPPING).getValue()).isEqualByComparingTo("0");
			assertThat(result.getTotal()).isEqualByComparingTo("50.00");
		}

		@Test
		void noShippingSummaryMeansNoShippingLineAndNoConfigLookup() throws Exception {
			givenNoVariations();

			OrderTotalSummary result = service.caculateOrderTotal(summary(item("A", "25.00", 2)), customer, store, language);

			assertThat(result.getTotals()).extracting(OrderTotal::getOrderTotalType)
					.doesNotContain(OrderTotalType.SHIPPING, OrderTotalType.HANDLING);
			verifyNoInteractions(shippingService);
		}

		@Test
		void handlingIsAddedWhenQuotedAndConfigured() throws Exception {
			givenNoVariations();
			givenShippingConfiguration("2.00");
			OrderSummary summary = summary(item("A", "25.00", 2));
			summary.setShippingSummary(shipping("7.50", "2.00", false));

			OrderTotalSummary result = service.caculateOrderTotal(summary, customer, store, language);

			OrderTotal handling = line(result, OrderTotalType.HANDLING);
			assertThat(handling.getValue()).isEqualByComparingTo("2.00");
			assertThat(handling.getSortOrder()).isEqualTo(120);
			assertThat(result.getTotal()).isEqualByComparingTo("59.50");
		}

		@Test
		void handlingIsAddedEvenWithFreeShipping() throws Exception {
			givenNoVariations();
			givenShippingConfiguration("2.00");
			OrderSummary summary = summary(item("A", "25.00", 2));
			summary.setShippingSummary(shipping("7.50", "2.00", true));

			OrderTotalSummary result = service.caculateOrderTotal(summary, customer, store, language);

			assertThat(result.getTotal()).isEqualByComparingTo("52.00");
		}

		@ParameterizedTest(name = "store handling fee [{0}]")
		@ValueSource(strings = { "", "0", "0.00", "-1.00" })
		void handlingIsIgnoredWhenStoreHasNoPositiveHandlingFee(String configuredFee) throws Exception {
			givenNoVariations();
			givenShippingConfiguration(configuredFee.isEmpty() ? null : configuredFee);
			OrderSummary summary = summary(item("A", "25.00", 2));
			summary.setShippingSummary(shipping("7.50", "2.00", false));

			OrderTotalSummary result = service.caculateOrderTotal(summary, customer, store, language);

			assertThat(result.getTotals()).extracting(OrderTotal::getOrderTotalType)
					.doesNotContain(OrderTotalType.HANDLING);
			assertThat(result.getTotal()).isEqualByComparingTo("57.50");
		}

		@ParameterizedTest(name = "quoted handling [{0}]")
		@ValueSource(strings = { "", "0", "-2.00" })
		void handlingIsIgnoredWhenQuoteHasNoPositiveHandling(String quotedHandling) throws Exception {
			givenNoVariations();
			givenShippingConfiguration("2.00");
			OrderSummary summary = summary(item("A", "25.00", 2));
			summary.setShippingSummary(shipping("7.50", quotedHandling.isEmpty() ? null : quotedHandling, false));

			OrderTotalSummary result = service.caculateOrderTotal(summary, customer, store, language);

			assertThat(result.getTotals()).extracting(OrderTotal::getOrderTotalType)
					.doesNotContain(OrderTotalType.HANDLING);
			assertThat(result.getTotal()).isEqualByComparingTo("57.50");
		}
	}

	@Nested
	class Taxes {

		@Test
		void taxesAreListedIndividuallyAndSummedIntoTotal() throws Exception {
			givenNoVariations();
			when(taxService.calculateTax(any(OrderSummary.class), eq(customer), eq(store), eq(language)))
					.thenReturn(Arrays.asList(tax("GST", "5.00"), tax("PST", "2.50")));

			OrderTotalSummary result = service.caculateOrderTotal(summary(item("A", "50.00", 2)), customer, store, language);

			assertThat(result.getTaxTotal()).isEqualByComparingTo("7.50");
			assertThat(result.getSubTotal()).isEqualByComparingTo("100.00");
			assertThat(result.getTotal()).isEqualByComparingTo("107.50");
			List<OrderTotal> taxLines = new ArrayList<>();
			for (OrderTotal total : result.getTotals()) {
				if (total.getOrderTotalType() == OrderTotalType.TAX) {
					taxLines.add(total);
				}
			}
			assertThat(taxLines).extracting(OrderTotal::getOrderTotalCode).containsExactly("GST", "PST");
			assertThat(taxLines).extracting(OrderTotal::getSortOrder).containsExactly(200, 201);
		}

		@Test
		void nullTaxListMeansNoTaxTotal() throws Exception {
			givenNoVariations();
			when(taxService.calculateTax(any(), any(), any(), any())).thenReturn(null);

			OrderTotalSummary result = service.caculateOrderTotal(summary(item("A", "50.00", 1)), customer, store, language);

			assertThat(result.getTaxTotal()).isNull();
			assertThat(result.getTotal()).isEqualByComparingTo("50.00");
		}

		@Test
		void zeroTaxStillProducesTaxLine() throws Exception {
			givenNoVariations();
			when(taxService.calculateTax(any(), any(), any(), any())).thenReturn(Collections.singletonList(tax("VAT", "0.00")));

			OrderTotalSummary result = service.caculateOrderTotal(summary(item("A", "50.00", 1)), customer, store, language);

			assertThat(result.getTaxTotal()).isEqualByComparingTo("0");
			assertThat(line(result, OrderTotalType.TAX).getValue()).isEqualByComparingTo("0");
		}

		@Test
		void grandTotalCombinesSubTotalVariationShippingHandlingAndTax() throws Exception {
			givenVariations(orderTotal("10.00"));
			givenShippingConfiguration("3.00");
			when(taxService.calculateTax(any(), any(), any(), any())).thenReturn(Collections.singletonList(tax("VAT", "12.34")));
			OrderSummary summary = summary(item("A", "19.99", 3), item("B", "0.01", 1));
			summary.setShippingSummary(shipping("8.00", "3.00", false));

			OrderTotalSummary result = service.caculateOrderTotal(summary, customer, store, language);

			// 59.97 + 0.01 - 10.00 = 49.98 ; + 8.00 shipping + 3.00 handling + 12.34 tax = 73.32
			assertThat(result.getSubTotal()).isEqualByComparingTo("49.98");
			assertThat(result.getTotal()).isEqualByComparingTo("73.32");
			assertThat(line(result, OrderTotalType.TOTAL).getValue()).isEqualByComparingTo("73.32");
		}
	}

	@Nested
	class ShoppingCartTotals {

		@Test
		void unavailableProductsAreExcludedFromTotals() throws Exception {
			givenNoVariations();
			ShoppingCartItem available = item("A", "10.00", 2);
			ShoppingCartItem unavailable = item("B", "99.00", 1);
			unavailable.getProduct().setAvailable(false);
			ShoppingCart cart = cart(available, unavailable);

			OrderTotalSummary result = service.calculateShoppingCartTotal(cart, customer, store, language);

			assertThat(result.getSubTotal()).isEqualByComparingTo("20.00");
			assertThat(result.getTotal()).isEqualByComparingTo("20.00");
			assertThat(unavailable.getSubTotal()).isNull();
			assertThat(cart.getLineItems()).hasSize(2);
			ArgumentCaptor<OrderSummary> summary = ArgumentCaptor.forClass(OrderSummary.class);
			verify(taxService).calculateTax(summary.capture(), eq(customer), eq(store), eq(language));
			assertThat(summary.getValue().getProducts()).containsExactly(available);
			assertThat(summary.getValue().getOrderSummaryType()).isEqualTo(OrderSummaryType.SHOPPINGCART);
		}

		@Test
		void cartWithOnlyUnavailableProductsTotalsZero() throws Exception {
			givenNoVariations();
			ShoppingCartItem unavailable = item("B", "99.00", 3);
			unavailable.getProduct().setAvailable(false);

			OrderTotalSummary result = service.calculateShoppingCartTotal(cart(unavailable), store, language);

			assertThat(result.getSubTotal()).isEqualByComparingTo("0");
			assertThat(result.getTotal()).isEqualByComparingTo("0");
		}

		@Test
		void emptyCartTotalsZero() throws Exception {
			givenNoVariations();

			OrderTotalSummary result = service.calculateShoppingCartTotal(cart(), customer, store, language);

			assertThat(result.getTotal()).isEqualByComparingTo("0");
		}

		@Test
		void largeQuantitiesInCartAreSummedExactly() throws Exception {
			givenNoVariations();
			ShoppingCart cart = cart(item("A", "0.01", Integer.MAX_VALUE), item("B", "999999.99", 1000));

			OrderTotalSummary result = service.calculateShoppingCartTotal(cart, customer, store, language);

			// 21474836.47 + 999999990.00
			assertThat(result.getTotal()).isEqualByComparingTo("1021474826.47");
		}

		@Test
		void cartWithoutPromoCodeCarriesNoPromo() throws Exception {
			givenNoVariations();

			service.calculateShoppingCartTotal(cart(item("A", "10.00", 1)), customer, store, language);

			assertThat(capturedSummary().getPromoCode()).isNull();
			verify(shoppingCartService, never()).saveOrUpdate(any(ShoppingCart.class));
		}

		@Test
		void promoAddedTodayIsApplied() throws Exception {
			givenNoVariations();
			ShoppingCart cart = cart(item("A", "10.00", 1));
			cart.setPromoCode("SAVE10");
			cart.setPromoAdded(new Date());

			service.calculateShoppingCartTotal(cart, customer, store, language);

			assertThat(capturedSummary().getPromoCode()).isEqualTo("SAVE10");
			assertThat(cart.getPromoCode()).isEqualTo("SAVE10");
			verify(shoppingCartService, never()).saveOrUpdate(any(ShoppingCart.class));
		}

		@Test
		void promoWithoutAddedDateIsTreatedAsAddedNow() throws Exception {
			givenNoVariations();
			ShoppingCart cart = cart(item("A", "10.00", 1));
			cart.setPromoCode("SAVE10");
			cart.setPromoAdded(null);

			service.calculateShoppingCartTotal(cart, customer, store, language);

			assertThat(capturedSummary().getPromoCode()).isEqualTo("SAVE10");
		}

		@Test
		void promoAddedInThePastIsStillApplied() throws Exception {
			givenNoVariations();
			ShoppingCart cart = cart(item("A", "10.00", 1));
			cart.setPromoCode("SAVE10");
			cart.setPromoAdded(daysFromToday(-30));

			service.calculateShoppingCartTotal(cart, customer, store, language);

			assertThat(capturedSummary().getPromoCode()).isEqualTo("SAVE10");
			verify(shoppingCartService, never()).saveOrUpdate(any(ShoppingCart.class));
		}

		@Test
		void promoDatedTomorrowOrLaterIsClearedAndCartSaved() throws Exception {
			givenNoVariations();
			ShoppingCart cart = cart(item("A", "10.00", 1));
			cart.setPromoCode("SAVE10");
			cart.setPromoAdded(daysFromToday(1));

			service.calculateShoppingCartTotal(cart, customer, store, language);

			assertThat(capturedSummary().getPromoCode()).isNull();
			assertThat(cart.getPromoCode()).isNull();
			verify(shoppingCartService).saveOrUpdate(cart);
		}

		@Test
		void blankPromoCodeIsIgnored() throws Exception {
			givenNoVariations();
			ShoppingCart cart = cart(item("A", "10.00", 1));
			cart.setPromoCode("   ");
			cart.setPromoAdded(daysFromToday(5));

			service.calculateShoppingCartTotal(cart, customer, store, language);

			assertThat(capturedSummary().getPromoCode()).isNull();
			verify(shoppingCartService, never()).saveOrUpdate(any(ShoppingCart.class));
		}

		@Test
		void requiredArgumentsAreValidated() {
			ShoppingCart cart = cart();

			assertThatThrownBy(() -> service.calculateShoppingCartTotal(null, customer, store, language))
					.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> service.calculateShoppingCartTotal(cart, null, store, language))
					.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> service.calculateShoppingCartTotal(cart, customer, null, language))
					.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> service.calculateShoppingCartTotal(null, store, language))
					.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> service.calculateShoppingCartTotal(cart, (MerchantStore) null, language))
					.isInstanceOf(NullPointerException.class);
		}

		@Test
		void calculationFailureIsWrappedInServiceException() throws Exception {
			when(orderTotalService.findOrderTotalVariation(any(), any(), any(), any()))
					.thenThrow(new IllegalStateException("boom"));

			assertThatThrownBy(() -> service.calculateShoppingCartTotal(cart(item("A", "1.00", 1)), store, language))
					.isInstanceOf(ServiceException.class).hasRootCauseMessage("boom");
		}

		private OrderSummary capturedSummary() throws ServiceException {
			ArgumentCaptor<OrderSummary> summary = ArgumentCaptor.forClass(OrderSummary.class);
			verify(taxService).calculateTax(summary.capture(), any(), any(), any());
			return summary.getValue();
		}
	}

	@Nested
	class ProcessOrder {

		private Order order;
		private Payment payment;
		private List<ShoppingCartItem> items;
		private OrderTotalSummary totals;

		@BeforeEach
		void setUpOrder() {
			order = new Order();
			payment = new Payment();
			items = Collections.singletonList(item("A", "10.00", 1));
			totals = new OrderTotalSummary();
		}

		@ParameterizedTest(name = "stock {0} - ordered {1} = {2}")
		@CsvSource({
			"10, 3, 7",
			"5, 5, 0",
			"1, 0, 1",
			"2, 5, -3",
			"0, 1, -1"
		})
		void inventoryIsDecrementedByOrderedQuantity(int stock, int ordered, int remaining) throws Exception {
			ProductAvailability availability = availability(stock);
			Product product = productWith(availability);
			order.getOrderProducts().add(orderProduct(50L, ordered));
			when(productService.getById(50L)).thenReturn(product);

			service.processOrder(order, customer, items, totals, payment, store);

			assertThat(availability.getProductQuantity()).isEqualTo(remaining);
			verify(productService).update(product);
		}

		@Test
		void everyAvailabilityOfAProductIsDecremented() throws Exception {
			ProductAvailability warehouseA = availability(10);
			ProductAvailability warehouseB = availability(4);
			warehouseA.setId(1L);
			warehouseB.setId(2L);
			order.getOrderProducts().add(orderProduct(50L, 3));
			when(productService.getById(50L)).thenReturn(productWith(warehouseA, warehouseB));

			service.processOrder(order, customer, items, totals, payment, store);

			assertThat(warehouseA.getProductQuantity()).isEqualTo(7);
			assertThat(warehouseB.getProductQuantity()).isEqualTo(1);
		}

		@Test
		void eachOrderLineDecrementsItsOwnProduct() throws Exception {
			ProductAvailability hats = availability(10);
			ProductAvailability shoes = availability(20);
			order.getOrderProducts().add(orderProduct(50L, 2));
			order.getOrderProducts().add(orderProduct(51L, 5));
			when(productService.getById(50L)).thenReturn(productWith(hats));
			when(productService.getById(51L)).thenReturn(productWith(shoes));

			service.processOrder(order, customer, items, totals, payment, store);

			assertThat(hats.getProductQuantity()).isEqualTo(8);
			assertThat(shoes.getProductQuantity()).isEqualTo(15);
		}

		@Test
		void missingProductFailsWithInventoryMismatchAfterOrderIsCreated() throws Exception {
			order.getOrderProducts().add(orderProduct(50L, 1));
			when(productService.getById(50L)).thenReturn(null);

			assertThatThrownBy(() -> service.processOrder(order, customer, items, totals, payment, store))
					.isInstanceOfSatisfying(ServiceException.class, e -> assertThat(e.getExceptionType())
							.isEqualTo(ServiceException.EXCEPTION_INVENTORY_MISMATCH));
			verify(orderRepository).saveAndFlush(order);
			verify(productService, never()).update(any(Product.class));
		}

		@Test
		void orderWithoutOrderProductsSkipsInventory() throws Exception {
			Order result = service.processOrder(order, customer, items, totals, payment, store);

			assertThat(result).isSameAs(order);
			verify(productService, never()).getById(anyLong());
			verify(orderRepository).saveAndFlush(order);
		}

		@Test
		void newOrderDefaultsToOrderedStatusWithHistory() throws Exception {
			service.processOrder(order, customer, items, totals, payment, store);

			assertThat(order.getStatus()).isEqualTo(OrderStatus.ORDERED);
			assertThat(order.getOrderHistory()).singleElement().satisfies(history -> {
				assertThat(history.getStatus()).isEqualTo(OrderStatus.ORDERED);
				assertThat(history.getOrder()).isSameAs(order);
				assertThat(history.getDateAdded()).isNotNull();
			});
		}

		@Test
		void presetStatusIsKeptAndRecordedInHistory() throws Exception {
			order.setStatus(OrderStatus.PROCESSED);

			service.processOrder(order, customer, items, totals, payment, store);

			assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSED);
			assertThat(order.getOrderHistory()).extracting(OrderStatusHistory::getStatus)
					.containsExactly(OrderStatus.PROCESSED);
		}

		@Test
		void existingHistoryAndStatusAreLeftUntouched() throws Exception {
			order.setStatus(OrderStatus.DELIVERED);
			OrderStatusHistory existing = new OrderStatusHistory();
			existing.setStatus(OrderStatus.DELIVERED);
			Set<OrderStatusHistory> history = new HashSet<>(Collections.singleton(existing));
			order.setOrderHistory(history);

			service.processOrder(order, customer, items, totals, payment, store);

			assertThat(order.getOrderHistory()).isSameAs(history).containsExactly(existing);
		}

		@ParameterizedTest(name = "customer id {0}")
		@CsvSource(value = { "NULL", "0" }, nullValues = "NULL")
		void unsavedCustomerIsCreatedBeforeTheOrder(Long customerId) throws Exception {
			customer.setId(customerId);
			doAnswer(invocation -> {
				invocation.<Customer>getArgument(0).setId(77L);
				return null;
			}).when(customerService).create(customer);

			service.processOrder(order, customer, items, totals, payment, store);

			assertThat(order.getCustomerId()).isEqualTo(77L);
		}

		@Test
		void existingCustomerIsNotRecreated() throws Exception {
			service.processOrder(order, customer, items, totals, payment, store);

			verify(customerService, never()).create(any(Customer.class));
			assertThat(order.getCustomerId()).isEqualTo(10L);
		}

		@Test
		void paymentTransactionIsLinkedToOrderAndCreated() throws Exception {
			Transaction processed = new Transaction();
			when(paymentService.processPayment(customer, store, payment, items, order)).thenReturn(processed);

			service.processOrder(order, customer, items, totals, payment, store);

			assertThat(processed.getOrder()).isSameAs(order);
			verify(transactionService).create(processed);
		}

		@Test
		void suppliedNewTransactionIsCreatedAndExistingOneUpdated() throws Exception {
			Transaction supplied = new Transaction();
			Transaction processed = new Transaction();
			processed.setId(9L);
			when(paymentService.processPayment(customer, store, payment, items, order)).thenReturn(processed);

			service.processOrder(order, customer, items, totals, payment, supplied, store);

			assertThat(supplied.getOrder()).isSameAs(order);
			verify(transactionService).create(supplied);
			verify(transactionService).update(processed);
		}

		@Test
		void paymentFailureAbortsBeforeOrderIsPersisted() throws Exception {
			when(paymentService.processPayment(customer, store, payment, items, order))
					.thenThrow(new ServiceException(ServiceException.EXCEPTION_PAYMENT_DECLINED));

			assertThatThrownBy(() -> service.processOrder(order, customer, items, totals, payment, store))
					.isInstanceOf(ServiceException.class);
			verifyNoInteractions(orderRepository, productService, transactionService);
		}

		@Test
		void ipAddressIsCapturedFromUserContext() throws Exception {
			try (UserContext context = UserContext.create()) {
				context.setIpAddress("10.0.0.7");

				service.processOrder(order, customer, items, totals, payment, store);
			}

			assertThat(order.getIpAddress()).isEqualTo("10.0.0.7");
		}

		@Test
		void requiredArgumentsAreValidated() {
			List<ShoppingCartItem> none = Collections.emptyList();

			assertThatThrownBy(() -> service.processOrder(null, customer, items, totals, payment, store))
					.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> service.processOrder(order, null, items, totals, payment, store))
					.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> service.processOrder(order, customer, none, totals, payment, store))
					.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> service.processOrder(order, customer, null, totals, payment, store))
					.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> service.processOrder(order, customer, items, totals, null, store))
					.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> service.processOrder(order, customer, items, totals, payment, null))
					.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> service.processOrder(order, customer, items, null, payment, store))
					.isInstanceOf(NullPointerException.class);
			verifyNoInteractions(paymentService, orderRepository);
		}
	}

	@Nested
	class CapturableOrders {

		private final Date start = new Date(0);
		private final Date end = new Date();

		@Test
		void noTransactionsReturnsNull() throws Exception {
			when(transactionService.listTransactions(start, end)).thenReturn(Collections.emptyList());

			assertThat(service.getCapturableOrders(store, start, end)).isNull();
		}

		@Test
		void authorizedOnlyOrderIsCapturable() throws Exception {
			Order order = order(1L);
			when(transactionService.listTransactions(start, end))
					.thenReturn(Collections.singletonList(transaction(order, TransactionType.AUTHORIZE)));

			assertThat(service.getCapturableOrders(store, start, end)).containsExactly(order);
		}

		@ParameterizedTest
		@EnumSource(value = TransactionType.class, names = { "CAPTURE", "AUTHORIZECAPTURE", "REFUND" })
		void authorizedOrderIsNotCapturableOnceSettled(TransactionType settlement) throws Exception {
			Order order = order(1L);
			when(transactionService.listTransactions(start, end)).thenReturn(Arrays.asList(
					transaction(order, TransactionType.AUTHORIZE), transaction(order, settlement)));

			assertThat(service.getCapturableOrders(store, start, end)).isEmpty();
		}

		@Test
		void onlyUnsettledOrdersAreReturnedFromAMixedBatch() throws Exception {
			Order pending = order(1L);
			Order captured = order(2L);
			when(transactionService.listTransactions(start, end)).thenReturn(Arrays.asList(
					transaction(pending, TransactionType.AUTHORIZE),
					transaction(captured, TransactionType.AUTHORIZE),
					transaction(captured, TransactionType.CAPTURE)));

			assertThat(service.getCapturableOrders(store, start, end)).containsExactly(pending);
		}

		@Test
		void orderWithoutAuthorizationYieldsNullEntry() throws Exception {
			when(transactionService.listTransactions(start, end))
					.thenReturn(Collections.singletonList(transaction(order(3L), TransactionType.INIT)));

			assertThat(service.getCapturableOrders(store, start, end)).containsExactly((Order) null);
		}
	}

	@Nested
	class DownloadsAndLookup {

		@Test
		void orderWithADownloadableLineHasDownloads() throws Exception {
			Order order = new Order();
			OrderProduct physical = orderProduct(1L, 1);
			OrderProduct digital = orderProduct(2L, 1);
			digital.setDownloads(new HashSet<>(Collections.singleton(new OrderProductDownload())));
			order.getOrderProducts().add(physical);
			order.getOrderProducts().add(digital);

			assertThat(service.hasDownloadFiles(order)).isTrue();
		}

		@Test
		void orderWithoutDownloadsHasNone() throws Exception {
			Order order = new Order();
			order.getOrderProducts().add(orderProduct(1L, 1));

			assertThat(service.hasDownloadFiles(order)).isFalse();
		}

		@Test
		void orderWithoutProductsIsRejected() {
			assertThatThrownBy(() -> service.hasDownloadFiles(new Order())).isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> service.hasDownloadFiles(null)).isInstanceOf(NullPointerException.class);
		}

		@Test
		void getOrderIsScopedToStore() {
			Order order = order(5L);
			when(orderRepository.findOne(5L, 1)).thenReturn(order);

			assertThat(service.getOrder(5L, store)).isSameAs(order);
			assertThatThrownBy(() -> service.getOrder(null, store)).isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> service.getOrder(5L, null)).isInstanceOf(NullPointerException.class);
		}
	}

	private void givenNoVariations() throws Exception {
		when(orderTotalService.findOrderTotalVariation(any(), any(), any(), any())).thenReturn(new RebatesOrderTotalVariation());
	}

	private void givenVariations(OrderTotal... variations) throws Exception {
		OrderTotalVariation variation = new RebatesOrderTotalVariation();
		variation.setVariations(new ArrayList<>(Arrays.asList(variations)));
		when(orderTotalService.findOrderTotalVariation(any(), any(), any(), any())).thenReturn(variation);
	}

	private void givenShippingConfiguration(String handlingFees) throws ServiceException {
		ShippingConfiguration configuration = new ShippingConfiguration();
		configuration.setHandlingFees(handlingFees == null ? null : new BigDecimal(handlingFees));
		when(shippingService.getShippingConfiguration(store)).thenReturn(configuration);
	}

	private static OrderTotal line(OrderTotalSummary summary, OrderTotalType type) {
		return summary.getTotals().stream().filter(t -> t.getOrderTotalType() == type).findFirst()
				.orElseThrow(() -> new AssertionError("No " + type + " line in " + summary.getTotals()));
	}

	private static OrderSummary summary(ShoppingCartItem... items) {
		OrderSummary summary = new OrderSummary();
		summary.setOrderSummaryType(OrderSummaryType.ORDERTOTAL);
		summary.setProducts(new ArrayList<>(Arrays.asList(items)));
		return summary;
	}

	private ShoppingCart cart(ShoppingCartItem... items) {
		ShoppingCart cart = new ShoppingCart();
		cart.setMerchantStore(store);
		Set<ShoppingCartItem> lineItems = new LinkedHashSet<>();
		long id = 1;
		for (ShoppingCartItem item : items) {
			item.setId(id++);
			item.setShoppingCart(cart);
			lineItems.add(item);
		}
		cart.setLineItems(lineItems);
		return cart;
	}

	private static ShoppingCartItem item(String sku, String unitPrice, int quantity) {
		Product product = new Product();
		product.setSku(sku);
		product.setAvailable(true);
		ShoppingCartItem item = new ShoppingCartItem(product);
		item.setSku(sku);
		item.setItemPrice(new BigDecimal(unitPrice));
		item.setQuantity(quantity);
		return item;
	}

	private static FinalPrice finalPriceWithAdditional(FinalPrice... additional) {
		FinalPrice price = new FinalPrice();
		price.setAdditionalPrices(Arrays.asList(additional));
		return price;
	}

	private static FinalPrice additionalPrice(String code, String amount, boolean isDefault, ProductPriceType type) {
		ProductPrice productPrice = new ProductPrice();
		productPrice.setCode(code);
		productPrice.setProductPriceType(type);
		FinalPrice price = new FinalPrice();
		price.setProductPrice(productPrice);
		price.setFinalPrice(new BigDecimal(amount));
		price.setDefaultPrice(isDefault);
		return price;
	}

	private static ShippingSummary shipping(String shipping, String handling, boolean free) {
		ShippingSummary summary = new ShippingSummary();
		summary.setShipping(new BigDecimal(shipping));
		summary.setHandling(handling == null ? null : new BigDecimal(handling));
		summary.setFreeShipping(free);
		return summary;
	}

	private static TaxItem tax(String label, String amount) {
		TaxItem tax = new TaxItem();
		tax.setLabel(label);
		tax.setItemPrice(new BigDecimal(amount));
		return tax;
	}

	private static OrderTotal orderTotal(String value) {
		OrderTotal total = new OrderTotal();
		total.setValue(new BigDecimal(value));
		return total;
	}

	private static Date daysFromToday(int days) {
		return Date.from(LocalDate.now().plusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant());
	}

	private static ProductAvailability availability(int quantity) {
		ProductAvailability availability = new ProductAvailability();
		availability.setProductQuantity(quantity);
		return availability;
	}

	private static Product productWith(ProductAvailability... availabilities) {
		Product product = new Product();
		product.setAvailabilities(new LinkedHashSet<>(Arrays.asList(availabilities)));
		return product;
	}

	private static OrderProduct orderProduct(Long id, int quantity) {
		OrderProduct orderProduct = new OrderProduct();
		orderProduct.setId(id);
		orderProduct.setProductQuantity(quantity);
		return orderProduct;
	}

	private static Order order(Long id) {
		Order order = new Order();
		order.setId(id);
		return order;
	}

	private static Transaction transaction(Order order, TransactionType type) {
		Transaction transaction = new Transaction();
		transaction.setOrder(order);
		transaction.setTransactionType(type);
		return transaction;
	}
}
