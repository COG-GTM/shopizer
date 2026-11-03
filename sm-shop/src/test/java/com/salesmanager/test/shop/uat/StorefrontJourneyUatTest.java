package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.shop.application.ShopApplication;
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

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
class StorefrontJourneyUatTest extends ServicesTestSupport {

    @Test
    void adminLoginProtectsPrivateApi() {
        ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/private/login",
                new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
                AuthenticationResponse.class);
        assertEquals(OK, login.getStatusCode());
        assertNotNull(login.getBody());
        assertNotNull(login.getBody().getToken());

        HttpHeaders validHeaders = bearer(login.getBody().getToken());
        ResponseEntity<String> authorized = testRestTemplate.exchange("/api/v1/private/customers",
                HttpMethod.GET, new HttpEntity<>(validHeaders), String.class);
        assertEquals(OK, authorized.getStatusCode());

        ResponseEntity<String> anonymous = testRestTemplate.getForEntity("/api/v1/private/customers", String.class);
        assertTrue(anonymous.getStatusCode().value() == 401 || anonymous.getStatusCode().value() == 403);

        HttpHeaders invalidHeaders = bearer("not-a-valid-token");
        ResponseEntity<String> invalid = testRestTemplate.exchange("/api/v1/private/customers",
                HttpMethod.GET, new HttpEntity<>(invalidHeaders), String.class);
        assertFalse(invalid.getStatusCode().is2xxSuccessful(), "Garbage token was accepted");
    }

    @Test
    void customerRegistrationLoginAndProfileWork() {
        String email = uniqueEmail("profile");
        registerCustomer(email);

        ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
                new HttpEntity<>(new AuthenticationRequest(email, "clear123")), AuthenticationResponse.class);
        assertEquals(OK, login.getStatusCode());
        assertNotNull(login.getBody());
        assertNotNull(login.getBody().getToken());

        ResponseEntity<String> profile = testRestTemplate.exchange("/api/v1/auth/customer/profile",
                HttpMethod.GET, new HttpEntity<>(bearer(login.getBody().getToken())), String.class);
        assertEquals(OK, profile.getStatusCode());
        assertTrue(profile.getBody().contains(email));
    }

    @Test
    void storefrontBrowseAndCartTotalsAreConsistent() {
        ReadableProduct sample = sampleProduct(uniqueSku("browse"));

        ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity(
                "/api/v1/store/DEFAULT", ReadableMerchantStore.class);
        assertEquals(OK, store.getStatusCode());
        assertNotNull(store.getBody());
        assertEquals("DEFAULT", store.getBody().getCode());

        ResponseEntity<ReadableCategoryList> categories = testRestTemplate.getForEntity(
                "/api/v1/category", ReadableCategoryList.class);
        assertEquals(OK, categories.getStatusCode());
        assertNotNull(categories.getBody());
        assertFalse(categories.getBody().getCategories().isEmpty());

        ResponseEntity<ReadableProductList> products = testRestTemplate.getForEntity(
                "/api/v1/products?store=DEFAULT&sku=" + sample.getSku(), ReadableProductList.class);
        assertEquals(OK, products.getStatusCode());
        assertNotNull(products.getBody());
        assertFalse(products.getBody().getProducts().isEmpty());
        assertEquals(sample.getSku(), products.getBody().getProducts().get(0).getSku());

        ResponseEntity<ReadableProduct> detail = testRestTemplate.getForEntity(
                "/api/v2/product/" + sample.getSku(), ReadableProduct.class);
        assertEquals(OK, detail.getStatusCode());
        assertNotNull(detail.getBody());
        assertEquals(sample.getSku(), detail.getBody().getSku());

        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct(sample.getSku());
        item.setQuantity(2);
        ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity("/api/v1/cart",
                new HttpEntity<>(item, getHeader()), ReadableShoppingCart.class);
        assertTrue(created.getStatusCode().is2xxSuccessful());
        assertNotNull(created.getBody());
        String code = created.getBody().getCode();

        item.setQuantity(3);
        ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
                "/api/v1/cart/" + code, HttpMethod.PUT, new HttpEntity<>(item, getHeader()),
                ReadableShoppingCart.class);
        assertTrue(updated.getStatusCode().is2xxSuccessful());

        ReadableShoppingCart cart = testRestTemplate.getForObject("/api/v1/cart/" + code,
                ReadableShoppingCart.class);
        assertNotNull(cart);
        assertEquals(3, cart.getQuantity());
        assertEquals(1, cart.getProducts().size());
        String formattedPrice = cart.getProducts().get(0).getFinalPrice();
        BigDecimal unitPrice = new BigDecimal(formattedPrice.replaceAll("[^0-9.-]", ""));
        BigDecimal expectedSubtotal = unitPrice.multiply(BigDecimal.valueOf(3));
        assertEquals(0, expectedSubtotal.compareTo(cart.getSubtotal()));
        assertEquals(0, expectedSubtotal.compareTo(cart.getProducts().get(0).getSubTotal()));
    }

    @Test
    void servicesBasicAuthActuatorAndSwaggerEndpointsRespond() throws Exception {
        HttpHeaders validBasic = new HttpHeaders();
        validBasic.setBasicAuth("admin@shopizer.com", "password");
        validBasic.setContentType(MediaType.TEXT_PLAIN);
        ResponseEntity<String> valid = testRestTemplate.exchange("/services/private/system/optin",
                HttpMethod.POST, new HttpEntity<>("{}", validBasic), String.class);
        assertEquals(OK, valid.getStatusCode());

        HttpHeaders invalidBasic = new HttpHeaders();
        invalidBasic.setBasicAuth("admin@shopizer.com", "wrong-password");
        invalidBasic.setContentType(MediaType.TEXT_PLAIN);
        ResponseEntity<String> invalid = testRestTemplate.exchange("/services/private/system/optin",
                HttpMethod.POST, new HttpEntity<>("{}", invalidBasic), String.class);
        assertEquals(UNAUTHORIZED, invalid.getStatusCode());

        ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
        assertEquals(OK, health.getStatusCode());
        assertTrue(health.getBody().contains("\"status\":\"UP\""));

        ResponseEntity<String> swagger = testRestTemplate.getForEntity("/v2/api-docs", String.class);
        assertEquals(OK, swagger.getStatusCode());
        assertTrue(swagger.getBody().contains("\"swagger\""));
    }

    private void registerCustomer(String email) {
        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress(email);
        customer.setPassword("clear123");
        customer.setGender("M");
        customer.setLanguage("en");
        customer.setStoreCode(Constants.DEFAULT_STORE);
        Address billing = new Address();
        billing.setFirstName("UAT");
        billing.setLastName("Customer");
        billing.setCountry("BE");
        customer.setBilling(billing);
        ResponseEntity<String> response = testRestTemplate.postForEntity("/api/v1/customer/register",
                new HttpEntity<>(customer, getHeader()), String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static String uniqueSku(String prefix) {
        return "uat-" + prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String uniqueEmail(String prefix) {
        return uniqueSku(prefix) + "@test.com";
    }
}
