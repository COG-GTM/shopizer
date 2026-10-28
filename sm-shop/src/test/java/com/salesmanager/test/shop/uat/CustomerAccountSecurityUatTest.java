package com.salesmanager.test.shop.uat;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpStatus.OK;

import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class CustomerAccountSecurityUatTest extends ServicesTestSupport {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void adminAuthenticationAndProtectedCustomerList() {
        ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
                "/api/v1/private/login",
                new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
                AuthenticationResponse.class);
        assertThat(login.getStatusCode(), is(OK));
        assertNotNull(login.getBody());
        assertNotNull(login.getBody().getToken());

        ResponseEntity<String> customers = testRestTemplate.exchange("/api/v1/private/customers", HttpMethod.GET,
                new HttpEntity<>(bearer(login.getBody().getToken())), String.class);
        assertThat(customers.getStatusCode(), is(OK));

        ResponseEntity<String> noToken = testRestTemplate.exchange("/api/v1/private/customers", HttpMethod.GET,
                HttpEntity.EMPTY, String.class);
        assertTrue(noToken.getStatusCodeValue() == 401 || noToken.getStatusCodeValue() == 403);
    }

    @Test
    public void customerRegistrationLoginAndProfile() {
        Account account = register("profile", "Profile");
        ReadableCustomer profile = profile(account.token);

        assertEquals(account.id, profile.getId());
        assertEquals(account.email, profile.getEmailAddress());
    }

    @Test
    public void customerIdOverwriteCannotChangeVictim() {
        Account victim = register("victim", "Victim");
        PersistableCustomer attackerBody = customer("attacker-" + UUID.randomUUID() + "@example.com", "Attacker");
        attackerBody.setId(victim.id);
        attackerBody.setPassword("clear123");

        ResponseEntity<AuthenticationResponse> attackerRegistration = testRestTemplate.postForEntity(
                "/api/v1/customer/register", new HttpEntity<>(attackerBody), AuthenticationResponse.class);
        assertThat(attackerRegistration.getStatusCode(), is(OK));
        Account attacker = loginAccount(attackerBody.getEmailAddress(), "clear123", "Attacker");
        ReadableCustomer attackerProfile = profile(attacker.token);
        ReadableCustomer victimProfile = profile(loginAccount(victim.email, victim.password, victim.firstName).token);

        assertNotEquals(victim.id, attackerProfile.getId());
        assertEquals(attacker.email, attackerProfile.getEmailAddress());
        assertEquals(victim.id, victimProfile.getId());
        assertEquals(victim.email, victimProfile.getEmailAddress());
        assertEquals(victim.firstName, victimProfile.getBilling().getFirstName());
    }

    @Test
    public void browseCartServicesHealthAndSwagger() throws Exception {
        ResponseEntity<String> store = testRestTemplate.getForEntity("/api/v1/store/DEFAULT", String.class);
        ResponseEntity<String> category = testRestTemplate.getForEntity("/api/v1/category", String.class);
        ResponseEntity<String> products = testRestTemplate.getForEntity("/api/v1/products", String.class);
        assertThat(store.getStatusCode(), is(OK));
        assertThat(category.getStatusCode(), is(OK));
        assertThat(products.getStatusCode(), is(OK));
        assertTrue(objectMapper.readTree(products.getBody()).path("products").isArray());

        String sku = "uat-" + UUID.randomUUID().toString().replace("-", "");
        ReadableProduct sample = sampleProduct(sku);
        ResponseEntity<ReadableProduct> product = testRestTemplate.getForEntity("/api/v2/product/" + sku,
                ReadableProduct.class);
        assertThat(product.getStatusCode(), is(OK));
        assertEquals(sample.getSku(), product.getBody().getSku());

        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct(sku);
        item.setQuantity(1);
        ResponseEntity<ReadableShoppingCart> cart = testRestTemplate.postForEntity("/api/v1/cart",
                new HttpEntity<>(item, getHeader()), ReadableShoppingCart.class);
        assertThat(cart.getStatusCode().value(), is(201));
        assertNotNull(cart.getBody().getCode());

        item.setQuantity(2);
        ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
                "/api/v1/cart/" + cart.getBody().getCode(), HttpMethod.PUT,
                new HttpEntity<>(item, getHeader()), ReadableShoppingCart.class);
        assertThat(updated.getStatusCode().value(), is(201));
        ResponseEntity<ReadableShoppingCart> fetched = testRestTemplate.getForEntity(
                "/api/v1/cart/" + cart.getBody().getCode(), ReadableShoppingCart.class);
        assertThat(fetched.getStatusCode(), is(OK));
        assertEquals(2, fetched.getBody().getQuantity());

        ResponseEntity<JsonNode> health = testRestTemplate.getForEntity("/actuator/health", JsonNode.class);
        assertThat(health.getStatusCode(), is(OK));
        assertEquals("UP", health.getBody().path("status").asText());
        ResponseEntity<JsonNode> docs = testRestTemplate.getForEntity("/v2/api-docs", JsonNode.class);
        assertThat(docs.getStatusCode(), is(OK));
        assertEquals("2.0", docs.getBody().path("swagger").asText());
        assertTrue(docs.getBody().path("paths").has("/api/v1/customer/register"));
    }

    @Test
    public void servicesEndpointsUseBasicAuthentication() {
        ResponseEntity<String> publicStore = testRestTemplate.getForEntity("/services/public/DEFAULT", String.class);
        assertTrue(publicStore.getStatusCode().is2xxSuccessful());

        HttpHeaders valid = new HttpHeaders();
        valid.setBasicAuth("admin@shopizer.com", "password");
        valid.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> validOptin = testRestTemplate.postForEntity("/services/private/system/optin",
                new HttpEntity<>("{}", valid), String.class);
        ResponseEntity<String> badOptin = testRestTemplate.postForEntity("/services/private/system/optin",
                new HttpEntity<>("{}", basic("admin@shopizer.com", "wrong")), String.class);
        ResponseEntity<String> noOptin = testRestTemplate.postForEntity("/services/private/system/optin",
                new HttpEntity<>("{}"), String.class);

        System.out.println("services optin statuses: valid=" + validOptin.getStatusCodeValue()
                + ", bad=" + badOptin.getStatusCodeValue() + ", none=" + noOptin.getStatusCodeValue());
        assertTrue(validOptin.getStatusCodeValue() != 401);
        assertThat(badOptin.getStatusCodeValue(), is(401));
        assertTrue(noOptin.getStatusCodeValue() == 401 || noOptin.getStatusCodeValue() == 302);
    }

    private Account register(String label, String firstName) {
        String email = label + "-" + UUID.randomUUID() + "@example.com";
        return loginAccount(email, "clear123", firstName, registerCustomer(email, firstName, "clear123"));
    }

    private Long registerCustomer(String email, String firstName, String password) {
        PersistableCustomer customer = customer(email, firstName);
        customer.setPassword(password);
        ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity(
                "/api/v1/customer/register", new HttpEntity<>(customer), AuthenticationResponse.class);
        assertThat(response.getStatusCode(), is(OK));
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getId());
        return response.getBody().getId();
    }

    private Account loginAccount(String email, String password, String firstName) {
        return loginAccount(email, password, firstName, null);
    }

    private Account loginAccount(String email, String password, String firstName, Long registeredId) {
        ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity(
                "/api/v1/customer/login", new HttpEntity<>(new AuthenticationRequest(email, password)),
                AuthenticationResponse.class);
        assertThat(response.getStatusCode(), is(OK));
        assertNotNull(response.getBody());
        return new Account(email, password, firstName,
                registeredId == null ? response.getBody().getId() : registeredId, response.getBody().getToken());
    }

    private ReadableCustomer profile(String token) {
        ResponseEntity<ReadableCustomer> response = testRestTemplate.exchange("/api/v1/auth/customer/profile",
                HttpMethod.GET, new HttpEntity<>(bearer(token)), ReadableCustomer.class);
        assertThat(response.getStatusCode(), is(OK));
        return response.getBody();
    }

    private PersistableCustomer customer(String email, String firstName) {
        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress(email);
        customer.setGender(CustomerGender.M.name());
        customer.setLanguage("en");
        customer.setStoreCode(Constants.DEFAULT_STORE);
        Address billing = new Address();
        billing.setFirstName(firstName);
        billing.setLastName("Customer");
        billing.setCountry("BE");
        customer.setBilling(billing);
        return customer;
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders basic(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, password);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static class Account {
        private final String email;
        private final String password;
        private final String firstName;
        private final Long id;
        private final String token;

        private Account(String email, String password, String firstName, Long id, String token) {
            this.email = email;
            this.password = password;
            this.firstName = firstName;
            this.id = id;
            this.token = token;
        }
    }
}
