package com.salesmanager.test.shop.integration.shipping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.kie.api.runtime.KieSession;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.salesmanager.core.business.configuration.DroolsBeanFactory;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.modules.integration.shipping.impl.CustomShippingQuoteRules;
import com.salesmanager.core.business.modules.integration.shipping.impl.DecisionResponse;
import com.salesmanager.core.business.modules.integration.shipping.impl.ShippingDecisionPreProcessorImpl;
import com.salesmanager.core.business.modules.integration.shipping.impl.ShippingInputParameters;
import com.salesmanager.core.business.modules.order.total.PromoCodeCalculatorModule;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.shipping.ShippingService;
import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.order.OrderSummary;
import com.salesmanager.core.model.reference.country.Country;
import com.salesmanager.core.model.shipping.PackageDetails;
import com.salesmanager.core.model.shipping.ShippingOption;
import com.salesmanager.core.model.shipping.ShippingQuote;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.core.modules.constants.Constants;
import com.salesmanager.core.modules.integration.shipping.model.ShippingQuotePrePostProcessModule;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.category.Category;
import com.salesmanager.shop.model.catalog.category.PersistableCategory;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.ProductSpecification;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.customer.address.AddressLocation;
import com.salesmanager.shop.model.order.shipping.ReadableShippingSummary;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.shipping.ExpeditionConfiguration;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public class DroolsShippingIntegrationTest extends ServicesTestSupport {

	@Autowired
	private DroolsBeanFactory droolsBeanFactory;

	@Autowired
	@Qualifier("customShippingPriceModule")
	private CustomShippingQuoteRules customShippingPriceModule;

	@Autowired
	private PromoCodeCalculatorModule promoCodeCalculatorModule;

	@Autowired
	@Qualifier("shippingModulePreProcessors")
	private List<ShippingQuotePrePostProcessModule> shippingModulePreProcessors;

	@Autowired
	private TestRestTemplate testRestTemplate;

	@Autowired
	private ShippingService shippingService;

	@Autowired
	private MerchantStoreService merchantStoreService;

	@Test
	public void springRulesAndShippingEndpointsUseConfiguredDrools() throws Exception {
		KieSession configuredSession = droolsBeanFactory.getKieSession();
		try {
			ShippingInputParameters input = new ShippingInputParameters();
			input.setWeight(70);
			input.setSize(78);
			input.setCountry("CA");
			input.setProvince("QC");
			input.setDistance(1000);
			DecisionResponse decision = new DecisionResponse();
			configuredSession.insert(input);
			configuredSession.setGlobal("decision", decision);
			configuredSession.fireAllRules();
			assertEquals("priceByDistance", decision.getModuleName());
			assertEquals("140", decision.getCustomPrice());
		} finally {
			configuredSession.dispose();
		}

		ShippingQuote quote = new ShippingQuote();
		quote.getQuoteInformations().put(Constants.DISTANCE_KEY, 1000.0);
		List<ShippingOption> options = customShippingPriceModule.getShippingQuotes(
				quote,
				Collections.singletonList(packageDetails(10, 10, 10, 10)),
				BigDecimal.ZERO,
				delivery("H3Z 2Y7"),
				null,
				null,
				null,
				new IntegrationModule(),
				null,
				Locale.CANADA);
		assertNotNull(options);
		assertEquals("customQuotesRules", options.get(0).getOptionCode());
		assertEquals(new BigDecimal("140"), options.get(0).getOptionPrice());

		OrderSummary summary = new OrderSummary();
		summary.setPromoCode("Test1234");
		assertNull(promoCodeCalculatorModule.caculateProductPiceVariation(
				summary, null, null, null, new com.salesmanager.core.model.merchant.MerchantStore()));

		ShippingQuotePrePostProcessModule decisionProcessor = shippingModulePreProcessors.stream()
				.filter(ShippingDecisionPreProcessorImpl.class::isInstance)
				.findFirst()
				.orElseThrow(() -> new AssertionError("Shipping decision pre-processor is not configured"));
		ShippingQuote decisionQuote = new ShippingQuote();
		IntegrationModule currentModule = new IntegrationModule();
		currentModule.setCode("canadapost");
		IntegrationModule distanceModule = new IntegrationModule();
		distanceModule.setCode("priceByDistance");
		decisionProcessor.prePostProcessShippingQuotes(
				decisionQuote,
				Collections.singletonList(packageDetails(70, 78, 78, 78)),
				BigDecimal.ZERO,
				delivery("H3Z 2Y7"),
				null,
				null,
				null,
				currentModule,
				null,
				java.util.Arrays.asList(currentModule, distanceModule),
				Locale.CANADA);
		assertEquals("priceByDistance", decisionQuote.getCurrentShippingModule().getCode());

		configureInternationalShipping();
		configureCustomShippingModule();
		ReadableShoppingCart cart = createCart();
		AddressLocation address = new AddressLocation();
		address.setCountryCode("CA");
		address.setPostalCode("H3Z 2Y7");
		ResponseEntity<ReadableShippingSummary> shippingResponse = testRestTemplate.postForEntity(
				"/api/v1/cart/" + cart.getCode() + "/shipping",
				address,
				ReadableShippingSummary.class);
		assertEquals(HttpStatus.OK, shippingResponse.getStatusCode());
		assertNotNull(shippingResponse.getBody());
		assertNotNull(shippingResponse.getBody().getShippingOptions());
		assertFalse(shippingResponse.getBody().getShippingOptions().isEmpty());
		List<ShippingOption> shippingOptions = shippingResponse.getBody().getShippingOptions();
		String optionDetails = shippingOptions.stream()
				.map(option -> option.getOptionCode() + "=" + option.getOptionPrice())
				.collect(Collectors.joining(", "));
		assertTrue(optionDetails, shippingOptions.stream()
				.anyMatch(option -> CustomShippingQuoteRules.MODULE_CODE.equals(option.getOptionCode())
						&& new BigDecimal("140").compareTo(option.getOptionPrice()) == 0));

		ResponseEntity<ReadableShoppingCart> promoResponse = testRestTemplate.exchange(
				"/api/v1/cart/" + cart.getCode() + "/promo/Test1234",
				HttpMethod.POST,
				new HttpEntity<Void>(getHeader()),
				ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, promoResponse.getStatusCode());
		assertNotNull(promoResponse.getBody());
		assertEquals(cart.getTotal(), promoResponse.getBody().getTotal());
	}

	private void configureCustomShippingModule() throws ServiceException {
		// Use the service because the pre-existing admin endpoint cannot create a new module config.
		IntegrationConfiguration configuration = new IntegrationConfiguration();
		configuration.setModuleCode(CustomShippingQuoteRules.MODULE_CODE);
		configuration.setActive(true);
		configuration.setDefaultSelected(true);
		shippingService.saveShippingQuoteModuleConfiguration(configuration, merchantStoreService.getByCode("DEFAULT"));
	}

	private void configureInternationalShipping() {
		ExpeditionConfiguration configuration = new ExpeditionConfiguration();
		configuration.setIternationalShipping(true);
		configuration.setShipToCountry(Collections.singletonList("CA"));
		ResponseEntity<Void> response = testRestTemplate.postForEntity(
				"/api/v1/private/shipping/expedition",
				new HttpEntity<ExpeditionConfiguration>(configuration, getHeader()),
				Void.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	private ReadableShoppingCart createCart() {
		ReadableProduct product = sampleShippableProduct("droolsShippingCart");
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> response = testRestTemplate.postForEntity(
				"/api/v1/cart",
				new HttpEntity<PersistableShoppingCartItem>(item, getHeader()),
				ReadableShoppingCart.class);
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody();
	}

	private ReadableProduct sampleShippableProduct(String code) {
		PersistableCategory newCategory = category(code);
		ResponseEntity<PersistableCategory> categoryResponse = testRestTemplate.postForEntity(
				"/api/v1/private/category?store=DEFAULT",
				new HttpEntity<PersistableCategory>(newCategory, getHeader()),
				PersistableCategory.class);
		assertEquals(HttpStatus.CREATED, categoryResponse.getStatusCode());
		assertNotNull(categoryResponse.getBody());

		PersistableProduct product = product(code);
		product.setCategories(Collections.<Category>singletonList(categoryResponse.getBody()));
		ProductSpecification specifications = new ProductSpecification();
		specifications.setManufacturer(
				com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer.DEFAULT_MANUFACTURER);
		product.setProductSpecifications(specifications);
		product.setAvailable(true);
		product.setProductShipeable(true);
		product.setPrice(BigDecimal.TEN);
		product.setSku(code);
		product.setQuantity(100);
		ResponseEntity<Entity> productResponse = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=DEFAULT",
				new HttpEntity<PersistableProduct>(product, getHeader()),
				Entity.class);
		assertEquals(HttpStatus.CREATED, productResponse.getStatusCode());

		ResponseEntity<ReadableProduct> readableProduct = testRestTemplate.getForEntity(
				"/api/v2/product/" + code, ReadableProduct.class);
		assertEquals(HttpStatus.OK, readableProduct.getStatusCode());
		assertNotNull(readableProduct.getBody());
		return readableProduct.getBody();
	}

	private PackageDetails packageDetails(double weight, double length, double height, double width) {
		PackageDetails details = new PackageDetails();
		details.setShippingWeight(weight);
		details.setShippingLength(length);
		details.setShippingHeight(height);
		details.setShippingWidth(width);
		return details;
	}

	private Delivery delivery(String postalCode) {
		Delivery delivery = new Delivery();
		delivery.setPostalCode(postalCode);
		delivery.setState("QC");
		Country country = new Country();
		country.setIsoCode("CA");
		delivery.setCountry(country);
		return delivery;
	}
}
