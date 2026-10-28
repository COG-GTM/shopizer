package com.salesmanager.test.shop.uat;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.model.system.IntegrationModuleConfiguration;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StorefrontJourneyUatTest extends ServicesTestSupport {
    private static final String ADMIN = "admin@shopizer.com";
    private final String email = "uat-" + UUID.randomUUID().toString().replace("-", "") + "@example.com";
    private String customerToken;
    private String cartCode;
    private String sku;
    private String currency;

    @Test
    @Order(1)
    void adminAuthenticationJourney() {
        AuthenticationResponse login = adminLogin();
        ResponseEntity<String> accepted = profile(login.getToken());
        ResponseEntity<String> noAuthorization =
                testRestTemplate.getForEntity("/api/v1/private/user/profile", String.class);
        ResponseEntity<String> forged = profile(token(ADMIN, "aSecret"));
        System.out.println("UAT admin profile statuses: signed=" + accepted.getStatusCode()
                + ", no Authorization=" + noAuthorization.getStatusCode()
                + ", forged aSecret=" + forged.getStatusCode());

        assertAll(
                () -> assertEquals(HttpStatus.OK, accepted.getStatusCode()),
                () -> assertEquals(HttpStatus.UNAUTHORIZED, noAuthorization.getStatusCode()),
                () -> assertFalse(forged.getStatusCode().is2xxSuccessful()));
    }

    @Test
    @Order(2)
    void customerAuthenticationJourney() {
        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress(email);
        customer.setPassword("clear123");
        customer.setGender(CustomerGender.M.name());
        customer.setLanguage("en");
        customer.setStoreCode(Constants.DEFAULT_STORE);
        Address billing = new Address();
        billing.setFirstName("UAT");
        billing.setLastName("Customer");
        billing.setCountry("BE");
        customer.setBilling(billing);

        ResponseEntity<AuthenticationResponse> registration = testRestTemplate.postForEntity(
                "/api/v1/customer/register", new HttpEntity<>(customer, getHeader()),
                AuthenticationResponse.class);
        assertTrue(registration.getStatusCode().is2xxSuccessful());

        ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
                "/api/v1/customer/login",
                new HttpEntity<>(new AuthenticationRequest(email, "clear123")),
                AuthenticationResponse.class);
        assertEquals(HttpStatus.OK, login.getStatusCode());
        assertNotNull(login.getBody());
        customerToken = login.getBody().getToken();

        ResponseEntity<ReadableCustomer> profile = customerProfile(customerToken);
        ResponseEntity<String> forged = customerProfile(token(email, "aSecret"), String.class);
        System.out.println("UAT customer profile statuses: signed=" + profile.getStatusCode()
                + ", forged aSecret=" + forged.getStatusCode());
        assertAll(
                () -> assertEquals(HttpStatus.OK, profile.getStatusCode()),
                () -> assertEquals(email, profile.getBody().getEmailAddress()),
                () -> assertFalse(forged.getStatusCode().is2xxSuccessful()));
    }

    @Test
    @Order(3)
    void browseProductsAndManageCart() {
        ResponseEntity<ReadableMerchantStore> store = testRestTemplate.getForEntity(
                "/api/v1/store/DEFAULT", ReadableMerchantStore.class);
        assertEquals(HttpStatus.OK, store.getStatusCode());
        currency = store.getBody().getCurrency();
        assertEquals(HttpStatus.OK, testRestTemplate.getForEntity(
                "/api/v1/category?store=DEFAULT", String.class).getStatusCode());
        assertEquals(HttpStatus.OK, testRestTemplate.getForEntity(
                "/api/v1/products?store=DEFAULT", String.class).getStatusCode());

        ReadableProduct product = sampleProduct("uat" + UUID.randomUUID().toString().replace("-", ""));
        sku = product.getSku();
        assertEquals(HttpStatus.OK, testRestTemplate.getForEntity(
                "/api/v2/product/" + sku + "?store=DEFAULT", String.class).getStatusCode());

        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct(sku);
        item.setQuantity(1);
        ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity(
                "/api/v1/cart", new HttpEntity<>(item), ReadableShoppingCart.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertEquals(1, created.getBody().getQuantity());
        cartCode = created.getBody().getCode();

        item.setQuantity(2);
        ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
                "/api/v1/cart/" + cartCode, HttpMethod.PUT, new HttpEntity<>(item),
                ReadableShoppingCart.class);
        assertEquals(HttpStatus.CREATED, updated.getStatusCode());
        assertEquals(2, updated.getBody().getQuantity());

        ResponseEntity<ReadableShoppingCart> read = testRestTemplate.getForEntity(
                "/api/v1/cart/" + cartCode, ReadableShoppingCart.class);
        assertEquals(HttpStatus.OK, read.getStatusCode());
        assertEquals(2, read.getBody().getQuantity());
    }

    @Test
    @Order(4)
    void authenticatedCustomerCanCheckoutAndReadOrder() {
        assertNotNull(customerToken);
        assertNotNull(cartCode);
        IntegrationModuleConfiguration moneyOrder = new IntegrationModuleConfiguration();
        moneyOrder.setCode("moneyorder");
        moneyOrder.setActive(true);
        moneyOrder.setDefaultSelected(true);
        moneyOrder.getIntegrationKeys().put("address", "Test Money Order Address");
        ResponseEntity<Void> paymentConfiguration = testRestTemplate.postForEntity(
                "/api/v1/private/modules/payment?store=DEFAULT",
                new HttpEntity<>(moneyOrder, getHeader()), Void.class);
        assertEquals(HttpStatus.OK, paymentConfiguration.getStatusCode());

        PersistablePayment payment = new PersistablePayment();
        payment.setPaymentModule("moneyorder");
        payment.setPaymentType("MONEYORDER");
        payment.setTransactionType("AUTHORIZECAPTURE");
        payment.setAmount("250.00");

        PersistableOrder order = new PersistableOrder();
        order.setCurrency(currency);
        order.setCustomerAgreement(true);
        order.setPayment(payment);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(customerToken);

        ResponseEntity<ReadableOrderConfirmation> checkout = testRestTemplate.postForEntity(
                "/api/v1/auth/cart/" + cartCode + "/checkout",
                new HttpEntity<>(order, headers), ReadableOrderConfirmation.class);
        System.out.println("UAT checkout status: " + checkout.getStatusCode());
        assertEquals(HttpStatus.OK, checkout.getStatusCode());
        assertNotNull(checkout.getBody());
        assertNotNull(checkout.getBody().getId());

        ResponseEntity<String> orderDetails = testRestTemplate.exchange(
                "/api/v1/auth/orders/" + checkout.getBody().getId(),
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertEquals(HttpStatus.OK, orderDetails.getStatusCode());
    }

    @Test
    @Order(5)
    void servicesBasicAuthHealthAndApiDocsWork() {
        HttpHeaders validCredentials = new HttpHeaders();
        validCredentials.setBasicAuth(ADMIN, "password");
        validCredentials.setContentType(MediaType.TEXT_PLAIN);
        ResponseEntity<String> validResponse = testRestTemplate.exchange(
                "/services/private/system/optin", HttpMethod.POST,
                new HttpEntity<>("uat", validCredentials), String.class);

        HttpHeaders invalidCredentials = new HttpHeaders();
        invalidCredentials.setBasicAuth(ADMIN, "incorrect");
        invalidCredentials.setContentType(MediaType.TEXT_PLAIN);
        ResponseEntity<String> invalidResponse = testRestTemplate.exchange(
                "/services/private/system/optin", HttpMethod.POST,
                new HttpEntity<>("uat", invalidCredentials), String.class);
        System.out.println("UAT services statuses: valid=" + validResponse.getStatusCode()
                + ", invalid=" + invalidResponse.getStatusCode());
        assertAll(
                () -> assertFalse(HttpStatus.UNAUTHORIZED.equals(validResponse.getStatusCode())),
                () -> assertEquals(HttpStatus.UNAUTHORIZED, invalidResponse.getStatusCode()),
                () -> assertEquals(HttpStatus.OK, testRestTemplate.getForEntity(
                        "/actuator/health", String.class).getStatusCode()),
                () -> assertTrue(testRestTemplate.getForEntity(
                        "/actuator/health", String.class).getBody().contains("\"status\":\"UP\"")),
                () -> assertEquals(HttpStatus.OK, testRestTemplate.getForEntity(
                        "/v2/api-docs", String.class).getStatusCode()));
    }

    private AuthenticationResponse adminLogin() {
        ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
                "/api/v1/private/login",
                new HttpEntity<>(new AuthenticationRequest(ADMIN, "password")),
                AuthenticationResponse.class);
        assertEquals(HttpStatus.OK, login.getStatusCode());
        return login.getBody();
    }

    private ResponseEntity<String> profile(String jwt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        return testRestTemplate.exchange(
                "/api/v1/private/user/profile", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<ReadableCustomer> customerProfile(String jwt) {
        return customerProfile(jwt, ReadableCustomer.class);
    }

    private <T> ResponseEntity<T> customerProfile(String jwt, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        return testRestTemplate.exchange(
                "/api/v1/auth/customer/profile", HttpMethod.GET, new HttpEntity<>(headers), responseType);
    }

    private String token(String subject, String signingSecret) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(subject)
                .setAudience("api")
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + 3_600_000))
                .signWith(SignatureAlgorithm.HS512, signingSecret)
                .compact();
    }
}
