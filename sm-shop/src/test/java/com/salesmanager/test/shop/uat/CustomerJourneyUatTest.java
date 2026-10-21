package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class CustomerJourneyUatTest extends ServicesTestSupport {

    @Test
    public void customerCanRegisterLoginViewProfileAndManageCart() {
        String email = uniqueEmail();
        AuthenticationResponse login = registerAndLogin(email);
        HttpHeaders headers = bearer(login.getToken());
        ResponseEntity<String> profile = testRestTemplate.exchange(
                "/api/v1/auth/customer/profile", GET, new HttpEntity<>(headers), String.class);
        assertEquals(OK, profile.getStatusCode());
        assertTrue(profile.getBody().contains(email));

        ReadableProduct product = sampleProduct("uat-cart-" + System.nanoTime());
        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct(product.getSku());
        item.setQuantity(1);
        ResponseEntity<ReadableShoppingCart> added = testRestTemplate.postForEntity(
                "/api/v1/cart", new HttpEntity<>(item, headers), ReadableShoppingCart.class);
        assertEquals(CREATED, added.getStatusCode());
        assertNotNull(added.getBody());
        assertEquals(1, added.getBody().getQuantity());

        item.setQuantity(2);
        ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
                "/api/v1/cart/" + added.getBody().getCode(), PUT,
                new HttpEntity<>(item, headers), ReadableShoppingCart.class);
        assertEquals(CREATED, updated.getStatusCode());

        ResponseEntity<ReadableShoppingCart> fetched = testRestTemplate.exchange(
                "/api/v1/cart/" + added.getBody().getCode(), GET,
                new HttpEntity<>(headers), ReadableShoppingCart.class);
        assertEquals(OK, fetched.getStatusCode());
        assertEquals(2, fetched.getBody().getQuantity());
    }

    private AuthenticationResponse registerAndLogin(String email) {
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
                "/api/v1/customer/register", new HttpEntity<>(customer), AuthenticationResponse.class);
        assertTrue(registration.getStatusCode().is2xxSuccessful());

        ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
                "/api/v1/customer/login",
                new HttpEntity<>(new AuthenticationRequest(email, "clear123")),
                AuthenticationResponse.class);
        assertEquals(OK, login.getStatusCode());
        assertNotNull(login.getBody());
        assertNotNull(login.getBody().getToken());
        return login.getBody();
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private String uniqueEmail() {
        return "uat-" + System.nanoTime() + "@test.com";
    }
}
