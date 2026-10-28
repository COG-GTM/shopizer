package com.salesmanager.test.shop.integration.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.catalog.PersistableCatalog;
import com.salesmanager.shop.model.catalog.catalog.ReadableCatalog;
import com.salesmanager.shop.model.catalog.product.attribute.ProductOptionDescription;
import com.salesmanager.shop.model.catalog.product.attribute.ProductOptionValueDescription;
import com.salesmanager.shop.model.catalog.product.attribute.PersistableProductOptionValue;
import com.salesmanager.shop.model.catalog.product.attribute.api.PersistableProductOptionEntity;
import com.salesmanager.shop.model.catalog.product.attribute.api.ReadableProductOptionEntity;
import com.salesmanager.shop.model.catalog.product.attribute.api.ReadableProductOptionValue;
import com.salesmanager.shop.model.catalog.product.attribute.optionset.PersistableProductOptionSet;
import com.salesmanager.shop.model.catalog.product.attribute.optionset.ReadableProductOptionSet;
import com.salesmanager.shop.model.catalog.product.type.PersistableProductType;
import com.salesmanager.shop.model.catalog.product.type.ReadableProductType;
import com.salesmanager.shop.model.catalog.product.variation.PersistableProductVariation;
import com.salesmanager.shop.model.catalog.product.variation.ReadableProductVariation;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.variant.PersistableProductVariant;
import com.salesmanager.shop.model.catalog.product.product.variantGroup.PersistableProductVariantGroup;
import com.salesmanager.shop.model.catalog.product.product.variantGroup.ReadableProductVariantGroup;
import com.salesmanager.shop.model.catalog.product.PersistableProductPrice;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.model.tax.PersistableTaxClass;
import com.salesmanager.shop.model.tax.ReadableTaxClass;
import com.salesmanager.test.shop.common.ServicesTestSupport;
import com.salesmanager.core.business.services.catalog.product.availability.ProductAvailabilityService;
import com.salesmanager.core.business.services.catalog.product.price.ProductPriceService;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.catalog.product.price.ProductPrice;
import com.salesmanager.core.model.merchant.MerchantStore;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
class CrossStoreClientIdIntegrationTest extends ServicesTestSupport {

	private static final String CHILD_STORE = "XSTB190";
	private static boolean childStoreReady;

	@Autowired
	private ProductAvailabilityService productAvailabilityService;

	@Autowired
	private ProductPriceService productPriceService;

	@Autowired
	private MerchantStoreService merchantStoreService;

	@BeforeEach
	void ensureChildStore() {
		if (childStoreReady) {
			return;
		}
		String exists = testRestTemplate.getForObject("/api/v1/store/unique?code=" + CHILD_STORE, String.class);
		if (exists == null || !exists.contains("true")) {
			PersistableMerchantStore store = new PersistableMerchantStore();
			store.setCode(CHILD_STORE);
			store.setCurrency("CAD");
			store.setDefaultLanguage("en");
			store.setEmail("xstb190@example.com");
			store.setName(CHILD_STORE);
			store.setPhone("444-555-0190");
			store.setSupportedLanguages(List.of("en"));
			PersistableAddress address = new PersistableAddress();
			address.setAddress("1 Test Road");
			address.setPostalCode("12345");
			address.setCountry("US");
			address.setCity("Test City");
			address.setStateProvince("CA");
			store.setAddress(address);
			ResponseEntity<Void> created = testRestTemplate.postForEntity("/api/v1/private/store",
					new HttpEntity<>(store, getHeader()), Void.class);
			assertEquals(HttpStatus.OK, created.getStatusCode());
		}
		childStoreReady = true;
	}

	@Test
	void taxClassPostAndPutCannotOverwriteDefaultStoreRow() {
		PersistableTaxClass ownerBody = taxClass(code("TA"));
		Entity owner = post("/api/v1/private/tax/class?store=DEFAULT", ownerBody, Entity.class);

		PersistableTaxClass createAttack = taxClass(code("TB"));
		createAttack.setId(owner.getId());
		Entity created = post("/api/v1/private/tax/class?store=" + CHILD_STORE, createAttack, Entity.class);
		assertNotNull(created.getId());
		assertNotEquals(owner.getId(), created.getId());

		PersistableTaxClass updateTargetBody = taxClass(code("TC"));
		Entity updateTarget = post("/api/v1/private/tax/class?store=" + CHILD_STORE, updateTargetBody, Entity.class);
		PersistableTaxClass updateAttack = taxClass(updateTargetBody.getCode());
		updateAttack.setId(owner.getId());
		updateAttack.setName("attacker changed name");
		ResponseEntity<Void> updated = testRestTemplate.exchange(
				"/api/v1/private/tax/class/" + updateTarget.getId() + "?store=" + CHILD_STORE,
				HttpMethod.PUT, new HttpEntity<>(updateAttack, getHeader()), Void.class);
		assertEquals(HttpStatus.OK, updated.getStatusCode());

		ReadableTaxClass ownerAfter = testRestTemplate.exchange(
				"/api/v1/private/tax/class/" + ownerBody.getCode() + "?store=DEFAULT",
				HttpMethod.GET, new HttpEntity<>(getHeader()), ReadableTaxClass.class).getBody();
		assertEquals(ownerBody.getCode(), ownerAfter.getCode());
		assertEquals(ownerBody.getName(), ownerAfter.getName());
	}

	@Test
	void productTypeAndCatalogCreatesIgnoreForeignIds() {
		PersistableProductType typeA = productType(code("TYPEA"));
		Entity ownerType = post("/api/v1/private/product/type?store=DEFAULT", typeA, Entity.class);
		PersistableProductType typeB = productType(code("TYPEB"));
		typeB.setId(ownerType.getId());
		Entity createdType = post("/api/v1/private/product/type?store=" + CHILD_STORE, typeB, Entity.class);
		assertNotNull(createdType.getId());
		assertNotEquals(ownerType.getId(), createdType.getId());
		ReadableProductType ownerTypeAfter = get("/api/v1/private/product/type/" + ownerType.getId()
				+ "?store=DEFAULT", ReadableProductType.class);
		assertEquals(typeA.getCode(), ownerTypeAfter.getCode());

		PersistableCatalog catalogA = catalog(code("CATA"));
		ReadableCatalog ownerCatalog = post("/api/v1/private/catalog?store=DEFAULT", catalogA,
				ReadableCatalog.class);
		PersistableCatalog catalogB = catalog(code("CATB"));
		catalogB.setId(ownerCatalog.getId());
		ReadableCatalog createdCatalog = post("/api/v1/private/catalog?store=" + CHILD_STORE, catalogB,
				ReadableCatalog.class);
		assertNotNull(createdCatalog.getId());
		assertNotEquals(ownerCatalog.getId(), createdCatalog.getId());
		ReadableCatalog ownerCatalogAfter = get("/api/v1/private/catalog/" + ownerCatalog.getId()
				+ "?store=DEFAULT", ReadableCatalog.class);
		assertEquals(catalogA.getCode(), ownerCatalogAfter.getCode());
	}

	@Test
	void optionSetAndVariationCreateAndUpdateAreStoreScoped() {
		OptionData defaultOption = createOptionData("DEFAULT");
		PersistableProductOptionSet optionSetA = new PersistableProductOptionSet();
		optionSetA.setCode(code("SET"));
		optionSetA.setOption(defaultOption.optionId);
		optionSetA.setOptionValues(List.of(defaultOption.valueId));
		ResponseEntity<Void> createdA = testRestTemplate.postForEntity(
				"/api/v1/private/product/property/set?store=DEFAULT",
				new HttpEntity<>(optionSetA, getHeader()), Void.class);
		assertEquals(HttpStatus.CREATED, createdA.getStatusCode());
		ReadableProductOptionSet ownerSet = findOptionSet("DEFAULT", optionSetA.getCode());
		assertNotNull(ownerSet);

		OptionData childOption = createOptionData(CHILD_STORE);
		PersistableProductOptionSet optionSetB = new PersistableProductOptionSet();
		optionSetB.setCode(code("SETB"));
		optionSetB.setOption(childOption.optionId);
		optionSetB.setOptionValues(List.of(childOption.valueId));
		optionSetB.setId(ownerSet.getId());
		ResponseEntity<Void> createdB = testRestTemplate.postForEntity(
				"/api/v1/private/product/property/set?store=" + CHILD_STORE,
				new HttpEntity<>(optionSetB, getHeader()), Void.class);
		assertEquals(HttpStatus.CREATED, createdB.getStatusCode());
		assertEquals(optionSetA.getCode(), get("/api/v1/private/product/property/set/"
				+ ownerSet.getId() + "?store=DEFAULT", ReadableProductOptionSet.class).getCode());

		PersistableProductVariation variationA = variation("VARA", defaultOption);
		Entity ownerVariation = post("/api/v2/private/product/variation?store=DEFAULT",
				variationA, Entity.class);
		PersistableProductVariation variationB = variation("VARB", childOption);
		variationB.setId(ownerVariation.getId());
		Entity createdVariation = post("/api/v2/private/product/variation?store=" + CHILD_STORE,
				variationB, Entity.class);
		assertNotNull(createdVariation.getId());
		assertNotEquals(ownerVariation.getId(), createdVariation.getId());

		PersistableProductVariation updateBody = variation("VARB-UPDATED", childOption);
		updateBody.setId(ownerVariation.getId());
		ResponseEntity<Void> updated = testRestTemplate.exchange(
				"/api/v2/private/product/variation/" + createdVariation.getId() + "?store=" + CHILD_STORE,
				HttpMethod.PUT, new HttpEntity<>(updateBody, getHeader()), Void.class);
		assertEquals(HttpStatus.OK, updated.getStatusCode());
		ReadableProductVariation ownerVariationAfter = get("/api/v2/private/product/variation/"
				+ ownerVariation.getId() + "?store=DEFAULT", ReadableProductVariation.class);
		assertEquals(variationA.getCode(), ownerVariationAfter.getCode());
	}

	@Test
	void variantGroupCreateWithForeignIdUsesNewRow() {
		List<Long> defaultVariants = createVariantFixtures("DEFAULT");
		PersistableProductVariantGroup defaultGroupBody = new PersistableProductVariantGroup();
		defaultGroupBody.setId(42L);
		defaultGroupBody.setproductVariants(defaultVariants);
		Entity defaultGroup = post("/api/v2/private/product/productVariantGroup?store=DEFAULT",
				defaultGroupBody, Entity.class);

		List<Long> childVariants = createVariantFixtures(CHILD_STORE);
		PersistableProductVariantGroup childGroupBody = new PersistableProductVariantGroup();
		childGroupBody.setId(defaultGroup.getId());
		childGroupBody.setproductVariants(childVariants);
		Entity childGroup = post("/api/v2/private/product/productVariantGroup?store=" + CHILD_STORE,
				childGroupBody, Entity.class);
		assertNotEquals(defaultGroup.getId(), childGroup.getId());
		ReadableProductVariantGroup ownerAfter = get("/api/v2/private/product/productVariantGroup/"
				+ defaultGroup.getId() + "?store=DEFAULT", ReadableProductVariantGroup.class);
		assertEquals(defaultGroup.getId(), ownerAfter.getId());
	}

	@Test
	void productAndPriceOperationsCannotChangeDefaultStoreRows() throws Exception {
		String ownerSku = code("PRODA");
		PersistableProduct ownerProductBody = product(ownerSku);
		ownerProductBody.setSku(ownerSku);
		Entity ownerProduct = post("/api/v1/private/product?store=DEFAULT", ownerProductBody, Entity.class);

		String childSku = code("PRODB");
		PersistableProduct childProductBody = product(childSku);
		childProductBody.setSku(childSku);
		childProductBody.setId(ownerProduct.getId());
		Entity childProduct = post("/api/v1/private/product?store=" + CHILD_STORE, childProductBody, Entity.class);
		assertNotEquals(ownerProduct.getId(), childProduct.getId());

		PersistableProduct updateAttack = product(ownerSku);
		updateAttack.setSku(ownerSku);
		updateAttack.setId(ownerProduct.getId());
		String attemptedName = code("ATTACK");
		updateAttack.getDescriptions().get(0).setName(attemptedName);
		ResponseEntity<String> foreignUpdate = testRestTemplate.exchange(
				"/api/v1/private/product/" + ownerProduct.getId() + "?store=" + CHILD_STORE,
				HttpMethod.PUT, new HttpEntity<>(updateAttack, getHeader()), String.class);
		assertTrue(!foreignUpdate.getStatusCode().is2xxSuccessful());
		String ownerAfterAttack = testRestTemplate.getForObject(
				"/api/v2/product/" + ownerSku + "?store=DEFAULT", String.class);
		assertTrue(ownerAfterAttack.contains(ownerSku));
		assertTrue(!ownerAfterAttack.contains(attemptedName));

		updateAttack.getDescriptions().get(0).setName(ownerSku + " updated");
		ResponseEntity<Void> sameStoreUpdate = testRestTemplate.exchange(
				"/api/v1/private/product/" + ownerProduct.getId() + "?store=DEFAULT",
				HttpMethod.PUT, new HttpEntity<>(updateAttack, getHeader()), Void.class);
		assertEquals(HttpStatus.OK, sameStoreUpdate.getStatusCode());

		MerchantStore defaultStore = merchantStoreService.getByCode("DEFAULT");
		MerchantStore childStore = merchantStoreService.getByCode(CHILD_STORE);
		ProductAvailability ownerAvailability = productAvailabilityService.getBySku(ownerSku, 0, 100)
				.getContent().get(0);
		ProductPrice ownerPrice = productPriceService.findByProductSku(ownerSku, defaultStore).get(0);
		BigDecimal originalPrice = ownerPrice.getProductPriceAmount();
		PersistableProductPrice priceAttack = new PersistableProductPrice();
		priceAttack.setId(ownerPrice.getId());
		priceAttack.setPrice(new BigDecimal("499.00"));
		Entity newPrice = post("/api/v1/private/product/" + childSku + "/price?store=" + CHILD_STORE,
				priceAttack, Entity.class);
		assertNotEquals(ownerPrice.getId(), newPrice.getId());

		ResponseEntity<Void> foreignPriceUpdate = testRestTemplate.exchange(
				"/api/v1/private/product/" + ownerSku + "/inventory/" + ownerAvailability.getId()
						+ "/price/" + ownerPrice.getId() + "?store=" + CHILD_STORE,
				HttpMethod.PUT, new HttpEntity<>(priceAttack, getHeader()), Void.class);
		assertTrue(!foreignPriceUpdate.getStatusCode().is2xxSuccessful());
		assertTrue(productPriceService.findByProductSku(ownerSku, defaultStore).stream()
				.anyMatch(price -> price.getId().equals(ownerPrice.getId())));
		assertEquals(originalPrice, productPriceService.findById(ownerPrice.getId(), ownerSku, defaultStore)
				.getProductPriceAmount());
		assertTrue(productPriceService.findByProductSku(ownerSku, childStore).stream()
				.noneMatch(price -> price.getId().equals(ownerPrice.getId())));
	}

	private List<Long> createVariantFixtures(String store) {
		String sku = code("BASE");
		PersistableProduct baseProduct = product(sku);
		baseProduct.setSku(sku);
		Entity product = post("/api/v1/private/product?store=" + store, baseProduct, Entity.class);

		OptionData firstOption = createOptionData(store);
		OptionData secondOption = createOptionData(store);
		String firstVariationCode = code("VARY");
		String secondVariationCode = code("VARY");
		post("/api/v2/private/product/variation?store=" + store,
				variation(firstVariationCode, firstOption), Entity.class);
		post("/api/v2/private/product/variation?store=" + store,
				variation(secondVariationCode, secondOption), Entity.class);

		PersistableProductVariant firstVariant = new PersistableProductVariant();
		firstVariant.setSku(code("SKU"));
		firstVariant.setVariationCode(firstVariationCode);
		firstVariant.setVariationValueCode(secondVariationCode);
		PersistableProductVariant secondVariant = new PersistableProductVariant();
		secondVariant.setSku(code("SKU"));
		secondVariant.setVariationCode(secondVariationCode);
		secondVariant.setVariationValueCode(firstVariationCode);
		Entity variantA = post("/api/v2/private/product/" + product.getId() + "/variant?store=" + store,
				firstVariant, Entity.class);
		Entity variantB = post("/api/v2/private/product/" + product.getId() + "/variant?store=" + store,
				secondVariant, Entity.class);
		return List.of(variantA.getId(), variantB.getId());
	}

	private OptionData createOptionData(String store) {
		String optionCode = code("OPT");
		PersistableProductOptionEntity option = new PersistableProductOptionEntity();
		option.setCode(optionCode);
		ProductOptionDescription optionDescription = new ProductOptionDescription();
		optionDescription.setLanguage("en");
		optionDescription.setName(optionCode);
		option.getDescriptions().add(optionDescription);
		ReadableProductOptionEntity createdOption = post("/api/v1/private/product/option?store=" + store,
				option, ReadableProductOptionEntity.class);

		String valueCode = code("VAL");
		PersistableProductOptionValue value = new PersistableProductOptionValue();
		value.setCode(valueCode);
		ProductOptionValueDescription valueDescription = new ProductOptionValueDescription();
		valueDescription.setLanguage("en");
		valueDescription.setName(valueCode);
		value.getDescriptions().add(valueDescription);
		ReadableProductOptionValue createdValue = post("/api/v1/private/product/option/value?store=" + store,
				value, ReadableProductOptionValue.class);
		return new OptionData(createdOption.getId(), createdValue.getId());
	}

	private PersistableProductVariation variation(String code, OptionData option) {
		PersistableProductVariation variation = new PersistableProductVariation();
		variation.setCode(code);
		variation.setOption(option.optionId);
		variation.setOptionValue(option.valueId);
		return variation;
	}

	private ReadableProductOptionSet findOptionSet(String store, String code) {
		List<ReadableProductOptionSet> sets = testRestTemplate.exchange(
				"/api/v1/private/product/property/set?store=" + store,
				HttpMethod.GET, new HttpEntity<>(getHeader()),
				new ParameterizedTypeReference<List<ReadableProductOptionSet>>() { }).getBody();
		return sets.stream().filter(set -> code.equals(set.getCode())).findFirst().orElseThrow();
	}

	private String code(String prefix) {
		return prefix + Long.toString(System.nanoTime(), 36).toUpperCase();
	}

	private PersistableTaxClass taxClass(String code) {
		PersistableTaxClass body = new PersistableTaxClass();
		body.setCode(code);
		body.setName(code + " name");
		return body;
	}

	private PersistableProductType productType(String code) {
		PersistableProductType body = new PersistableProductType();
		body.setCode(code);
		body.setVisible(true);
		body.setAllowAddToCart(true);
		return body;
	}

	private PersistableCatalog catalog(String code) {
		PersistableCatalog body = new PersistableCatalog();
		body.setCode(code);
		body.setVisible(true);
		return body;
	}

	private <T> T post(String url, Object body, Class<T> responseType) {
		ResponseEntity<T> response = testRestTemplate.postForEntity(url, new HttpEntity<>(body, getHeader()),
				responseType);
		assertTrue(response.getStatusCode().is2xxSuccessful());
		return response.getBody();
	}

	private <T> T get(String url, Class<T> responseType) {
		return testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(getHeader()), responseType).getBody();
	}

	private static class OptionData {
		private final Long optionId;
		private final Long valueId;

		private OptionData(Long optionId, Long valueId) {
			this.optionId = optionId;
			this.valueId = valueId;
		}
	}
}
