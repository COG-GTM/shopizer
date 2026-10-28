package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
import com.salesmanager.shop.model.catalog.product.attribute.ProductOptionDescription;
import com.salesmanager.shop.model.catalog.product.attribute.api.PersistableProductOptionEntity;
import com.salesmanager.shop.model.catalog.product.attribute.api.ReadableProductOptionEntity;
import com.salesmanager.shop.model.catalog.product.attribute.optionset.PersistableProductOptionSet;
import com.salesmanager.shop.model.catalog.product.attribute.optionset.ReadableProductOptionSet;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ProductOptionSetIntegrationTest extends ServicesTestSupport {

	private static final String OPTION_SET_URL = "/api/v1/private/product/property/set";

	@Test
	public void createIgnoresIdFromRequestBody() throws Exception {

		Long optionId = createOption("OSCOLOR");

		ReadableProductOptionSet victim = createOptionSet("OSVICTIM", optionId, null);

		ReadableProductOptionSet created = createOptionSet("OSATTACKER", optionId, victim.getId());
		assertNotEquals(victim.getId(), created.getId());

		ResponseEntity<ReadableProductOptionSet> victimAfter = testRestTemplate.exchange(
				OPTION_SET_URL + "/" + victim.getId(), HttpMethod.GET, new HttpEntity<>(getHeader()),
				ReadableProductOptionSet.class);
		assertEquals(HttpStatus.OK, victimAfter.getStatusCode());
		assertEquals("OSVICTIM", victimAfter.getBody().getCode());
	}

	private Long createOption(String code) {
		PersistableProductOptionEntity option = new PersistableProductOptionEntity();
		option.setCode(code);
		option.setType("select");
		ProductOptionDescription description = new ProductOptionDescription();
		description.setLanguage("en");
		description.setName(code);
		option.setDescriptions(Collections.singletonList(description));

		ResponseEntity<ReadableProductOptionEntity> response = testRestTemplate.postForEntity(
				"/api/v1/private/product/option", new HttpEntity<>(option, getHeader()),
				ReadableProductOptionEntity.class);
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		assertNotNull(response.getBody().getId());
		return response.getBody().getId();
	}

	private ReadableProductOptionSet createOptionSet(String code, Long optionId, Long id) {
		PersistableProductOptionSet optionSet = new PersistableProductOptionSet();
		optionSet.setId(id);
		optionSet.setCode(code);
		optionSet.setOption(optionId);

		ResponseEntity<Void> response = testRestTemplate.postForEntity(OPTION_SET_URL,
				new HttpEntity<>(optionSet, getHeader()), Void.class);
		assertEquals(HttpStatus.CREATED, response.getStatusCode());

		Optional<ReadableProductOptionSet> found = listOptionSets().stream()
				.filter(s -> code.equals(s.getCode())).findFirst();
		assertTrue("option set [" + code + "] was not created", found.isPresent());
		return found.get();
	}

	private List<ReadableProductOptionSet> listOptionSets() {
		ResponseEntity<ReadableProductOptionSet[]> response = testRestTemplate.exchange(OPTION_SET_URL,
				HttpMethod.GET, new HttpEntity<>(getHeader()), ReadableProductOptionSet[].class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		return Arrays.asList(response.getBody());
	}

}
