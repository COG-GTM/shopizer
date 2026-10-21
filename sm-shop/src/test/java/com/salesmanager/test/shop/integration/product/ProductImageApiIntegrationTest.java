package com.salesmanager.test.shop.integration.product;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableImage;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ProductImageApiIntegrationTest extends ServicesTestSupport {

	@Test
	public void customersCannotWriteProductImages() {
		final String email = "product-image-authz-" + UUID.randomUUID() + "@test.com";
		final PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(Constants.DEFAULT_STORE);

		final Address billing = new Address();
		billing.setFirstName("image");
		billing.setLastName("authz");
		billing.setCountry("BE");
		customer.setBilling(billing);

		final ResponseEntity<PersistableCustomer> registrationResponse = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer, getHeader()), PersistableCustomer.class);
		assertThat(registrationResponse.getStatusCode(), is(OK));

		final ResponseEntity<AuthenticationResponse> loginResponse = testRestTemplate.postForEntity(
				"/api/v1/customer/login", new HttpEntity<>(new AuthenticationRequest(email, "clear123")),
				AuthenticationResponse.class);
		assertThat(loginResponse.getStatusCode(), is(OK));
		assertNotNull(loginResponse.getBody().getToken());

		final String productCode = "IMAGE-AUTHZ-" + UUID.randomUUID().toString().replace("-", "");
		final ReadableProduct product = sampleProduct(productCode);
		final Long productId = product.getId();

		final LinkedMultiValueMap<String, Object> adminUpload = new LinkedMultiValueMap<>();
		adminUpload.add("file", new ClassPathResource("image.jpg"));
		final HttpHeaders adminUploadHeaders = getHeader();
		adminUploadHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
		final ResponseEntity<Void> adminUploadResponse = testRestTemplate.exchange(
				"/api/v1/private/product/" + productId + "/image?store=" + Constants.DEFAULT_STORE,
				HttpMethod.POST, new HttpEntity<>(adminUpload, adminUploadHeaders), Void.class);
		assertThat(adminUploadResponse.getStatusCode(), is(CREATED));

		final ResponseEntity<ReadableImage[]> imageResponse = testRestTemplate.getForEntity(
				"/api/v1/product/" + productId + "/images?store=" + Constants.DEFAULT_STORE,
				ReadableImage[].class);
		assertThat(imageResponse.getStatusCode(), is(OK));
		assertThat(imageResponse.getBody().length, is(1));
		final Long imageId = imageResponse.getBody()[0].getId();

		final HttpHeaders customerHeaders = new HttpHeaders();
		customerHeaders.setBearerAuth(loginResponse.getBody().getToken());
		final ResponseEntity<Void> customerDeleteResponse = testRestTemplate.exchange(
				"/api/v1/auth/product/images/" + imageId, HttpMethod.DELETE,
				new HttpEntity<>(customerHeaders), Void.class);
		assertFalse(customerDeleteResponse.getStatusCode().is2xxSuccessful());

		final ResponseEntity<ReadableImage[]> afterCustomerDelete = testRestTemplate.getForEntity(
				"/api/v1/product/" + productId + "/images?store=" + Constants.DEFAULT_STORE,
				ReadableImage[].class);
		assertThat(afterCustomerDelete.getBody().length, is(1));

		final LinkedMultiValueMap<String, Object> customerUpload = new LinkedMultiValueMap<>();
		customerUpload.add("file", new ClassPathResource("image.jpg"));
		final HttpHeaders customerUploadHeaders = new HttpHeaders();
		customerUploadHeaders.setBearerAuth(loginResponse.getBody().getToken());
		customerUploadHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
		final ResponseEntity<Void> customerUploadResponse = testRestTemplate.exchange(
				"/api/v1/auth/product/" + productId + "/image?store=" + Constants.DEFAULT_STORE,
				HttpMethod.POST, new HttpEntity<>(customerUpload, customerUploadHeaders), Void.class);
		assertFalse(customerUploadResponse.getStatusCode().is2xxSuccessful());

		final ResponseEntity<ReadableImage[]> afterCustomerUpload = testRestTemplate.getForEntity(
				"/api/v1/product/" + productId + "/images?store=" + Constants.DEFAULT_STORE,
				ReadableImage[].class);
		assertThat(afterCustomerUpload.getBody().length, is(1));

		final ResponseEntity<Void> adminDeleteResponse = testRestTemplate.exchange(
				"/api/v1/private/product/image/" + imageId + "?store=" + Constants.DEFAULT_STORE,
				HttpMethod.DELETE, new HttpEntity<>(getHeader()), Void.class);
		assertThat(adminDeleteResponse.getStatusCode().is2xxSuccessful(), is(true));

		final ResponseEntity<ReadableImage[]> afterAdminDelete = testRestTemplate.getForEntity(
				"/api/v1/product/" + productId + "/images?store=" + Constants.DEFAULT_STORE,
				ReadableImage[].class);
		assertThat(afterAdminDelete.getBody().length, is(0));
	}
}
