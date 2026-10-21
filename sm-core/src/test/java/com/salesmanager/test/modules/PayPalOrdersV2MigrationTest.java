package com.salesmanager.test.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
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
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.OrderTotalSummary;
import com.salesmanager.core.model.payments.Payment;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.core.model.payments.Transaction;
import com.salesmanager.core.model.payments.TransactionType;
import com.salesmanager.core.model.reference.currency.Currency;
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
    IntegrationConfiguration configuration = configuration();
    configuration.getIntegrationKeys().put("transaction", "AUTHORIZECAPTURE");
    OrderTotalSummary summary = new OrderTotalSummary();
    summary.setSubTotal(new BigDecimal("12.00"));
    summary.setTotal(new BigDecimal("12.00"));
    Payment payment = new Payment();

    Transaction transaction = paymentModule.initPaypalTransaction(store, Collections.emptyList(),
        summary, payment, configuration, null);

    org.mockito.ArgumentCaptor<CreateOrderInput> input =
        org.mockito.ArgumentCaptor.forClass(CreateOrderInput.class);
    org.mockito.Mockito.verify(orders).createOrderAsync(input.capture());
    OrderRequest orderRequest = input.getValue().getBody();
    assertEquals(CheckoutPaymentIntent.CAPTURE, orderRequest.getIntent());
    PurchaseUnitRequest purchaseUnit = orderRequest.getPurchaseUnits().get(0);
    assertEquals("12.00", purchaseUnit.getAmount().getValue());
    assertEquals("12.00", purchaseUnit.getAmount().getBreakdown().getItemTotal().getValue());
    assertEquals(TransactionType.INIT, transaction.getTransactionType());
    assertEquals(PaymentType.PAYPAL, transaction.getPaymentType());
    assertEquals("order-123", transaction.getTransactionDetails().get("TOKEN"));
    assertEquals("debug-123", transaction.getTransactionDetails().get("CORRELATION"));
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
}
