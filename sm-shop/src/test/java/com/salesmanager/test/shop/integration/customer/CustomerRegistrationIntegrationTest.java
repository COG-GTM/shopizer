package com.salesmanager.test.shop.integration.customer;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.springframework.http.HttpStatus.OK;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
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
    public void registerCustomerIgnoresBodyId() {

        final PersistableCustomer victim = buildCustomer("victim@test.com", "victim123");
        final ResponseEntity<PersistableCustomer> victimResponse = testRestTemplate.postForEntity("/api/v1/customer/register",
                new HttpEntity<>(victim, getHeader()), PersistableCustomer.class);
        assertThat(victimResponse.getStatusCode(), is(OK));
        final ResponseEntity<AuthenticationResponse> victimLogin = testRestTemplate.postForEntity("/api/v1/customer/login",
                new HttpEntity<>(new AuthenticationRequest("victim@test.com", "victim123")), AuthenticationResponse.class);
        assertThat(victimLogin.getStatusCode(), is(OK));
        final Long victimId = victimLogin.getBody().getId();
        assertNotNull(victimId);

        final PersistableCustomer attacker = buildCustomer("attacker@test.com", "attacker123");
        attacker.setId(victimId);
        final ResponseEntity<PersistableCustomer> attackerResponse = testRestTemplate.postForEntity("/api/v1/customer/register",
                new HttpEntity<>(attacker, getHeader()), PersistableCustomer.class);
        assertThat(attackerResponse.getStatusCode(), is(OK));

        final ResponseEntity<AuthenticationResponse> attackerLogin = testRestTemplate.postForEntity("/api/v1/customer/login",
                new HttpEntity<>(new AuthenticationRequest("attacker@test.com", "attacker123")), AuthenticationResponse.class);
        assertThat(attackerLogin.getStatusCode(), is(OK));
        assertNotEquals(victimId, attackerLogin.getBody().getId());

        final ResponseEntity<AuthenticationResponse> victimLoginAgain = testRestTemplate.postForEntity("/api/v1/customer/login",
                new HttpEntity<>(new AuthenticationRequest("victim@test.com", "victim123")), AuthenticationResponse.class);
        assertThat(victimLoginAgain.getStatusCode(), is(OK));
        assertThat(victimLoginAgain.getBody().getId(), is(victimId));
    }

    private PersistableCustomer buildCustomer(final String email, final String password) {
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