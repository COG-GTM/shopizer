package com.salesmanager.test.shop.integration.product;

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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.catalog.product.group.ProductGroup;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ProductGroupStoreScopeIntegrationTest extends ServicesTestSupport {

	private static final String SECOND_STORE = "GROUPSTORE";

	@Test
	public void missingProductRemovalReturns404() {
		ResponseEntity<String> response = testRestTemplate.exchange(
				"/api/v1/private/products/999999999/group/FEATURED_ITEM?store=" + Constants.DEFAULT_STORE,
				HttpMethod.DELETE, new HttpEntity<>(getHeader()), String.class);
		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}

	@Test
	public void rejectedForeignRemovalPreservesExistingOwnerMembership() {
		ensureSecondStoreExists();
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		String group = "GRP" + suffix.toUpperCase();
		ReadableProduct product = sampleProduct("grppreserve" + suffix);
		ProductGroup definition = new ProductGroup();
		definition.setCode(group);
		definition.setActive(true);
		for (String store : Arrays.asList(Constants.DEFAULT_STORE, SECOND_STORE)) {
			ResponseEntity<String> created = testRestTemplate.exchange(
					"/api/v1/private/products/group?store=" + store, HttpMethod.POST,
					new HttpEntity<>(definition, getHeader()), String.class);
			assertEquals(HttpStatus.OK, created.getStatusCode());
		}
		String itemUrl = "/api/v1/private/products/" + product.getId() + "/group/" + group;
		ResponseEntity<String> added = testRestTemplate.exchange(itemUrl + "?store=" + Constants.DEFAULT_STORE,
				HttpMethod.POST, new HttpEntity<>(getHeader()), String.class);
		assertEquals(HttpStatus.CREATED, added.getStatusCode());

		ResponseEntity<String> rejected = testRestTemplate.exchange(itemUrl + "?store=" + SECOND_STORE,
				HttpMethod.DELETE, new HttpEntity<>(getHeader()), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, rejected.getStatusCode());
		ResponseEntity<ReadableProductList> ownerGroup = testRestTemplate.getForEntity(
				"/api/v1/products/group/" + group + "?store=" + Constants.DEFAULT_STORE, ReadableProductList.class);
		assertEquals(HttpStatus.OK, ownerGroup.getStatusCode());
		assertNotNull(ownerGroup.getBody());
		assertTrue(ownerGroup.getBody().getProducts().stream().anyMatch(p -> p.getId().equals(product.getId())));

		ResponseEntity<String> removed = testRestTemplate.exchange(itemUrl + "?store=" + Constants.DEFAULT_STORE,
				HttpMethod.DELETE, new HttpEntity<>(getHeader()), String.class);
		assertEquals(HttpStatus.OK, removed.getStatusCode());
		ResponseEntity<ReadableProductList> emptyGroup = testRestTemplate.getForEntity(
				"/api/v1/products/group/" + group + "?store=" + Constants.DEFAULT_STORE, ReadableProductList.class);
		assertEquals(HttpStatus.OK, emptyGroup.getStatusCode());
		assertNotNull(emptyGroup.getBody());
		assertFalse(emptyGroup.getBody().getProducts().stream().anyMatch(p -> p.getId().equals(product.getId())));
	}

	@Test
	public void cannotAddOrRemoveAnotherStoresProductInGroup() {
		ensureSecondStoreExists();

		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		String group = "GRP" + suffix.toUpperCase();
		ReadableProduct defaultStoreProduct = sampleProduct("grpscope" + suffix);

		ProductGroup productGroup = new ProductGroup();
		productGroup.setCode(group);
		productGroup.setActive(true);
		ResponseEntity<String> createGroup = testRestTemplate.exchange(
				"/api/v1/private/products/group?store=" + SECOND_STORE, HttpMethod.POST,
				new HttpEntity<>(productGroup, getHeader()), String.class);
		assertTrue("Group creation failed: " + createGroup.getStatusCode(),
				createGroup.getStatusCode().is2xxSuccessful());

		String itemUrl = "/api/v1/private/products/" + defaultStoreProduct.getId() + "/group/" + group;

		ResponseEntity<String> crossStoreAdd = testRestTemplate.exchange(itemUrl + "?store=" + SECOND_STORE,
				HttpMethod.POST, new HttpEntity<>(getHeader()), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, crossStoreAdd.getStatusCode());

		ResponseEntity<String> crossStoreRemove = testRestTemplate.exchange(itemUrl + "?store=" + SECOND_STORE,
				HttpMethod.DELETE, new HttpEntity<>(getHeader()), String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, crossStoreRemove.getStatusCode());

		ResponseEntity<ReadableProductList> secondStoreGroup = testRestTemplate.getForEntity(
				"/api/v1/products/group/" + group + "?store=" + SECOND_STORE, ReadableProductList.class);
		assertEquals(HttpStatus.OK, secondStoreGroup.getStatusCode());
		assertNotNull(secondStoreGroup.getBody());
		assertFalse(secondStoreGroup.getBody().getProducts().stream()
				.anyMatch(p -> p.getId().equals(defaultStoreProduct.getId())));

		ResponseEntity<String> missingProduct = testRestTemplate.exchange(
				"/api/v1/private/products/999999999/group/" + group + "?store=" + SECOND_STORE,
				HttpMethod.POST, new HttpEntity<>(getHeader()), String.class);
		assertEquals(HttpStatus.NOT_FOUND, missingProduct.getStatusCode());
	}

	@Test
	public void canAddAndRemoveOwnStoreProductInGroup() {
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		String group = "GRP" + suffix.toUpperCase();
		ReadableProduct product = sampleProduct("grpown" + suffix);

		ProductGroup productGroup = new ProductGroup();
		productGroup.setCode(group);
		productGroup.setActive(true);
		ResponseEntity<String> createGroup = testRestTemplate.exchange(
				"/api/v1/private/products/group?store=" + Constants.DEFAULT_STORE, HttpMethod.POST,
				new HttpEntity<>(productGroup, getHeader()), String.class);
		assertTrue(createGroup.getStatusCode().is2xxSuccessful());

		String itemUrl = "/api/v1/private/products/" + product.getId() + "/group/" + group
				+ "?store=" + Constants.DEFAULT_STORE;

		ResponseEntity<ReadableProductList> add = testRestTemplate.exchange(itemUrl, HttpMethod.POST,
				new HttpEntity<>(getHeader()), ReadableProductList.class);
		assertEquals(HttpStatus.CREATED, add.getStatusCode());
		assertTrue(add.getBody().getProducts().stream().anyMatch(p -> p.getId().equals(product.getId())));

		ResponseEntity<ReadableProductList> remove = testRestTemplate.exchange(itemUrl, HttpMethod.DELETE,
				new HttpEntity<>(getHeader()), ReadableProductList.class);
		assertEquals(HttpStatus.OK, remove.getStatusCode());
	}

	private void ensureSecondStoreExists() {
		PersistableAddress address = new PersistableAddress();
		address.setAddress("121212 simple address");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("FT LD");
		address.setStateProvince("FL");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(SECOND_STORE);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail("group-store@test.com");
		store.setName(SECOND_STORE);
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Arrays.asList("en"));
		store.setAddress(address);

		ResponseEntity<Void> createResponse = testRestTemplate.exchange(
				"/api/v1/private/store/", HttpMethod.POST,
				new HttpEntity<>(store, getHeader()), Void.class);
		ResponseEntity<String> existingStore = testRestTemplate.getForEntity(
				"/api/v1/store/" + SECOND_STORE, String.class);
		assertTrue("Second store creation failed: " + createResponse.getStatusCode(),
				createResponse.getStatusCode().is2xxSuccessful() || existingStore.getStatusCode() == HttpStatus.OK);
	}
}
