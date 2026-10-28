package com.salesmanager.test.shop.integration.customer;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotEquals;
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
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
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
    public void registrationWithAdministratorUserNameIsRejected() {
        final HttpEntity<PersistableCustomer> entity = new HttpEntity<>(customer("admin@shopizer.com", "clear123"), getHeader());

        final ResponseEntity<String> response = testRestTemplate.postForEntity("/api/v1/customer/register", entity, String.class);
        assertNotEquals(OK, response.getStatusCode());
    }

    @Test
    public void customerTokenIsNotAcceptedOnPrivateAdminApi() {
        final String sharedName = "shared-principal@test.com";

        final ResponseEntity<AuthenticationResponse> registered = testRestTemplate.postForEntity("/api/v1/customer/register",
                new HttpEntity<>(customer(sharedName, "clear123"), getHeader()), AuthenticationResponse.class);
        assertThat(registered.getStatusCode(), is(OK));
        final String customerToken = registered.getBody().getToken();
        assertNotNull(customerToken);

        // an administrator with the same login name (admin creation does not consult the customer table)
        final PersistableUser admin = new PersistableUser();
        admin.setDefaultLanguage("en");
        admin.setEmailAddress(sharedName);
        admin.setUserName(sharedName);
        admin.setFirstName("Shared");
        admin.setLastName("Principal");
        admin.setPassword("Password1");
        admin.setRepeatPassword("Password1");
        final PersistableGroup group = new PersistableGroup();
        group.setName("ADMIN");
        admin.getGroups().add(group);
        final ResponseEntity<ReadableUser> created = testRestTemplate.exchange("/api/v1/private/user/", HttpMethod.POST,
                new HttpEntity<>(admin, getHeader()), ReadableUser.class);
        assertThat(created.getStatusCode(), is(OK));

        final HttpHeaders customerHeaders = new HttpHeaders();
        customerHeaders.add("Authorization", "Bearer " + customerToken);
        final ResponseEntity<String> privateCall = testRestTemplate.exchange("/api/v1/private/users/" + created.getBody().getId(),
                HttpMethod.GET, new HttpEntity<>(customerHeaders), String.class);
        assertThat(privateCall.getStatusCode(), is(UNAUTHORIZED));

        final ResponseEntity<String> refresh = testRestTemplate.exchange("/api/v1/auth/refresh",
                HttpMethod.GET, new HttpEntity<>(customerHeaders), String.class);
        assertThat(refresh.getStatusCode(), is(UNAUTHORIZED));
    }

    private PersistableCustomer customer(final String email, final String password) {
        final PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress(email);
        customer.setPassword(password);
        customer.setGender(CustomerGender.M.name());
        customer.setLanguage("en");
        final Address billing = new Address();
        billing.setFirstName("customer");
        billing.setLastName("customer");
        billing.setCountry("BE");
        customer.setBilling(billing);
        customer.setStoreCode(Constants.DEFAULT_STORE);
        return customer;
    }

}
