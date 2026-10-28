package com.salesmanager.test.shop.integration.product;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.manufacturer.PersistableManufacturer;
import com.salesmanager.shop.model.catalog.manufacturer.ReadableManufacturer;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ManufacturerStoreScopeIntegrationTest extends ServicesTestSupport {

  private static final String OTHER_STORE = "mfrscope";

  @Test
  public void deletingAnotherStoresManufacturerReturnsNotFoundAndKeepsIt() throws Exception {
    createStore(OTHER_STORE);

    ResponseEntity<PersistableManufacturer> created = testRestTemplate.postForEntity(
        "/api/v1/private/manufacturer?store=" + OTHER_STORE,
        new HttpEntity<>(manufacturer("otherstorebrand"), getHeader()), PersistableManufacturer.class);
    assertThat(created.getStatusCode(), is(HttpStatus.CREATED));
    Long id = created.getBody().getId();
    assertNotNull(id);

    ResponseEntity<String> crossStoreDelete = testRestTemplate.exchange(
        "/api/v1/private/manufacturer/" + id + "?store=" + MerchantStore.DEFAULT_STORE, HttpMethod.DELETE,
        new HttpEntity<>(getHeader()), String.class);
    assertThat(crossStoreDelete.getStatusCode(), is(HttpStatus.NOT_FOUND));

    ResponseEntity<ReadableManufacturer> stillThere = testRestTemplate.exchange(
        "/api/v1/manufacturer/" + id + "?store=" + OTHER_STORE, HttpMethod.GET,
        new HttpEntity<>(getHeader()), ReadableManufacturer.class);
    assertThat(stillThere.getStatusCode(), is(HttpStatus.OK));
    assertThat(stillThere.getBody().getId(), is(id));

    ResponseEntity<String> ownStoreDelete = testRestTemplate.exchange(
        "/api/v1/private/manufacturer/" + id + "?store=" + OTHER_STORE, HttpMethod.DELETE,
        new HttpEntity<>(getHeader()), String.class);
    assertThat(ownStoreDelete.getStatusCode(), is(HttpStatus.OK));
  }

  private void createStore(String code) {
    PersistableAddress address = new PersistableAddress();
    address.setAddress("1 Scope street");
    address.setPostalCode("12345");
    address.setCountry("US");
    address.setCity("FT LD");
    address.setStateProvince("FL");

    PersistableMerchantStore store = new PersistableMerchantStore();
    store.setCode(code);
    store.setCurrency("CAD");
    store.setDefaultLanguage("en");
    store.setEmail("scope@test.com");
    store.setName(code);
    store.setPhone("444-555-6666");
    store.setSupportedLanguages(Arrays.asList("en"));
    store.setAddress(address);

    ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/store/", HttpMethod.POST,
        new HttpEntity<>(store, getHeader()), Void.class);
    assertThat(response.getStatusCode(), is(HttpStatus.OK));
  }
}
