package com.salesmanager.test.shop.uat;

import static org.junit.Assert.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.*;
import org.springframework.test.context.junit4.SpringRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class StorefrontJourneyUatTest extends ServicesTestSupport {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void customerRegistersLogsInAndReadsOwnProfile() throws Exception {
        HttpHeaders customer = registerAndLogin();
        JsonNode profile = request("/api/v1/auth/customer/profile", HttpMethod.GET, null, customer, HttpStatus.OK);
        assertTrue(profile.path("id").asLong() > 0);
        assertTrue(profile.path("emailAddress").asText().endsWith("@example.com"));
    }

    @Test
    public void browseStoreCategoriesProductsAndProductDetail() throws Exception {
        JsonNode product = createProduct();
        assertEquals("DEFAULT", request("/api/v1/store/DEFAULT", HttpMethod.GET, null, null, HttpStatus.OK).path("code").asText());
        assertTrue(request("/api/v1/category", HttpMethod.GET, null, null, HttpStatus.OK).path("categories").size() > 0);
        assertTrue(request("/api/v1/products", HttpMethod.GET, null, null, HttpStatus.OK).path("products").size() > 0);
        JsonNode detail = request("/api/v2/product/" + product.path("sku").asText(), HttpMethod.GET, null, null, HttpStatus.OK);
        assertEquals(product.path("sku").asText(), detail.path("sku").asText());
    }

    @Test
    public void cartCreatesUpdatesAndReturnsQuantitiesAndTotals() throws Exception {
        JsonNode product = createProduct();
        ObjectNode item = mapper.createObjectNode().put("product", product.path("sku").asText()).put("quantity", 1);
        JsonNode cart = request("/api/v1/cart", HttpMethod.POST, item, null, HttpStatus.CREATED);
        assertEquals(1, cart.path("quantity").asInt());
        String code = cart.path("code").asText();
        assertFalse(code.isEmpty());
        BigDecimal subtotal = cart.path("subtotal").decimalValue();
        assertTrue(subtotal.signum() > 0);
        item.put("quantity", 3);
        JsonNode updated = request("/api/v1/cart/" + code, HttpMethod.PUT, item, null, HttpStatus.CREATED);
        assertEquals(3, updated.path("quantity").asInt());
        JsonNode fetched = request("/api/v1/cart/" + code, HttpMethod.GET, null, null, HttpStatus.OK);
        assertEquals(code, fetched.path("code").asText());
        assertEquals(3, fetched.path("quantity").asInt());
        assertEquals(3, fetched.path("products").path(0).path("quantity").asInt());
        assertEquals(0, subtotal.multiply(BigDecimal.valueOf(3)).compareTo(fetched.path("subtotal").decimalValue()));
        assertEquals(0, updated.path("total").decimalValue().compareTo(fetched.path("total").decimalValue()));
        assertTrue(fetched.path("total").decimalValue().signum() > 0);
    }

    @Test
    public void authenticatedCustomerChecksOutAndReadsOrder() throws Exception {
        ObjectNode paymentConfiguration = mapper.createObjectNode().put("code", "moneyorder")
                .put("active", true).put("defaultSelected", true);
        paymentConfiguration.putObject("integrationKeys").put("address", "123 Test Street, Montreal, CA");
        request("/api/v1/private/modules/payment", HttpMethod.POST, paymentConfiguration, getHeader(), HttpStatus.OK);
        HttpHeaders customer = registerAndLogin();
        JsonNode product = createProduct();
        ObjectNode item = mapper.createObjectNode().put("product", product.path("sku").asText()).put("quantity", 1);
        JsonNode cart = request("/api/v1/cart", HttpMethod.POST, item, customer, HttpStatus.CREATED);
        ObjectNode order = mapper.createObjectNode().put("customerAgreement", true).put("currency", "CAD");
        order.putObject("payment").put("paymentType", "MONEYORDER").put("transactionType", "AUTHORIZECAPTURE")
                .put("paymentModule", "moneyorder").put("amount", cart.path("total").asText());
        JsonNode confirmation = request("/api/v1/auth/cart/" + cart.path("code").asText() + "/checkout",
                HttpMethod.POST, order, customer, HttpStatus.OK);
        long id = confirmation.path("id").asLong();
        assertTrue(id > 0);
        assertTrue(confirmation.path("products").size() > 0);
        JsonNode fetched = request("/api/v1/auth/orders/" + id, HttpMethod.GET, null, customer, HttpStatus.OK);
        assertEquals(id, fetched.path("id").asLong());
    }

    private HttpHeaders registerAndLogin() throws Exception {
        String email = "uat-" + UUID.randomUUID().toString().replace("-", "") + "@example.com";
        ObjectNode customer = mapper.createObjectNode().put("emailAddress", email).put("password", "clear123")
                .put("gender", "M").put("language", "en").put("storeCode", "DEFAULT");
        customer.putObject("billing").put("firstName", "Uat").put("lastName", "Customer")
                .put("country", "CA").put("zone", "QC").put("address", "123 Test Street")
                .put("city", "Montreal").put("postalCode", "H2H2H2");
        JsonNode registered = request("/api/v1/customer/register", HttpMethod.POST, customer, getHeader(), HttpStatus.OK);
        assertFalse(registered.path("token").asText().isEmpty());
        JsonNode login = request("/api/v1/customer/login", HttpMethod.POST,
                mapper.createObjectNode().put("username", email).put("password", "clear123"), null, HttpStatus.OK);
        String token = login.path("token").asText();
        assertFalse(token.isEmpty());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private JsonNode createProduct() throws Exception {
        String code = "uat-" + UUID.randomUUID();
        HttpHeaders admin = getHeader();
        ObjectNode category = mapper.createObjectNode().put("code", code).put("visible", true).put("depth", 0);
        category.putArray("descriptions").addObject().put("language", "en").put("name", code).put("friendlyUrl", code);
        JsonNode createdCategory = request("/api/v1/private/category", HttpMethod.POST, category, admin, HttpStatus.CREATED);
        ObjectNode product = mapper.createObjectNode().put("sku", code).put("available", true).put("price", 10).put("quantity", 100);
        product.putArray("descriptions").addObject().put("name", code).put("language", "en");
        product.putArray("categories").addObject().put("id", createdCategory.path("id").asLong());
        product.putObject("productSpecifications").put("manufacturer", "DEFAULT");
        ObjectNode inventory = product.putObject("inventory").put("quantity", 100).put("sku", code);
        inventory.putObject("price").put("defaultPrice", true).put("price", 10);
        JsonNode created = request("/api/v1/private/product", HttpMethod.POST, product, admin, HttpStatus.CREATED);
        JsonNode detail = request("/api/v2/product/" + code, HttpMethod.GET, null, null, HttpStatus.OK);
        assertEquals(created.path("id").asLong(), detail.path("id").asLong());
        return detail;
    }

    private JsonNode request(String url, HttpMethod method, JsonNode body, HttpHeaders headers, HttpStatus expected) throws Exception {
        HttpHeaders requestHeaders = new HttpHeaders();
        if (headers != null) requestHeaders.putAll(headers);
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = testRestTemplate.exchange(url, method,
                new HttpEntity<>(body == null ? null : body.toString(), requestHeaders), String.class);
        assertEquals(url + " body=" + response.getBody(), expected, response.getStatusCode());
        return response.getBody() == null || response.getBody().isEmpty()
                ? mapper.nullNode() : mapper.readTree(response.getBody());
    }
}
