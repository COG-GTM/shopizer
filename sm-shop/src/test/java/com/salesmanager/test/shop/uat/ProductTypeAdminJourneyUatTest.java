package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.type.PersistableProductType;
import com.salesmanager.shop.model.catalog.product.type.ReadableProductType;
import com.salesmanager.shop.model.catalog.product.type.ReadableProductTypeList;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.store.api.exception.ErrorEntity;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ProductTypeAdminJourneyUatTest extends ServicesTestSupport {

	@Test
	public void storeAdminCannotChangeGlobalTypesAndCanManageOwnedTypes() {
		HttpHeaders superAdminHeaders = login("admin@shopizer.com", "password");
		String email = "product-type-uat-" + System.nanoTime() + "@example.test";
		String password = "Password1";

		PersistableUser user = new PersistableUser();
		user.setDefaultLanguage("en");
		user.setEmailAddress(email);
		user.setFirstName("Product");
		user.setLastName("Type UAT");
		user.setUserName(email);
		user.setPassword(password);
		user.setRepeatPassword(password);
		PersistableGroup group = new PersistableGroup();
		group.setName("ADMIN");
		user.getGroups().add(group);

		ResponseEntity<ReadableUser> createdUser = testRestTemplate.postForEntity("/api/v1/private/user/",
				new HttpEntity<>(user, superAdminHeaders), ReadableUser.class);
		assertEquals(OK, createdUser.getStatusCode());
		HttpHeaders adminHeaders = login(email, password);

		Long generalId = findGeneralTypeId(adminHeaders);
		assertNotNull(generalId);
		PersistableProductType attemptedUpdate = productType("GENERAL", false, false);
		ResponseEntity<ErrorEntity> update = testRestTemplate.exchange(typeUrl(generalId), PUT,
				new HttpEntity<>(attemptedUpdate, adminHeaders), ErrorEntity.class);
		assertEquals(UNAUTHORIZED, update.getStatusCode());
		ResponseEntity<ErrorEntity> delete = testRestTemplate.exchange(typeUrl(generalId), DELETE,
				new HttpEntity<>(adminHeaders), ErrorEntity.class);
		assertEquals(UNAUTHORIZED, delete.getStatusCode());

		ResponseEntity<ReadableProductType> general = testRestTemplate.exchange(typeUrl(generalId), GET,
				new HttpEntity<>(adminHeaders), ReadableProductType.class);
		assertEquals(OK, general.getStatusCode());
		assertEquals("GENERAL", general.getBody().getCode());

		ReadableProductTypeList initialList = testRestTemplate.exchange(
				"/api/v1/private/product/types?store=DEFAULT&count=100", GET,
				new HttpEntity<>(adminHeaders), ReadableProductTypeList.class).getBody();
		assertNotNull(initialList);
		assertTrue(initialList.getList().stream().noneMatch(type -> "GENERAL".equals(type.getCode())));

		String code = "uat-type-" + System.nanoTime();
		ResponseEntity<Entity> created = testRestTemplate.postForEntity(typeCollectionUrl(),
				new HttpEntity<>(productType(code, true, true), adminHeaders), Entity.class);
		assertEquals(OK, created.getStatusCode());
		Long id = created.getBody().getId();

		ResponseEntity<ReadableProductType> ownedType = testRestTemplate.exchange(typeUrl(id), GET,
				new HttpEntity<>(adminHeaders), ReadableProductType.class);
		assertEquals(OK, ownedType.getStatusCode());
		assertEquals(code, ownedType.getBody().getCode());

		ResponseEntity<Void> updated = testRestTemplate.exchange(typeUrl(id), PUT,
				new HttpEntity<>(productType(code, true, false), adminHeaders), Void.class);
		assertEquals(OK, updated.getStatusCode());

		ReadableProductTypeList updatedList = testRestTemplate.exchange(
				"/api/v1/private/product/types?store=DEFAULT&count=100", GET,
				new HttpEntity<>(adminHeaders), ReadableProductTypeList.class).getBody();
		assertTrue(updatedList.getList().stream().anyMatch(type -> code.equals(type.getCode())));

		ResponseEntity<Void> deleted = testRestTemplate.exchange(typeUrl(id), DELETE,
				new HttpEntity<>(adminHeaders), Void.class);
		assertEquals(OK, deleted.getStatusCode());
	}

	private Long findGeneralTypeId(HttpHeaders headers) {
		for (long id = 1; id <= 50; id++) {
			ResponseEntity<ReadableProductType> response = testRestTemplate.exchange(typeUrl(id), GET,
					new HttpEntity<>(headers), ReadableProductType.class);
			if (response.getStatusCode() == OK && response.getBody() != null
					&& "GENERAL".equals(response.getBody().getCode())) {
				return id;
			}
		}
		return null;
	}

	private HttpHeaders login(String username, String password) {
		ResponseEntity<AuthenticationResponse> response = testRestTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest(username, password)), AuthenticationResponse.class);
		assertEquals(OK, response.getStatusCode());
		assertNotNull(response.getBody());
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
		headers.setBearerAuth(response.getBody().getToken());
		return headers;
	}

	private static String typeUrl(Long id) {
		return "/api/v1/private/product/type/" + id + "?store=DEFAULT";
	}

	private static String typeCollectionUrl() {
		return "/api/v1/private/product/type?store=DEFAULT";
	}

	private static PersistableProductType productType(String code, boolean visible, boolean allowAddToCart) {
		PersistableProductType type = new PersistableProductType();
		type.setCode(code);
		type.setVisible(visible);
		type.setAllowAddToCart(allowAddToCart);
		return type;
	}
}
