package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class StorefrontJourneyUatTest extends ServicesTestSupport {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    public void adminLoginAuthorizesCatalogAndRejectsMissingToken() throws Exception {
        HttpHeaders admin = login("/api/v1/private/login", "admin@shopizer.com", "password");
        expect("/api/v1/private/product/types", HttpMethod.GET, null, admin, 200);
        int status = exchange("/api/v1/private/product/types", HttpMethod.GET, null, headers()).getStatusCodeValue();
        assertTrue("Missing token status: " + status, status == 401 || status == 403);
    }

    @Test
    public void customerRegistersLogsInAndReadsOwnProfile() throws Exception {
        String email = unique("customer") + "@example.com";
        HttpHeaders customer = register(email);
        JsonNode profile = expect("/api/v1/auth/customer/profile", HttpMethod.GET, null, customer, 200);
        assertEquals(email, profile.path("emailAddress").asText());
        int status = exchange("/api/v1/auth/customer/profile", HttpMethod.GET, null, headers()).getStatusCodeValue();
        assertTrue("Anonymous profile status: " + status, status == 401 || status == 403);
    }

    @Test
    public void visitorBrowsesStoreCategoriesProductsAndDetail() throws Exception {
        ReadableProduct product = sampleProduct(unique("browse"));
        JsonNode store = expect("/api/v1/store/DEFAULT", HttpMethod.GET, null, headers(), 200);
        assertEquals("DEFAULT", store.path("code").asText());
        JsonNode categories = expect("/api/v1/category?store=DEFAULT", HttpMethod.GET, null, headers(), 200);
        assertTrue(categories.path("categories").isArray());
        assertTrue(categories.path("categories").size() > 0);
        JsonNode products = expect("/api/v1/products?store=DEFAULT&count=100", HttpMethod.GET, null, headers(), 200);
        assertTrue(contains(products.path("products"), "sku", product.getSku()));
        JsonNode detail = expect("/api/v2/product/" + product.getSku(), HttpMethod.GET, null, headers(), 200);
        assertEquals(product.getSku(), detail.path("sku").asText());
        assertEquals(product.getId().longValue(), detail.path("id").asLong());
    }

    @Test
    public void visitorCreatesAddsUpdatesAndReadsCart() throws Exception {
        ReadableProduct first = sampleProduct(unique("cartfirst"));
        ReadableProduct second = sampleProduct(unique("cartsecond"));
        String code = createCart(first.getSku());
        expect("/api/v1/cart/" + code, HttpMethod.PUT, item(second.getSku(), 1), headers(), 201);
        expect("/api/v1/cart/" + code + "/multi", HttpMethod.POST,
                json.createArrayNode().add(item(first.getSku(), 2)), headers(), 201);
        JsonNode cart = expect("/api/v1/cart/" + code, HttpMethod.GET, null, headers(), 200);
        assertEquals(code, cart.path("code").asText());
        assertEquals(2, cart.path("products").size());
        boolean updated = false;
        for (JsonNode product : cart.path("products")) {
            if (first.getSku().equals(product.path("sku").asText())) {
                assertEquals(2, product.path("quantity").asInt());
                updated = true;
            }
        }
        assertTrue("Updated item must remain in the cart", updated);
    }

    @Test
    public void customerChecksOutAndReadsOrder() throws Exception {
        HttpHeaders customer = register(unique("checkout") + "@example.com");
        ReadableProduct product = sampleProduct(unique("orderproduct"));
        String code = createCart(product.getSku());
        expect("/api/v1/auth/customer/cart?cart=" + code, HttpMethod.GET, null, customer, 200);
        expect("/api/v1/private/modules/payment", HttpMethod.POST,
                json.readTree("{\"code\":\"moneyorder\",\"active\":true,\"defaultSelected\":true,"
                        + "\"integrationKeys\":{\"address\":\"1 Test Street\"},\"integrationOptions\":{}}"), getHeader(), 200);
        JsonNode total = expect("/api/v1/cart/" + code + "/total", HttpMethod.GET, null, headers(), 200);
        ObjectNode payment = json.createObjectNode().put("paymentModule", "moneyorder")
                .put("paymentType", "MONEYORDER").put("transactionType", "AUTHORIZECAPTURE")
                .put("amount", total.path("total").asText().replaceAll("[^0-9.]", ""));
        ObjectNode order = json.createObjectNode().put("currency", fetchStore().getCurrency()).put("customerAgreement", true);
        order.set("payment", payment);
        JsonNode confirmation = expect("/api/v1/auth/cart/" + code + "/checkout", HttpMethod.POST, order, customer, 200);
        assertTrue(confirmation.path("id").asLong() > 0);
        JsonNode saved = expect("/api/v1/auth/orders/" + confirmation.path("id").asLong(), HttpMethod.GET, null, customer, 200);
        assertEquals(confirmation.path("id"), saved.path("id"));
    }

    @Test
    public void servicesRequireValidBasicCredentials() {
        HttpHeaders valid = headers();
        valid.setBasicAuth("admin@shopizer.com", "password");
        assertEquals(200, exchange("/services/private/system/optin", HttpMethod.POST, json.createObjectNode(), valid).getStatusCodeValue());
        HttpHeaders invalid = headers();
        invalid.setBasicAuth("admin@shopizer.com", "wrong");
        int status = exchange("/services/private/system/optin", HttpMethod.POST, json.createObjectNode(), invalid).getStatusCodeValue();
        assertTrue("Invalid Basic credentials status: " + status, status == 401 || status == 403);
    }

    @Test
    public void healthIsUpAndSpringfoxDocumentsApi() throws Exception {
        JsonNode health = expect("/actuator/health", HttpMethod.GET, null, headers(), 200);
        assertEquals("UP", health.path("status").asText());
        JsonNode docs = expect("/v2/api-docs", HttpMethod.GET, null, headers(), 200);
        assertEquals("2.0", docs.path("swagger").asText());
        assertTrue(docs.path("paths").size() > 0);
    }

    @Test
    public void adminCatalogCreatesListsReadsAndIgnoresForeignIds() throws Exception {
        HttpHeaders admin = getHeader();
        String optionCode = unique("option");
        ObjectNode optionBody = json.createObjectNode().put("code", optionCode).put("type", "select");
        optionBody.putArray("descriptions").addObject().put("language", "en").put("name", optionCode);
        JsonNode option = expect("/api/v1/private/product/option", HttpMethod.POST, optionBody, admin, 201);
        long optionId = option.path("id").asLong();
        assertTrue(optionId > 0);
        assertEquals(optionCode, expect("/api/v1/private/product/option/" + optionId, HttpMethod.GET, null, admin, 200).path("code").asText());
        assertTrue(contains(expect("/api/v1/private/product/options?count=100", HttpMethod.GET, null, admin, 200).path("options"), "code", optionCode));

        String setsPath = "/api/v1/private/product/property/set";
        String setCode = unique("set");
        ObjectNode setBody = json.createObjectNode().put("code", setCode).put("option", optionId);
        expect(setsPath, HttpMethod.POST, setBody, admin, 201);
        long setId = find(expect(setsPath, HttpMethod.GET, null, admin, 200), "code", setCode).path("id").asLong();
        assertTrue(setId > 0);
        assertEquals(setCode, expect(setsPath + "/" + setId, HttpMethod.GET, null, admin, 200).path("code").asText());
        String newSetCode = unique("setnew");
        setBody.put("id", setId).put("code", newSetCode);
        expect(setsPath, HttpMethod.POST, setBody, admin, 201);
        JsonNode newSet = find(expect(setsPath, HttpMethod.GET, null, admin, 200), "code", newSetCode);
        assertNotEquals(setId, newSet.path("id").asLong());
        assertEquals(newSetCode, expect(setsPath + "/" + newSet.path("id").asLong(), HttpMethod.GET, null, admin, 200).path("code").asText());
        assertEquals(setCode, expect(setsPath + "/" + setId, HttpMethod.GET, null, admin, 200).path("code").asText());

        String typeCode = unique("type");
        ObjectNode typeBody = json.createObjectNode().put("code", typeCode);
        typeBody.putArray("descriptions").addObject().put("language", "en").put("name", typeCode);
        String typesPath = "/api/v1/private/product/type";
        long typeId = expect(typesPath, HttpMethod.POST, typeBody, admin, 200).path("id").asLong();
        assertTrue(typeId > 0);
        assertEquals(typeCode, expect(typesPath + "/" + typeId, HttpMethod.GET, null, admin, 200).path("code").asText());
        assertTrue(contains(expect("/api/v1/private/product/types?count=100", HttpMethod.GET, null, admin, 200).path("list"), "code", typeCode));
        String newTypeCode = unique("typenew");
        typeBody.put("id", typeId).put("code", newTypeCode);
        long newTypeId = expect(typesPath, HttpMethod.POST, typeBody, admin, 200).path("id").asLong();
        assertTrue(newTypeId > 0);
        assertNotEquals(typeId, newTypeId);
        assertEquals(typeCode, expect(typesPath + "/" + typeId, HttpMethod.GET, null, admin, 200).path("code").asText());
        assertEquals(newTypeCode, expect(typesPath + "/" + newTypeId, HttpMethod.GET, null, admin, 200).path("code").asText());
        assertTrue(contains(expect("/api/v1/private/product/types?count=100", HttpMethod.GET, null, admin, 200).path("list"), "code", newTypeCode));
    }

    private HttpHeaders register(String email) throws Exception {
        ObjectNode address = json.createObjectNode().put("firstName", "UAT").put("lastName", "Customer")
                .put("country", "BE").put("address", "1 Test Street").put("city", "Brussels")
                .put("postalCode", "1000").put("stateProvince", "Brussels");
        ObjectNode customer = json.createObjectNode().put("emailAddress", email).put("password", "clear123")
                .put("gender", "M").put("language", "en").put("storeCode", "DEFAULT");
        customer.set("billing", address);
        customer.set("delivery", address);
        expect("/api/v1/customer/register", HttpMethod.POST, customer, headers(), 200);
        return login("/api/v1/customer/login", email, "clear123");
    }

    private HttpHeaders login(String path, String username, String password) throws Exception {
        JsonNode response = expect(path, HttpMethod.POST,
                json.createObjectNode().put("username", username).put("password", password), headers(), 200);
        assertFalse(response.path("token").asText().isEmpty());
        HttpHeaders authorized = headers();
        authorized.setBearerAuth(response.path("token").asText());
        return authorized;
    }

    private String createCart(String sku) throws Exception {
        String code = expect("/api/v1/cart", HttpMethod.POST, item(sku, 1), headers(), 201).path("code").asText();
        assertFalse(code.isEmpty());
        return code;
    }

    private ObjectNode item(String sku, int quantity) {
        return json.createObjectNode().put("product", sku).put("quantity", quantity);
    }

    private JsonNode expect(String path, HttpMethod method, JsonNode body, HttpHeaders headers, int status) throws Exception {
        ResponseEntity<String> response = exchange(path, method, body, headers);
        assertEquals(method + " " + path + ": " + response.getBody(), status, response.getStatusCodeValue());
        return response.getBody() == null ? json.nullNode() : json.readTree(response.getBody());
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, JsonNode body, HttpHeaders headers) {
        return testRestTemplate.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private boolean contains(JsonNode array, String field, String value) {
        for (JsonNode item : array) {
            if (value.equals(item.path(field).asText())) {
                return true;
            }
        }
        return false;
    }

    private JsonNode find(JsonNode array, String field, String value) {
        for (JsonNode item : array) {
            if (value.equals(item.path(field).asText())) {
                return item;
            }
        }
        throw new AssertionError("Missing " + field + "=" + value);
    }

    private String unique(String prefix) {
        return "uat" + prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
