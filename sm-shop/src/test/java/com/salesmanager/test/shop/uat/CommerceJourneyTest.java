package com.salesmanager.test.shop.uat;

import static org.junit.Assert.*;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.*;
import org.springframework.test.context.junit4.SpringRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.ReadableOrderTotalSummary;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
public class CommerceJourneyTest extends ServicesTestSupport {
    @Test
    public void browseStoreCategoriesAndProducts() {
        ReadableProduct product = sampleProduct("browse" + UUID.randomUUID().toString().replace("-", ""));
        for (String path : new String[] { "/api/v1/store/DEFAULT", "/api/v1/category", "/api/v1/products",
                "/api/v2/product/" + product.getSku() }) {
            ResponseEntity<JsonNode> response = testRestTemplate.getForEntity(path, JsonNode.class);
            assertEquals(path + ": " + response, 200, response.getStatusCodeValue());
            assertNotNull(response.getBody());
        }
    }

    @Test
    public void registeredCustomerBuysAndReadsOrder() {
        String email = "uat-" + UUID.randomUUID() + "@test.com";
        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress(email);
        customer.setPassword("Clear123!");
        customer.setGender("M");
        customer.setLanguage("en");
        customer.setStoreCode("DEFAULT");
        Address billing = new Address();
        billing.setFirstName("Journey");
        billing.setLastName("Customer");
        billing.setCountry("CA");
        billing.setStateProvince("QC");
        billing.setCity("Montreal");
        billing.setAddress("123 Test Street");
        billing.setPostalCode("H2X1Y4");
        customer.setBilling(billing);
        ResponseEntity<PersistableCustomer> registered = testRestTemplate.postForEntity("/api/v1/customer/register",
                new HttpEntity<>(customer, getHeader()), PersistableCustomer.class);
        assertEquals(registered.toString(), 200, registered.getStatusCodeValue());
        ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
                new AuthenticationRequest(email, "Clear123!"), AuthenticationResponse.class);
        assertEquals(login.toString(), 200, login.getStatusCodeValue());
        assertNotNull(login.getBody().getToken());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.getBody().getToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> orders = testRestTemplate.exchange("/api/v1/auth/orders", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertEquals(orders.toString(), 200, orders.getStatusCodeValue());

        ReadableProduct product = sampleProduct("buy" + UUID.randomUUID().toString().replace("-", ""));
        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct(product.getSku());
        item.setQuantity(1);
        ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart",
                new HttpEntity<>(item, headers), ReadableShoppingCart.class);
        assertEquals(created.toString(), 201, created.getStatusCodeValue());
        String code = created.getBody().getCode();
        item.setQuantity(2);
        ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange("/api/v1/cart/" + code,
                HttpMethod.PUT, new HttpEntity<>(item, headers), ReadableShoppingCart.class);
        assertEquals(updated.toString(), 201, updated.getStatusCodeValue());
        assertEquals(2, updated.getBody().getQuantity());
        ResponseEntity<ReadableShoppingCart> read = testRestTemplate.getForEntity("/api/v1/cart/" + code, ReadableShoppingCart.class);
        assertEquals(read.toString(), 200, read.getStatusCodeValue());
        assertEquals(2, read.getBody().getQuantity());

        ResponseEntity<ReadableOrderTotalSummary> total = testRestTemplate.getForEntity("/api/v1/cart/" + code + "/total",
                ReadableOrderTotalSummary.class);
        assertEquals(total.toString(), 200, total.getStatusCodeValue());
        PersistablePayment payment = new PersistablePayment();
        ResponseEntity<String> configured = testRestTemplate.postForEntity("/api/v1/private/modules/payment",
                new HttpEntity<>("{\"code\":\"moneyorder\",\"active\":true,\"defaultSelected\":true,"
                        + "\"integrationKeys\":{\"address\":\"123 Test Street\"},\"integrationOptions\":{}}", getHeader()), String.class);
        assertTrue(configured.toString(), configured.getStatusCode().is2xxSuccessful());
        payment.setAmount(total.getBody().getTotal().replaceAll("[^0-9.]", ""));
        payment.setPaymentType("MONEYORDER");
        payment.setPaymentModule("moneyorder");
        payment.setTransactionType("AUTHORIZECAPTURE");
        PersistableOrder order = new PersistableOrder();
        order.setCurrency("CAD");
        order.setCustomerAgreement(true);
        order.setPayment(payment);
        ResponseEntity<ReadableOrderConfirmation> checkout = testRestTemplate.postForEntity(
                "/api/v1/auth/cart/" + code + "/checkout", new HttpEntity<>(order, headers), ReadableOrderConfirmation.class);
        assertEquals(checkout.toString(), 200, checkout.getStatusCodeValue());
        assertNotNull(checkout.getBody().getId());
        ResponseEntity<JsonNode> saved = testRestTemplate.exchange("/api/v1/auth/orders/" + checkout.getBody().getId(),
                HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        assertEquals(saved.toString(), 200, saved.getStatusCodeValue());
        assertEquals(checkout.getBody().getId().longValue(), saved.getBody().path("id").asLong());
    }
}
