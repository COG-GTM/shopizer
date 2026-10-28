package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.UUID;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.PersistableProductReview;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class ProductReviewPrivacyUatTest extends ServicesTestSupport {

    @Test
    public void customerCanPublishReviewAndAnonymousShopperSeesOnlyPublicName() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        ReadableProduct product = sampleProduct("privacy" + suffix);

        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress("review-" + suffix + "@example.test");
        customer.setPassword("clear123");
        customer.setLanguage("en");
        customer.setStoreCode("DEFAULT");
        Address billing = new Address();
        billing.setFirstName("Alice");
        billing.setLastName("SensitiveSurname");
        billing.setCountry("BE");
        billing.setAddress("13 Private Road");
        billing.setPostalCode("PRIVATE123");
        billing.setPhone("555-987-6543");
        customer.setBilling(billing);
        ResponseEntity<JsonNode> registered = testRestTemplate.postForEntity(
                "/api/v1/customer/register", customer, JsonNode.class);
        assertEquals(HttpStatus.OK, registered.getStatusCode());
        assertNotNull(registered.getBody());
        long customerId = registered.getBody().get("id").asLong();
        assertTrue(customerId > 0);

        ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity(
                "/api/v1/customer/login", new AuthenticationRequest(customer.getEmailAddress(), "clear123"),
                AuthenticationResponse.class);
        assertEquals(HttpStatus.OK, login.getStatusCode());
        assertNotNull(login.getBody().getToken());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.getBody().getToken());

        PersistableProductReview review = new PersistableProductReview();
        review.setCustomerId(customerId);
        review.setProductId(product.getId());
        review.setDescription("Useful product");
        review.setRating(4.0);
        review.setLanguage("en");
        String publishUrl = "/api/v1/auth/products/" + product.getId() + "/reviews";
        ResponseEntity<JsonNode> created = testRestTemplate.postForEntity(
                publishUrl, new HttpEntity<>(review, headers), JsonNode.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());

        ResponseEntity<JsonNode> publicReviews = testRestTemplate.getForEntity(
                "/api/v1/product/" + product.getId() + "/reviews?store=DEFAULT&lang=en", JsonNode.class);
        assertEquals(HttpStatus.OK, publicReviews.getStatusCode());
        assertTrue(publicReviews.getBody().isArray());
        assertEquals(1, publicReviews.getBody().size());
        JsonNode readable = publicReviews.getBody().get(0);
        assertEquals(created.getBody().get("id"), readable.get("id"));
        assertEquals(product.getId().longValue(), readable.get("productId").asLong());
        assertEquals("Useful product", readable.get("description").asText());
        assertEquals(4.0, readable.get("rating").asDouble(), 0.0);
        assertEquals("en", readable.get("language").asText());
        assertTrue(readable.get("date").isTextual());
        JsonNode author = readable.get("customer");
        assertEquals(2, author.size());
        assertEquals("Alice", author.get("firstName").asText());
        assertEquals("Alice S.", author.get("displayName").asText());
        for (String privateValue : new String[] {customer.getEmailAddress(), "SensitiveSurname",
                "13 Private Road", "PRIVATE123", "555-987-6543", "billing", "delivery", "customerId", "userName"}) {
            assertFalse("Public response contains " + privateValue,
                    publicReviews.getBody().toString().contains(privateValue));
        }

        ResponseEntity<String> unauthenticated = testRestTemplate.postForEntity(publishUrl, review, String.class);
        assertTrue(unauthenticated.getStatusCode() == HttpStatus.UNAUTHORIZED
                || unauthenticated.getStatusCode() == HttpStatus.FORBIDDEN);
    }

    @Test
    public void anonymousShopperCannotReadAnotherStoresProductReviews() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        ReadableProduct product = sampleProduct("storeprivacy" + suffix);
        String otherStoreCode = "privacy" + suffix;
        PersistableAddress address = new PersistableAddress();
        address.setAddress("1 Test Road");
        address.setPostalCode("12345");
        address.setCountry("US");
        address.setCity("Test City");
        address.setStateProvince("FL");
        PersistableMerchantStore otherStore = new PersistableMerchantStore();
        otherStore.setCode(otherStoreCode);
        otherStore.setName("Review privacy test");
        otherStore.setCurrency("CAD");
        otherStore.setDefaultLanguage("en");
        otherStore.setSupportedLanguages(Arrays.asList("en"));
        otherStore.setEmail("store@example.test");
        otherStore.setPhone("444-555-6666");
        otherStore.setAddress(address);
        ResponseEntity<Void> storeCreated = testRestTemplate.exchange("/api/v1/private/store/",
                HttpMethod.POST, new HttpEntity<>(otherStore, getHeader()), Void.class);
        assertEquals(HttpStatus.OK, storeCreated.getStatusCode());

        ResponseEntity<JsonNode> owned = testRestTemplate.getForEntity(
                "/api/v1/product/" + product.getId() + "/reviews?store=DEFAULT", JsonNode.class);
        assertEquals(HttpStatus.OK, owned.getStatusCode());
        assertTrue(owned.getBody().isArray());
        assertEquals(0, owned.getBody().size());

        ResponseEntity<String> foreign = testRestTemplate.getForEntity(
                "/api/v1/product/" + product.getId() + "/reviews?store=" + otherStoreCode, String.class);
        assertEquals(HttpStatus.NOT_FOUND, foreign.getStatusCode());
    }

}
