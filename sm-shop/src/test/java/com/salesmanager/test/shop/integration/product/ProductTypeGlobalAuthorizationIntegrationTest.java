package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.services.catalog.product.type.ProductTypeService;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.model.catalog.product.type.ProductType;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.type.PersistableProductType;
import com.salesmanager.shop.model.catalog.product.type.ReadableProductType;
import com.salesmanager.shop.model.catalog.product.type.ReadableProductTypeList;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.security.PersistableGroup;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.user.PersistableUser;
import com.salesmanager.shop.model.user.ReadableUser;
import com.salesmanager.shop.store.api.exception.ErrorEntity;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ProductTypeGlobalAuthorizationIntegrationTest extends ServicesTestSupport {

	private static final String PASSWORD = "Password1";
	private static String storeAdminEmail;

	@Autowired
	private ProductTypeService productTypeService;

	@Autowired
	private MerchantStoreService merchantStoreService;

	private MerchantStore defaultStore;
	private Language language;
	private ProductType globalType;

	@Before
	public void setUp() throws Exception {
		defaultStore = merchantStoreService.getByCode(MerchantStore.DEFAULT_STORE);
		language = defaultStore.getDefaultLanguage();

		if (storeAdminEmail == null) {
			storeAdminEmail = "product-type-admin-" + System.nanoTime() + "@example.test";
			PersistableUser user = new PersistableUser();
			user.setDefaultLanguage("en");
			user.setEmailAddress(storeAdminEmail);
			user.setFirstName("Product");
			user.setLastName("Type Admin");
			user.setUserName(storeAdminEmail);
			user.setPassword(PASSWORD);
			user.setRepeatPassword(PASSWORD);
			PersistableGroup group = new PersistableGroup();
			group.setName("ADMIN");
			user.getGroups().add(group);

			ResponseEntity<ReadableUser> created = testRestTemplate.postForEntity("/api/v1/private/user/",
					new HttpEntity<>(user, getHeader()), ReadableUser.class);
			assertEquals(OK, created.getStatusCode());
			assertNotNull(created.getBody());
		}

		globalType = new ProductType();
		globalType.setCode("global-" + System.nanoTime());
		globalType.setVisible(true);
		globalType.setAllowAddToCart(true);
		globalType.setMerchantStore(null);
		productTypeService.create(globalType);
		globalType = productTypeService.getByCode(globalType.getCode(), defaultStore, language);
		assertNotNull(globalType);
	}

	@Test
	public void storeAdminCannotUpdateGlobalType() throws Exception {
		PersistableProductType update = productType(globalType.getCode(), false, false);
		ResponseEntity<ErrorEntity> response = testRestTemplate.exchange(typeUrl(globalType.getId()), PUT,
				new HttpEntity<>(update, storeAdminHeaders()), ErrorEntity.class);

		assertEquals(UNAUTHORIZED, response.getStatusCode());
		assertNotNull(response.getBody());
		assertTrue(response.getBody().getMessage().contains("shared"));
		ProductType reloaded = productTypeService.getById(globalType.getId(), defaultStore);
		assertNotNull(reloaded);
		assertNull(reloaded.getMerchantStore());
		assertTrue(reloaded.getVisible());
		assertTrue(reloaded.isAllowAddToCart());

		ReadableProductTypeList list = testRestTemplate.exchange(
				"/api/v1/private/product/types?store=DEFAULT&count=100", GET,
				new HttpEntity<>(getHeader()), ReadableProductTypeList.class).getBody();
		assertNotNull(list);
		assertFalse(list.getList().stream().anyMatch(type -> globalType.getCode().equals(type.getCode())));
	}

	@Test
	public void storeAdminCannotDeleteGlobalType() throws Exception {
		ResponseEntity<ErrorEntity> response = testRestTemplate.exchange(typeUrl(globalType.getId()), DELETE,
				new HttpEntity<>(storeAdminHeaders()), ErrorEntity.class);

		assertEquals(UNAUTHORIZED, response.getStatusCode());
		assertNotNull(productTypeService.getById(globalType.getId(), defaultStore));
	}

	@Test
	public void superAdminCanUpdateGlobalTypeWithoutAssigningAnOwner() throws Exception {
		PersistableProductType update = productType(globalType.getCode(), true, false);
		ResponseEntity<Void> response = testRestTemplate.exchange(typeUrl(globalType.getId()), PUT,
				new HttpEntity<>(update, getHeader()), Void.class);

		assertEquals(OK, response.getStatusCode());
		ProductType reloaded = productTypeService.getById(globalType.getId(), defaultStore);
		assertNotNull(reloaded);
		assertNull(reloaded.getMerchantStore());
		assertFalse(reloaded.isAllowAddToCart());
	}

	@Test
	public void superAdminCanDeleteGlobalType() throws Exception {
		ResponseEntity<Void> response = testRestTemplate.exchange(typeUrl(globalType.getId()), DELETE,
				new HttpEntity<>(getHeader()), Void.class);

		assertEquals(OK, response.getStatusCode());
		assertNull(productTypeService.getById(globalType.getId(), defaultStore));
	}

	@Test
	public void storeAdminCanCreateUpdateAndDeleteOwnedType() throws Exception {
		String code = "own-" + System.nanoTime();
		ResponseEntity<Entity> created = testRestTemplate.postForEntity(typeCollectionUrl(MerchantStore.DEFAULT_STORE),
				new HttpEntity<>(productType(code, true, true), storeAdminHeaders()), Entity.class);
		assertEquals(OK, created.getStatusCode());
		assertNotNull(created.getBody());
		Long id = created.getBody().getId();

		ResponseEntity<Void> updated = testRestTemplate.exchange(typeUrl(id), PUT,
				new HttpEntity<>(productType(code, true, false), storeAdminHeaders()), Void.class);
		assertEquals(OK, updated.getStatusCode());
		ProductType reloaded = productTypeService.getById(id, defaultStore);
		assertNotNull(reloaded);
		assertEquals(defaultStore.getId(), reloaded.getMerchantStore().getId());

		ResponseEntity<Void> deleted = testRestTemplate.exchange(typeUrl(id), DELETE,
				new HttpEntity<>(storeAdminHeaders()), Void.class);
		assertEquals(OK, deleted.getStatusCode());
		assertNull(productTypeService.getById(id, defaultStore));
	}

	@Test
	public void storeAdminCannotModifyAnotherStoresType() throws Exception {
		String code = "s" + Long.toString(System.nanoTime(), 36);
		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(code);
		store.setName(code);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail(code + "@example.test");
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		PersistableAddress address = new PersistableAddress();
		address.setAddress("1 Test Street");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("Test City");
		address.setStateProvince("CA");
		store.setAddress(address);

		ResponseEntity<Void> storeCreated = testRestTemplate.postForEntity("/api/v1/private/store/",
				new HttpEntity<>(store, getHeader()), Void.class);
		assertEquals(OK, storeCreated.getStatusCode());

		String typeCode = "other-" + System.nanoTime();
		ResponseEntity<Entity> typeCreated = testRestTemplate.postForEntity(typeCollectionUrl(code),
				new HttpEntity<>(productType(typeCode, true, true), getHeader()), Entity.class);
		assertEquals(OK, typeCreated.getStatusCode());
		Long id = typeCreated.getBody().getId();

		ResponseEntity<ErrorEntity> update = testRestTemplate.exchange(typeUrl(id), PUT,
				new HttpEntity<>(productType(typeCode, false, false), storeAdminHeaders()), ErrorEntity.class);
		assertEquals(NOT_FOUND, update.getStatusCode());
		ResponseEntity<ErrorEntity> delete = testRestTemplate.exchange(typeUrl(id), DELETE,
				new HttpEntity<>(storeAdminHeaders()), ErrorEntity.class);
		assertEquals(NOT_FOUND, delete.getStatusCode());

		MerchantStore otherStore = merchantStoreService.getByCode(code);
		ProductType stillOwned = productTypeService.getById(id, otherStore, otherStore.getDefaultLanguage());
		assertNotNull(stillOwned);
		assertEquals(otherStore.getId(), stillOwned.getMerchantStore().getId());
	}

	@Test
	public void missingTypesReturnNotFoundForUpdateAndDelete() {
		Long missingId = 99999999L;
		ResponseEntity<ErrorEntity> update = testRestTemplate.exchange(typeUrl(missingId), PUT,
				new HttpEntity<>(productType("missing", true, true), getHeader()), ErrorEntity.class);
		assertEquals(NOT_FOUND, update.getStatusCode());

		ResponseEntity<ErrorEntity> delete = testRestTemplate.exchange(typeUrl(missingId), DELETE,
				new HttpEntity<>(getHeader()), ErrorEntity.class);
		assertEquals(NOT_FOUND, delete.getStatusCode());
	}

	@Test
	public void storeAdminCanReadGlobalType() {
		ResponseEntity<ReadableProductType> response = testRestTemplate.exchange(typeUrl(globalType.getId()), GET,
				new HttpEntity<>(storeAdminHeaders()), ReadableProductType.class);

		assertEquals(OK, response.getStatusCode());
		assertEquals(globalType.getCode(), response.getBody().getCode());
	}

	@Test
	public void storeAdminCannotModifySeededGeneralType() throws Exception {
		ProductType general = productTypeService.getByCode(ProductType.GENERAL_TYPE, defaultStore, language);
		assertNotNull(general);
		assertNull(general.getMerchantStore());

		ResponseEntity<ErrorEntity> update = testRestTemplate.exchange(typeUrl(general.getId()), PUT,
				new HttpEntity<>(productType(ProductType.GENERAL_TYPE, false, false), storeAdminHeaders()),
				ErrorEntity.class);
		assertEquals(UNAUTHORIZED, update.getStatusCode());

		ResponseEntity<ErrorEntity> delete = testRestTemplate.exchange(typeUrl(general.getId()), DELETE,
				new HttpEntity<>(storeAdminHeaders()), ErrorEntity.class);
		assertEquals(UNAUTHORIZED, delete.getStatusCode());
		assertNull(productTypeService.getById(general.getId(), defaultStore).getMerchantStore());
	}

	private HttpHeaders storeAdminHeaders() {
		return getHeader(storeAdminEmail, PASSWORD);
	}

	private static String typeUrl(Long id) {
		return "/api/v1/private/product/type/" + id + "?store=DEFAULT";
	}

	private static String typeCollectionUrl(String store) {
		return "/api/v1/private/product/type?store=" + store;
	}

	private static PersistableProductType productType(String code, boolean visible, boolean allowAddToCart) {
		PersistableProductType type = new PersistableProductType();
		type.setCode(code);
		type.setVisible(visible);
		type.setAllowAddToCart(allowAddToCart);
		return type;
	}
}
