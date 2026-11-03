package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class AuthorizationJourneyTest extends ServicesTestSupport {

    @Test
    public void adminCanDelegateWithoutGrantingPlatformOrRetailerPrivileges() {
        PersistableUser admin = user("ADMIN");
        JsonNode created = ok("/api/v1/private/user/", HttpMethod.POST, admin, getHeader(), HttpStatus.OK);
        assertTrue(created.path("id").isIntegralNumber());
        assertEquals(admin.getUserName(), created.path("userName").asText());
        assertEquals("DEFAULT", created.path("merchant").asText());
        HttpHeaders headers = getHeader(admin.getUserName(), "Password1");
        String profile = "/api/v1/private/users/" + created.path("id").asLong();
        assertEquals(admin.getUserName(), ok(profile, HttpMethod.GET, null, headers,
                HttpStatus.OK).path("userName").asText());
        denied(profile, HttpMethod.GET, null, new HttpHeaders());

        for (String group : Arrays.asList("SUPERADMIN", "ADMIN_RETAILER")) {
            PersistableUser forbidden = user(group);
            assertEquals(HttpStatus.UNAUTHORIZED,
                    exchange("/api/v1/private/user/", HttpMethod.POST, forbidden, headers).getStatusCode());
            assertEquals(HttpStatus.UNAUTHORIZED, exchange("/api/v1/private/login", HttpMethod.POST,
                    new AuthenticationRequest(forbidden.getUserName(), "Password1"), new HttpHeaders()).getStatusCode());
        }

        PersistableUser delegated = user("ADMIN_CATALOGUE");
        JsonNode lower = ok("/api/v1/private/user/", HttpMethod.POST, delegated, headers, HttpStatus.OK);
        assertEquals(1, lower.path("groups").size());
        assertEquals("ADMIN_CATALOGUE", lower.path("groups").get(0).path("name").asText());
        assertNotNull(getHeader(delegated.getUserName(), "Password1").getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    public void customerCanRegisterLoginAndReadOwnProfile() {
        PersistableCustomer customer = customer();
        HttpHeaders headers = registerAndLogin(customer);
        JsonNode profile = ok("/api/v1/auth/customer/profile", HttpMethod.GET, null, headers, HttpStatus.OK);
        assertEquals(customer.getEmailAddress(), profile.path("emailAddress").asText());
        denied("/api/v1/auth/customer/profile", HttpMethod.GET, null, new HttpHeaders());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange("/api/v1/private/login", HttpMethod.POST,
                new AuthenticationRequest(customer.getEmailAddress(), "Password1"), new HttpHeaders()).getStatusCode());
    }

    @Test
    public void storefrontCanBrowseCreateUpdateAndReadCart() {
        ReadableProduct product = sampleProduct("uat" + suffix());
        JsonNode store = ok("/api/v1/store/DEFAULT", HttpMethod.GET, null, new HttpHeaders(), HttpStatus.OK);
        assertEquals("DEFAULT", store.path("code").asText());
        JsonNode categories = ok("/api/v1/category", HttpMethod.GET, null, new HttpHeaders(), HttpStatus.OK);
        assertTrue(categories.path("categories").isArray());
        JsonNode products = ok("/api/v1/products?sku=" + product.getSku(), HttpMethod.GET,
                null, new HttpHeaders(), HttpStatus.OK);
        assertTrue(products.path("products").isArray());
        assertFalse(products.path("products").isEmpty());
        JsonNode detail = ok("/api/v2/product/" + product.getSku(), HttpMethod.GET,
                null, new HttpHeaders(), HttpStatus.OK);
        assertEquals(product.getSku(), detail.path("sku").asText());
        ReadableShoppingCart cart = cart(product);
        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct(product.getSku());
        item.setQuantity(2);
        ok("/api/v1/cart/" + cart.getCode() + "/multi", HttpMethod.POST,
                Collections.singletonList(item), new HttpHeaders(), HttpStatus.CREATED);
        JsonNode read = ok("/api/v1/cart/" + cart.getCode(), HttpMethod.GET, null,
                new HttpHeaders(), HttpStatus.OK);
        assertEquals(2, read.path("quantity").asInt());
        assertEquals(product.getSku(), read.path("products").get(0).path("sku").asText());
        assertTrue(read.path("total").isNumber());
    }

    @Test
    public void authenticatedCustomerCanCheckoutAndReadOrder() {
        IntegrationModuleConfiguration configuration = new IntegrationModuleConfiguration();
        configuration.setCode("moneyorder");
        configuration.setActive(true);
        configuration.setDefaultSelected(true);
        configuration.setIntegrationKeys(Collections.singletonMap("address", "1 Test Street"));
        assertEquals(HttpStatus.OK, exchange("/api/v1/private/modules/payment", HttpMethod.POST,
                configuration, getHeader()).getStatusCode());
        ReadableProduct product = sampleProduct("checkout" + suffix());
        ReadableShoppingCart cart = cart(product);
        PersistableCustomer customer = customer();
        HttpHeaders headers = registerAndLogin(customer);
        PersistablePayment payment = new PersistablePayment();
        payment.setPaymentModule("moneyorder");
        payment.setPaymentType("MONEYORDER");
        payment.setTransactionType("AUTHORIZECAPTURE");
        payment.setAmount(cart.getTotal().toPlainString());
        PersistableOrder order = new PersistableOrder();
        order.setCustomerAgreement(true);
        order.setCurrency("CAD");
        order.setPayment(payment);
        JsonNode confirmation = ok("/api/v1/auth/cart/" + cart.getCode() + "/checkout", HttpMethod.POST,
                order, headers, HttpStatus.OK);
        assertTrue(confirmation.path("id").isIntegralNumber());
        JsonNode read = ok("/api/v1/auth/orders/" + confirmation.path("id").asLong(), HttpMethod.GET,
                null, headers, HttpStatus.OK);
        assertEquals(confirmation.path("id"), read.path("id"));
        assertEquals(customer.getEmailAddress(), read.path("customer").path("emailAddress").asText());
    }

    @Test
    public void servicesBasicAuthenticationAcceptsValidAndRejectsInvalidCredentials() {
        ResponseEntity<JsonNode> valid = testRestTemplate.withBasicAuth("admin@shopizer.com", "password")
                .postForEntity("/services/private/system/optin", "{}", JsonNode.class);
        assertEquals(HttpStatus.OK, valid.getStatusCode());
        assertEquals(200, valid.getBody().path("status").asInt());
        assertEquals(HttpStatus.UNAUTHORIZED, testRestTemplate.withBasicAuth("admin@shopizer.com", "wrong")
                .postForEntity("/services/private/system/optin", "{}", JsonNode.class).getStatusCode());
    }

    @Test
    public void healthAndSwaggerRemainAvailable() {
        assertEquals("UP", ok("/actuator/health", HttpMethod.GET, null,
                new HttpHeaders(), HttpStatus.OK).path("status").asText());
        JsonNode docs = ok("/v2/api-docs", HttpMethod.GET, null, new HttpHeaders(), HttpStatus.OK);
        assertEquals("2.0", docs.path("swagger").asText());
        assertTrue(docs.path("paths").has("/api/v1/private/user/"));
    }

    private HttpHeaders registerAndLogin(PersistableCustomer customer) {
        ok("/api/v1/customer/register", HttpMethod.POST, customer, new HttpHeaders(), HttpStatus.OK);
        JsonNode login = ok("/api/v1/customer/login", HttpMethod.POST,
                new AuthenticationRequest(customer.getEmailAddress(), "Password1"), new HttpHeaders(), HttpStatus.OK);
        assertTrue(login.path("token").isTextual());
        assertFalse(login.path("token").asText().isEmpty());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.path("token").asText());
        return headers;
    }

    private ReadableShoppingCart cart(ReadableProduct product) {
        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct(product.getSku());
        item.setQuantity(1);
        ResponseEntity<ReadableShoppingCart> response = testRestTemplate.postForEntity("/api/v1/cart",
                item, ReadableShoppingCart.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody().getCode());
        assertEquals(1, response.getBody().getQuantity());
        return response.getBody();
    }

    private PersistableCustomer customer() {
        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress("c-" + suffix() + "@example.test");
        customer.setPassword("Password1");
        customer.setRepeatPassword("Password1");
        customer.setGender("M");
        customer.setLanguage("en");
        customer.setStoreCode("DEFAULT");
        Address address = new Address();
        address.setFirstName("Acceptance");
        address.setLastName("Customer");
        address.setAddress("1 Test Street");
        address.setCity("Montreal");
        address.setCountry("CA");
        address.setZone("QC");
        address.setPostalCode("H2X1Y4");
        address.setPhone("5145550100");
        customer.setBilling(address);
        customer.setDelivery(address);
        return customer;
    }

    private PersistableUser user(String name) {
        PersistableUser user = new PersistableUser();
        user.setUserName("uat-" + suffix() + "@example.test");
        user.setEmailAddress(user.getUserName());
        user.setFirstName("Acceptance");
        user.setLastName("Admin");
        user.setDefaultLanguage("en");
        user.setPassword("Password1");
        user.setRepeatPassword("Password1");
        user.setActive(true);
        PersistableGroup group = new PersistableGroup();
        group.setName(name);
        user.getGroups().add(group);
        return user;
    }

    private JsonNode ok(String path, HttpMethod method, Object body, HttpHeaders headers, HttpStatus expected) {
        ResponseEntity<JsonNode> response = exchange(path, method, body, headers);
        assertEquals(path, expected, response.getStatusCode());
        assertNotNull(path, response.getBody());
        return response.getBody();
    }

    private void denied(String path, HttpMethod method, Object body, HttpHeaders headers) {
        HttpStatus status = exchange(path, method, body, headers).getStatusCode();
        assertTrue(path + " should deny access, got " + status,
                status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN);
    }

    private ResponseEntity<JsonNode> exchange(String path, HttpMethod method, Object body, HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        return testRestTemplate.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
