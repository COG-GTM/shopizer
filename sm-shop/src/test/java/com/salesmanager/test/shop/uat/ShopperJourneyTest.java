package com.salesmanager.test.shop.uat;

import static org.junit.Assert.*;
import java.util.UUID;
import javax.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.http.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.shoppingcart.*;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.UserJourneySupport;
import com.salesmanager.core.business.services.payments.PaymentService;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.system.IntegrationConfiguration;

@RunWith(SpringRunner.class)
public class ShopperJourneyTest extends UserJourneySupport {
    @Inject private PaymentService payments;
    @Inject private MerchantStoreService stores;
    @Test public void adminJwtJourney() {
        HttpHeaders headers = getHeader(); assertNotNull(headers.getFirst("Authorization"));
        assertEquals(HttpStatus.OK, get("/api/v1/private/users/1", headers).getStatusCode());
        assertDenied(get("/api/v1/private/users/1", new HttpHeaders()).getStatusCode());
    }

    @Test public void publicDiscoveryJourney() {
        assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/api/v1/store/DEFAULT", String.class).getStatusCode());
        assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/api/v1/category", String.class).getStatusCode());
        ReadableProduct product = sampleProduct("browse-" + UUID.randomUUID());
        assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/api/v1/products", String.class).getStatusCode());
        ResponseEntity<ReadableProduct> detail = testRestTemplate.getForEntity("/api/v2/product/" + product.getSku(), ReadableProduct.class);
        assertEquals(HttpStatus.OK, detail.getStatusCode());
        assertEquals(product.getSku(), detail.getBody().getSku());
    }

    @Test public void healthAndApiDocs() {
        ResponseEntity<JsonNode> health = testRestTemplate.getForEntity("/actuator/health", JsonNode.class);
        assertEquals(HttpStatus.OK, health.getStatusCode());
        assertEquals("UP", health.getBody().path("status").asText());
        assertEquals(HttpStatus.OK, testRestTemplate.getForEntity("/v2/api-docs", String.class).getStatusCode());
    }

    @Test public void servicesBasicAuthenticationJourney() {
        ResponseEntity<String> valid = testRestTemplate.withBasicAuth("admin@shopizer.com", "password")
            .postForEntity("/services/private/system/optin", "{}", String.class);
        assertEquals(HttpStatus.OK, valid.getStatusCode());
        assertDenied(testRestTemplate.withBasicAuth("admin@shopizer.com", "incorrect")
            .postForEntity("/services/private/system/optin", "{}", String.class).getStatusCode());
    }

    @Test public void customerCartCheckoutJourney() throws Exception {
        String email = "c-" + UUID.randomUUID() + "@test.com";
        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress(email); customer.setPassword(PASSWORD);
        customer.setGender("M"); customer.setLanguage("en"); customer.setStoreCode("DEFAULT");
        Address billing = new Address(); billing.setFirstName("Journey"); billing.setLastName("Customer");
        billing.setCountry("CA"); billing.setStateProvince("QC"); billing.setAddress("123 Test Street");
        billing.setCity("Montreal"); billing.setPostalCode("H2X1Y4");
        customer.setBilling(billing);
        assertEquals(HttpStatus.OK, testRestTemplate.postForEntity("/api/v1/customer/register", customer, String.class).getStatusCode());
        ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
            new AuthenticationRequest(email, PASSWORD), AuthenticationResponse.class);
        assertEquals(HttpStatus.OK, login.getStatusCode()); assertNotNull(login.getBody().getToken());
        HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(login.getBody().getToken());
        assertEquals(HttpStatus.OK, get("/api/v1/auth/customer/profile", headers).getStatusCode());
        ReadableProduct product = sampleProduct("checkout-" + UUID.randomUUID());
        PersistableShoppingCartItem item = new PersistableShoppingCartItem(); item.setProduct(product.getSku()); item.setQuantity(1);
        ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart/", new HttpEntity<>(item, headers), ReadableShoppingCart.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        String cart = "/api/v1/cart/" + created.getBody().getCode();
        item.setQuantity(2);
        ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(cart + "/multi", HttpMethod.POST,
            new HttpEntity<>(new PersistableShoppingCartItem[]{item}, headers), ReadableShoppingCart.class);
        assertEquals(HttpStatus.CREATED, updated.getStatusCode()); assertEquals(2, updated.getBody().getQuantity());
        ResponseEntity<ReadableShoppingCart> read = testRestTemplate.getForEntity(cart, ReadableShoppingCart.class);
        assertEquals(HttpStatus.OK, read.getStatusCode()); assertEquals(2, read.getBody().getQuantity());
        PersistablePayment payment = new PersistablePayment(); payment.setPaymentType("MONEYORDER"); payment.setPaymentModule("moneyorder");
        payment.setTransactionType("AUTHORIZECAPTURE"); payment.setAmount(read.getBody().getTotal().toPlainString());
        PersistableOrder order = new PersistableOrder(); order.setCustomerAgreement(true); order.setPayment(payment); order.setCurrency("CAD");
        MerchantStore store = stores.getByCode("DEFAULT");
        IntegrationConfiguration original = payments.getPaymentConfiguration("moneyorder", store);
        IntegrationConfiguration configured = new IntegrationConfiguration();
        configured.setModuleCode("moneyorder"); configured.setActive(true);
        configured.getIntegrationKeys().put("address", "123 Test Street, Montreal");
        payments.savePaymentModuleConfiguration(configured, store);
        try {
        ResponseEntity<ReadableOrderConfirmation> checkout = testRestTemplate.postForEntity(
            "/api/v1/auth/cart/" + created.getBody().getCode() + "/checkout", new HttpEntity<>(order, headers), ReadableOrderConfirmation.class);
        assertEquals(checkout.toString(), HttpStatus.OK, checkout.getStatusCode()); assertNotNull(checkout.getBody().getId());
        assertEquals(HttpStatus.OK, get("/api/v1/auth/orders/" + checkout.getBody().getId(), headers).getStatusCode());
        } finally {
            if (original == null) payments.removePaymentModuleConfiguration("moneyorder", store);
            else payments.savePaymentModuleConfiguration(original, store);
        }
    }

    private ResponseEntity<String> get(String path, HttpHeaders headers) {
        return testRestTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
    private void assertDenied(HttpStatus status) {
        assertTrue("Expected 401/403 but got " + status, status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN);
    }
}
