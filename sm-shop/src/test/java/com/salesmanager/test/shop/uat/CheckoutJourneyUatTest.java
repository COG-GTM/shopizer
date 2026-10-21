package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.v0.ReadableOrder;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class CheckoutJourneyUatTest extends ServicesTestSupport {

  private HttpEntity<?> adminEntity;
  private IntegrationModuleConfiguration originalMoneyOrderConfiguration;

  @After
  public void restoreMoneyOrderConfiguration() {
    if (adminEntity == null || originalMoneyOrderConfiguration == null) {
      return;
    }

    Map<String, String> integrationKeys =
        originalMoneyOrderConfiguration.getIntegrationKeys() == null
            ? new HashMap<>()
            : new HashMap<>(originalMoneyOrderConfiguration.getIntegrationKeys());
    String address = integrationKeys.get("address");
    if (address == null || address.trim().isEmpty()) {
      integrationKeys.put("address", "Disabled UAT money order");
    }
    IntegrationModuleConfiguration restore = paymentConfiguration(
        originalMoneyOrderConfiguration.isActive(),
        originalMoneyOrderConfiguration.isDefaultSelected(),
        integrationKeys,
        originalMoneyOrderConfiguration.getIntegrationOptions());
    ResponseEntity<Void> response = testRestTemplate.exchange(
        "/api/v1/private/modules/payment?store=" + Constants.DEFAULT_STORE,
        HttpMethod.POST, new HttpEntity<>(restore, adminEntity.getHeaders()), Void.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
  }

  @Test
  public void customerCanCheckoutAndBothOrderReadsIncludeTheCustomer() {
    String suffix = UUID.randomUUID().toString().replace("-", "");
    String email = "checkout-" + suffix + "@test.com";
    String password = "clear123";

    adminEntity = new HttpEntity<>(getHeader());
    ResponseEntity<IntegrationModuleConfiguration> previousConfiguration = testRestTemplate.exchange(
        "/api/v1/private/modules/payment/moneyorder?store=" + Constants.DEFAULT_STORE,
        HttpMethod.GET, adminEntity, IntegrationModuleConfiguration.class);
    assertEquals(HttpStatus.OK, previousConfiguration.getStatusCode());
    assertNotNull(previousConfiguration.getBody());
    originalMoneyOrderConfiguration = previousConfiguration.getBody();

    Map<String, String> integrationKeys = new HashMap<>();
    integrationKeys.put("address", "42 Money Order Way");
    IntegrationModuleConfiguration moneyOrderConfiguration =
        paymentConfiguration(true, true, integrationKeys, Collections.emptyMap());
    ResponseEntity<Void> configured = testRestTemplate.exchange(
        "/api/v1/private/modules/payment?store=" + Constants.DEFAULT_STORE,
        HttpMethod.POST,
        new HttpEntity<>(moneyOrderConfiguration, adminEntity.getHeaders()),
        Void.class);
    assertEquals(HttpStatus.OK, configured.getStatusCode());
    ResponseEntity<IntegrationModuleConfiguration> activeConfiguration = testRestTemplate.exchange(
        "/api/v1/private/modules/payment/moneyorder?store=" + Constants.DEFAULT_STORE,
        HttpMethod.GET, adminEntity, IntegrationModuleConfiguration.class);
    assertEquals(HttpStatus.OK, activeConfiguration.getStatusCode());
    assertNotNull(activeConfiguration.getBody());
    assertTrue(activeConfiguration.getBody().isActive());
    assertTrue(activeConfiguration.getBody().isDefaultSelected());

    ReadableProduct product = sampleProduct("checkout" + suffix);

    PersistableCustomer registration = new PersistableCustomer();
    registration.setEmailAddress(email);
    registration.setPassword(password);
    registration.setGender(CustomerGender.M.name());
    registration.setLanguage("en");
    registration.setStoreCode(Constants.DEFAULT_STORE);
    Address billing = new Address();
    billing.setFirstName("Checkout");
    billing.setLastName("Customer");
    billing.setAddress("42 Checkout Way");
    billing.setCity("Boston");
    billing.setPostalCode("02108");
    billing.setStateProvince("MA");
    billing.setCountry("US");
    billing.setPhone("617-555-0142");
    registration.setBilling(billing);
    ResponseEntity<PersistableCustomer> registered = testRestTemplate.postForEntity(
        "/api/v1/customer/register",
        new HttpEntity<>(registration, getHeader()),
        PersistableCustomer.class);
    assertEquals(HttpStatus.OK, registered.getStatusCode());
    assertNotNull(registered.getBody());
    assertNotNull(registered.getBody().getId());

    ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
        "/api/v1/customer/login",
        new HttpEntity<>(new AuthenticationRequest(email, password)),
        AuthenticationResponse.class);
    assertEquals(HttpStatus.OK, login.getStatusCode());
    assertNotNull(login.getBody());
    assertNotNull(login.getBody().getToken());
    HttpHeaders customerHeaders = new HttpHeaders();
    customerHeaders.setBearerAuth(login.getBody().getToken());
    customerHeaders.setContentType(MediaType.APPLICATION_JSON);

    PersistableShoppingCartItem cartItem = new PersistableShoppingCartItem();
    cartItem.setProduct(product.getSku());
    cartItem.setQuantity(1);
    ResponseEntity<ReadableShoppingCart> cart = testRestTemplate.postForEntity(
        "/api/v1/cart",
        new HttpEntity<>(cartItem, getHeader()),
        ReadableShoppingCart.class);
    assertEquals(HttpStatus.CREATED, cart.getStatusCode());
    assertNotNull(cart.getBody());
    assertNotNull(cart.getBody().getCode());

    PersistablePayment payment = new PersistablePayment();
    payment.setAmount(cart.getBody().getTotal().toPlainString());
    payment.setPaymentModule("moneyorder");
    payment.setPaymentType("MONEYORDER");
    payment.setTransactionType("INIT");
    PersistableOrder order = new PersistableOrder();
    order.setCurrency("USD");
    order.setCustomerAgreement(true);
    order.setPayment(payment);
    HttpEntity<PersistableOrder> checkoutRequest = new HttpEntity<>(order, customerHeaders);
    ResponseEntity<ReadableOrderConfirmation> checkout = testRestTemplate.postForEntity(
        "/api/v1/auth/cart/" + cart.getBody().getCode() + "/checkout?store=" + Constants.DEFAULT_STORE,
        checkoutRequest,
        ReadableOrderConfirmation.class);
    assertEquals(HttpStatus.OK, checkout.getStatusCode());
    assertNotNull(checkout.getBody());
    assertNotNull(checkout.getBody().getId());

    Long orderId = checkout.getBody().getId();
    HttpEntity<?> customerEntity = new HttpEntity<>(customerHeaders);
    ResponseEntity<ReadableOrder> customerOrder = testRestTemplate.exchange(
        "/api/v1/auth/orders/" + orderId + "?store=" + Constants.DEFAULT_STORE,
        HttpMethod.GET, customerEntity, ReadableOrder.class);
    assertEquals(HttpStatus.OK, customerOrder.getStatusCode());
    assertNotNull(customerOrder.getBody());

    ResponseEntity<ReadableOrder> adminOrder = testRestTemplate.exchange(
        "/api/v1/private/orders/" + orderId + "?store=" + Constants.DEFAULT_STORE,
        HttpMethod.GET, adminEntity, ReadableOrder.class);
    assertEquals(HttpStatus.OK, adminOrder.getStatusCode());
    assertNotNull(adminOrder.getBody());
    assertNotNull(adminOrder.getBody().getCustomer());
    assertEquals(registered.getBody().getId(), adminOrder.getBody().getCustomer().getId());
    assertEquals(email, adminOrder.getBody().getCustomer().getEmailAddress());
  }

  private IntegrationModuleConfiguration paymentConfiguration(
      boolean active,
      boolean defaultSelected,
      Map<String, String> integrationKeys,
      Map<String, List<String>> integrationOptions) {
    IntegrationModuleConfiguration configuration = new IntegrationModuleConfiguration();
    configuration.setCode("moneyorder");
    configuration.setActive(active);
    configuration.setDefaultSelected(defaultSelected);
    configuration.setIntegrationKeys(integrationKeys);
    configuration.setIntegrationOptions(integrationOptions);
    return configuration;
  }
}
