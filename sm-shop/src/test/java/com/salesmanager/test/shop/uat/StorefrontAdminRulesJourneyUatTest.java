package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/** AB-542 UAT: storefront/admin journeys, including the Drools-backed promo, shipping and total flows. */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
class StorefrontAdminRulesJourneyUatTest extends ServicesTestSupport {

    private static final String PASSWORD = "clear123";

    @Test
    void adminLoginThenPrivateApiAndRejectsMissingOrBadToken() {
        ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
                new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
                AuthenticationResponse.class);
        assertEquals(OK, login.getStatusCode());
        assertNotNull(login.getBody().getToken());

        assertEquals(OK, testRestTemplate.exchange("/api/v1/private/customers", GET,
                new HttpEntity<>(bearer(login.getBody().getToken())), String.class).getStatusCode());
        assertEquals(UNAUTHORIZED, testRestTemplate.exchange("/api/v1/private/customers", GET,
                new HttpEntity<>(new HttpHeaders()), String.class).getStatusCode());
        HttpStatus badToken = testRestTemplate.exchange("/api/v1/private/customers", GET,
                new HttpEntity<>(bearer("not.a.jwt")), String.class).getStatusCode();
        assertNotEquals(OK, badToken);
        assertEquals(UNAUTHORIZED, testRestTemplate.postForEntity("/api/v1/private/login",
                new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "wrong")), String.class)
                .getStatusCode());
    }

    @Test
    void customerRegistersLogsInAndReadsProfile() {
        String email = "uat-ab542-profile-" + System.nanoTime() + "@example.com";
        HttpHeaders customer = registerAndLogin(email);
        ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange("/api/v1/auth/customer/profile?store=DEFAULT",
                GET, new HttpEntity<>(customer), ReadableCustomer.class);
        assertEquals(OK, profile.getStatusCode());
        assertEquals(email, profile.getBody().getEmailAddress());
        assertEquals(UNAUTHORIZED, testRestTemplate.exchange("/api/v1/auth/customer/profile", GET,
                new HttpEntity<>(new HttpHeaders()), String.class).getStatusCode());
    }

    @Test
    void shopperBrowsesStoreCategoriesAndProducts() {
        ReadableProduct product = sampleProduct("uat-ab542-browse-" + System.nanoTime());
        ResponseEntity<JsonNode> store = testRestTemplate.getForEntity("/api/v1/store/DEFAULT", JsonNode.class);
        assertEquals(OK, store.getStatusCode());
        assertEquals("DEFAULT", store.getBody().path("code").asText());
        ResponseEntity<JsonNode> categories = testRestTemplate.getForEntity("/api/v1/category?store=DEFAULT&lang=en",
                JsonNode.class);
        assertEquals(OK, categories.getStatusCode());
        assertTrue(categories.getBody().path("categories").size() > 0);
        ResponseEntity<JsonNode> products = testRestTemplate.getForEntity("/api/v1/products?store=DEFAULT&lang=en",
                JsonNode.class);
        assertEquals(OK, products.getStatusCode());
        assertTrue(products.getBody().path("recordsTotal").asInt() >= 1);
        ResponseEntity<ReadableProduct> detail = testRestTemplate.getForEntity(
                "/api/v2/product/" + product.getSku() + "?store=DEFAULT&lang=en", ReadableProduct.class);
        assertEquals(OK, detail.getStatusCode());
        assertEquals(product.getSku(), detail.getBody().getSku());
    }

    @Test
    void shopperCreatesUpdatesAndReadsCartAndAppliesPromo() {
        ReadableProduct product = sampleProduct("uat-ab542-cart-" + System.nanoTime());
        ReadableShoppingCart created = createCart(product, new HttpHeaders());
        String code = created.getCode();

        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct(product.getSku());
        item.setQuantity(2);
        assertEquals(CREATED, testRestTemplate.exchange("/api/v1/cart/" + code + "?store=DEFAULT", PUT,
                new HttpEntity<>(item), ReadableShoppingCart.class).getStatusCode());

        ResponseEntity<ReadableShoppingCart> promo = testRestTemplate.exchange(
                "/api/v1/cart/" + code + "/promo/Test1234?store=DEFAULT", POST, new HttpEntity<>(new HttpHeaders()),
                ReadableShoppingCart.class);
        assertEquals(CREATED, promo.getStatusCode());

        ResponseEntity<ReadableShoppingCart> read = testRestTemplate.getForEntity("/api/v1/cart/" + code + "?store=DEFAULT",
                ReadableShoppingCart.class);
        assertEquals(OK, read.getStatusCode());
        assertEquals(2, read.getBody().getQuantity());
        assertTrue(read.getBody().getSubtotal().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(0, read.getBody().getSubtotal().compareTo(read.getBody().getTotal()));
    }

    @Test
    void customerChecksOutWithPromoAndOrderReadBack() {
        String email = "uat-ab542-checkout-" + System.nanoTime() + "@example.com";
        HttpHeaders customer = registerAndLogin(email);

        IntegrationModuleConfiguration moneyorder = new IntegrationModuleConfiguration();
        moneyorder.setCode("moneyorder");
        moneyorder.setActive(true);
        moneyorder.setDefaultSelected(true);
        moneyorder.getIntegrationKeys().put("address", "Shopizer test payment address");
        assertEquals(OK, testRestTemplate.postForEntity("/api/v1/private/modules/payment?store=DEFAULT",
                new HttpEntity<>(moneyorder, getHeader()), String.class).getStatusCode());

        ReadableProduct product = sampleProduct("uat-ab542-order-" + System.nanoTime());
        ReadableShoppingCart cart = createCart(product, customer);
        assertEquals(CREATED, testRestTemplate.exchange("/api/v1/cart/" + cart.getCode() + "/promo/Test1234?store=DEFAULT",
                POST, new HttpEntity<>(customer), ReadableShoppingCart.class).getStatusCode());

        ResponseEntity<JsonNode> customerCart = testRestTemplate.exchange(
                "/api/v1/auth/customer/cart?cart=" + cart.getCode() + "&store=DEFAULT", GET, new HttpEntity<>(customer),
                JsonNode.class);
        assertEquals(OK, customerCart.getStatusCode());
        assertEquals(cart.getCode(), customerCart.getBody().path("code").asText());

        PersistablePayment payment = new PersistablePayment();
        payment.setPaymentModule("moneyorder");
        payment.setPaymentType("MONEYORDER");
        payment.setTransactionType("CAPTURE");
        payment.setAmount(cart.getTotal().toPlainString());
        PersistableOrder order = new PersistableOrder();
        order.setId(0L);
        order.setCurrency("USD");
        order.setCustomerAgreement(true);
        order.setPayment(payment);
        ResponseEntity<ReadableOrderConfirmation> confirmation = testRestTemplate.postForEntity(
                "/api/v1/auth/cart/" + cart.getCode() + "/checkout?store=DEFAULT", new HttpEntity<>(order, customer),
                ReadableOrderConfirmation.class);
        assertEquals(OK, confirmation.getStatusCode());
        Long orderId = confirmation.getBody().getId();
        assertTrue(orderId > 0);

        ResponseEntity<ReadableOrder> mine = testRestTemplate.exchange("/api/v1/auth/orders/" + orderId, GET,
                new HttpEntity<>(customer), ReadableOrder.class);
        assertEquals(OK, mine.getStatusCode());
        assertEquals(orderId, mine.getBody().getId());
        assertEquals(OK, testRestTemplate.exchange("/api/v1/private/orders/" + orderId, GET,
                new HttpEntity<>(getHeader()), ReadableOrder.class).getStatusCode());
    }

    @Test
    void servicesBasicAuthHealthAndApiDocs() {
        HttpHeaders valid = new HttpHeaders();
        valid.setBasicAuth("admin@shopizer.com", "password");
        valid.setContentType(MediaType.APPLICATION_JSON);
        assertEquals(OK, testRestTemplate.postForEntity("/services/private/system/optin",
                new HttpEntity<>("{}", valid), String.class).getStatusCode());
        HttpHeaders invalid = new HttpHeaders();
        invalid.setBasicAuth("admin@shopizer.com", "wrong");
        invalid.setContentType(MediaType.APPLICATION_JSON);
        assertEquals(UNAUTHORIZED, testRestTemplate.postForEntity("/services/private/system/optin",
                new HttpEntity<>("{}", invalid), String.class).getStatusCode());

        ResponseEntity<JsonNode> health = testRestTemplate.getForEntity("/actuator/health", JsonNode.class);
        assertEquals(OK, health.getStatusCode());
        assertEquals("UP", health.getBody().path("status").asText());
        ResponseEntity<JsonNode> docs = testRestTemplate.getForEntity("/v2/api-docs", JsonNode.class);
        assertEquals(OK, docs.getStatusCode());
        assertEquals("2.0", docs.getBody().path("swagger").asText());
    }

    private HttpHeaders registerAndLogin(String email) {
        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress(email);
        customer.setPassword(PASSWORD);
        customer.setGender("M");
        customer.setLanguage("en");
        customer.setStoreCode("DEFAULT");
        Address billing = new Address();
        billing.setFirstName("Uat");
        billing.setLastName("Rules");
        billing.setCountry("CA");
        billing.setZone("QC");
        billing.setCity("Boucherville");
        billing.setAddress("358 Du Languedoc");
        billing.setPostalCode("J4B 8J9");
        customer.setBilling(billing);
        assertEquals(OK, testRestTemplate.postForEntity("/api/v1/customer/register",
                new HttpEntity<>(customer, getHeader()), String.class).getStatusCode());
        ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
                new HttpEntity<>(new AuthenticationRequest(email, PASSWORD)), AuthenticationResponse.class);
        assertEquals(OK, login.getStatusCode());
        return bearer(login.getBody().getToken());
    }

    private ReadableShoppingCart createCart(ReadableProduct product, HttpHeaders headers) {
        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct(product.getSku());
        item.setQuantity(1);
        ResponseEntity<ReadableShoppingCart> cart = testRestTemplate.postForEntity("/api/v1/cart?store=DEFAULT",
                new HttpEntity<>(item, headers), ReadableShoppingCart.class);
        assertEquals(CREATED, cart.getStatusCode());
        assertNotNull(cart.getBody().getCode());
        return cart.getBody();
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }
}
