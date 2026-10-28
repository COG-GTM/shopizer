package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
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

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ContentAdminJourneyUatTest extends ServicesTestSupport {

	@Test
	public void adminCanCreateReadUpdateAndDeletePageAndBox() {
		String suffix = Long.toString(System.nanoTime());
		String pageCode = "uat-page-" + suffix;
		String boxCode = "uat-box-" + suffix;

		ResponseEntity<Entity> createdPage = testRestTemplate.postForEntity(
				"/api/v1/private/content/page?store=DEFAULT", new HttpEntity<>(page(pageCode, "Page title"), getHeader()),
				Entity.class);
		assertEquals(HttpStatus.CREATED, createdPage.getStatusCode());
		assertNotNull(createdPage.getBody());
		Long pageId = createdPage.getBody().getId();

		ReadableContentPage page = testRestTemplate.getForEntity(
				"/api/v1/content/pages/" + pageCode + "?store=DEFAULT", ReadableContentPage.class).getBody();
		assertEquals(pageCode, page.getCode());

		ResponseEntity<String> pageUpdate = testRestTemplate.exchange(
				"/api/v1/private/content/page/" + pageId + "?store=DEFAULT", HttpMethod.PUT,
				new HttpEntity<>(page(pageCode, "Updated page"), getHeader()), String.class);
		assertEquals(HttpStatus.OK, pageUpdate.getStatusCode());
		ReadableContentPage updatedPage = testRestTemplate.getForEntity(
				"/api/v1/content/pages/" + pageCode + "?store=DEFAULT", ReadableContentPage.class).getBody();
		assertEquals("Updated page", updatedPage.getDescription().getTitle());

		ResponseEntity<Entity> createdBox = testRestTemplate.postForEntity(
				"/api/v1/private/content/box?store=DEFAULT", new HttpEntity<>(box(boxCode, "Box title"), getHeader()),
				Entity.class);
		assertEquals(HttpStatus.CREATED, createdBox.getStatusCode());
		assertNotNull(createdBox.getBody());
		Long boxId = createdBox.getBody().getId();

		ReadableContentBox box = testRestTemplate.getForEntity(
				"/api/v1/content/boxes/" + boxCode + "?store=DEFAULT", ReadableContentBox.class).getBody();
		assertEquals("Box title", box.getDescription().getTitle());

		ResponseEntity<String> boxUpdate = testRestTemplate.exchange(
				"/api/v1/private/content/box/" + boxId + "?store=DEFAULT", HttpMethod.PUT,
				new HttpEntity<>(box(boxCode, "Updated box"), getHeader()), String.class);
		assertEquals(HttpStatus.OK, boxUpdate.getStatusCode());
		ReadableContentBox updatedBox = testRestTemplate.getForEntity(
				"/api/v1/content/boxes/" + boxCode + "?store=DEFAULT", ReadableContentBox.class).getBody();
		assertEquals("Updated box", updatedBox.getDescription().getTitle());

		assertEquals(HttpStatus.OK, testRestTemplate.exchange(
				"/api/v1/private/content/page/" + pageId + "?store=DEFAULT", HttpMethod.DELETE,
				new HttpEntity<>(getHeader()), String.class).getStatusCode());
		assertEquals(HttpStatus.OK, testRestTemplate.exchange(
				"/api/v1/private/content/box/" + boxId + "?store=DEFAULT", HttpMethod.DELETE,
				new HttpEntity<>(getHeader()), String.class).getStatusCode());
	}

	private static PersistableContentPage page(String code, String title) {
		PersistableContentPage page = new PersistableContentPage();
		page.setId(null);
		page.setCode(code);
		page.setVisible(true);
		page.setDescriptions(Collections.singletonList(description(title)));
		return page;
	}

	private static PersistableContentBox box(String code, String title) {
		PersistableContentBox box = new PersistableContentBox();
		box.setId(null);
		box.setCode(code);
		box.setVisible(true);
		box.setDescriptions(Collections.singletonList(description(title)));
		return box;
	}

	private static ContentDescription description(String title) {
		ContentDescription description = new ContentDescription();
		description.setLanguage("en");
		description.setName(title);
		description.setTitle(title);
		description.setDescription("<p>" + title + "</p>");
		description.setFriendlyUrl(title.toLowerCase().replace(' ', '-'));
		return description;
	}
}
