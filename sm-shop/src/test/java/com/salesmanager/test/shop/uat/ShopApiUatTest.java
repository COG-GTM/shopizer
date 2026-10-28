package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.salesmanager.core.business.constants.Constants;
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
@RunWith(SpringRunner.class)
public class ShopApiUatTest extends ServicesTestSupport {

    @Test
    public void adminAuthorizationProtectsCustomerList() {
        ResponseEntity<String> unauthenticated = testRestTemplate.exchange(
                "/api/v1/private/customers", HttpMethod.GET, HttpEntity.EMPTY, String.class);
        assertAuthenticationFailure(unauthenticated.getStatusCode());

        HttpHeaders invalidHeaders = new HttpHeaders();
        invalidHeaders.set("Authorization", "Bearer garbage");
        ResponseEntity<String> invalid = testRestTemplate.exchange(
                "/api/v1/private/customers", HttpMethod.GET, new HttpEntity<>(invalidHeaders), String.class);
        assertFalse(invalid.getStatusCode().is2xxSuccessful());

        ResponseEntity<String> authorized = testRestTemplate.exchange(
                "/api/v1/private/customers", HttpMethod.GET, new HttpEntity<>(getHeader()), String.class);
        assertEquals(HttpStatus.OK, authorized.getStatusCode());
    }

    @Test
    public void customerCanRegisterLoginAndReadProfile() {
        String email = "uat-" + UUID.randomUUID() + "@example.test";
        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress(email);
        customer.setPassword("clear123");
        customer.setGender("M");
        customer.setLanguage("en");
        customer.setStoreCode(Constants.DEFAULT_STORE);
        Address billing = new Address();
        billing.setFirstName("UAT");
        billing.setLastName("Customer");
        billing.setCountry("BE");
        customer.setBilling(billing);

        ResponseEntity<PersistableCustomer> registration = testRestTemplate.postForEntity(
                "/api/v1/customer/register", new HttpEntity<>(customer, getHeader()), PersistableCustomer.class);
        assertEquals(HttpStatus.OK, registration.getStatusCode());

        AuthenticationRequest credentials = new AuthenticationRequest(email, "clear123");
        ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
                "/api/v1/customer/login", new HttpEntity<>(credentials), AuthenticationResponse.class);
        assertEquals(HttpStatus.OK, login.getStatusCode());
        assertNotNull(login.getBody());
        assertNotNull(login.getBody().getToken());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.getBody().getToken());
        ResponseEntity<String> profile = testRestTemplate.exchange(
                "/api/v1/auth/customer/profile", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertEquals(HttpStatus.OK, profile.getStatusCode());
        assertTrue(profile.getBody(), profile.getBody().contains(email));
    }

    @Test
    public void publicStoreBrowseAndOperationsEndpointsRespond() {
        ResponseEntity<String> store = testRestTemplate.getForEntity("/api/v1/store/DEFAULT", String.class);
        assertEquals(HttpStatus.OK, store.getStatusCode());
        assertTrue(store.getBody(), store.getBody().contains("DEFAULT"));

        ResponseEntity<String> categories = testRestTemplate.getForEntity(
                "/api/v1/category?store=DEFAULT", String.class);
        assertEquals(HttpStatus.OK, categories.getStatusCode());

        ResponseEntity<String> products = testRestTemplate.getForEntity(
                "/api/v1/products?store=DEFAULT", String.class);
        assertEquals(HttpStatus.OK, products.getStatusCode());

        String productCode = "uat" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        ReadableProduct product = sampleProduct(productCode);
        ResponseEntity<String> productDetails = testRestTemplate.getForEntity(
                "/api/v1/products/" + product.getId() + "?store=DEFAULT", String.class);
        assertEquals(HttpStatus.NOT_FOUND, productDetails.getStatusCode());
        ResponseEntity<String> productBySku = testRestTemplate.getForEntity(
                "/api/v2/product/" + product.getSku() + "?store=DEFAULT", String.class);
        assertEquals(HttpStatus.OK, productBySku.getStatusCode());
        assertTrue(productBySku.getBody(), productBySku.getBody().contains(product.getSku()));

        ResponseEntity<String> health = testRestTemplate.getForEntity("/actuator/health", String.class);
        assertEquals(HttpStatus.OK, health.getStatusCode());
        assertTrue(health.getBody(), health.getBody().contains("\"status\":\"UP\""));

        ResponseEntity<String> apiDocs = testRestTemplate.getForEntity("/v2/api-docs", String.class);
        assertEquals(HttpStatus.OK, apiDocs.getStatusCode());
        assertTrue(apiDocs.getBody(), apiDocs.getBody().contains("\"swagger\""));
    }

    @Test
    public void cartCanBeCreatedUpdatedAndRead() {
        ReadableProduct product = sampleProduct("uat-cart-" + UUID.randomUUID());
        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct(product.getSku());
        item.setQuantity(1);

        ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity(
                "/api/v1/cart", new HttpEntity<>(item, getHeader()), ReadableShoppingCart.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertNotNull(created.getBody());
        assertNotNull(created.getBody().getCode());

        item.setQuantity(3);
        ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
                "/api/v1/cart/" + created.getBody().getCode(), HttpMethod.PUT,
                new HttpEntity<>(item, getHeader()), ReadableShoppingCart.class);
        assertEquals(HttpStatus.CREATED, updated.getStatusCode());

        ResponseEntity<ReadableShoppingCart> fetched = testRestTemplate.getForEntity(
                "/api/v1/cart/" + created.getBody().getCode(), ReadableShoppingCart.class);
        assertEquals(HttpStatus.OK, fetched.getStatusCode());
        assertEquals(updated.getBody().getQuantity(), fetched.getBody().getQuantity());
        assertTrue(fetched.getBody().getQuantity() >= 3);
    }

    @Test
    public void legacyPrivateEndpointRequiresValidBasicAuthentication() {
        HttpHeaders validHeaders = new HttpHeaders();
        validHeaders.setBasicAuth("admin@shopizer.com", "password");
        validHeaders.setContentType(MediaType.TEXT_PLAIN);
        HttpEntity<String> validRequest = new HttpEntity<>("{}", validHeaders);
        ResponseEntity<String> valid = testRestTemplate.postForEntity(
                "/services/private/system/optin", validRequest, String.class);
        assertEquals(HttpStatus.OK, valid.getStatusCode());

        HttpHeaders invalidHeaders = new HttpHeaders();
        invalidHeaders.setBasicAuth("admin@shopizer.com", "wrong-password");
        invalidHeaders.setContentType(MediaType.TEXT_PLAIN);
        ResponseEntity<String> invalid = testRestTemplate.postForEntity(
                "/services/private/system/optin", new HttpEntity<>("{}", invalidHeaders), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, invalid.getStatusCode());
    }

    @Test
    public void adminCanUploadAndSafelyRejectTraversalProductImages() throws Exception {
        ReadableProduct product = sampleProduct(
                "uat-image-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));

        ResponseEntity<String> safeUpload = uploadProductImage(product.getId(), "image.jpg");
        assertEquals(HttpStatus.CREATED, safeUpload.getStatusCode());

        ResponseEntity<String> safeImages = testRestTemplate.getForEntity(
                "/api/v1/product/" + product.getId() + "/images?store=DEFAULT", String.class);
        assertEquals(HttpStatus.OK, safeImages.getStatusCode());
        assertNotNull(safeImages.getBody());
        assertTrue(safeImages.getBody().contains("image.jpg"));

        ResponseEntity<String> traversalUpload = uploadProductImage(product.getId(), "../../../../evil.jpg");
        assertFalse(traversalUpload.getStatusCode().is2xxSuccessful());

        ResponseEntity<String> images = testRestTemplate.getForEntity(
                "/api/v1/product/" + product.getId() + "/images?store=DEFAULT", String.class);
        assertEquals(HttpStatus.OK, images.getStatusCode());
        assertNotNull(images.getBody());
        assertFalse(images.getBody().contains("evil"));
    }

    private ResponseEntity<String> uploadProductImage(Long productId, String filename) throws IOException {
        HttpHeaders headers = getHeader();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        byte[] image;
        try (InputStream stream = new ClassPathResource("image.jpg").getInputStream()) {
            image = FileCopyUtils.copyToByteArray(stream);
        }
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(image) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        return testRestTemplate.postForEntity(
                "/api/v1/private/product/" + productId + "/image?store=DEFAULT",
                new HttpEntity<>(body, headers), String.class);
    }

    private static void assertAuthenticationFailure(HttpStatus status) {
        assertTrue(status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN);
    }
}
