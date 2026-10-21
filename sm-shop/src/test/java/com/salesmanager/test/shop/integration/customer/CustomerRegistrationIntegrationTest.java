package com.salesmanager.test.shop.integration.customer;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class CustomerRegistrationIntegrationTest extends ServicesTestSupport {

    @Test
    public void registerCustomer() {
        
      
        final PersistableCustomer testCustomer = new PersistableCustomer();
        testCustomer.setEmailAddress("customer1@test.com");
        testCustomer.setPassword("clear123");
        testCustomer.setGender(CustomerGender.M.name());
        testCustomer.setLanguage("en");
        final Address billing = new Address();
        billing.setFirstName("customer1");
        billing.setLastName("ccstomer1");
        billing.setCountry("BE");
        testCustomer.setBilling(billing);
        testCustomer.setStoreCode(Constants.DEFAULT_STORE);
        final HttpEntity<PersistableCustomer> entity = new HttpEntity<>(testCustomer, getHeader());

        final ResponseEntity<PersistableCustomer> response = testRestTemplate.postForEntity("/api/v1/customer/register", entity, PersistableCustomer.class);
        assertThat(response.getStatusCode(), is(OK));

        // created customer can login

        final ResponseEntity<AuthenticationResponse> loginResponse = testRestTemplate.postForEntity("/api/v1/customer/login", new HttpEntity<>(new AuthenticationRequest("customer1@test.com", "clear123")),
                AuthenticationResponse.class);
        assertThat(loginResponse.getStatusCode(), is(OK));
        assertNotNull(loginResponse.getBody().getToken());

    }

    @Test
    public void customerRegisteredWithAdminEmailCannotAccessPrivateProfile() {
        final PersistableCustomer testCustomer = new PersistableCustomer();
        testCustomer.setEmailAddress("admin@shopizer.com");
        testCustomer.setPassword("clear123");
        testCustomer.setGender(CustomerGender.M.name());
        testCustomer.setLanguage("en");
        final Address billing = new Address();
        billing.setFirstName("admin");
        billing.setLastName("shopizer");
        billing.setCountry("BE");
        testCustomer.setBilling(billing);
        testCustomer.setStoreCode(Constants.DEFAULT_STORE);

        final ResponseEntity<AuthenticationResponse> registrationResponse = testRestTemplate.postForEntity(
                "/api/v1/customer/register", new HttpEntity<>(testCustomer, getHeader()), AuthenticationResponse.class);
        String customerToken = registrationResponse.getBody() == null
                ? null : registrationResponse.getBody().getToken();

        if (customerToken == null) {
            final ResponseEntity<AuthenticationResponse> loginResponse = testRestTemplate.postForEntity(
                    "/api/v1/customer/login",
                    new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "clear123")),
                    AuthenticationResponse.class);
            customerToken = loginResponse.getBody() == null ? null : loginResponse.getBody().getToken();
        }

        if (customerToken != null) {
            final HttpHeaders customerHeaders = new HttpHeaders();
            customerHeaders.set("Authorization", "Bearer " + customerToken);
            final ResponseEntity<String> customerProfile = testRestTemplate.exchange(
                    "/api/v1/private/user/profile", HttpMethod.GET, new HttpEntity<>(customerHeaders), String.class);
            assertFalse(customerProfile.getStatusCode().is2xxSuccessful());
            final ResponseEntity<String> customerRefresh = testRestTemplate.exchange(
                    "/api/v1/auth/refresh", HttpMethod.GET, new HttpEntity<>(customerHeaders), String.class);
            assertThat(customerRefresh.getStatusCode(), is(UNAUTHORIZED));
        } else {
            assertFalse(registrationResponse.getStatusCode().is2xxSuccessful());
            final ResponseEntity<String> anonymousProfile = testRestTemplate.exchange(
                    "/api/v1/private/user/profile", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);
            assertFalse(anonymousProfile.getStatusCode().is2xxSuccessful());
        }

        final ResponseEntity<String> adminProfile = testRestTemplate.exchange(
                "/api/v1/private/user/profile", HttpMethod.GET, new HttpEntity<>(getHeader()), String.class);
        assertThat(adminProfile.getStatusCode(), is(OK));
    }

}