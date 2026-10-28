package com.salesmanager.test.shop.integration.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.OK;

import java.util.UUID;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.util.UriComponentsBuilder;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.catalog.product.attribute.ProductAttributeService;
import com.salesmanager.core.business.services.catalog.product.attribute.ProductOptionService;
import com.salesmanager.core.business.services.catalog.product.attribute.ProductOptionValueService;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.attribute.ProductAttribute;
import com.salesmanager.core.model.catalog.product.attribute.ProductOption;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionDescription;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionType;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionValue;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionValueDescription;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ProductAttributeCriteriaFilterIntegrationTest extends ServicesTestSupport {

	@Inject
	private TestRestTemplate restTemplate;

	@Inject
	private ProductService productService;

	@Inject
	private ProductOptionService productOptionService;

	@Inject
	private ProductOptionValueService productOptionValueService;

	@Inject
	private ProductAttributeService productAttributeService;

	@Inject
	private MerchantStoreService merchantStoreService;

	@Inject
	private LanguageService languageService;

	private String sku;
	private String optionCode;
	private MerchantStore store;
	private Language english;

	@Before
	public void createProductAttribute() throws Exception {
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
		sku = "ATTRCRITHTTP" + suffix;
		optionCode = "ATTRCRIT_HTTP_COLOR_" + suffix;
		store = merchantStoreService.getByCode(MerchantStore.DEFAULT_STORE);
		english = languageService.getByCode("en");

		ReadableProduct readableProduct = sampleProduct(sku);
		ProductOption option = new ProductOption();
		option.setMerchantStore(store);
		option.setCode(optionCode);
		option.setProductOptionType(ProductOptionType.Select.name());
		ProductOptionDescription optionDescription = new ProductOptionDescription();
		optionDescription.setLanguage(english);
		optionDescription.setName(optionCode);
		optionDescription.setDescription(optionCode);
		optionDescription.setProductOption(option);
		option.getDescriptions().add(optionDescription);
		productOptionService.saveOrUpdate(option);

		ProductOptionValue value = new ProductOptionValue();
		value.setMerchantStore(store);
		value.setCode(optionCode + "_RED");
		ProductOptionValueDescription valueDescription = new ProductOptionValueDescription();
		valueDescription.setLanguage(english);
		valueDescription.setName("Red");
		valueDescription.setDescription("Red");
		valueDescription.setProductOptionValue(value);
		value.getDescriptions().add(valueDescription);
		productOptionValueService.saveOrUpdate(value);

		Product product = productService.getBySku(readableProduct.getSku(), store, english);
		ProductAttribute attribute = new ProductAttribute();
		attribute.setProduct(product);
		attribute.setProductOption(option);
		attribute.setProductOptionValue(value);
		productAttributeService.saveOrUpdate(attribute);
	}

	@Test
	public void matchingValueReturnsProductAndConsistentTotal() {
		ResponseEntity<ReadableProductList> response = query(optionCode, "Red");

		assertEquals(OK, response.getStatusCode());
		assertTrue(response.getBody().getProducts().stream().map(ReadableProduct::getSku).anyMatch(sku::equals));
		assertEquals(response.getBody().getProducts().size(), response.getBody().getRecordsTotal());
	}

	@Test
	public void nonMatchingValueReturnsEmptyList() {
		ResponseEntity<ReadableProductList> response = query(optionCode, "Purple");

		assertEquals(OK, response.getStatusCode());
		assertTrue(response.getBody().getProducts().isEmpty());
		assertEquals(0, response.getBody().getRecordsTotal());
	}

	@Test
	public void hostileCodeReturnsEmptyListWithoutServerError() {
		ResponseEntity<ReadableProductList> response = query(optionCode + " or 1=1", "Red");

		assertEquals(OK, response.getStatusCode());
		assertTrue(response.getBody().getProducts().isEmpty());
		assertEquals(0, response.getBody().getRecordsTotal());
	}

	@Test
	public void twoCriteriaReturnProduct() {
		ResponseEntity<ReadableProductList> response = query(optionCode, "Red", optionCode, "Red");

		assertEquals(OK, response.getStatusCode());
		assertTrue(response.getBody().getProducts().stream().map(ReadableProduct::getSku).anyMatch(sku::equals));
		assertEquals(response.getBody().getProducts().size(), response.getBody().getRecordsTotal());
	}

	@Test
	public void noAttributeCriteriaStillListsProduct() {
		ResponseEntity<ReadableProductList> response = restTemplate.exchange(
				UriComponentsBuilder.fromPath("/api/v2/products")
						.queryParam("store", MerchantStore.DEFAULT_STORE).queryParam("lang", "en")
						.build().encode().toUri(),
				GET, HttpEntity.EMPTY, ReadableProductList.class);

		assertEquals(OK, response.getStatusCode());
		assertFalse(response.getBody().getProducts().isEmpty());
		assertTrue(response.getBody().getProducts().stream().map(ReadableProduct::getSku).anyMatch(sku::equals));
	}

	private ResponseEntity<ReadableProductList> query(String... pairs) {
		UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/api/v2/products")
				.queryParam("store", MerchantStore.DEFAULT_STORE)
				.queryParam("lang", "en");
		for (int i = 0; i < pairs.length; i += 2) {
			int index = i / 2;
			builder.queryParam("attributeCriteria[" + index + "].attributeCode", pairs[i]);
			builder.queryParam("attributeCriteria[" + index + "].attributeValue", pairs[i + 1]);
		}
		return restTemplate.exchange(builder.build().encode().toUri(), GET, HttpEntity.EMPTY, ReadableProductList.class);
	}
}
