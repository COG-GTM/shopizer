package com.salesmanager.test.shop.integration.security;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.springframework.http.HttpStatus.OK;

import java.nio.charset.Charset;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/**
 * Customer tokens must not be accepted on admin endpoints and vice versa,
 * even when a customer is registered with the same username as an admin user.
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class JWTPrincipalTypeIntegrationTest extends ServicesTestSupport {

    private static final String ADMIN_USERNAME = "admin@shopizer.com";

    @Test
    public void tokensAreNotInterchangeableBetweenAdminAndCustomer() {

        final PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress(ADMIN_USERNAME);
        customer.setPassword("clear123");
        customer.setGender(CustomerGender.M.name());
        customer.setLanguage("en");
        final Address billing = new Address();
        billing.setFirstName("attacker");
        billing.setLastName("attacker");
        billing.setCountry("CA");
        customer.setBilling(billing);
        customer.setStoreCode(Constants.DEFAULT_STORE);

        final HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(new MediaType("application", "json", Charset.forName("UTF-8")));

        final ResponseEntity<AuthenticationResponse> registerResponse = testRestTemplate.postForEntity(
                "/api/v1/customer/register", new HttpEntity<>(customer, jsonHeaders), AuthenticationResponse.class);
        assertThat(registerResponse.getStatusCode(), is(OK));
        final String customerToken = registerResponse.getBody().getToken();
        assertNotNull(customerToken);

        final HttpHeaders customerHeaders = new HttpHeaders();
        customerHeaders.add("Authorization", "Bearer " + customerToken);
        final HttpHeaders adminHeaders = getHeader();

        // customer token is valid on customer endpoints
        assertThat(testRestTemplate.exchange("/api/v1/auth/customer/profile", HttpMethod.GET,
                new HttpEntity<>(customerHeaders), String.class).getStatusCode(), is(OK));
        // admin token is valid on admin endpoints
        assertThat(testRestTemplate.exchange("/api/v1/private/users/1", HttpMethod.GET,
                new HttpEntity<>(adminHeaders), String.class).getStatusCode(), is(OK));

        // customer token must be rejected on admin endpoints
        assertThat(testRestTemplate.exchange("/api/v1/private/users/1", HttpMethod.GET,
                new HttpEntity<>(customerHeaders), String.class).getStatusCode(), not(OK));
        // admin token must be rejected on customer endpoints
        assertThat(testRestTemplate.exchange("/api/v1/auth/customer/profile", HttpMethod.GET,
                new HttpEntity<>(adminHeaders), String.class).getStatusCode(), not(OK));
    }

}
