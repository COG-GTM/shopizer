package com.salesmanager.test.shop.integration.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.math.BigDecimal;
import java.util.ArrayList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.core.business.repositories.catalog.product.availability.ProductAvailabilityRepository;
import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.PersistableProductReview;
import com.salesmanager.shop.model.catalog.product.attribute.PersistableProductOptionValue;
import com.salesmanager.shop.model.catalog.product.attribute.ProductOptionDescription;
import com.salesmanager.shop.model.catalog.product.attribute.ProductOptionValueDescription;
import com.salesmanager.shop.model.catalog.product.attribute.api.PersistableProductOptionEntity;
import com.salesmanager.shop.model.catalog.product.attribute.api.ReadableProductOptionEntity;
import com.salesmanager.shop.model.catalog.product.attribute.api.ReadableProductOptionValue;
import com.salesmanager.shop.model.catalog.product.inventory.PersistableInventory;
import com.salesmanager.shop.model.catalog.product.inventory.ReadableInventory;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.ProductSpecification;
import com.salesmanager.shop.model.catalog.product.product.variant.PersistableProductVariant;
import com.salesmanager.shop.model.catalog.product.product.variant.ReadableProductVariant;
import com.salesmanager.shop.model.catalog.product.variation.PersistableProductVariation;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.PersistableCustomerReview;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.entity.ReadableEntityList;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RewrittenQueryEndpointsIntegrationTest extends ServicesTestSupport {

	@Autowired
	private ProductAvailabilityRepository productAvailabilityRepository;

	@Autowired
	private ProductService productService;

	@Autowired
	private MerchantStoreService merchantStoreService;

	@Test
	public void storeAndMerchantEndpointsExecuteRewrittenQueries() {
		HttpEntity<?> adminRequest = new HttpEntity<>(getHeader());
		ResponseEntity<JsonNode> publicStore = testRestTemplate.exchange("/api/v1/store/DEFAULT", GET,
				adminRequest, JsonNode.class);
		assertEquals(OK, publicStore.getStatusCode());
		assertNotNull(publicStore.getBody());
		assertTrue(publicStore.getBody().has("address"));
		assertTrue(publicStore.getBody().get("address").has("country"));
		assertTrue(publicStore.getBody().has("currency"));
		assertTrue(publicStore.getBody().has("defaultLanguage"));

		ResponseEntity<JsonNode> privateStore = testRestTemplate.exchange("/api/v1/private/store/DEFAULT", GET,
				adminRequest, JsonNode.class);
		assertEquals(OK, privateStore.getStatusCode());
		assertNotNull(privateStore.getBody());
		assertTrue(privateStore.getBody().get("address").has("country"));
		assertTrue(privateStore.getBody().has("currency"));
		assertTrue(privateStore.getBody().has("defaultLanguage"));
		assertEquals(OK, testRestTemplate.exchange("/api/v1/private/merchant/DEFAULT/stores", GET,
				adminRequest, JsonNode.class).getStatusCode());
		assertEquals(OK, testRestTemplate.exchange("/api/v1/private/merchant/DEFAULT/children", GET,
				adminRequest, JsonNode.class).getStatusCode());
		assertEquals(OK, testRestTemplate.exchange("/api/v1/private/stores", GET,
				adminRequest, JsonNode.class).getStatusCode());
	}

	@Test
	public void inventoryAndVariantEndpointsReturnPersistedProductData() throws Exception {
		String suffix = Long.toString(System.nanoTime());
		String sku = "query-product-" + suffix;
		PersistableProduct product = product(sku);
		product.setSku(sku);
		product.setAvailable(true);
		product.setPrice(BigDecimal.TEN);
		product.setQuantity(5);
		ProductSpecification specification = new ProductSpecification();
		specification.setManufacturer(Manufacturer.DEFAULT_MANUFACTURER);
		product.setProductSpecifications(specification);

		ResponseEntity<Entity> createdProduct = testRestTemplate.postForEntity("/api/v1/private/product?store=DEFAULT",
				new HttpEntity<>(product, getHeader()), Entity.class);
		assertEquals(CREATED, createdProduct.getStatusCode());
		assertNotNull(createdProduct.getBody());
		Long productId = createdProduct.getBody().getId();

		MerchantStore store = merchantStoreService.getByCode("DEFAULT");
		com.salesmanager.core.model.catalog.product.Product storedProduct = productService.findOne(productId, store);
		for (ProductAvailability availability : storedProduct.getAvailabilities()) {
			if (availability.getMerchantStore() == null) {
				availability.setMerchantStore(store);
				productAvailabilityRepository.save(availability);
			}
		}

		PersistableInventory inventory = new PersistableInventory();
		inventory.setProductId(productId);
		inventory.setQuantity(3);
		inventory.setAvailable(true);
		inventory.setPrices(new ArrayList<>());
		ResponseEntity<ReadableInventory> createdInventory = testRestTemplate.postForEntity(
				"/api/v1/private/product/" + productId + "/inventory?store=DEFAULT",
				new HttpEntity<>(inventory, getHeader()), ReadableInventory.class);
		assertEquals(CREATED, createdInventory.getStatusCode());
		assertNotNull(createdInventory.getBody());
		assertTrue(createdInventory.getBody().getId() > 0);

		ResponseEntity<ReadableEntityList> bySku = testRestTemplate.exchange(
				"/api/v1/private/product/" + sku + "/inventory?store=DEFAULT", GET,
				new HttpEntity<>(getHeader()), ReadableEntityList.class);
		assertEquals(OK, bySku.getStatusCode());
		assertNotNull(bySku.getBody());
		assertTrue(!bySku.getBody().getItems().isEmpty());

		ResponseEntity<ReadableEntityList> byId = testRestTemplate.exchange(
				"/api/v1/private/product/inventory?productId=" + productId + "&store=DEFAULT", GET,
				new HttpEntity<>(getHeader()), ReadableEntityList.class);
		assertEquals(OK, byId.getStatusCode());
		assertNotNull(byId.getBody());
		assertTrue(!byId.getBody().getItems().isEmpty());

		PersistableProductVariation firstVariation = createVariation(suffix, "color", "red");
		PersistableProductVariation secondVariation = createVariation(suffix, "size", "medium");
		PersistableProductVariant variant = new PersistableProductVariant();
		variant.setProductId(productId);
		variant.setSku("query-variant-" + suffix);
		variant.setAvailable(true);
		variant.setVariation(firstVariation.getId());
		variant.setVariationValue(secondVariation.getId());
		ResponseEntity<Entity> createdVariant = testRestTemplate.postForEntity(
				"/api/v2/private/product/" + productId + "/variant?store=DEFAULT",
				new HttpEntity<>(variant, getHeader()), Entity.class);
		assertEquals(CREATED, createdVariant.getStatusCode());
		assertNotNull(createdVariant.getBody());

		ResponseEntity<ReadableEntityList> variants = testRestTemplate.exchange(
				"/api/v2/private/product/" + productId + "/variants?store=DEFAULT", GET,
				new HttpEntity<>(getHeader()), ReadableEntityList.class);
		assertEquals(OK, variants.getStatusCode());
		assertNotNull(variants.getBody());
		assertTrue(!variants.getBody().getItems().isEmpty());
		ResponseEntity<ReadableProductVariant> variantById = testRestTemplate.exchange(
				"/api/v2/private/product/" + productId + "/variant/" + createdVariant.getBody().getId()
						+ "?store=DEFAULT", GET, new HttpEntity<>(getHeader()), ReadableProductVariant.class);
		assertEquals(OK, variantById.getStatusCode());
		assertEquals(firstVariation.getCode(), variantById.getBody().getVariation().getCode());
		assertEquals(secondVariation.getCode(), variantById.getBody().getVariationValue().getCode());
		JsonNode productBySku = testRestTemplate.exchange("/api/v2/product/" + sku, GET,
				new HttpEntity<>(getHeader()), JsonNode.class).getBody();
		assertNotNull(productBySku);
		assertTrue(productBySku.toString().contains(variant.getSku()));
	}

	@Test
	public void productAndCustomerReviewEndpointsReturnCreatedReviews() {
		String suffix = Long.toString(System.nanoTime());
		String email = "query-review-" + suffix + "@example.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setLanguage("en");
		customer.setStoreCode("DEFAULT");
		Address billing = new Address();
		billing.setFirstName("Query");
		billing.setLastName("Review");
		billing.setCountry("BE");
		customer.setBilling(billing);
		ResponseEntity<ReadableCustomer> registered = testRestTemplate.postForEntity(
				"/api/v1/customer/register", new HttpEntity<>(customer, getHeader()), ReadableCustomer.class);
		assertEquals(OK, registered.getStatusCode());
		assertNotNull(registered.getBody());
		ResponseEntity<AuthenticationResponse> login = testRestTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")), AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody());
		HttpHeaders customerHeaders = new HttpHeaders();
		customerHeaders.setBearerAuth(login.getBody().getToken());

		String sku = "query-reviewed-product-" + suffix;
		com.salesmanager.shop.model.catalog.product.ReadableProduct product = sampleProduct(sku);
		PersistableProductReview productReview = new PersistableProductReview();
		productReview.setCustomerId(registered.getBody().getId());
		productReview.setDescription("review-" + suffix);
		productReview.setRating(5.0);
		ResponseEntity<PersistableProductReview> createdProductReview = testRestTemplate.postForEntity(
				"/api/v1/auth/products/" + product.getId() + "/reviews?store=DEFAULT",
				new HttpEntity<>(productReview, customerHeaders), PersistableProductReview.class);
		assertEquals(CREATED, createdProductReview.getStatusCode());
		JsonNode productReviews = testRestTemplate.getForEntity(
				"/api/v1/product/" + product.getId() + "/reviews?store=DEFAULT", JsonNode.class).getBody();
		assertNotNull(productReviews);
		assertTrue(productReviews.toString().contains(productReview.getDescription()));

		PersistableCustomerReview customerReview = new PersistableCustomerReview();
		customerReview.setCustomerId(registered.getBody().getId());
		customerReview.setDescription("customer-review-" + suffix);
		customerReview.setRating(5.0);
		ResponseEntity<PersistableCustomerReview> createdCustomerReview = testRestTemplate.postForEntity(
				"/api/v1/private/customers/" + registered.getBody().getId() + "/reviews?store=DEFAULT",
				new HttpEntity<>(customerReview, getHeader()), PersistableCustomerReview.class);
		assertEquals(CREATED, createdCustomerReview.getStatusCode());
		JsonNode customerReviews = testRestTemplate.getForEntity(
				"/api/v1/customers/" + registered.getBody().getId() + "/reviews?store=DEFAULT",
				JsonNode.class).getBody();
		assertNotNull(customerReviews);
		assertTrue(customerReviews.toString().contains(customerReview.getDescription()));
	}

	private PersistableProductVariation createVariation(String suffix, String optionCode, String valueCode) {
		PersistableProductOptionEntity option = new PersistableProductOptionEntity();
		option.setCode("query-" + optionCode + "-" + suffix);
		option.setType("select");
		ProductOptionDescription optionDescription = new ProductOptionDescription();
		optionDescription.setName(optionCode);
		optionDescription.setLanguage("en");
		option.getDescriptions().add(optionDescription);
		ResponseEntity<ReadableProductOptionEntity> savedOption = testRestTemplate.postForEntity(
				"/api/v1/private/product/option?store=DEFAULT", new HttpEntity<>(option, getHeader()),
				ReadableProductOptionEntity.class);
		assertEquals(CREATED, savedOption.getStatusCode());
		assertNotNull(savedOption.getBody());

		PersistableProductOptionValue optionValue = new PersistableProductOptionValue();
		optionValue.setCode("query-" + valueCode + "-" + suffix);
		optionValue.setSortOrder(1);
		ProductOptionValueDescription valueDescription = new ProductOptionValueDescription();
		valueDescription.setName(valueCode);
		valueDescription.setLanguage("en");
		optionValue.getDescriptions().add(valueDescription);
		ResponseEntity<ReadableProductOptionValue> savedValue = testRestTemplate.postForEntity(
				"/api/v1/private/product/option/value?store=DEFAULT", new HttpEntity<>(optionValue, getHeader()),
				ReadableProductOptionValue.class);
		assertEquals(CREATED, savedValue.getStatusCode());
		assertNotNull(savedValue.getBody());

		PersistableProductVariation variation = new PersistableProductVariation();
		variation.setCode("query-" + optionCode + "-" + valueCode + "-" + suffix);
		variation.setOption(savedOption.getBody().getId());
		variation.setOptionValue(savedValue.getBody().getId());
		ResponseEntity<Entity> savedVariation = testRestTemplate.postForEntity(
				"/api/v2/private/product/variation?store=DEFAULT",
				new HttpEntity<>(variation, getHeader()), Entity.class);
		assertEquals(CREATED, savedVariation.getStatusCode());
		assertNotNull(savedVariation.getBody());
		variation.setId(savedVariation.getBody().getId());
		return variation;
	}
}
