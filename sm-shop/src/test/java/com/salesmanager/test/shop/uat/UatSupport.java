package com.salesmanager.test.shop.uat;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.springframework.http.HttpStatus.OK;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

/** Shared helpers for black-box user-journey tests. */
public abstract class UatSupport extends ServicesTestSupport {

  protected static final String PASSWORD = "clear123";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  protected JsonNode json(String body) {
    try {
      return MAPPER.readTree(body);
    } catch (Exception e) {
      throw new IllegalStateException("Not JSON: " + body, e);
    }
  }

  protected HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
    return headers;
  }

  protected HttpHeaders bearer(String token) {
    HttpHeaders headers = jsonHeaders();
    headers.add("Authorization", "Bearer " + token);
    return headers;
  }

  protected void registerCustomer(String email) {
    PersistableCustomer customer = new PersistableCustomer();
    customer.setEmailAddress(email);
    customer.setPassword(PASSWORD);
    customer.setGender(CustomerGender.M.name());
    customer.setLanguage("en");
    Address billing = new Address();
    billing.setFirstName("uat");
    billing.setLastName("customer");
    billing.setCountry("CA");
    billing.setZone("QC");
    billing.setCity("Montreal");
    billing.setAddress("1 Main street");
    billing.setPostalCode("H2H2H2");
    billing.setPhone("5555555555");
    customer.setBilling(billing);
    customer.setStoreCode(Constants.DEFAULT_STORE);
    ResponseEntity<PersistableCustomer> registration =
        testRestTemplate.postForEntity(
            "/api/v1/customer/register",
            new HttpEntity<>(customer, getHeader()),
            PersistableCustomer.class);
    assertThat(registration.getStatusCode(), is(OK));
  }

  protected String customerLogin(String email) {
    ResponseEntity<AuthenticationResponse> login =
        testRestTemplate.postForEntity(
            "/api/v1/customer/login",
            new HttpEntity<>(new AuthenticationRequest(email, PASSWORD)),
            AuthenticationResponse.class);
    assertThat(login.getStatusCode(), is(OK));
    assertNotNull(login.getBody().getToken());
    return login.getBody().getToken();
  }

  protected String registerAndLogin(String email) {
    registerCustomer(email);
    return customerLogin(email);
  }

  protected JsonNode customerProfile(String token) {
    ResponseEntity<String> profile =
        testRestTemplate.exchange(
            "/api/v1/auth/customer/profile",
            HttpMethod.GET,
            new HttpEntity<>(bearer(token)),
            String.class);
    assertThat(profile.getStatusCode(), is(OK));
    return json(profile.getBody());
  }
}
