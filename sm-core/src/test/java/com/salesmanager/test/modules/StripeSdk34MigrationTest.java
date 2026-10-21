package com.salesmanager.test.modules;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.modules.integration.payment.impl.Stripe3Payment;
import com.salesmanager.core.business.modules.integration.payment.impl.StripePayment;
import com.salesmanager.core.business.utils.ProductPriceUtils;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.payments.Payment;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.core.model.payments.Transaction;
import com.salesmanager.core.model.payments.TransactionType;
import com.salesmanager.core.model.reference.currency.Currency;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.StripeObject;
import com.stripe.net.ApiMode;
import com.stripe.net.ApiResource;
import com.stripe.net.BaseAddress;
import com.stripe.net.RequestOptions;
import com.stripe.net.StripeResponseGetter;

public class StripeSdk34MigrationTest {

  @Test
  public void chargeApiAuthorizeCaptureAndRefundKeepTransactionDetails() throws Exception {
    BigDecimal amount = new BigDecimal("10.00");
    MerchantStore store = store();
    ProductPriceUtils priceUtils = mock(ProductPriceUtils.class);
    when(priceUtils.getAdminFormatedAmount(store, amount)).thenReturn("10.00");
    StripePayment paymentModule = new StripePayment();
    ReflectionTestUtils.setField(paymentModule, "productPriceUtils", priceUtils);
    IntegrationConfiguration configuration = configuration();

    Charge charge = new Charge();
    charge.setId("ch_123");
    charge.setStatus("succeeded");
    charge.setResponseGetter(responseGetter(charge));
    Refund refund = new Refund();
    refund.setId("re_123");
    refund.setReason("requested_by_customer");
    try (MockedStatic<Charge> chargeApi = Mockito.mockStatic(Charge.class);
        MockedStatic<Refund> refundApi = Mockito.mockStatic(Refund.class)) {
      chargeApi.when(() -> Charge.create(anyMap())).thenReturn(charge);
      chargeApi.when(() -> Charge.retrieve("ch_123")).thenReturn(charge);
      refundApi.when(() -> Refund.create(anyMap())).thenReturn(refund);

      configuration.getIntegrationKeys().put("publishableKey", "pk_test");
      Payment payment = new Payment();
      payment.setPaymentType(PaymentType.CREDITCARD);
      payment.setTransactionType(TransactionType.AUTHORIZE);
      Transaction initialized = paymentModule.initTransaction(store, null, amount, payment,
          configuration, null);
      assertEquals("pk_test", initialized.getDetails());
      assertEquals(TransactionType.AUTHORIZE, initialized.getTransactionType());

      payment.setPaymentMetaData(Collections.singletonMap("stripe_token", "tok_123"));
      Transaction authorized = paymentModule.authorize(store, null, Collections.emptyList(),
          amount, payment, configuration, null);
      assertEquals(TransactionType.AUTHORIZE, authorized.getTransactionType());
      assertEquals(PaymentType.CREDITCARD, authorized.getPaymentType());
      assertEquals("tok_123", authorized.getTransactionDetails().get("TRANSACTIONID"));
      assertEquals("ch_123", authorized.getTransactionDetails().get("TRNORDERNUMBER"));

      Transaction capturable = new Transaction();
      capturable.getTransactionDetails().put("TRNORDERNUMBER", "ch_123");
      capturable.getTransactionDetails().put("TRANSACTIONID", "tok_123");
      Order order = new Order();
      order.setTotal(amount);
      Transaction captured = paymentModule.capture(store, null, order, capturable,
          configuration, null);
      assertEquals(TransactionType.CAPTURE, captured.getTransactionType());
      assertEquals("ch_123", captured.getTransactionDetails().get("TRNORDERNUMBER"));

      Transaction original = new Transaction();
      original.getTransactionDetails().put("TRNORDERNUMBER", "ch_123");
      original.getTransactionDetails().put("TRANSACTIONID", "tok_123");
      Transaction refunded = paymentModule.refund(true, store, original, order, amount,
          configuration, null);
      assertEquals(TransactionType.CAPTURE, refunded.getTransactionType());
      assertEquals("re_123", refunded.getTransactionDetails().get("TRNORDERNUMBER"));
    }
  }

  @Test
  public void paymentIntentApiAuthorizeCaptureAndRefundKeepTransactionDetails() throws Exception {
    BigDecimal amount = new BigDecimal("10.00");
    MerchantStore store = store();
    ProductPriceUtils priceUtils = mock(ProductPriceUtils.class);
    when(priceUtils.getAdminFormatedAmount(store, amount)).thenReturn("10.00");
    Stripe3Payment paymentModule = new Stripe3Payment();
    ReflectionTestUtils.setField(paymentModule, "productPriceUtils", priceUtils);
    IntegrationConfiguration configuration = configuration();

    PaymentIntent intent = new PaymentIntent();
    intent.setId("pi_123");
    intent.setStatus("requires_capture");
    intent.setClientSecret("client_secret");
    intent.setResponseGetter(responseGetter(intent));
    Refund refund = new Refund();
    refund.setId("re_456");
    refund.setReason("requested_by_customer");
    try (MockedStatic<PaymentIntent> intentApi = Mockito.mockStatic(PaymentIntent.class);
        MockedStatic<Refund> refundApi = Mockito.mockStatic(Refund.class)) {
      intentApi.when(() -> PaymentIntent.retrieve("pi_123")).thenReturn(intent);
      intentApi.when(() -> PaymentIntent.create(Mockito.any(
          com.stripe.param.PaymentIntentCreateParams.class))).thenReturn(intent);
      refundApi.when(() -> Refund.create(anyMap())).thenReturn(refund);

      Payment payment = new Payment();
      payment.setPaymentMetaData(Collections.singletonMap("stripe_token", "pi_123"));
      Transaction initiated = paymentModule.initTransaction(store, null, amount, payment,
          configuration, null);
      assertEquals(TransactionType.AUTHORIZE, initiated.getTransactionType());
      assertEquals("pi_123", initiated.getTransactionDetails().get("TRANSACTIONID"));

      Transaction authorized = paymentModule.authorize(store, null, Collections.emptyList(),
          amount, payment, configuration, null);
      assertEquals(TransactionType.AUTHORIZE, authorized.getTransactionType());
      assertEquals("pi_123", authorized.getTransactionDetails().get("TRNORDERNUMBER"));

      Transaction capturable = new Transaction();
      capturable.getTransactionDetails().put("TRNORDERNUMBER", "pi_123");
      capturable.getTransactionDetails().put("TRANSACTIONID", "pi_123");
      Order order = new Order();
      order.setTotal(amount);
      Transaction captured = paymentModule.capture(store, null, order, capturable,
          configuration, null);
      assertEquals(TransactionType.CAPTURE, captured.getTransactionType());
      assertEquals("pi_123", captured.getTransactionDetails().get("TRNORDERNUMBER"));

      Transaction original = new Transaction();
      original.getTransactionDetails().put("TRNORDERNUMBER", "pi_123");
      original.getTransactionDetails().put("TRANSACTIONID", "pi_123");
      Transaction refunded = paymentModule.refund(true, store, original, order, amount,
          configuration, null);
      assertEquals(TransactionType.CAPTURE, refunded.getTransactionType());
      assertEquals("re_456", refunded.getTransactionDetails().get("TRNORDERNUMBER"));
    }
  }

  private MerchantStore store() {
    Currency currency = new Currency();
    currency.setCurrency(java.util.Currency.getInstance("USD"));
    MerchantStore store = new MerchantStore();
    store.setCurrency(currency);
    store.setStorename("test-store");
    return store;
  }

  private IntegrationConfiguration configuration() {
    IntegrationConfiguration configuration = new IntegrationConfiguration();
    Map<String, String> keys = new HashMap<String, String>();
    keys.put("secretKey", "sk_test");
    configuration.setIntegrationKeys(keys);
    return configuration;
  }

  private StripeResponseGetter responseGetter(StripeObject response) {
    return new StripeResponseGetter() {
      @SuppressWarnings("unchecked")
      @Override
      public <T extends StripeObject> T request(BaseAddress baseAddress,
          ApiResource.RequestMethod method, String path, Map<String, Object> params, Type type,
          RequestOptions options, ApiMode apiMode) throws StripeException {
        return (T) response;
      }

      @Override
      public InputStream requestStream(BaseAddress baseAddress, ApiResource.RequestMethod method,
          String path, Map<String, Object> params, RequestOptions options, ApiMode apiMode) {
        return new ByteArrayInputStream(new byte[0]);
      }
    };
  }
}
