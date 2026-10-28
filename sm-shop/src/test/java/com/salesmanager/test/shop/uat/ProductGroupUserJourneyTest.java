package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
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
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.payments.PaymentService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ProductGroupUserJourneyTest extends ServicesTestSupport {

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private MerchantStoreService merchantStoreService;

    @Autowired
    private PaymentService paymentService;

    @Test
    public void adminCanCurateOwnGroupButAnonymousCannotMutateIt() throws Exception {
        HttpHeaders admin = getHeader();
        assertTrue(admin.getFirst(HttpHeaders.AUTHORIZATION).startsWith("Bearer "));
        String group = "UAT" + suffix().toUpperCase();
        ReadableProduct product = sampleProduct("uat" + suffix());
        request(HttpMethod.POST, "/api/v1/private/products/group", groupBody(group), admin, 200);
        String item = "/api/v1/private/products/" + product.getId() + "/group/" + group;

        request(HttpMethod.POST, item, null, jsonHeaders(), 401);
        assertEquals(0, groupProducts(group, "DEFAULT").size());
        request(HttpMethod.POST, item, null, admin, 201);
        JsonNode members = groupProducts(group, "DEFAULT");
        assertEquals(1, members.size());
        assertEquals(product.getId().longValue(), members.get(0).path("id").asLong());
        request(HttpMethod.DELETE, item, null, jsonHeaders(), 401);
        assertEquals(1, groupProducts(group, "DEFAULT").size());
        request(HttpMethod.DELETE, item, null, admin, 200);
        assertEquals(0, groupProducts(group, "DEFAULT").size());
    }

    @Test
    public void rejectedForeignMutationsLeaveOwnerGroupAndStorefrontIntact() throws Exception {
        HttpHeaders admin = getHeader();
        String store = "UAT" + suffix().toUpperCase();
        String group = "GRP" + suffix().toUpperCase();
        String storeBody = "{\"code\":\"" + store + "\",\"currency\":\"CAD\","
                + "\"defaultLanguage\":\"en\",\"email\":\"uat@example.test\",\"name\":\"UAT store\","
                + "\"phone\":\"5145550100\",\"supportedLanguages\":[\"en\"],\"address\":{"
                + "\"address\":\"1 Test Street\",\"postalCode\":\"H2B1A0\",\"country\":\"CA\","
                + "\"city\":\"Montreal\",\"stateProvince\":\"QC\"}}";
        request(HttpMethod.POST, "/api/v1/private/store/", storeBody, admin, 200);
        ReadableProduct product = sampleProduct("uat" + suffix());
        request(HttpMethod.POST, "/api/v1/private/products/group", groupBody(group), admin, 200);
        request(HttpMethod.POST, "/api/v1/private/products/group?store=" + store, groupBody(group), admin, 200);
        String item = "/api/v1/private/products/" + product.getId() + "/group/" + group;
        request(HttpMethod.POST, item, null, admin, 201);

        for (HttpMethod method : new HttpMethod[] {HttpMethod.POST, HttpMethod.DELETE}) {
            JsonNode error = request(method, item + "?store=" + store, null, admin, 401);
            assertEquals("401", error.path("errorCode").asText());
            assertEquals(0, groupProducts(group, store).size());
            JsonNode ownerProducts = groupProducts(group, "DEFAULT");
            assertEquals(1, ownerProducts.size());
            assertEquals(product.getId().longValue(), ownerProducts.get(0).path("id").asLong());
        }
        request(HttpMethod.DELETE, item, null, admin, 200);
        assertEquals(0, groupProducts(group, "DEFAULT").size());
    }

    @Test
    public void customerCanBrowseManageCartCheckoutAndReadOwnOrder() throws Exception {
        String email = "uat" + suffix() + "@example.test";
        HttpHeaders anonymous = jsonHeaders();
        String customer = "{\"emailAddress\":\"" + email + "\",\"password\":\"clear123\","
                + "\"gender\":\"M\",\"language\":\"en\",\"storeCode\":\"DEFAULT\","
                + "\"billing\":{\"firstName\":\"Upgrade\",\"lastName\":\"Test\",\"country\":\"BE\","
                + "\"address\":\"1 Test Street\",\"city\":\"Brussels\",\"postalCode\":\"1000\"}}";
        request(HttpMethod.POST, "/api/v1/customer/register", customer, anonymous, 200);
        JsonNode login = request(HttpMethod.POST, "/api/v1/customer/login",
                "{\"username\":\"" + email + "\",\"password\":\"clear123\"}", anonymous, 200);
        assertTrue(login.path("token").isTextual());
        assertTrue(!login.path("token").asText().isEmpty());
        HttpHeaders customerHeaders = jsonHeaders();
        customerHeaders.setBearerAuth(login.path("token").asText());
        JsonNode profile = request(HttpMethod.GET, "/api/v1/auth/customer/profile", null, customerHeaders, 200);
        assertEquals(email, profile.path("emailAddress").asText());

        assertEquals("DEFAULT", request(HttpMethod.GET, "/api/v1/store/DEFAULT", null, anonymous, 200)
                .path("code").asText());
        assertTrue(request(HttpMethod.GET, "/api/v1/category", null, anonymous, 200).path("categories").isArray());
        assertTrue(request(HttpMethod.GET, "/api/v1/products", null, anonymous, 200).path("products").isArray());
        ReadableProduct product = sampleProduct("uat" + suffix());
        JsonNode detail = request(HttpMethod.GET, "/api/v2/product/" + product.getSku(), null, anonymous, 200);
        assertEquals(product.getId().longValue(), detail.path("id").asLong());
        assertEquals(product.getSku(), detail.path("sku").asText());

        String item = "{\"product\":\"" + product.getSku() + "\",\"quantity\":1}";
        JsonNode cart = request(HttpMethod.POST, "/api/v1/cart", item, anonymous, 201);
        String cartUrl = "/api/v1/cart/" + cart.path("code").asText();
        assertTrue(cart.path("code").isTextual());
        assertEquals(1, cart.path("quantity").asInt());
        request(HttpMethod.PUT, cartUrl, item.replace(":1}", ":2}"), anonymous, 201);
        JsonNode read = request(HttpMethod.GET, cartUrl, null, anonymous, 200);
        assertEquals(2, read.path("quantity").asInt());
        assertEquals(product.getSku(), read.path("products").get(0).path("sku").asText());
        JsonNode total = request(HttpMethod.GET, cartUrl + "/total", null, anonymous, 200);
        assertTrue(total.path("total").isTextual());

        MerchantStore store = merchantStoreService.getByCode("DEFAULT");
        IntegrationConfiguration previousPayment = paymentService.getPaymentConfiguration("moneyorder", store);
        try {
            request(HttpMethod.POST, "/api/v1/private/modules/payment",
                    "{\"code\":\"moneyorder\",\"active\":true,\"defaultSelected\":true,"
                    + "\"integrationKeys\":{\"address\":\"1 Test Street\"},\"integrationOptions\":{}}", getHeader(), 200);
            String amount = total.path("total").asText().replaceAll("[^0-9.]", "");
            JsonNode order = request(HttpMethod.POST, "/api/v1/auth/cart/" + cart.path("code").asText() + "/checkout",
                    "{\"currency\":\"CAD\",\"customerAgreement\":true,\"payment\":{\"paymentModule\":\"moneyorder\","
                    + "\"paymentType\":\"MONEYORDER\",\"transactionType\":\"AUTHORIZECAPTURE\",\"amount\":\"" + amount
                    + "\"}}", customerHeaders, 200);
            assertTrue(order.path("id").isIntegralNumber());
            JsonNode readOrder = request(HttpMethod.GET, "/api/v1/auth/orders/" + order.path("id").asLong(),
                    null, customerHeaders, 200);
            assertEquals(order.path("id").asLong(), readOrder.path("id").asLong());
            assertTrue(request(HttpMethod.GET, "/api/v1/auth/orders", null, customerHeaders, 200)
                    .path("orders").isArray());
        } finally {
            if (previousPayment == null) {
                paymentService.removePaymentModuleConfiguration("moneyorder", store);
            } else {
                paymentService.savePaymentModuleConfiguration(previousPayment, store);
            }
        }
    }

    @Test
    public void servicesBasicAuthRejectsBadCredentialsBeforeRouting() throws Exception {
        HttpHeaders valid = jsonHeaders();
        valid.setBasicAuth("admin@shopizer.com", "password");
        HttpHeaders invalid = jsonHeaders();
        invalid.setBasicAuth("admin@shopizer.com", "invalid");
        String unmappedService = "/services/private/upgrade-test-probe";
        request(HttpMethod.GET, unmappedService, null, valid, 404);
        request(HttpMethod.GET, unmappedService, null, invalid, 401);
    }

    @Test
    public void healthAndApiDocumentationAreAvailable() throws Exception {
        assertEquals("UP", request(HttpMethod.GET, "/actuator/health", null, jsonHeaders(), 200)
                .path("status").asText());
        JsonNode docs = request(HttpMethod.GET, "/v2/api-docs", null, jsonHeaders(), 200);
        assertEquals("2.0", docs.path("swagger").asText());
        assertTrue(docs.path("paths").has("/api/v1/private/products/{productId}/group/{code}"));
    }

    private JsonNode groupProducts(String group, String store) throws Exception {
        JsonNode products = request(HttpMethod.GET, "/api/v1/products/group/" + group + "?store=" + store,
                null, jsonHeaders(), 200).path("products");
        assertTrue(products.isArray());
        return products;
    }

    private JsonNode request(HttpMethod method, String path, String body, HttpHeaders headers, int status)
            throws Exception {
        ResponseEntity<String> response = testRestTemplate.exchange(path, method,
                new HttpEntity<>(body, headers), String.class);
        assertEquals(method + " " + path + ": " + response.getBody(), status, response.getStatusCodeValue());
        return response.getBody() == null || response.getBody().isEmpty()
                ? mapper.nullNode() : mapper.readTree(response.getBody());
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String groupBody(String group) {
        return "{\"code\":\"" + group + "\",\"active\":true}";
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
