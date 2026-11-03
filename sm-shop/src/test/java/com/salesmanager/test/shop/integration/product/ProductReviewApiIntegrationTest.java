package com.salesmanager.test.shop.integration.product;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.nio.charset.Charset;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.PersistableProductReview;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ProductReviewApiIntegrationTest extends ServicesTestSupport {

  @Test
  public void customerReviewIsBoundToAuthenticatedCustomerAndPathProduct() {

    ReadableProduct victimProduct = sampleProduct("reviewvictimprod");
    ReadableProduct targetProduct = sampleProduct("reviewtargetprod");

    HttpHeaders victimHeaders = customerHeader("reviewvictim@test.com");
    HttpHeaders attackerHeaders = customerHeader("reviewattacker@test.com");

    ResponseEntity<PersistableProductReview> victimReview =
        postReview(victimProduct.getId(), review(null, null), victimHeaders);
    assertThat(victimReview.getStatusCode(), is(CREATED));
    Long victimId = victimReview.getBody().getCustomerId();
    assertNotNull(victimId);

    ResponseEntity<PersistableProductReview> spoofed =
        postReview(targetProduct.getId(), review(victimId, victimProduct.getId()), attackerHeaders);
    assertThat(spoofed.getStatusCode(), is(CREATED));
    assertNotNull(spoofed.getBody().getCustomerId());
    assertThat(spoofed.getBody().getCustomerId(), not(victimId));
    assertThat(spoofed.getBody().getProductId(), is(targetProduct.getId()));

    ResponseEntity<PersistableProductReview> duplicate =
        postReview(targetProduct.getId(), review(victimId, victimProduct.getId()), attackerHeaders);
    assertThat(duplicate.getStatusCode(), not(CREATED));
  }

  private ResponseEntity<PersistableProductReview> postReview(
      Long productId, PersistableProductReview review, HttpHeaders headers) {
    return testRestTemplate.postForEntity(
        "/api/v1/auth/products/" + productId + "/reviews?store=" + Constants.DEFAULT_STORE,
        new HttpEntity<>(review, headers),
        PersistableProductReview.class);
  }

  private PersistableProductReview review(Long customerId, Long productId) {
    PersistableProductReview review = new PersistableProductReview();
    review.setCustomerId(customerId);
    review.setProductId(productId);
    review.setLanguage("en");
    review.setRating(1D);
    review.setDescription("Review description");
    review.setDate("2021-06-06");
    return review;
  }

  private HttpHeaders customerHeader(String email) {
    PersistableCustomer customer = new PersistableCustomer();
    customer.setEmailAddress(email);
    customer.setPassword("clear123");
    customer.setGender(CustomerGender.M.name());
    customer.setLanguage("en");
    Address billing = new Address();
    billing.setFirstName("review");
    billing.setLastName("customer");
    billing.setCountry("BE");
    customer.setBilling(billing);
    customer.setStoreCode(Constants.DEFAULT_STORE);
    ResponseEntity<PersistableCustomer> registration =
        testRestTemplate.postForEntity(
            "/api/v1/customer/register", new HttpEntity<>(customer, getHeader()), PersistableCustomer.class);
    assertThat(registration.getStatusCode(), is(OK));

    ResponseEntity<AuthenticationResponse> login =
        testRestTemplate.postForEntity(
            "/api/v1/customer/login",
            new HttpEntity<>(new AuthenticationRequest(email, "clear123")),
            AuthenticationResponse.class);
    assertThat(login.getStatusCode(), is(OK));

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(new MediaType("application", "json", Charset.forName("UTF-8")));
    headers.add("Authorization", "Bearer " + login.getBody().getToken());
    return headers;
  }
}
