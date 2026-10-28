package com.salesmanager.test.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.springframework.data.domain.Page;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.ProductCriteria;
import com.salesmanager.core.model.catalog.product.attribute.AttributeCriteria;
import com.salesmanager.core.model.catalog.product.attribute.ProductAttribute;
import com.salesmanager.core.model.catalog.product.attribute.ProductOption;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionDescription;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionType;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionValue;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionValueDescription;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.catalog.product.description.ProductDescription;
import com.salesmanager.core.model.catalog.product.price.ProductPrice;
import com.salesmanager.core.model.catalog.product.price.ProductPriceDescription;
import com.salesmanager.core.model.catalog.product.type.ProductType;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;

public class ProductAttributeCriteriaTest extends com.salesmanager.test.common.AbstractSalesManagerCoreTestCase {

	private MerchantStore store;
	private Language english;
	private ProductOption option;
	private Product redFirst;
	private Product redSecond;
	private Product blue;

	@Before
	public void createProductsWithOptionValues() throws Exception {
		store = merchantService.getByCode(MerchantStore.DEFAULT_STORE);
		english = languageService.getByCode("en");
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
		String optionCode = "ATTRCRIT_COLOR_" + suffix;

		option = new ProductOption();
		option.setMerchantStore(store);
		option.setCode(optionCode);
		option.setProductOptionType(ProductOptionType.Select.name());
		ProductOptionDescription optionDescription = new ProductOptionDescription();
		optionDescription.setLanguage(english);
		optionDescription.setName("Attribute criteria colour " + suffix);
		optionDescription.setDescription("Attribute criteria colour " + suffix);
		optionDescription.setProductOption(option);
		option.getDescriptions().add(optionDescription);
		productOptionService.saveOrUpdate(option);

		ProductOptionValue red = createOptionValue(optionCode + "_RED", "Red");
		ProductOptionValue blueValue = createOptionValue(optionCode + "_BLUE", "Blue");

		redFirst = createProduct("Red product one " + suffix, "ATTRCRIT_RED1_" + suffix, red);
		redSecond = createProduct("Red product two " + suffix, "ATTRCRIT_RED2_" + suffix, red);
		blue = createProduct("Blue product " + suffix, "ATTRCRIT_BLUE_" + suffix, blueValue);
	}

	@Test
	public void matchingCodeAndValueReturnsDistinctMatchingProducts() {
		Page<Product> result = list(criteria(attribute(option.getCode(), "Red")));

		assertEquals(2, result.getTotalElements());
		assertEquals(2, result.getContent().size());
		assertTrue(result.getContent().stream().map(Product::getId).collect(Collectors.toSet())
				.containsAll(Arrays.asList(redFirst.getId(), redSecond.getId())));
	}

	@Test
	public void nonMatchingValueReturnsNoProducts() {
		Page<Product> result = list(criteria(attribute(option.getCode(), "Purple")));

		assertTrue(result.getContent().isEmpty());
		assertEquals(0, result.getTotalElements());
	}

	@Test
	public void hostileCodesAreBoundAsValuesAndReturnNoProducts() {
		for (String hostileCode : Arrays.asList(option.getCode() + " or 1=1",
				option.getCode() + "') or ('1'='1")) {
			Page<Product> result = list(criteria(attribute(hostileCode, "Red")));

			assertTrue(result.getContent().isEmpty());
			assertEquals(0, result.getTotalElements());
		}
	}

	@Test
	public void twoCriteriaReturnMatchingProductsWithConsistentTotal() {
		Page<Product> result = list(criteria(attribute(option.getCode(), "Red"),
				attribute(option.getCode(), "Red")));

		assertFalse(result.getContent().isEmpty());
		assertEquals(result.getContent().size(), result.getTotalElements());
		assertTrue(result.getContent().stream().map(Product::getId).collect(Collectors.toSet())
				.containsAll(Arrays.asList(redFirst.getId(), redSecond.getId())));
	}

	@Test
	public void adminOriginIgnoresAttributeCriteriaWithoutOptionValueIds() {
		ProductCriteria criteria = criteria(attribute(option.getCode(), "Not present"));
		criteria.setOrigin(ProductCriteria.ORIGIN_ADMIN);

		Page<Product> result = list(criteria);

		assertTrue(result.getContent().stream().map(Product::getId).collect(Collectors.toSet())
				.containsAll(Arrays.asList(redFirst.getId(), redSecond.getId(), blue.getId())));
	}

	private Page<Product> list(ProductCriteria criteria) {
		return productService.listByStore(store, english, criteria, 0, 10);
	}

	private ProductCriteria criteria(AttributeCriteria... attributes) {
		ProductCriteria criteria = new ProductCriteria();
		criteria.setLanguage(english.getCode());
		criteria.setAttributeCriteria(Arrays.asList(attributes));
		return criteria;
	}

	private Product createProduct(String name, String sku, ProductOptionValue value) throws ServiceException {
		Product product = new Product();
		product.setMerchantStore(store);
		product.setType(productTypeService.getProductType(ProductType.GENERAL_TYPE));
		product.setSku(sku);
		product.setAvailable(true);

		ProductDescription description = new ProductDescription();
		description.setName(name);
		description.setLanguage(english);
		description.setProduct(product);
		product.getDescriptions().add(description);

		ProductAvailability availability = new ProductAvailability();
		availability.setProduct(product);
		availability.setMerchantStore(store);
		availability.setProductDateAvailable(new Date());
		availability.setProductQuantity(10);
		availability.setRegion("*");
		availability.setAvailable(true);
		product.getAvailabilities().add(availability);

		ProductPrice price = new ProductPrice();
		price.setDefaultPrice(true);
		price.setProductPriceAmount(BigDecimal.TEN);
		price.setProductAvailability(availability);
		ProductPriceDescription priceDescription = new ProductPriceDescription();
		priceDescription.setName("Base price");
		priceDescription.setLanguage(english);
		priceDescription.setProductPrice(price);
		price.getDescriptions().add(priceDescription);
		availability.getPrices().add(price);

		ProductAttribute attribute = new ProductAttribute();
		attribute.setProduct(product);
		attribute.setProductOption(option);
		attribute.setProductOptionValue(value);
		attribute.setProductAttributePrice(BigDecimal.ZERO);
		attribute.setProductAttributeWeight(BigDecimal.ZERO);
		product.getAttributes().add(attribute);

		return productService.saveProduct(product);
	}

	private ProductOptionValue createOptionValue(String code, String name) throws ServiceException {
		ProductOptionValue value = new ProductOptionValue();
		value.setMerchantStore(store);
		value.setCode(code);
		ProductOptionValueDescription description = new ProductOptionValueDescription();
		description.setLanguage(english);
		description.setName(name);
		description.setDescription(name);
		description.setProductOptionValue(value);
		value.getDescriptions().add(description);
		productOptionValueService.saveOrUpdate(value);
		return value;
	}

	private static AttributeCriteria attribute(String code, String value) {
		AttributeCriteria criteria = new AttributeCriteria();
		criteria.setAttributeCode(code);
		criteria.setAttributeValue(value);
		return criteria;
	}
}
