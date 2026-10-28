package com.salesmanager.test.shop.integration.customer;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.springframework.http.HttpStatus.OK;

import java.nio.charset.StandardCharsets;
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
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v1.PersistableAnonymousOrder;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class CustomerIdOverwriteIntegrationTest extends ServicesTestSupport {

    @Test
    public void adminCreateIgnoresBodyId() {
        Account victim = register("victim", "Victim");
        PersistableCustomer attacker = customer("admin-created-" + UUID.randomUUID() + "@example.com",
                "AdminCreated");
        attacker.setId(victim.id);

        ResponseEntity<ReadableCustomer> created = testRestTemplate.postForEntity("/api/v1/private/customer",
                new HttpEntity<>(attacker, getHeader()), ReadableCustomer.class);

        assertThat(created.getStatusCode(), is(OK));
        assertNotNull(created.getBody());
        assertNotEquals(victim.id, created.getBody().getId());

        ReadableCustomer victimAfter = readCustomer(victim.id);
        assertEquals(victim.email, victimAfter.getEmailAddress());
        assertEquals(victim.firstName, victimAfter.getBilling().getFirstName());
        assertEquals(victim.id, login(victim).getId());
    }

    @Test
    public void anonymousCheckoutIgnoresCustomerBodyId() {
        Account victim = register("victim", "Victim");
        String sku = "anonymous-" + UUID.randomUUID().toString().replace("-", "");
        sampleProduct(sku);

        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct(sku);
        item.setQuantity(1);
        ResponseEntity<ReadableShoppingCart> cartResponse = testRestTemplate.postForEntity("/api/v1/cart",
                new HttpEntity<>(item, getHeader()), ReadableShoppingCart.class);
        assertThat(cartResponse.getStatusCode().value(), is(201));
        String cartCode = cartResponse.getBody().getCode();

        PersistableCustomer guest = customer("guest-" + UUID.randomUUID() + "@example.com", "Guest");
        guest.setId(victim.id);
        guest.setPassword(null);
        PersistableAnonymousOrder order = new PersistableAnonymousOrder();
        order.setCustomer(guest);
        PersistablePayment payment = new PersistablePayment();
        payment.setPaymentModule("moneyorder");
        payment.setPaymentType("MONEYORDER");
        payment.setTransactionType("CAPTURE");
        payment.setAmount("10");
        order.setPayment(payment);

        ResponseEntity<String> checkout = testRestTemplate.postForEntity(
                "/api/v1/cart/" + cartCode + "/checkout", new HttpEntity<>(order, getHeader()), String.class);
        System.out.println("anonymous checkout status: " + checkout.getStatusCode().value());

        assertEquals(victim.id, login(victim).getId());
        ReadableCustomer victimAfter = readCustomer(victim.id);
        assertEquals(victim.email, victimAfter.getEmailAddress());
        assertEquals(victim.firstName, victimAfter.getBilling().getFirstName());
    }

    @Test
    public void selfUpdateWithForeignBodyIdOnlyChangesCaller() {
        Account victim = register("victim", "Victim");
        Account attacker = register("attacker", "Attacker");
        PersistableCustomer update = customer(attacker.email, "Changed");
        update.setId(victim.id);
        update.setPassword(null);

        HttpHeaders headers = bearer(attacker.token);
        testRestTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
        ResponseEntity<String> response = testRestTemplate.getRestTemplate().exchange(
                "/api/v1/auth/customer/", HttpMethod.PATCH, new HttpEntity<>(update, headers), String.class);

        assertThat(response.getStatusCode(), is(OK));
        ReadableCustomer victimAfter = readCustomer(victim.id);
        ReadableCustomer attackerAfter = readCustomer(attacker.id);
        assertEquals(victim.email, victimAfter.getEmailAddress());
        assertEquals(victim.firstName, victimAfter.getBilling().getFirstName());
        assertEquals("Changed", attackerAfter.getBilling().getFirstName());
    }

    @Test
    public void adminUpdateUsesPathIdNotBodyId() {
        Account first = register("first", "First");
        Account second = register("second", "Second");
        PersistableCustomer update = customer(first.email, "AdminEdited");
        update.setId(second.id);
        update.setPassword(null);

        ResponseEntity<PersistableCustomer> response = testRestTemplate.exchange(
                "/api/v1/private/customer/" + first.id, HttpMethod.PUT,
                new HttpEntity<>(update, getHeader()), PersistableCustomer.class);

        assertThat(response.getStatusCode(), is(OK));
        ReadableCustomer firstAfter = readCustomer(first.id);
        ReadableCustomer secondAfter = readCustomer(second.id);
        assertEquals(first.email, firstAfter.getEmailAddress());
        assertEquals("AdminEdited", firstAfter.getBilling().getFirstName());
        assertEquals(second.email, secondAfter.getEmailAddress());
        assertEquals(second.firstName, secondAfter.getBilling().getFirstName());
    }

    private Account register(String label, String firstName) {
        String email = label + "-" + UUID.randomUUID() + "@example.com";
        String password = "clear123";
        PersistableCustomer customer = customer(email, firstName);
        customer.setPassword(password);
        ResponseEntity<AuthenticationResponse> registered = testRestTemplate.postForEntity(
                "/api/v1/customer/register", new HttpEntity<>(customer), AuthenticationResponse.class);
        assertThat(registered.getStatusCode(), is(OK));
        AuthenticationResponse login = login(email, password);
        return new Account(email, password, firstName, login.getId(), login.getToken());
    }

    private AuthenticationResponse login(Account account) {
        return login(account.email, account.password);
    }

    private AuthenticationResponse login(String email, String password) {
        ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity(
                "/api/v1/customer/login", new HttpEntity<>(new AuthenticationRequest(email, password)),
                AuthenticationResponse.class);
        assertThat(response.getStatusCode(), is(OK));
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private ReadableCustomer readCustomer(Long id) {
        ResponseEntity<ReadableCustomer> response = testRestTemplate.exchange(
                "/api/v1/private/customer/" + id, HttpMethod.GET, new HttpEntity<>(getHeader()),
                ReadableCustomer.class);
        assertThat(response.getStatusCode(), is(OK));
        return response.getBody();
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
        headers.setBearerAuth(token);
        return headers;
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
