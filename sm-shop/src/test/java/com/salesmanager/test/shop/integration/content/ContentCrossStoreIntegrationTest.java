package com.salesmanager.test.shop.integration.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.content.box.PersistableContentBox;
import com.salesmanager.shop.model.content.box.ReadableContentBox;
import com.salesmanager.shop.model.content.common.ContentDescription;
import com.salesmanager.shop.model.content.page.PersistableContentPage;
import com.salesmanager.shop.model.content.page.ReadableContentPage;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ContentCrossStoreIntegrationTest extends ServicesTestSupport {

  private static final String PASSWORD = "Password1";

  @Autowired private org.springframework.boot.test.web.client.TestRestTemplate restTemplate;

  private String secondStoreCode;
  private String secondStoreAdminEmail;
  private HttpHeaders secondStoreAdminHeaders;

  @Before
  public void createSecondStoreAndAdmin() {
    String suffix = Long.toString(System.nanoTime());
    secondStoreCode = "cross" + suffix;
    secondStoreAdminEmail = "cross-store-" + suffix + "@shopizer.com";

    PersistableMerchantStore store = new PersistableMerchantStore();
    store.setAddress(address());
    store.setCode(secondStoreCode);
    store.setCurrency("USD");
    store.setDefaultLanguage("en");
    store.setEmail(secondStoreAdminEmail);
    store.setName("Cross-store test " + suffix);
    store.setPhone("555-0100");
    store.setSupportedLanguages(Collections.singletonList("en"));
    ResponseEntity<Entity> storeResponse = restTemplate.exchange(
        "/api/v1/private/store/",
        HttpMethod.POST,
        new HttpEntity<>(store, getHeader()),
        Entity.class);
    assertEquals(HttpStatus.OK, storeResponse.getStatusCode());

    PersistableUser user = new PersistableUser();
    user.setEmailAddress(secondStoreAdminEmail);
    user.setUserName(secondStoreAdminEmail);
    user.setPassword(PASSWORD);
    user.setRepeatPassword(PASSWORD);
    user.setStore(secondStoreCode);
    user.setActive(true);
    user.setGroups(Collections.singletonList(new PersistableGroup("ADMIN")));
    ResponseEntity<Entity> userResponse = restTemplate.exchange(
        "/api/v1/private/user/?store=" + secondStoreCode,
        HttpMethod.POST,
        new HttpEntity<>(user, getHeader()),
        Entity.class);
    assertEquals(HttpStatus.OK, userResponse.getStatusCode());

    ResponseEntity<AuthenticationResponse> loginResponse = restTemplate.postForEntity(
        "/api/v1/private/login",
        new HttpEntity<>(new AuthenticationRequest(secondStoreAdminEmail, PASSWORD)),
        AuthenticationResponse.class);
    assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
    assertNotNull(loginResponse.getBody());
    assertNotNull(loginResponse.getBody().getToken());
    secondStoreAdminHeaders = getHeader(secondStoreAdminEmail, PASSWORD);
    assertNotNull(secondStoreAdminHeaders.getFirst(HttpHeaders.AUTHORIZATION));
  }

  @Test
  public void pageCreateCannotOverwriteAnotherStoresPage() {
    String suffix = Long.toString(System.nanoTime());
    String victimCode = "victim-page-" + suffix;
    String secondStorePageCode = "s2-page-" + suffix;
    String victimName = "Default victim " + suffix;
    Long victimId = createPage(victimCode, victimName, null, "DEFAULT", getHeader());
    assertNotNull(victimId);

    Entity created = createPageResponse(
        secondStorePageCode, "Second store page " + suffix, victimId, secondStoreCode, secondStoreAdminHeaders);
    assertNotNull(created.getId());
    assertNotEquals(victimId, created.getId());

    ReadableContentPage victim = readPage(victimCode, "DEFAULT");
    assertEquals(HttpStatus.OK, readPageResponse(victimCode, "DEFAULT").getStatusCode());
    assertEquals(victimId, victim.getId());
    assertEquals(victimCode, victim.getCode());
    assertEquals(victimName, victim.getDescription().getName());

    ReadableContentPage secondStorePage = readPage(secondStorePageCode, secondStoreCode);
    assertEquals("Second store page " + suffix, secondStorePage.getDescription().getName());
  }

  @Test
  public void boxCreateCannotOverwriteAnotherStoresBox() {
    String suffix = Long.toString(System.nanoTime());
    String victimCode = "victim-box-" + suffix;
    String secondStoreBoxCode = "s2-box-" + suffix;
    String victimName = "Default victim box " + suffix;
    Long victimId = createBox(victimCode, victimName, null, "DEFAULT", getHeader());
    assertNotNull(victimId);

    Entity created = createBoxResponse(
        secondStoreBoxCode, "Second store box " + suffix, victimId, secondStoreCode, secondStoreAdminHeaders);
    assertNotNull(created.getId());
    assertNotEquals(victimId, created.getId());

    ReadableContentBox victim = readBox(victimCode, "DEFAULT");
    assertEquals(victimName, victim.getDescription().getName());

    ReadableContentBox secondStoreBox = readBox(secondStoreBoxCode, secondStoreCode);
    assertEquals("Second store box " + suffix, secondStoreBox.getDescription().getName());
  }

  @Test
  public void createWithoutRequestIdReturnsReadableGeneratedId() {
    String suffix = Long.toString(System.nanoTime());
    String pageCode = "generated-page-" + suffix;
    String boxCode = "generated-box-" + suffix;

    Long pageId = createPage(pageCode, "Generated page " + suffix, null, "DEFAULT", getHeader());
    assertNotNull(pageId);
    assertTrue(pageId > 0);
    assertEquals(pageId, readPage(pageCode, "DEFAULT").getId());

    Long boxId = createBox(boxCode, "Generated box " + suffix, null, "DEFAULT", getHeader());
    assertNotNull(boxId);
    assertTrue(boxId > 0);
    Long readableBoxId = readBox(boxCode, "DEFAULT").getId();
    if (readableBoxId != null && readableBoxId > 0) {
      assertEquals(boxId, readableBoxId);
    }
  }

  @Test
  public void owningStoreCanUpdateItsPage() {
    String suffix = Long.toString(System.nanoTime());
    String code = "owner-page-" + suffix;
    Long id = createPage(code, "Original name " + suffix, null, "DEFAULT", getHeader());

    ResponseEntity<Void> updateResponse = restTemplate.exchange(
        "/api/v1/private/content/page/" + id + "?store=DEFAULT&lang=en",
        HttpMethod.PUT,
        new HttpEntity<>(page(code, "Updated name " + suffix, null), getHeader()),
        Void.class);

    assertEquals(HttpStatus.OK, updateResponse.getStatusCode());
    assertEquals("Updated name " + suffix, readPage(code, "DEFAULT").getDescription().getName());
  }

  @Test
  public void secondStoreAdminCannotUpdateAnotherStoresPageOrBox() {
    String suffix = Long.toString(System.nanoTime());
    String pageCode = "foreign-page-" + suffix;
    String boxCode = "foreign-box-" + suffix;
    String pageName = "Foreign page " + suffix;
    String boxName = "Foreign box " + suffix;
    Long pageId = createPage(pageCode, pageName, null, "DEFAULT", getHeader());
    Long boxId = createBox(boxCode, boxName, null, "DEFAULT", getHeader());

    ResponseEntity<String> pageUpdateResponse = restTemplate.exchange(
        "/api/v1/private/content/page/" + pageId + "?store=" + secondStoreCode + "&lang=en",
        HttpMethod.PUT,
        new HttpEntity<>(page(pageCode, "Changed page " + suffix, null), secondStoreAdminHeaders),
        String.class);
    ResponseEntity<String> boxUpdateResponse = restTemplate.exchange(
        "/api/v1/private/content/box/" + boxId + "?store=" + secondStoreCode + "&lang=en",
        HttpMethod.PUT,
        new HttpEntity<>(box(boxCode, "Changed box " + suffix, null), secondStoreAdminHeaders),
        String.class);

    assertTrue(!pageUpdateResponse.getStatusCode().is2xxSuccessful());
    assertTrue(!boxUpdateResponse.getStatusCode().is2xxSuccessful());
    assertEquals(pageName, readPage(pageCode, "DEFAULT").getDescription().getName());
    assertEquals(boxName, readBox(boxCode, "DEFAULT").getDescription().getName());
  }

  private Long createPage(String code, String name, Long id, String store, HttpHeaders headers) {
    return createPageResponse(code, name, id, store, headers).getId();
  }

  private Entity createPageResponse(String code, String name, Long id, String store, HttpHeaders headers) {
    PersistableContentPage page = page(code, name, id);
    ResponseEntity<Entity> response = restTemplate.exchange(
        "/api/v1/private/content/page?store=" + store + "&lang=en",
        HttpMethod.POST,
        new HttpEntity<>(page, headers),
        Entity.class);
    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertNotNull(response.getBody());
    return response.getBody();
  }

  private Long createBox(String code, String name, Long id, String store, HttpHeaders headers) {
    return createBoxResponse(code, name, id, store, headers).getId();
  }

  private Entity createBoxResponse(String code, String name, Long id, String store, HttpHeaders headers) {
    PersistableContentBox box = box(code, name, id);
    ResponseEntity<Entity> response = restTemplate.exchange(
        "/api/v1/private/content/box?store=" + store + "&lang=en",
        HttpMethod.POST,
        new HttpEntity<>(box, headers),
        Entity.class);
    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertNotNull(response.getBody());
    return response.getBody();
  }

  private ResponseEntity<ReadableContentPage> readPageResponse(String code, String store) {
    return restTemplate.getForEntity(
        "/api/v1/content/pages/" + code + "?store=" + store + "&lang=en",
        ReadableContentPage.class);
  }

  private ReadableContentPage readPage(String code, String store) {
    ResponseEntity<ReadableContentPage> response = readPageResponse(code, store);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    return response.getBody();
  }

  private ReadableContentBox readBox(String code, String store) {
    ResponseEntity<ReadableContentBox> response = restTemplate.getForEntity(
        "/api/v1/content/boxes/" + code + "?store=" + store + "&lang=en",
        ReadableContentBox.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    return response.getBody();
  }

  private static PersistableContentPage page(String code, String name, Long id) {
    PersistableContentPage page = new PersistableContentPage();
    page.setCode(code);
    page.setId(id);
    page.setDescriptions(Collections.singletonList(description(name)));
    return page;
  }

  private static PersistableContentBox box(String code, String name, Long id) {
    PersistableContentBox box = new PersistableContentBox();
    box.setCode(code);
    box.setId(id);
    box.setDescriptions(Collections.singletonList(description(name)));
    return box;
  }

  private static ContentDescription description(String name) {
    ContentDescription description = new ContentDescription();
    description.setLanguage("en");
    description.setName(name);
    description.setFriendlyUrl(name.toLowerCase().replace(' ', '-'));
    description.setDescription(name + " description");
    return description;
  }

  private static PersistableAddress address() {
    PersistableAddress address = new PersistableAddress();
    address.setAddress("1212 test avenue");
    address.setCity("Testville");
    address.setCountry("US");
    address.setPostalCode("10001");
    address.setStateProvince("NY");
    return address;
  }
}
