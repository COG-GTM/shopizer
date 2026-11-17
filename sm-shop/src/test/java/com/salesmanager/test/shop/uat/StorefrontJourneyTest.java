package com.salesmanager.test.shop.uat;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
public class StorefrontJourneyTest extends ServicesTestSupport {
    @Test
    public void adminJwtAndLegacyBasicAuthentication() {
        HttpHeaders admin = getHeader();
        assertNotNull(admin.getFirst("Authorization"));
        assertEquals(200, testRestTemplate.exchange("/api/v1/private/customers", HttpMethod.GET,
                new HttpEntity<>(admin), JsonNode.class).getStatusCodeValue());
        assertEquals(401, testRestTemplate.getForEntity("/api/v1/private/customers", String.class).getStatusCodeValue());
        HttpHeaders basic = new HttpHeaders();
        basic.setContentType(MediaType.APPLICATION_JSON);
        basic.setBasicAuth("admin@shopizer.com", "password");
        assertEquals(200, testRestTemplate.postForEntity("/services/private/system/optin",
                new HttpEntity<>("{}", basic), String.class).getStatusCodeValue());
        basic.setBasicAuth("admin@shopizer.com", "wrong-password");
        assertEquals(401, testRestTemplate.postForEntity("/services/private/system/optin",
                new HttpEntity<>("{}", basic), String.class).getStatusCodeValue());
    }

    @Test
    public void browseHealthAndApiDocumentation() {
        assertEquals(200, testRestTemplate.getForEntity("/api/v1/store/DEFAULT", JsonNode.class).getStatusCodeValue());
        assertEquals(200, testRestTemplate.getForEntity("/api/v1/category", JsonNode.class).getStatusCodeValue());
        ReadableProduct product = sampleProduct("browse-" + java.util.UUID.randomUUID());
        JsonNode listing = testRestTemplate.getForObject("/api/v1/products?count=3&page=0", JsonNode.class);
        assertTrue(listing.path("products").isArray());
        assertTrue(listing.path("products").size() > 0);
        ResponseEntity<ReadableProduct> detail = testRestTemplate.getForEntity("/api/v2/product/" + product.getSku(), ReadableProduct.class);
        assertEquals(200, detail.getStatusCodeValue());
        assertEquals(product.getSku(), detail.getBody().getSku());
        JsonNode health = testRestTemplate.getForObject("/actuator/health", JsonNode.class);
        assertEquals("UP", health.path("status").asText());
        ResponseEntity<JsonNode> docs = testRestTemplate.getForEntity("/v2/api-docs", JsonNode.class);
        assertEquals(200, docs.getStatusCodeValue());
        assertEquals("2.0", docs.getBody().path("swagger").asText());
    }

    @Test
    public void registeredCustomerCanUpdateCartCheckoutAndReadOrder() {
        String email = java.util.UUID.randomUUID().toString().replace("-", "") + "@example.com";
        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress(email);
        customer.setPassword("clear123");
        customer.setGender("M");
        customer.setLanguage("en");
        customer.setStoreCode("DEFAULT");
        Address billing = new Address();
        billing.setFirstName("Journey");
        billing.setLastName("Customer");
        billing.setCountry("CA");
        billing.setZone("QC");
        billing.setCity("Montreal");
        billing.setAddress("123 Test Street");
        billing.setPostalCode("H2X1Y4");
        billing.setPhone("5145550100");
        customer.setBilling(billing);
        assertEquals(200, testRestTemplate.postForEntity("/api/v1/customer/register", customer,
                PersistableCustomer.class).getStatusCodeValue());
        ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
                new AuthenticationRequest(email, "clear123"), AuthenticationResponse.class);
        assertEquals(200, login.getStatusCodeValue());
        assertNotNull(login.getBody().getToken());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.getBody().getToken());
        assertEquals(200, testRestTemplate.exchange("/api/v1/auth/customer/profile", HttpMethod.GET,
                new HttpEntity<>(headers), JsonNode.class).getStatusCodeValue());
        ReadableProduct product = sampleProduct("checkout-" + java.util.UUID.randomUUID());
        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct(product.getSku());
        item.setQuantity(1);
        ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart",
                new HttpEntity<>(item, headers), ReadableShoppingCart.class);
        assertEquals(201, created.getStatusCodeValue());
        String cartUrl = "/api/v1/cart/" + created.getBody().getCode();
        item.setQuantity(2);
        ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(cartUrl + "/multi", HttpMethod.POST,
                new HttpEntity<>(new PersistableShoppingCartItem[] {item}, headers), ReadableShoppingCart.class);
        assertEquals(201, updated.getStatusCodeValue());
        ResponseEntity<ReadableShoppingCart> read = testRestTemplate.getForEntity(cartUrl, ReadableShoppingCart.class);
        assertEquals(200, read.getStatusCodeValue());
        assertEquals(2, read.getBody().getQuantity());
        HttpHeaders admin = getHeader();
        admin.setContentType(MediaType.APPLICATION_JSON);
        assertEquals(200, testRestTemplate.postForEntity("/api/v1/private/modules/payment",
                new HttpEntity<>("{\"code\":\"moneyorder\",\"active\":true,\"defaultSelected\":true,\"integrationKeys\":{\"address\":\"123 Test Street\"},\"integrationOptions\":{}}", admin),
                String.class).getStatusCodeValue());
        PersistableOrder order = new PersistableOrder();
        order.setCurrency("CAD");
        order.setCustomerAgreement(true);
        PersistablePayment payment = new PersistablePayment();
        payment.setPaymentModule("moneyorder");
        payment.setPaymentType("MONEYORDER");
        payment.setTransactionType("AUTHORIZECAPTURE");
        payment.setAmount(read.getBody().getTotal().toPlainString());
        order.setPayment(payment);
        ResponseEntity<ReadableOrderConfirmation> checkout = testRestTemplate.postForEntity(
                "/api/v1/auth/cart/" + created.getBody().getCode() + "/checkout",
                new HttpEntity<>(order, headers), ReadableOrderConfirmation.class);
        assertEquals(200, checkout.getStatusCodeValue());
        assertNotNull(checkout.getBody().getId());
        assertEquals(200, testRestTemplate.exchange("/api/v1/auth/orders/" + checkout.getBody().getId(),
                HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class).getStatusCodeValue());
    }
}
