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
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public class CustomerJourneyUatTest extends ServicesTestSupport {

    private static final String EMAIL = "customer.journey.uat@example.com";
    private static final String PASSWORD = "clear123";

    @Test
    public void customerJourney() {
        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress(EMAIL);
        customer.setPassword(PASSWORD);
        customer.setGender("M");
        customer.setLanguage("en");
        customer.setStoreCode("DEFAULT");

        Address billing = new Address();
        billing.setFirstName("Customer");
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

        HttpHeaders customerHeaders = new HttpHeaders();
        customerHeaders.setBearerAuth(login.getBody().getToken());
        customerHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange(
                "/api/v1/auth/customer/profile", HttpMethod.GET,
                new HttpEntity<>(customerHeaders), ReadableCustomer.class);
        assertThat(profile.getStatusCode(), is(HttpStatus.OK));
        assertThat(profile.getBody().getEmailAddress(), is(EMAIL));

        ResponseEntity<String> profileWithoutToken = testRestTemplate.exchange(
                "/api/v1/auth/customer/profile", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), String.class);
        assertThat(profileWithoutToken.getStatusCode(), is(HttpStatus.UNAUTHORIZED));
    }
}
