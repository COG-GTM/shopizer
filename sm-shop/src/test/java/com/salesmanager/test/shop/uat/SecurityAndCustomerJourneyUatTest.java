package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class SecurityAndCustomerJourneyUatTest extends ServicesTestSupport {

  @Test
  public void adminCanAccessPrivateApiAndInvalidBearerTokensAreRejected() {
    ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
        "/api/v1/private/login",
        new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
        AuthenticationResponse.class);
    assertEquals(HttpStatus.OK, login.getStatusCode());
    assertNotNull(login.getBody().getToken());

    HttpHeaders adminHeaders = new HttpHeaders();
    adminHeaders.setBearerAuth(login.getBody().getToken());
    ResponseEntity<String> customers = testRestTemplate.exchange(
        "/api/v1/private/customers", HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);
    assertEquals(HttpStatus.OK, customers.getStatusCode());

    ResponseEntity<String> noToken = testRestTemplate.exchange(
        "/api/v1/private/customers", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);
    assertEquals(HttpStatus.UNAUTHORIZED, noToken.getStatusCode());

    HttpHeaders garbageHeaders = new HttpHeaders();
    garbageHeaders.setBearerAuth("not-a-valid-token");
    ResponseEntity<String> garbageToken = testRestTemplate.exchange(
        "/api/v1/private/customers", HttpMethod.GET, new HttpEntity<>(garbageHeaders), String.class);
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, garbageToken.getStatusCode());
  }

  @Test
  public void customerCanRegisterLoginReadProfileAndPatchAddress() {
    String email = "uat.profile.customer@test.com";
    String password = "clear123";
    PersistableCustomer registration = new PersistableCustomer();
    registration.setEmailAddress(email);
    registration.setPassword(password);
    registration.setGender(CustomerGender.M.name());
    registration.setLanguage("en");
    registration.setStoreCode(Constants.DEFAULT_STORE);
    Address registrationAddress = new Address();
    registrationAddress.setFirstName("UAT");
    registrationAddress.setLastName("Customer");
    registrationAddress.setCountry("US");
    registration.setBilling(registrationAddress);

    ResponseEntity<PersistableCustomer> registered = testRestTemplate.postForEntity(
        "/api/v1/customer/register",
        new HttpEntity<>(registration, getHeader()),
        PersistableCustomer.class);
    assertEquals(HttpStatus.OK, registered.getStatusCode());

    ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
        "/api/v1/customer/login",
        new HttpEntity<>(new AuthenticationRequest(email, password)),
        AuthenticationResponse.class);
    assertEquals(HttpStatus.OK, login.getStatusCode());
    assertNotNull(login.getBody().getToken());

    HttpHeaders customerHeaders = new HttpHeaders();
    customerHeaders.setBearerAuth(login.getBody().getToken());
    ResponseEntity<ReadableCustomer> profile = testRestTemplate.exchange(
        "/api/v1/auth/customer/profile", HttpMethod.GET,
        new HttpEntity<>(customerHeaders), ReadableCustomer.class);
    assertEquals(HttpStatus.OK, profile.getStatusCode());
    assertEquals(email, profile.getBody().getEmailAddress());

    PersistableCustomer addressUpdate = new PersistableCustomer();
    Address billing = new Address();
    billing.setFirstName("UAT");
    billing.setLastName("Customer");
    billing.setAddress("42 UAT Way");
    billing.setCity("Boston");
    billing.setPostalCode("02108");
    billing.setStateProvince("MA");
    billing.setCountry("US");
    addressUpdate.setBilling(billing);
    ResponseEntity<String> patched = testRestTemplate.exchange(
        "/api/v1/auth/customer/address", HttpMethod.PATCH,
        new HttpEntity<>(addressUpdate, customerHeaders), String.class);
    assertEquals(HttpStatus.OK, patched.getStatusCode());

    ResponseEntity<ReadableCustomer> updatedProfile = testRestTemplate.exchange(
        "/api/v1/auth/customer/profile", HttpMethod.GET,
        new HttpEntity<>(customerHeaders), ReadableCustomer.class);
    assertEquals(HttpStatus.OK, updatedProfile.getStatusCode());
    assertEquals("42 UAT Way", updatedProfile.getBody().getBilling().getAddress());
  }
}
