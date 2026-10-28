package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.test.shop.common.ProductGroupTestSupport;

@RunWith(SpringRunner.class)
public class ProductGroupTenantIntegrationTest extends ProductGroupTestSupport {

	@Test
	public void catalogueAdminCannotDeleteAnotherStoresGroup() {
		String otherStore = createStore();
		HttpHeaders otherAdmin = createAdmin(otherStore, Constants.GROUP_ADMIN_CATALOGUE);
		String code = createGroup("DEFAULT", getHeader());

		ResponseEntity<String> response = testRestTemplate.exchange(
				"/api/v1/private/products/group/" + code + "?store=DEFAULT", HttpMethod.DELETE,
				new HttpEntity<>(otherAdmin), String.class);

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
		assertGroupExists("DEFAULT", code, true);
	}

	@Test
	public void existingForeignProductCannotBeAddedOrRemovedEvenBySuperadmin() {
		String otherStore = createStore();
		ReadableProduct product = sampleProduct(uniqueCode());
		String code = createGroup(otherStore, getHeader());
		String path = "/api/v1/private/products/" + product.getId() + "/group/" + code + "?store=" + otherStore;

		for (HttpMethod method : new HttpMethod[] { HttpMethod.POST, HttpMethod.DELETE }) {
			ResponseEntity<String> response = testRestTemplate.exchange(path, method,
					new HttpEntity<>(getHeader()), String.class);
			assertEquals(method.toString(), HttpStatus.NOT_FOUND, response.getStatusCode());
		}
		ResponseEntity<ReadableProductList> publicRead = testRestTemplate.getForEntity(
				"/api/v1/products/group/" + code + "?store=" + otherStore, ReadableProductList.class);
		assertEquals(HttpStatus.OK, publicRead.getStatusCode());
		assertNotNull(publicRead.getBody());
		assertEquals(0, publicRead.getBody().getProducts().size());
		assertGroupExists(otherStore, code, true);
	}

	@Test
	public void orderAdminCannotAttachOrRemoveAProduct() {
		ReadableProduct product = sampleProduct(uniqueCode());
		String code = createGroup("DEFAULT", getHeader());
		HttpHeaders orderAdmin = createAdmin("DEFAULT", Constants.GROUP_ADMIN_ORDER);
		String path = "/api/v1/private/products/" + product.getId() + "/group/" + code;
		for (HttpMethod method : new HttpMethod[] { HttpMethod.POST, HttpMethod.DELETE }) {
			ResponseEntity<String> response = testRestTemplate.exchange(path, method,
					new HttpEntity<>(orderAdmin), String.class);
			assertEquals(method.toString(), HttpStatus.UNAUTHORIZED, response.getStatusCode());
		}
		assertGroupExists("DEFAULT", code, true);
	}
}
