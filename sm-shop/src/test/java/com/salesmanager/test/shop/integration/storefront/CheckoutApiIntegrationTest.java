package com.salesmanager.test.shop.integration.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.core.model.payments.TransactionType;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.order.ReadableOrderProduct;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v0.ReadableOrder;
import com.salesmanager.shop.model.order.v0.ReadableOrderList;
import com.salesmanager.shop.model.order.v1.PersistableAnonymousOrder;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;

/**
 * Checkout through the public API using the offline money order payment module, so no external
 * payment gateway is contacted. Covers guest checkout, registered customer checkout and the
 * server-side total check that protects against stale or tampered carts.
 */
@DisplayName("Storefront API: checkout")
class CheckoutApiIntegrationTest extends StorefrontApiTestSupport {

  private ReadableProduct product;
  private String currency;

  @BeforeEach
  void setUp() {
    enableMoneyOrderPayment();
    currency = fetchStore().getCurrency();
    product = createProduct(unique("checkout"));
  }

  @Test
  @DisplayName("money order payment module is active for the store")
  void paymentModuleIsConfigured() {
    ResponseEntity<IntegrationModuleConfiguration> response =
        testRestTemplate.exchange(
            "/api/v1/private/modules/payment/" + MONEY_ORDER + "?store=" + STORE,
            HttpMethod.GET,
            new HttpEntity<>(getHeader()),
            IntegrationModuleConfiguration.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().isActive()).isTrue();
    assertThat(response.getBody().getIntegrationKeys()).containsKey("address");
  }

  @Test
  @DisplayName("guest can check out a cart and the order is visible to the merchant")
  void guestCheckout() {
    ReadableShoppingCart cart = newCart(product.getSku(), 2);
    BigDecimal expectedTotal = UNIT_PRICE.multiply(BigDecimal.valueOf(2));
    assertThat(cart.getTotal()).isEqualByComparingTo(expectedTotal);

    String email = unique("guest") + "@example.com";
    ResponseEntity<ReadableOrderConfirmation> response =
        anonymousExchange(
            "/api/v1/cart/" + cart.getCode() + "/checkout",
            HttpMethod.POST,
            guestOrder(email, cart.getTotal()),
            ReadableOrderConfirmation.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    ReadableOrderConfirmation confirmation = response.getBody();
    assertThat(confirmation.getId()).isNotNull().isPositive();
    assertThat(confirmation.getProducts())
        .extracting(ReadableOrderProduct::getSku, ReadableOrderProduct::getOrderedQuantity)
        .containsExactly(tuple(product.getSku(), 2));
    assertThat(confirmation.getTotal().getTotals())
        .filteredOn(total -> "order.total.total".equals(total.getCode()))
        .singleElement()
        .satisfies(total -> assertThat(total.getValue()).isEqualByComparingTo(expectedTotal));
    assertThat(confirmation.getBilling().getCountry()).isEqualTo("CA");

    ResponseEntity<ReadableOrder> order =
        testRestTemplate.exchange(
            "/api/v1/private/orders/" + confirmation.getId() + "?store=" + STORE,
            HttpMethod.GET,
            new HttpEntity<>(getHeader()),
            ReadableOrder.class);
    assertThat(order.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(order.getBody().getId()).isEqualTo(confirmation.getId());
    assertThat(order.getBody().getPaymentModule()).isEqualTo(MONEY_ORDER);
    assertThat(order.getBody().getTotal().getValue()).isEqualByComparingTo(expectedTotal);
    assertThat(order.getBody().getCustomer().getEmailAddress()).isEqualTo(email);
  }

  @Test
  @DisplayName("registered customer can check out and sees the order in their history")
  void registeredCustomerCheckout() {
    String email = unique("buyer") + "@example.com";
    String password = "Password123";
    String token = registerAndLogin(email, password);

    ReadableShoppingCart cart = newCart(product.getSku(), 1);
    PersistableOrder order = new PersistableOrder();
    order.setCurrency(currency);
    order.setPayment(moneyOrderPayment(cart.getTotal()));

    ResponseEntity<ReadableOrderConfirmation> response =
        testRestTemplate.exchange(
            "/api/v1/auth/cart/" + cart.getCode() + "/checkout",
            HttpMethod.POST,
            new HttpEntity<>(order, bearer(token)),
            ReadableOrderConfirmation.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    Long orderId = response.getBody().getId();
    assertThat(orderId).isNotNull();

    ResponseEntity<ReadableOrderList> history =
        testRestTemplate.exchange(
            "/api/v1/auth/orders",
            HttpMethod.GET,
            new HttpEntity<>(bearer(token)),
            ReadableOrderList.class);
    assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(history.getBody().getOrders()).extracting(ReadableOrder::getId).containsExactly(orderId);
  }

  @Test
  @DisplayName("checkout is rejected when the payment amount does not match the cart total")
  void checkoutRejectsWrongAmount() {
    ReadableShoppingCart cart = newCart(product.getSku(), 1);

    ResponseEntity<String> response =
        anonymousExchange(
            "/api/v1/cart/" + cart.getCode() + "/checkout",
            HttpMethod.POST,
            guestOrder(unique("guest") + "@example.com", cart.getTotal().add(BigDecimal.ONE)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("Payment.amount does not match");
  }

  @Test
  @DisplayName("checkout of an unknown cart is rejected")
  void checkoutUnknownCart() {
    ResponseEntity<String> response =
        anonymousExchange(
            "/api/v1/cart/" + unique("nocart") + "/checkout",
            HttpMethod.POST,
            guestOrder(unique("guest") + "@example.com", UNIT_PRICE),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  private PersistablePayment moneyOrderPayment(BigDecimal amount) {
    PersistablePayment payment = new PersistablePayment();
    payment.setPaymentModule(MONEY_ORDER);
    payment.setPaymentType(PaymentType.MONEYORDER.name());
    payment.setTransactionType(TransactionType.AUTHORIZECAPTURE.name());
    payment.setAmount(amount.toPlainString());
    return payment;
  }

  private PersistableAnonymousOrder guestOrder(String email, BigDecimal amount) {
    PersistableCustomer customer = new PersistableCustomer();
    customer.setEmailAddress(email);
    customer.setLanguage("en");
    customer.setGender(CustomerGender.F.name());
    customer.setStoreCode(STORE);
    customer.setBilling(address("Grace", "Hopper"));
    customer.setDelivery(address("Grace", "Hopper"));

    PersistableAnonymousOrder order = new PersistableAnonymousOrder();
    order.setCustomer(customer);
    order.setCurrency(currency);
    order.setPayment(moneyOrderPayment(amount));
    return order;
  }

  private String registerAndLogin(String email, String password) {
    PersistableCustomer customer = new PersistableCustomer();
    customer.setEmailAddress(email);
    customer.setPassword(password);
    customer.setLanguage("en");
    customer.setGender(CustomerGender.M.name());
    customer.setStoreCode(STORE);
    customer.setBilling(address("Alan", "Turing"));

    ResponseEntity<String> registration =
        anonymousExchange("/api/v1/customer/register", HttpMethod.POST, customer, String.class);
    assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<AuthenticationResponse> login =
        testRestTemplate.postForEntity(
            "/api/v1/customer/login",
            new HttpEntity<>(new AuthenticationRequest(email, password), jsonHeaders()),
            AuthenticationResponse.class);
    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(login.getBody().getToken()).isNotBlank();
    return login.getBody().getToken();
  }
}
