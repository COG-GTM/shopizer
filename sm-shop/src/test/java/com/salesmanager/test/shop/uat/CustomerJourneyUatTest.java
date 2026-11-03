package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.ReadableOrderTotalSummary;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.order.v1.ReadableOrder;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CustomerJourneyUatTest extends ServicesTestSupport {

  @Test
  public void customerRegistersLogsInBrowsesCartAndPlacesAnOrder() {
    String suffix = Long.toString(System.nanoTime());
    String email = "uat-customer-" + suffix + "@shopizer.com";
    String password = "Password1";

    PersistableCustomer customer = new PersistableCustomer();
    customer.setEmailAddress(email);
    customer.setPassword(password);
    customer.setGender(CustomerGender.M.name());
    customer.setLanguage("en");
    customer.setStoreCode(Constants.DEFAULT_STORE);
    Address billing = new Address();
    billing.setFirstName("UAT");
    billing.setLastName("Customer");
    billing.setCountry("US");
    customer.setBilling(billing);

    ResponseEntity<PersistableCustomer> registrationResponse = testRestTemplate.postForEntity(
        "/api/v1/customer/register",
        new HttpEntity<>(customer, getHeader()),
        PersistableCustomer.class);
    assertEquals(HttpStatus.OK, registrationResponse.getStatusCode());

    ResponseEntity<AuthenticationResponse> loginResponse = testRestTemplate.postForEntity(
        "/api/v1/customer/login",
        new HttpEntity<>(new AuthenticationRequest(email, password)),
        AuthenticationResponse.class);
    assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
    assertNotNull(loginResponse.getBody());
    assertTrue(loginResponse.getBody().getToken() != null && !loginResponse.getBody().getToken().isEmpty());

    HttpHeaders customerHeaders = new HttpHeaders();
    customerHeaders.setBearerAuth(loginResponse.getBody().getToken());
    ResponseEntity<String> profileResponse = testRestTemplate.exchange(
        "/api/v1/auth/customer/profile?store=DEFAULT&lang=en",
        HttpMethod.GET,
        new HttpEntity<>(customerHeaders),
        String.class);
    assertEquals(HttpStatus.OK, profileResponse.getStatusCode());

    ReadableProduct product = sampleProduct("uat-cart-" + suffix);
    PersistableShoppingCartItem cartItem = new PersistableShoppingCartItem();
    cartItem.setProduct(product.getSku());
    cartItem.setQuantity(1);
    ResponseEntity<ReadableShoppingCart> createCartResponse = testRestTemplate.postForEntity(
        "/api/v1/cart?store=DEFAULT&lang=en",
        new HttpEntity<>(cartItem, customerHeaders),
        ReadableShoppingCart.class);
    assertEquals(HttpStatus.CREATED, createCartResponse.getStatusCode());
    assertNotNull(createCartResponse.getBody());

    cartItem.setQuantity(2);
    ResponseEntity<ReadableShoppingCart> updateCartResponse = testRestTemplate.exchange(
        "/api/v1/cart/" + createCartResponse.getBody().getCode() + "?store=DEFAULT&lang=en",
        HttpMethod.PUT,
        new HttpEntity<>(cartItem, customerHeaders),
        ReadableShoppingCart.class);
    assertEquals(HttpStatus.CREATED, updateCartResponse.getStatusCode());

    String cartCode = createCartResponse.getBody().getCode();
    ResponseEntity<ReadableShoppingCart> readCartResponse = testRestTemplate.getForEntity(
        "/api/v1/cart/" + cartCode + "?store=DEFAULT&lang=en",
        ReadableShoppingCart.class);
    assertEquals(HttpStatus.OK, readCartResponse.getStatusCode());
    assertEquals(cartCode, readCartResponse.getBody().getCode());

    ResponseEntity<ReadableOrderTotalSummary> totalResponse = testRestTemplate.getForEntity(
        "/api/v1/cart/" + cartCode + "/total?store=DEFAULT&lang=en",
        ReadableOrderTotalSummary.class);
    assertEquals(HttpStatus.OK, totalResponse.getStatusCode());
    assertNotNull(totalResponse.getBody());

    IntegrationModuleConfiguration paymentConfiguration = new IntegrationModuleConfiguration();
    paymentConfiguration.setCode("moneyorder");
    paymentConfiguration.setActive(true);
    paymentConfiguration.setDefaultSelected(true);
    paymentConfiguration.setIntegrationKeys(Collections.singletonMap("address", "UAT test address"));
    ResponseEntity<Void> paymentConfigurationResponse = testRestTemplate.exchange(
        "/api/v1/private/modules/payment?store=DEFAULT",
        HttpMethod.POST,
        new HttpEntity<>(paymentConfiguration, getHeader()),
        Void.class);
    assertEquals(HttpStatus.OK, paymentConfigurationResponse.getStatusCode());

    PersistablePayment payment = new PersistablePayment();
    payment.setAmount(totalResponse.getBody().getTotal().replaceAll("[^0-9.-]", ""));
    payment.setPaymentModule("moneyorder");
    payment.setPaymentType("MONEYORDER");
    payment.setTransactionType("AUTHORIZECAPTURE");
    PersistableOrder order = new PersistableOrder();
    order.setCurrency("USD");
    order.setCustomerAgreement(true);
    order.setPayment(payment);

    ResponseEntity<ReadableOrderConfirmation> checkoutResponse = testRestTemplate.exchange(
        "/api/v1/auth/cart/" + cartCode + "/checkout?store=DEFAULT&lang=en",
        HttpMethod.POST,
        new HttpEntity<>(order, customerHeaders),
        ReadableOrderConfirmation.class);
    assertEquals(HttpStatus.OK, checkoutResponse.getStatusCode());
    assertNotNull(checkoutResponse.getBody());
    assertNotNull(checkoutResponse.getBody().getId());

    ResponseEntity<ReadableOrder> orderResponse = testRestTemplate.exchange(
        "/api/v1/auth/orders/" + checkoutResponse.getBody().getId() + "?store=DEFAULT&lang=en",
        HttpMethod.GET,
        new HttpEntity<>(customerHeaders),
        ReadableOrder.class);
    assertEquals(HttpStatus.OK, orderResponse.getStatusCode());
    assertEquals(checkoutResponse.getBody().getId(), orderResponse.getBody().getId());
  }
}
