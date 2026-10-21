package com.salesmanager.test.shop.uat;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

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
@ExtendWith(SpringExtension.class)
public class CartCheckoutJourneyUatTest extends ServicesTestSupport {

    private static final String EMAIL = "cart.checkout.journey.uat@example.com";
    private static final String PASSWORD = "clear123";

    @Test
    public void cartCheckoutJourney() {
        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress(EMAIL);
        customer.setPassword(PASSWORD);
        customer.setGender("M");
        customer.setLanguage("en");
        customer.setStoreCode("DEFAULT");

        Address billing = new Address();
        billing.setFirstName("Cart");
        billing.setLastName("Journey");
        billing.setCountry("CA");
        customer.setBilling(billing);

        ResponseEntity<String> registration = testRestTemplate.postForEntity(
                "/api/v1/customer/register", new HttpEntity<>(customer, getHeader()), String.class);
        assertThat(registration.getStatusCode(), is(HttpStatus.OK));

        ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
                "/api/v1/customer/login",
                new HttpEntity<>(new AuthenticationRequest(EMAIL, PASSWORD)),
                AuthenticationResponse.class);
        assertThat(login.getStatusCode(), is(HttpStatus.OK));
        assertThat(login.getBody() != null && login.getBody().getToken() != null
                && !login.getBody().getToken().isEmpty(), is(true));

        ReadableProduct product = sampleProduct("CartCheckoutJourneyUatTest");
        PersistableShoppingCartItem cartItem = new PersistableShoppingCartItem();
        cartItem.setProduct(product.getSku());
        cartItem.setQuantity(1);

        ResponseEntity<ReadableShoppingCart> createdCart = testRestTemplate.postForEntity(
                "/api/v1/cart?store=DEFAULT", new HttpEntity<>(cartItem), ReadableShoppingCart.class);
        assertThat(createdCart.getStatusCode(), is(HttpStatus.CREATED));
        assertThat(createdCart.getBody().getQuantity(), is(1));

        String cartCode = createdCart.getBody().getCode();
        cartItem.setQuantity(2);
        ResponseEntity<ReadableShoppingCart> updatedCart = testRestTemplate.exchange(
                "/api/v1/cart/" + cartCode + "?store=DEFAULT", HttpMethod.PUT,
                new HttpEntity<>(cartItem), ReadableShoppingCart.class);
        assertThat(updatedCart.getStatusCode(), is(HttpStatus.CREATED));

        ResponseEntity<ReadableShoppingCart> readCart = testRestTemplate.getForEntity(
                "/api/v1/cart/" + cartCode + "?store=DEFAULT", ReadableShoppingCart.class);
        assertThat(readCart.getStatusCode(), is(HttpStatus.OK));
        assertThat(readCart.getBody().getQuantity(), is(2));
        assertThat(readCart.getBody().getProducts().stream()
                .anyMatch(item -> product.getSku().equals(item.getSku()) && item.getQuantity() == 2), is(true));
    }
}
