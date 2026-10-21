package com.salesmanager.test.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.constants.Constants;
import com.paypal.sdk.Environment;
import com.paypal.sdk.PaypalServerSdkClient;
import com.paypal.sdk.authentication.ClientCredentialsAuthModel;
import com.paypal.sdk.controllers.OrdersController;
import com.paypal.sdk.http.Headers;
import com.paypal.sdk.http.response.ApiResponse;
import com.paypal.sdk.models.CheckoutPaymentIntent;
import com.paypal.sdk.models.CreateOrderInput;
import com.paypal.sdk.models.OrderRequest;
import com.paypal.sdk.models.PurchaseUnitRequest;
import com.salesmanager.core.business.modules.integration.payment.impl.PayPalExpressCheckoutPayment;
import com.salesmanager.core.business.services.catalog.pricing.PricingService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.description.ProductDescription;
import com.salesmanager.core.model.catalog.product.price.FinalPrice;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.OrderTotalSummary;
import com.salesmanager.core.model.payments.Payment;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.core.model.payments.Transaction;
import com.salesmanager.core.model.payments.TransactionType;
import com.salesmanager.core.model.reference.currency.Currency;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.modules.integration.IntegrationException;

public class PayPalOrdersV2MigrationTest {

  @Test
  public void validatesRestAppCredentialNames() throws Exception {
    IntegrationConfiguration configuration = new IntegrationConfiguration();
    configuration.setIntegrationKeys(Collections.<String, String>emptyMap());
    try {
      new PayPalExpressCheckoutPayment().validateModuleConfiguration(configuration, null);
    } catch (IntegrationException e) {
      assertEquals(java.util.Arrays.asList("clientId", "clientSecret"), e.getErrorFields());
      return;
    }
    throw new AssertionError("Expected validation to fail");
  }

  @Test
  public void constructsRealClientWithoutMakingARequest() {
    IntegrationConfiguration configuration = configuration();
    ExposedPayment payment = new ExposedPayment();

    PaypalServerSdkClient client = payment.createClient(configuration);

    assertNotNull(client);
    assertEquals(Environment.SANDBOX, client.getEnvironment());
    ClientCredentialsAuthModel credentials = client.getClientCredentialsAuthModel();
    assertEquals("test-client", credentials.getOAuthClientId());
    assertEquals("test-secret", credentials.getOAuthClientSecret());

    configuration.setEnvironment(Constants.PRODUCTION_ENVIRONMENT);
    assertEquals(Environment.PRODUCTION, payment.createClient(configuration).getEnvironment());
  }

  @Test
  public void createsCaptureOrderAndMapsPaypalDebugHeader() throws Exception {
    IntegrationConfiguration configuration = configuration();
    configuration.getIntegrationKeys().put("transaction", "AUTHORIZECAPTURE");
    OrderTotalSummary summary = summary("12.00", "12.00");

    OrderInitialization initialized = initializeOrder(
        Collections.singletonList(cartItem("12.00")), summary, configuration);

    OrderRequest orderRequest = initialized.orderRequest;
    Transaction transaction = initialized.transaction;
    assertEquals(CheckoutPaymentIntent.CAPTURE, orderRequest.getIntent());
    PurchaseUnitRequest purchaseUnit = orderRequest.getPurchaseUnits().get(0);
    assertEquals("12.00", purchaseUnit.getAmount().getValue());
    assertEquals("12.00", purchaseUnit.getAmount().getBreakdown().getItemTotal().getValue());
    assertEquals(TransactionType.INIT, transaction.getTransactionType());
    assertEquals(PaymentType.PAYPAL, transaction.getPaymentType());
    assertEquals("order-123", transaction.getTransactionDetails().get("TOKEN"));
    assertEquals("debug-123", transaction.getTransactionDetails().get("CORRELATION"));
  }

  @Test
  public void defaultsMissingTransactionKeyToCapture() throws Exception {
    OrderInitialization initialized = initializeOrder(Collections.emptyList(),
        summary("12.00", "12.00"), configuration());

    assertEquals(CheckoutPaymentIntent.CAPTURE, initialized.orderRequest.getIntent());
  }

  @Test
  public void usesCartItemSumAndDiscountInAmountBreakdown() throws Exception {
    IntegrationConfiguration configuration = configuration();
    configuration.getIntegrationKeys().put("transaction", "AUTHORIZE");
    OrderTotalSummary summary = summary("15.00", "15.00");
    OrderInitialization initialized = initializeOrder(
        Arrays.asList(cartItem("10.00"), cartItem("10.00")), summary, configuration);

    PurchaseUnitRequest purchaseUnit = initialized.orderRequest.getPurchaseUnits().get(0);
    assertEquals(CheckoutPaymentIntent.AUTHORIZE, initialized.orderRequest.getIntent());
    assertEquals("15.00", purchaseUnit.getAmount().getValue());
    assertEquals("20.00", purchaseUnit.getAmount().getBreakdown().getItemTotal().getValue());
    assertEquals("5.00", purchaseUnit.getAmount().getBreakdown().getDiscount().getValue());
  }

  private ShoppingCartItem cartItem(String unitPrice) {
    ShoppingCartItem item = mock(ShoppingCartItem.class);
    FinalPrice finalPrice = new FinalPrice();
    finalPrice.setFinalPrice(new BigDecimal(unitPrice));
    when(item.getFinalPrice()).thenReturn(finalPrice);
    when(item.getQuantity()).thenReturn(1);
    Product product = mock(Product.class);
    ProductDescription description = mock(ProductDescription.class);
    when(description.getName()).thenReturn("item");
    when(item.getProduct()).thenReturn(product);
    when(product.getProductDescription()).thenReturn(description);
    return item;
  }

  private OrderInitialization initializeOrder(List<ShoppingCartItem> items, OrderTotalSummary summary,
      IntegrationConfiguration configuration) throws Exception {
    OrdersController orders = mock(OrdersController.class);
    PaypalServerSdkClient client = mock(PaypalServerSdkClient.class);
    when(client.getOrdersController()).thenReturn(orders);
    com.paypal.sdk.models.Order createdOrder = new com.paypal.sdk.models.Order();
    createdOrder.setId("order-123");
    Headers headers = new Headers();
    headers.add("paypal-debug-id", "debug-123");
    when(orders.createOrderAsync(any(CreateOrderInput.class))).thenReturn(
        CompletableFuture.completedFuture(
            new ApiResponse<com.paypal.sdk.models.Order>(201, headers, createdOrder)));

    PricingService pricingService = mock(PricingService.class);
    MerchantStore store = new MerchantStore();
    Currency currency = new Currency();
    currency.setCurrency(java.util.Currency.getInstance("USD"));
    store.setCurrency(currency);
    when(pricingService.getStringAmount(any(BigDecimal.class), any(MerchantStore.class)))
        .thenAnswer(invocation -> ((BigDecimal) invocation.getArgument(0)).toPlainString());

    PayPalExpressCheckoutPayment paymentModule = new PayPalExpressCheckoutPayment() {
      @Override
      protected PaypalServerSdkClient client(IntegrationConfiguration configuration) {
        return client;
      }
    };
    ReflectionTestUtils.setField(paymentModule, "pricingService", pricingService);
    Payment payment = new Payment();

    Transaction transaction = paymentModule.initPaypalTransaction(store, items,
        summary, payment, configuration, null);

    org.mockito.ArgumentCaptor<CreateOrderInput> input =
        org.mockito.ArgumentCaptor.forClass(CreateOrderInput.class);
    org.mockito.Mockito.verify(orders).createOrderAsync(input.capture());
    OrderRequest orderRequest = input.getValue().getBody();
    return new OrderInitialization(orderRequest, transaction);
  }

  private OrderTotalSummary summary(String subTotal, String total) {
    OrderTotalSummary summary = new OrderTotalSummary();
    summary.setSubTotal(new BigDecimal(subTotal));
    summary.setTotal(new BigDecimal(total));
    return summary;
  }

  private IntegrationConfiguration configuration() {
    IntegrationConfiguration configuration = new IntegrationConfiguration();
    Map<String, String> keys = new HashMap<String, String>();
    keys.put("clientId", "test-client");
    keys.put("clientSecret", "test-secret");
    configuration.setIntegrationKeys(keys);
    configuration.setEnvironment("SANDBOX");
    return configuration;
  }

  private static class ExposedPayment extends PayPalExpressCheckoutPayment {
    PaypalServerSdkClient createClient(IntegrationConfiguration configuration) {
      return client(configuration);
    }
  }

  private static class OrderInitialization {
    private final OrderRequest orderRequest;
    private final Transaction transaction;

    private OrderInitialization(OrderRequest orderRequest, Transaction transaction) {
      this.orderRequest = orderRequest;
      this.transaction = transaction;
    }
  }
}
