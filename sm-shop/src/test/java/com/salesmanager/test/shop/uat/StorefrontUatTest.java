package com.salesmanager.test.shop.uat;

import static org.junit.Assert.*;

import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.core.business.services.payments.PaymentService;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
public class StorefrontUatTest extends ServicesTestSupport {
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private MerchantStoreService merchantStoreService;

    @Test
    public void adminJwtProtectsPrivateEndpoints() {
        HttpHeaders admin = getHeader();
        assertNotNull(admin.getFirst("Authorization"));
        assertEquals(200, get("/api/v1/private/orders", admin).getStatusCodeValue());
        assertEquals(401, get("/api/v1/private/orders", new HttpHeaders()).getStatusCodeValue());
        HttpHeaders bad = new HttpHeaders();
        bad.set("Authorization", "invalid-token");
        assertEquals(401, get("/api/v1/private/orders", bad).getStatusCodeValue());
    }

    @Test
    public void customerCanBrowseCheckoutAndReadOrder() throws Exception {
        IntegrationConfiguration configuration = new IntegrationConfiguration();
        configuration.setModuleCode("moneyorder");
        configuration.setActive(true);
        configuration.setEnvironment("TEST");
        configuration.getIntegrationKeys().put("address", "1 Test Street, Montreal");
        paymentService.savePaymentModuleConfiguration(configuration, merchantStoreService.getByCode("DEFAULT"));
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "uat-" + suffix + "@example.com";
        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress(email);
        customer.setPassword("clear123");
        customer.setGender("M");
        customer.setLanguage("en");
        customer.setStoreCode("DEFAULT");
        Address billing = new Address();
        billing.setFirstName("Uat");
        billing.setLastName("Customer");
        billing.setCountry("CA");
        billing.setStateProvince("Quebec");
        billing.setCity("Montreal");
        billing.setAddress("1 Test Street");
        billing.setPostalCode("H2X1Y4");
        billing.setPhone("5145551234");
        customer.setBilling(billing);
        assertEquals(200, testRestTemplate.postForEntity("/api/v1/customer/register", customer, String.class).getStatusCodeValue());
        ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login", new AuthenticationRequest(email, "clear123"), AuthenticationResponse.class);
        assertEquals(200, login.getStatusCodeValue());
        assertNotNull(login.getBody().getToken());
        HttpHeaders token = new HttpHeaders();
        token.setBearerAuth(login.getBody().getToken());
        token.setContentType(MediaType.APPLICATION_JSON);
        assertEquals(200, get("/api/v1/auth/orders", token).getStatusCodeValue());
        assertEquals(200, get("/api/v1/store/DEFAULT", new HttpHeaders()).getStatusCodeValue());
        assertEquals(200, get("/api/v1/category/", new HttpHeaders()).getStatusCodeValue());
        String sku = sampleProduct("uat-" + suffix).getSku();
        assertEquals(200, get("/api/v1/products", new HttpHeaders()).getStatusCodeValue());
        assertEquals(200, get("/api/v2/product/" + sku, new HttpHeaders()).getStatusCodeValue());
        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct(sku);
        item.setQuantity(1);
        ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart/", item, ReadableShoppingCart.class);
        assertEquals(201, created.getStatusCodeValue());
        String cartUrl = "/api/v1/cart/" + created.getBody().getCode();
        item.setQuantity(2);
        assertEquals(201, testRestTemplate.postForEntity(cartUrl + "/multi", new PersistableShoppingCartItem[]{item}, ReadableShoppingCart.class).getStatusCodeValue());
        ResponseEntity<ReadableShoppingCart> read = testRestTemplate.getForEntity(cartUrl, ReadableShoppingCart.class);
        assertEquals(200, read.getStatusCodeValue());
        assertEquals(2, read.getBody().getQuantity());
        String order = "{\"currency\":\"CAD\",\"customerAgreement\":true,\"payment\":{\"paymentModule\":\"moneyorder\",\"paymentType\":\"MONEYORDER\",\"transactionType\":\"AUTHORIZECAPTURE\",\"amount\":\"" + read.getBody().getTotal().toPlainString() + "\"}}";
        ResponseEntity<JsonNode> checkout = testRestTemplate.postForEntity("/api/v1/auth/cart/" + created.getBody().getCode() + "/checkout", new HttpEntity<>(order, token), JsonNode.class);
        assertEquals(String.valueOf(checkout.getBody()), 200, checkout.getStatusCodeValue());
        assertNotNull(checkout.getBody().get("id"));
        long orderId = checkout.getBody().get("id").asLong();
        assertTrue(orderId > 0);
        ResponseEntity<JsonNode> saved = testRestTemplate.exchange("/api/v1/auth/orders/" + orderId, HttpMethod.GET, new HttpEntity<>(token), JsonNode.class);
        assertEquals(200, saved.getStatusCodeValue());
        assertEquals(orderId, saved.getBody().get("id").asLong());
    }

    @Test
    public void malformedBearerTokenCurrentlyReturnsServerError() {
        HttpHeaders bad = new HttpHeaders();
        bad.setBearerAuth("invalid-token");
        assertEquals(500, get("/api/v1/private/orders", bad).getStatusCodeValue());
    }

    @Test
    public void basicAuthenticationProtectsLegacyServices() {
        String url = "/services/private/system/optin";
        assertEquals(200, testRestTemplate.withBasicAuth("admin@shopizer.com", "password").postForEntity(url, "{}", String.class).getStatusCodeValue());
        assertEquals(401, testRestTemplate.withBasicAuth("admin@shopizer.com", "incorrect").postForEntity(url, "{}", String.class).getStatusCodeValue());
    }

    @Test
    public void healthAndSwaggerAreAvailable() {
        ResponseEntity<JsonNode> health = testRestTemplate.getForEntity("/actuator/health", JsonNode.class);
        assertEquals(200, health.getStatusCodeValue());
        assertEquals("UP", health.getBody().get("status").asText());
        ResponseEntity<JsonNode> docs = testRestTemplate.getForEntity("/v2/api-docs", JsonNode.class);
        assertEquals(200, docs.getStatusCodeValue());
        assertEquals("2.0", docs.getBody().get("swagger").asText());
        assertTrue(docs.getBody().get("paths").size() > 0);
    }

    private ResponseEntity<String> get(String url, HttpHeaders headers) {
        return testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
