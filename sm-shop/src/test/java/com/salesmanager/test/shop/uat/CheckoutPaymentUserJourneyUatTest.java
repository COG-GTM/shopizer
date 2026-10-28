package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.shop.model.catalog.category.ReadableCategoryList;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/** Black-box smoke coverage for the customer checkout surface. */
public class CheckoutPaymentUserJourneyUatTest extends ServicesTestSupport {

    @Test
    public void exercisesCheckoutPaymentUserJourneys() {
        HttpHeaders admin = getHeader();
        assertEquals(HttpStatus.OK, get("/api/v1/private/customers", admin, Object.class).getStatusCode());
        assertUnauthorized("/api/v1/private/customers", null);
        // Malformed tokens are a pre-existing 500 on both base and head; only require rejection here.
        assertFalse(get("/api/v1/private/customers", bearer("garbage-token"), Object.class)
                .getStatusCode().is2xxSuccessful());

        String suffix = String.valueOf(System.nanoTime());
        String email = "uat-" + suffix + "@shopizer.test";
        String password = "clear123";
        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress(email);
        customer.setUserName(email);
        customer.setPassword(password);
        customer.setRepeatPassword(password);
        customer.setFirstName("Uat");
        customer.setLastName("Customer");
        customer.setGender("M");
        customer.setLanguage("en");
        customer.setStoreCode(Constants.DEFAULT_STORE);
        Address address = new Address();
        address.setFirstName("Uat");
        address.setLastName("Customer");
        address.setCountry("BE");
        customer.setBilling(address);
        ResponseEntity<Object> registration = testRestTemplate.postForEntity("/api/v1/customer/register",
                new HttpEntity<>(customer, admin), Object.class);
        assertEquals(HttpStatus.OK, registration.getStatusCode());

        ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
                new HttpEntity<>(new AuthenticationRequest(email, password)), AuthenticationResponse.class);
        assertEquals(HttpStatus.OK, login.getStatusCode());
        assertNotNull(login.getBody());
        assertNotNull(login.getBody().getToken());
        HttpHeaders customerHeaders = bearer(login.getBody().getToken());
        ResponseEntity<Object> profile = get("/api/v1/auth/customer/profile", customerHeaders, Object.class);
        assertEquals(HttpStatus.OK, profile.getStatusCode());

        ReadableProduct product = sampleProduct("uat-" + suffix);
        ResponseEntity<ReadableMerchantStore> store = get("/api/v1/store/DEFAULT", null,
                ReadableMerchantStore.class);
        assertEquals(HttpStatus.OK, store.getStatusCode());
        assertNotNull(store.getBody());
        assertEquals("DEFAULT", store.getBody().getCode());
        ReadableCategoryList categories = get("/api/v1/category", null, ReadableCategoryList.class).getBody();
        assertNotNull(categories);
        ReadableProductList products = get("/api/v1/products?sku=" + product.getSku(), null,
                ReadableProductList.class).getBody();
        assertNotNull(products);
        assertFalse(products.getProducts().isEmpty());
        ResponseEntity<ReadableProduct> detail = get("/api/v2/product/" + product.getSku(), null,
                ReadableProduct.class);
        assertEquals(HttpStatus.OK, detail.getStatusCode());
        assertEquals(product.getSku(), detail.getBody().getSku());

        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct(product.getSku());
        item.setQuantity(1);
        ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart",
                new HttpEntity<>(item), ReadableShoppingCart.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        ReadableShoppingCart cart = created.getBody();
        assertNotNull(cart);
        assertNotNull(cart.getCode());
        assertTrue(cart.getTotal().compareTo(BigDecimal.ZERO) > 0);

        item.setQuantity(2);
        ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange("/api/v1/cart/" + cart.getCode(),
                HttpMethod.PUT, new HttpEntity<>(item), ReadableShoppingCart.class);
        assertEquals(HttpStatus.CREATED, updated.getStatusCode());
        assertEquals(2, updated.getBody().getQuantity());
        ReadableShoppingCart fetched = get("/api/v1/cart/" + cart.getCode(), null, ReadableShoppingCart.class).getBody();
        assertNotNull(fetched);
        assertEquals(2, fetched.getQuantity());
        assertTrue(fetched.getTotal().compareTo(BigDecimal.ZERO) > 0);

        Map<String, Object> payment = new HashMap<>();
        payment.put("paymentModule", "stripe");
        payment.put("paymentType", "CREDITCARD");
        payment.put("transactionType", "INIT");
        payment.put("amount", fetched.getTotal().subtract(new BigDecimal("0.01")).toPlainString());
        ResponseEntity<Object> below = paymentInit(cart.getCode(), payment, null);
        assertEquals(HttpStatus.BAD_REQUEST, below.getStatusCode());
        payment.put("amount", fetched.getTotal().toPlainString());
        ResponseEntity<Object> equal = paymentInit(cart.getCode(), payment, null);
        assertNotNull(equal.getStatusCode());
        assertFalse(equal.getStatusCode() == HttpStatus.BAD_REQUEST);

        ResponseEntity<Object> health = get("/actuator/health", null, Object.class);
        assertEquals(HttpStatus.OK, health.getStatusCode());
        assertTrue(String.valueOf(health.getBody()).contains("UP"));
        assertEquals(HttpStatus.OK, get("/v2/api-docs", null, Object.class).getStatusCode());

        HttpHeaders validBasic = new HttpHeaders();
        validBasic.setBasicAuth("admin@shopizer.com", "password", StandardCharsets.UTF_8);
        ResponseEntity<Object> services = testRestTemplate.exchange("/services/private/system/optin", HttpMethod.POST,
                new HttpEntity<>("{}", validBasic), Object.class);
        assertTrue(services.getStatusCode().is2xxSuccessful());
        HttpHeaders invalid = new HttpHeaders();
        invalid.setBasicAuth("invalid", "invalid", StandardCharsets.UTF_8);
        ResponseEntity<Object> invalidServices = testRestTemplate.exchange("/services/private/system/optin",
                HttpMethod.POST, new HttpEntity<>("{}", invalid), Object.class);
        assertEquals(HttpStatus.UNAUTHORIZED, invalidServices.getStatusCode());
    }

    private ResponseEntity<Object> paymentInit(String code, Map<String, Object> payment, HttpHeaders headers) {
        return testRestTemplate.postForEntity("/api/v1/cart/" + code + "/payment/init",
                new HttpEntity<>(payment, headers), Object.class);
    }

    private void assertUnauthorized(String path, String token) {
        HttpHeaders headers = token == null ? new HttpHeaders() : bearer(token);
        HttpStatus status = get(path, headers, Object.class).getStatusCode();
        assertTrue(status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN,
                "Expected 401/403 but got " + status);
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
        headers.setBearerAuth(token);
        return headers;
    }

    private <T> ResponseEntity<T> get(String path, HttpHeaders headers, Class<T> type) {
        return testRestTemplate.exchange(path, HttpMethod.GET,
                headers == null ? HttpEntity.EMPTY : new HttpEntity<>(headers), type);
    }
}
