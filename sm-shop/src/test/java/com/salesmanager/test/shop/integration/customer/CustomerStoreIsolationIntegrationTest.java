package com.salesmanager.test.shop.integration.customer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;

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
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class CustomerStoreIsolationIntegrationTest extends ServicesTestSupport {

    private static final String OTHER_STORE = "isolation";
    private static final String CUSTOMER_EMAIL = "isolation.customer@test.com";

    @Test
    public void customerOfAnotherStoreCannotBeAccessedById() {

        HttpHeaders headers = getHeader();
        createStore(headers);
        Long customerId = registerDefaultStoreCustomer(headers);

        String otherStoreUrl = "/api/v1/private/customer/" + customerId + "?store=" + OTHER_STORE;

        ResponseEntity<String> get = testRestTemplate.exchange(otherStoreUrl, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(get.getStatusCode(), is(HttpStatus.NOT_FOUND));

        PersistableCustomer update = customer();
        update.setPassword("changed123");
        ResponseEntity<String> put = testRestTemplate.exchange(otherStoreUrl, HttpMethod.PUT,
                new HttpEntity<>(update, headers), String.class);
        assertThat(put.getStatusCode(), is(HttpStatus.NOT_FOUND));

        ResponseEntity<String> patch = testRestTemplate.exchange(
                "/api/v1/private/customer/" + customerId + "/address?store=" + OTHER_STORE, HttpMethod.PATCH,
                new HttpEntity<>(customer(), headers), String.class);
        assertThat(patch.getStatusCode(), is(HttpStatus.NOT_FOUND));

        ResponseEntity<String> delete = testRestTemplate.exchange(otherStoreUrl, HttpMethod.DELETE,
                new HttpEntity<>(headers), String.class);
        assertThat(delete.getStatusCode(), is(HttpStatus.NOT_FOUND));

        ResponseEntity<ReadableCustomer> own = testRestTemplate.exchange(
                "/api/v1/private/customer/" + customerId + "?store=" + Constants.DEFAULT_STORE, HttpMethod.GET,
                new HttpEntity<>(headers), ReadableCustomer.class);
        assertThat(own.getStatusCode(), is(HttpStatus.OK));
        assertThat(own.getBody().getEmailAddress(), is(CUSTOMER_EMAIL));
    }

    private void createStore(HttpHeaders headers) {
        PersistableAddress address = new PersistableAddress();
        address.setAddress("1 isolation street");
        address.setPostalCode("12345");
        address.setCountry("US");
        address.setCity("FT LD");
        address.setStateProvince("FL");

        PersistableMerchantStore store = new PersistableMerchantStore();
        store.setCode(OTHER_STORE);
        store.setCurrency("CAD");
        store.setDefaultLanguage("en");
        store.setEmail("isolation@test.com");
        store.setName(OTHER_STORE);
        store.setPhone("444-555-6666");
        store.setSupportedLanguages(Arrays.asList("en"));
        store.setAddress(address);

        ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/store/", HttpMethod.POST,
                new HttpEntity<>(store, headers), Void.class);
        assertThat(response.getStatusCode(), is(HttpStatus.OK));
    }

    private Long registerDefaultStoreCustomer(HttpHeaders headers) {
        PersistableCustomer customer = customer();
        customer.setPassword("clear123");
        ResponseEntity<PersistableCustomer> response = testRestTemplate.postForEntity("/api/v1/customer/register",
                new HttpEntity<>(customer, headers), PersistableCustomer.class);
        assertThat(response.getStatusCode(), is(HttpStatus.OK));

        Long id = fetchCustomers().getCustomers().stream()
                .filter(c -> CUSTOMER_EMAIL.equals(c.getEmailAddress()))
                .map(ReadableCustomer::getId)
                .findFirst().orElse(null);
        assertNotNull(id);
        return id;
    }

    private PersistableCustomer customer() {
        PersistableCustomer customer = new PersistableCustomer();
        customer.setEmailAddress(CUSTOMER_EMAIL);
        customer.setGender(CustomerGender.M.name());
        customer.setLanguage("en");
        customer.setStoreCode(Constants.DEFAULT_STORE);
        Address billing = new Address();
        billing.setFirstName("isolation");
        billing.setLastName("customer");
        billing.setAddress("1 isolation street");
        billing.setCity("FT LD");
        billing.setPostalCode("12345");
        billing.setCountry("US");
        customer.setBilling(billing);
        return customer;
    }
}
