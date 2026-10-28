package com.salesmanager.test.shop.uat;

import static org.junit.Assert.*;

import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
public class MoneyOrderCheckoutUatTest extends ServicesTestSupport {
    private final ObjectMapper json = new ObjectMapper();
    private HttpHeaders admin;
    private String currency;

    @Before
    public void configureOfflineCheckout() throws Exception {
        admin = getHeader();
        currency = fetchStore().getCurrency();
        ObjectNode configuration = json.createObjectNode();
        configuration.put("code", "moneyorder");
        configuration.put("active", true);
        configuration.putObject("integrationKeys").put("address", "Test remittance address");
        assertEquals(200, request("/api/v1/private/modules/payment", HttpMethod.POST, configuration, admin).getStatusCodeValue());
    }

    @Test
    public void anonymousMoneyOrderCanBeCheckedOutAndReadByAdminAsUnpaid() throws Exception {
        ReadableShoppingCart cart = cart();
        ObjectNode order = order(cart, "MONEYORDER");
        order.set("customer", customer(false));
        ResponseEntity<String> response = request("/api/v1/cart/" + cart.getCode() + "/checkout", HttpMethod.POST, order, null);
        assertEquals(response.getBody(), 200, response.getStatusCodeValue());
        long id = json.readTree(response.getBody()).path("id").asLong();
        assertTrue(id > 0);
        assertUnpaidOrder("/api/v1/private/orders/" + id, admin);
    }

    @Test
    public void registeredCustomerCanLoginCheckoutAndReadOwnUnpaidOrder() throws Exception {
        HttpHeaders customer = registeredCustomer();
        ReadableShoppingCart cart = cart();
        ResponseEntity<String> response = request("/api/v1/auth/cart/" + cart.getCode() + "/checkout", HttpMethod.POST,
                order(cart, "MONEYORDER"), customer);
        assertEquals(response.getBody(), 200, response.getStatusCodeValue());
        long id = json.readTree(response.getBody()).path("id").asLong();
        assertTrue(id > 0);
        assertUnpaidOrder("/api/v1/auth/orders/" + id, customer);
    }

    @Test
    public void anonymousSpoofIsRejectedWithoutCreatingAnOrderAndCartCanBeRetried() throws Exception {
        ReadableShoppingCart cart = cart();
        int before = orderCount();
        ObjectNode order = order(cart, "CREDITCARD");
        order.set("customer", customer(false));
        ResponseEntity<String> rejected = request("/api/v1/cart/" + cart.getCode() + "/checkout", HttpMethod.POST, order, null);
        assertEquals(rejected.getBody(), 400, rejected.getStatusCodeValue());
        assertEquals(before, orderCount());
        assertEquals(200, request("/api/v1/cart/" + cart.getCode(), HttpMethod.GET, null, null).getStatusCodeValue());
        ((ObjectNode) order.get("payment")).put("paymentType", "MONEYORDER");
        ResponseEntity<String> retry = request("/api/v1/cart/" + cart.getCode() + "/checkout", HttpMethod.POST, order, null);
        assertEquals(retry.getBody(), 200, retry.getStatusCodeValue());
        assertUnpaidOrder("/api/v1/private/orders/" + json.readTree(retry.getBody()).path("id").asLong(), admin);
    }

    @Test
    public void authenticatedSpoofIsRejectedWithoutCreatingAnOrder() throws Exception {
        HttpHeaders customer = registeredCustomer();
        ReadableShoppingCart cart = cart();
        int before = orderCount();
        ResponseEntity<String> rejected = request("/api/v1/auth/cart/" + cart.getCode() + "/checkout", HttpMethod.POST,
                order(cart, "CREDITCARD"), customer);
        assertEquals(rejected.getBody(), 503, rejected.getStatusCodeValue());
        assertEquals(before, orderCount());
    }

    @Test
    public void adminSecurityBrowseCartHealthAndDocsContractsRemainAvailable() throws Exception {
        String privatePath = "/api/v1/private/orders";
        assertEquals(200, request(privatePath, HttpMethod.GET, null, admin).getStatusCodeValue());
        assertUnauthorized(request(privatePath, HttpMethod.GET, null, null));
        for (String path : new String[] {"/api/v1/store/DEFAULT", "/api/v1/category", "/api/v1/products", "/v2/api-docs"}) {
            ResponseEntity<String> response = request(path, HttpMethod.GET, null, null);
            assertEquals(path + ": " + response.getBody(), 200, response.getStatusCodeValue());
            assertNotNull(json.readTree(response.getBody()));
        }
        ResponseEntity<String> health = request("/actuator/health", HttpMethod.GET, null, null);
        assertEquals(200, health.getStatusCodeValue());
        assertEquals("UP", json.readTree(health.getBody()).path("status").asText());
        ReadableShoppingCart cart = cart();
        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct(cart.getProducts().get(0).getSku());
        item.setQuantity(2);
        ResponseEntity<String> update = request("/api/v1/cart/" + cart.getCode() + "/multi", HttpMethod.POST,
                new PersistableShoppingCartItem[] {item}, null);
        assertEquals(update.getBody(), 201, update.getStatusCodeValue());
        JsonNode read = json.readTree(request("/api/v1/cart/" + cart.getCode(), HttpMethod.GET, null, null).getBody());
        assertEquals(2, read.path("quantity").asInt());
    }

    private ReadableShoppingCart cart() throws Exception {
        String sku = "payment-" + UUID.randomUUID().toString().substring(0, 8);
        ReadableProduct product = sampleProduct(sku);
        ResponseEntity<String> detail = request("/api/v2/product/" + product.getSku(), HttpMethod.GET, null, null);
        assertEquals(200, detail.getStatusCodeValue());
        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct(product.getSku());
        item.setQuantity(1);
        ResponseEntity<ReadableShoppingCart> response = testRestTemplate.postForEntity("/api/v1/cart",
                new HttpEntity<>(item), ReadableShoppingCart.class);
        assertEquals(201, response.getStatusCodeValue());
        assertNotNull(response.getBody().getCode());
        return response.getBody();
    }

    private ObjectNode order(ReadableShoppingCart cart, String type) {
        ObjectNode order = json.createObjectNode();
        order.put("currency", currency);
        order.put("customerAgreement", true);
        ObjectNode payment = order.putObject("payment");
        payment.put("paymentModule", "moneyorder");
        payment.put("paymentType", type);
        payment.put("transactionType", "AUTHORIZECAPTURE");
        payment.put("amount", cart.getTotal().toPlainString());
        return order;
    }

    private ObjectNode customer(boolean registered) {
        ObjectNode customer = json.createObjectNode();
        customer.put("emailAddress", "payment-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        customer.put("storeCode", "DEFAULT");
        customer.put("language", "en");
        if (registered) {
            customer.put("password", "Test12345!");
            customer.put("repeatPassword", "Test12345!");
        }
        ObjectNode billing = customer.putObject("billing");
        billing.put("firstName", "Payment");
        billing.put("lastName", "Tester");
        billing.put("country", "CA");
        billing.put("zone", "QC");
        billing.put("address", "123 Test Street");
        billing.put("city", "Montreal");
        billing.put("postalCode", "H2X1Y4");
        billing.put("phone", "5145550123");
        customer.set("delivery", billing.deepCopy());
        return customer;
    }

    private HttpHeaders registeredCustomer() throws Exception {
        ObjectNode customer = customer(true);
        ResponseEntity<String> register = request("/api/v1/customer/register", HttpMethod.POST, customer, null);
        assertEquals(register.getBody(), 200, register.getStatusCodeValue());
        ObjectNode credentials = json.createObjectNode();
        credentials.put("username", customer.path("emailAddress").asText());
        credentials.put("password", "Test12345!");
        ResponseEntity<String> login = request("/api/v1/customer/login", HttpMethod.POST, credentials, null);
        assertEquals(login.getBody(), 200, login.getStatusCodeValue());
        JsonNode token = json.readTree(login.getBody()).path("token");
        assertTrue(token.isTextual() && !token.asText().isEmpty());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token.asText());
        assertEquals(200, request("/api/v1/auth/orders", HttpMethod.GET, null, headers).getStatusCodeValue());
        return headers;
    }

    private void assertUnpaidOrder(String path, HttpHeaders headers) throws Exception {
        ResponseEntity<String> response = request(path, HttpMethod.GET, null, headers);
        assertEquals(response.getBody(), 200, response.getStatusCodeValue());
        JsonNode order = json.readTree(response.getBody());
        assertEquals("ORDERED", order.path("orderStatus").asText());
        assertEquals("MONEYORDER", order.path("paymentType").asText());
    }

    private int orderCount() throws Exception {
        ResponseEntity<String> response = request("/api/v1/private/orders", HttpMethod.GET, null, admin);
        assertEquals(200, response.getStatusCodeValue());
        JsonNode count = json.readTree(response.getBody()).path("recordsTotal");
        assertTrue(count.isIntegralNumber());
        return count.asInt();
    }

    private ResponseEntity<String> request(String path, HttpMethod method, Object body, HttpHeaders headers) {
        HttpHeaders requestHeaders = new HttpHeaders();
        if (headers != null) requestHeaders.putAll(headers);
        requestHeaders.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return testRestTemplate.exchange(path, method, new HttpEntity<>(body, requestHeaders), String.class);
    }

    private void assertUnauthorized(ResponseEntity<String> response) {
        assertTrue(response.getStatusCode().toString(), response.getStatusCodeValue() == 401 || response.getStatusCodeValue() == 403);
    }
}
