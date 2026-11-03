package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
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
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ContentJourneyUatTest extends ServicesTestSupport {

  @Test
  public void adminCreatesReadsListsAndDeletesContent() {
    String suffix = Long.toString(System.nanoTime());
    String pageCode = "uat-page-" + suffix;
    String boxCode = "uat-box-" + suffix;
    String pageName = "UAT page " + suffix;
    String boxName = "UAT box " + suffix;

    ResponseEntity<Entity> pageCreateResponse = testRestTemplate.exchange(
        "/api/v1/private/content/page?store=DEFAULT&lang=en",
        HttpMethod.POST,
        new HttpEntity<>(page(pageCode, pageName), getHeader()),
        Entity.class);
    assertEquals(HttpStatus.CREATED, pageCreateResponse.getStatusCode());
    assertNotNull(pageCreateResponse.getBody());

    ResponseEntity<Entity> boxCreateResponse = testRestTemplate.exchange(
        "/api/v1/private/content/box?store=DEFAULT&lang=en",
        HttpMethod.POST,
        new HttpEntity<>(box(boxCode, boxName), getHeader()),
        Entity.class);
    assertEquals(HttpStatus.CREATED, boxCreateResponse.getStatusCode());
    assertNotNull(boxCreateResponse.getBody());

    ResponseEntity<ReadableContentPage> pageReadResponse = testRestTemplate.getForEntity(
        "/api/v1/content/pages/" + pageCode + "?store=DEFAULT&lang=en",
        ReadableContentPage.class);
    ResponseEntity<ReadableContentBox> boxReadResponse = testRestTemplate.getForEntity(
        "/api/v1/content/boxes/" + boxCode + "?store=DEFAULT&lang=en",
        ReadableContentBox.class);
    assertEquals(HttpStatus.OK, pageReadResponse.getStatusCode());
    assertEquals(pageName, pageReadResponse.getBody().getDescription().getName());
    assertEquals(HttpStatus.OK, boxReadResponse.getStatusCode());
    assertEquals(boxName, boxReadResponse.getBody().getDescription().getName());

    ResponseEntity<String> pageListResponse = testRestTemplate.getForEntity(
        "/api/v1/content/pages?store=DEFAULT&lang=en&page=0&count=100", String.class);
    assertEquals(HttpStatus.OK, pageListResponse.getStatusCode());
    assertTrue(pageListResponse.getBody().contains(pageCode));

    ResponseEntity<Void> deleteResponse = testRestTemplate.exchange(
        "/api/v1/private/content/page/" + pageCreateResponse.getBody().getId() + "?store=DEFAULT",
        HttpMethod.DELETE,
        new HttpEntity<>(getHeader()),
        Void.class);
    assertTrue(deleteResponse.getStatusCode().is2xxSuccessful());

    ResponseEntity<String> deletedPageResponse = testRestTemplate.getForEntity(
        "/api/v1/content/pages/" + pageCode + "?store=DEFAULT&lang=en", String.class);
    assertEquals(HttpStatus.NOT_FOUND, deletedPageResponse.getStatusCode());
  }

  private static PersistableContentPage page(String code, String name) {
    PersistableContentPage page = new PersistableContentPage();
    page.setCode(code);
    page.setDescriptions(Collections.singletonList(description(name)));
    return page;
  }

  private static PersistableContentBox box(String code, String name) {
    PersistableContentBox box = new PersistableContentBox();
    box.setCode(code);
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
}
