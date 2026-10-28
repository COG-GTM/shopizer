package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.*;

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

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.manufacturer.PersistableManufacturer;
import com.salesmanager.shop.model.catalog.manufacturer.ReadableManufacturerList;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ManufacturerDeleteScopeIntegrationTest extends ServicesTestSupport {
  @Test
  public void unknownDeleteReturns404() {
    assertEquals(HttpStatus.NOT_FOUND, delete(Long.MAX_VALUE, "DEFAULT", getHeader()).getStatusCode());
  }

  @Test
  public void crossStoreDeleteKeepsManufacturerInOwnerList() {
    String owner = createStore();
    Long id = createManufacturer(owner);
    assertEquals(HttpStatus.NOT_FOUND, delete(id, "DEFAULT", getHeader()).getStatusCode());
    assertListed(owner, id, true);
  }

  @Test
  public void ownDeleteRemovesManufacturerAndRepeatedDeleteReturns404() {
    String owner = createStore();
    Long id = createManufacturer(owner);
    assertListed(owner, id, true);
    assertEquals(HttpStatus.OK, delete(id, owner, getHeader()).getStatusCode());
    assertEquals(HttpStatus.NOT_FOUND, delete(id, owner, getHeader()).getStatusCode());
    assertListed(owner, id, false);
  }

  @Test
  public void missingAuthorizationDoesNotDeleteManufacturer() {
    String owner = createStore();
    Long id = createManufacturer(owner);
    int status = delete(id, owner, new HttpHeaders()).getStatusCodeValue();
    System.out.println("Manufacturer delete without Authorization status=" + status);
    assertTrue("Unauthorized DELETE status=" + status, status == 401 || status == 403);
    assertListed(owner, id, true);
  }

  @Test
  public void crossStoreGetDoesNotExposeManufacturer() {
    String owner = createStore();
    Long id = createManufacturer(owner);
    ResponseEntity<String> response = testRestTemplate.exchange(
        "/api/v1/manufacturer/" + id + "?store=DEFAULT", HttpMethod.GET,
        new HttpEntity<>(getHeader()), String.class);
    System.out.println("Manufacturer cross-store GET status=" + response.getStatusCodeValue());
    assertNotEquals(HttpStatus.OK, response.getStatusCode());
  }

  private ResponseEntity<String> delete(Long id, String store, HttpHeaders headers) {
    return testRestTemplate.exchange("/api/v1/private/manufacturer/" + id + "?store=" + store,
        HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
  }

  private Long createManufacturer(String store) {
    ResponseEntity<PersistableManufacturer> response = testRestTemplate.postForEntity(
        "/api/v1/private/manufacturer?store=" + store,
        new HttpEntity<>(manufacturer("brand" + UUID.randomUUID().toString().replace("-", "")), getHeader()),
        PersistableManufacturer.class);
    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertNotNull(response.getBody());
    assertNotNull(response.getBody().getId());
    return response.getBody().getId();
  }

  private void assertListed(String store, Long id, boolean expected) {
    ResponseEntity<ReadableManufacturerList> response = testRestTemplate.exchange(
        "/api/v1/private/manufacturers?store=" + store + "&count=100", HttpMethod.GET,
        new HttpEntity<>(getHeader()), ReadableManufacturerList.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(expected, response.getBody().getManufacturers().stream().anyMatch(m -> id.equals(m.getId())));
  }

  private String createStore() {
    String code = "delscope" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    PersistableAddress address = new PersistableAddress();
    address.setAddress("1 Delete street");
    address.setPostalCode("12345");
    address.setCountry("US");
    address.setCity("FT LD");
    address.setStateProvince("FL");
    PersistableMerchantStore store = new PersistableMerchantStore();
    store.setCode(code);
    store.setCurrency("CAD");
    store.setDefaultLanguage("en");
    store.setEmail("delete@test.com");
    store.setName(code);
    store.setPhone("444-555-6666");
    store.setSupportedLanguages(Arrays.asList("en"));
    store.setAddress(address);
    ResponseEntity<Void> response = testRestTemplate.exchange("/api/v1/private/store/", HttpMethod.POST,
        new HttpEntity<>(store, getHeader()), Void.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    return code;
  }
}
