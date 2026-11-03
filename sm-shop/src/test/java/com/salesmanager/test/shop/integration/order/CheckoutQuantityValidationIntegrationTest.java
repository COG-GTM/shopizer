package com.salesmanager.test.shop.integration.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.shoppingcart.ShoppingCartService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderList;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
class CheckoutQuantityValidationIntegrationTest extends ServicesTestSupport {

    @Autowired
    private ShoppingCartService shoppingCartService;

    @Autowired
    private MerchantStoreService merchantStoreService;

    @Test
    void checkoutRejectsInvalidAndEmptyCarts() throws Exception {
        String customerEmail = "checkout-" + UUID.randomUUID().toString().replace("-", "") + "@test.com";
        registerCustomer(customerEmail);
        HttpHeaders customerHeaders = customerHeaders(customerEmail);
        ReadableProduct product = sampleProduct(uniqueSku("checkout-product"));

        ReadableShoppingCart tamperedCart = createCart(product.getSku());
        ShoppingCart persistedCart = loadCart(tamperedCart.getCode());
        ShoppingCartItem line = persistedCart.getLineItems().iterator().next();
        ReadableOrderList beforeInvalidCheckouts = orders(customerHeaders);

        line.setQuantity(-1);
        shoppingCartService.saveOrUpdate(persistedCart);
        ResponseEntity<String> negativeCheckout = checkout(tamperedCart.getCode(), customerHeaders);
        assertFalse(negativeCheckout.getStatusCode().is2xxSuccessful());
        assertEquals(orderCount(beforeInvalidCheckouts), orderCount(orders(customerHeaders)));

        line.setQuantity(0);
        shoppingCartService.saveOrUpdate(persistedCart);
        ResponseEntity<String> zeroCheckout = checkout(tamperedCart.getCode(), customerHeaders);
        assertFalse(zeroCheckout.getStatusCode().is2xxSuccessful());
        assertEquals(orderCount(beforeInvalidCheckouts), orderCount(orders(customerHeaders)));

        ReadableShoppingCart emptyCart = createCart(product.getSku());
        ShoppingCart persistedEmptyCart = loadCart(emptyCart.getCode());
        for (ShoppingCartItem item : persistedEmptyCart.getLineItems()) {
            shoppingCartService.deleteShoppingCartItem(item.getId());
        }
        ResponseEntity<String> emptyCheckout = checkout(emptyCart.getCode(), customerHeaders);
        assertFalse(emptyCheckout.getStatusCode().is2xxSuccessful());
        assertEquals(orderCount(beforeInvalidCheckouts), orderCount(orders(customerHeaders)));

        assertEquals(orderCount(beforeInvalidCheckouts), orderCount(orders(customerHeaders)));
    }

    private void registerCustomer(String email) {
        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress(email);
        customer.setPassword("clear123");
        customer.setGender("M");
        customer.setLanguage("en");
        customer.setStoreCode(Constants.DEFAULT_STORE);
        Address billing = new Address();
        billing.setFirstName("Checkout");
        billing.setLastName("Customer");
        billing.setCountry("BE");
        customer.setBilling(billing);

        ResponseEntity<String> registration = testRestTemplate.postForEntity(
                "/api/v1/customer/register", new HttpEntity<>(customer, getHeader()), String.class);
        assertTrue(registration.getStatusCode().is2xxSuccessful(),
                "Customer registration failed: " + registration.getStatusCode() + " " + registration.getBody());
    }

    private HttpHeaders customerHeaders(String email) {
        ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
                new HttpEntity<>(new AuthenticationRequest(email, "clear123")), AuthenticationResponse.class);
        assertTrue(login.getStatusCode().is2xxSuccessful());
        assertNotNull(login.getBody());
        assertNotNull(login.getBody().getToken());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.getBody().getToken());
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }

    private ReadableShoppingCart createCart(String sku) {
        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct(sku);
        item.setQuantity(1);
        ResponseEntity<ReadableShoppingCart> response = testRestTemplate.postForEntity(
                "/api/v1/cart", new HttpEntity<>(item, getHeader()), ReadableShoppingCart.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private ShoppingCart loadCart(String code) throws Exception {
        MerchantStore store = merchantStoreService.getByCode(Constants.DEFAULT_STORE);
        return shoppingCartService.getByCode(code, store);
    }

    private ResponseEntity<String> checkout(String cartCode, HttpHeaders headers) throws Exception {
        return testRestTemplate.exchange("/api/v1/auth/cart/" + cartCode + "/checkout", HttpMethod.POST,
                new HttpEntity<>(order("10"), headers), String.class);
    }

    private PersistableOrder order(String amount) throws Exception {
        PersistableOrder order = new PersistableOrder();
        order.setCurrency(merchantStoreService.getByCode(Constants.DEFAULT_STORE).getCurrency().getCode());
        PersistablePayment payment = new PersistablePayment();
        payment.setPaymentType("MONEYORDER");
        payment.setPaymentModule("moneyorder");
        payment.setTransactionType("AUTHORIZECAPTURE");
        payment.setAmount(amount);
        order.setPayment(payment);
        return order;
    }

    private ReadableOrderList orders(HttpHeaders headers) {
        ResponseEntity<ReadableOrderList> response = testRestTemplate.exchange("/api/v1/auth/orders",
                HttpMethod.GET, new HttpEntity<>(headers), ReadableOrderList.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        return response.getBody();
    }

    private static int orderCount(ReadableOrderList orderList) {
        return orderList == null || orderList.getOrders() == null ? 0 : orderList.getOrders().size();
    }

    private static String uniqueSku(String prefix) {
        return "qty-" + prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }
}
