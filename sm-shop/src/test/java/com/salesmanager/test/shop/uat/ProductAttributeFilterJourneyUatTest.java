package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.math.BigDecimal;
import java.util.UUID;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.util.UriComponentsBuilder;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.catalog.product.attribute.ProductAttributeService;
import com.salesmanager.core.business.services.catalog.product.attribute.ProductOptionService;
import com.salesmanager.core.business.services.catalog.product.attribute.ProductOptionValueService;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.payments.PaymentService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.attribute.ProductAttribute;
import com.salesmanager.core.model.catalog.product.attribute.ProductOption;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionDescription;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionType;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionValue;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionValueDescription;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.catalog.product.ReadableProductList;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.order.v0.ReadableOrder;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class ProductAttributeFilterJourneyUatTest extends ServicesTestSupport {

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

	@Inject
	private PaymentService paymentService;

	@Test
	public void adminAuthenticationAndPublicStorefrontEndpointsWork() {
		ResponseEntity<AuthenticationResponse> login = restTemplate.postForEntity("/api/v1/private/login",
				new HttpEntity<>(new AuthenticationRequest("admin@shopizer.com", "password")),
				AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody().getToken());

		HttpHeaders adminHeaders = bearer(login.getBody().getToken());
		ResponseEntity<String> customers = restTemplate.exchange("/api/v1/private/customers?store=DEFAULT", GET,
				new HttpEntity<>(adminHeaders), String.class);
		assertEquals(OK, customers.getStatusCode());

		ResponseEntity<String> noToken = restTemplate.getForEntity("/api/v1/private/customers?store=DEFAULT",
				String.class);
		assertTrue(noToken.getStatusCode().is4xxClientError());
		HttpHeaders invalidHeaders = bearer("not-a-valid-token");
		ResponseEntity<String> invalidToken = restTemplate.exchange("/api/v1/private/customers?store=DEFAULT", GET,
				new HttpEntity<>(invalidHeaders), String.class);
		assertTrue(invalidToken.getStatusCode().isError());

		assertEquals(OK, restTemplate.getForEntity("/api/v1/store/DEFAULT", String.class).getStatusCode());
		assertEquals(OK, restTemplate.getForEntity("/api/v1/category?store=DEFAULT&lang=en", String.class)
				.getStatusCode());
		assertEquals(OK, restTemplate.getForEntity("/api/v2/products?store=DEFAULT&lang=en", String.class)
				.getStatusCode());
		assertEquals(OK, restTemplate.getForEntity("/actuator/health", String.class).getStatusCode());
		ResponseEntity<String> apiDocs = restTemplate.getForEntity("/v2/api-docs", String.class);
		assertEquals(OK, apiDocs.getStatusCode());
		assertTrue(apiDocs.getBody().contains("\"paths\""));
	}

	@Test
	public void servicesEndpointsRequireBasicAuthentication() {
		HttpHeaders validHeaders = new HttpHeaders();
		validHeaders.setBasicAuth("admin@shopizer.com", "password");
		validHeaders.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> valid = restTemplate.exchange("/services/private/system/optin",
				POST, new HttpEntity<>("{}", validHeaders), String.class);
		assertEquals(OK, valid.getStatusCode());

		HttpHeaders invalidHeaders = new HttpHeaders();
		invalidHeaders.setBasicAuth("invalid@example.com", "wrong");
		invalidHeaders.setContentType(MediaType.TEXT_PLAIN);
		ResponseEntity<String> invalid = restTemplate.exchange("/services/private/system/optin",
				POST, new HttpEntity<>("{}", invalidHeaders), String.class);
		assertTrue(invalid.getStatusCode().is4xxClientError());
	}

	@Test
	public void customerCanBrowseFilterCartAndReadBackAnOrder() throws Exception {
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		String email = "uat-" + suffix + "@example.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setPassword("clear123");
		customer.setRepeatPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(MerchantStore.DEFAULT_STORE);
		Address billing = new Address();
		billing.setFirstName("Journey");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);
		ResponseEntity<PersistableCustomer> registration = restTemplate.postForEntity("/api/v1/customer/register",
				new HttpEntity<>(customer), PersistableCustomer.class);
		assertEquals(OK, registration.getStatusCode());

		ResponseEntity<AuthenticationResponse> login = restTemplate.postForEntity("/api/v1/customer/login",
				new HttpEntity<>(new AuthenticationRequest(email, "clear123")), AuthenticationResponse.class);
		assertEquals(OK, login.getStatusCode());
		assertNotNull(login.getBody().getToken());
		HttpHeaders customerHeaders = bearer(login.getBody().getToken());

		assertEquals(OK, restTemplate.exchange("/api/v1/auth/customer/profile?store=DEFAULT", GET,
				new HttpEntity<>(customerHeaders), String.class).getStatusCode());
		assertEquals(OK, restTemplate.getForEntity("/api/v1/store/DEFAULT", String.class).getStatusCode());
		assertEquals(OK, restTemplate.getForEntity("/api/v1/category?store=DEFAULT&lang=en", String.class)
				.getStatusCode());

		ReadableProduct product = sampleProduct("uat" + suffix.toUpperCase());
		assertEquals(OK, restTemplate.getForEntity("/api/v2/products?store=DEFAULT&lang=en", String.class)
				.getStatusCode());
		assertEquals(OK, restTemplate.getForEntity("/api/v2/product/" + product.getSku(), String.class)
				.getStatusCode());

		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		ResponseEntity<ReadableShoppingCart> createdCart = restTemplate.postForEntity("/api/v1/cart",
				new HttpEntity<>(item, customerHeaders), ReadableShoppingCart.class);
		assertEquals(CREATED, createdCart.getStatusCode());
		assertNotNull(createdCart.getBody().getCode());

		item.setQuantity(2);
		ResponseEntity<ReadableShoppingCart> updatedCart = restTemplate.exchange(
				"/api/v1/cart/" + createdCart.getBody().getCode(), HttpMethod.PUT,
				new HttpEntity<>(item, customerHeaders), ReadableShoppingCart.class);
		assertEquals(CREATED, updatedCart.getStatusCode());
		ResponseEntity<ReadableShoppingCart> readCart = restTemplate.getForEntity(
				"/api/v1/cart/" + createdCart.getBody().getCode(), ReadableShoppingCart.class);
		assertEquals(OK, readCart.getStatusCode());
		assertEquals(2, readCart.getBody().getQuantity());

		PersistableOrder order = new PersistableOrder();
		order.setCurrency("USD");
		order.setCustomerAgreement(true);
		MerchantStore store = merchantStoreService.getByCode(MerchantStore.DEFAULT_STORE);
		IntegrationConfiguration paymentConfiguration = new IntegrationConfiguration();
		paymentConfiguration.setModuleCode("moneyorder");
		paymentConfiguration.setActive(true);
		paymentConfiguration.setDefaultSelected(true);
		paymentConfiguration.setEnvironment(IntegrationConfiguration.TEST_ENVIRONMENT);
		paymentConfiguration.getIntegrationKeys().put("address", "UAT payment address");
		paymentService.savePaymentModuleConfiguration(paymentConfiguration, store);
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType("MONEYORDER");
		payment.setTransactionType("CAPTURE");
		payment.setAmount(BigDecimal.valueOf(250).setScale(2).toString());
		order.setPayment(payment);
		ResponseEntity<ReadableOrderConfirmation> checkout = restTemplate.postForEntity(
				"/api/v1/auth/cart/" + createdCart.getBody().getCode() + "/checkout?store=DEFAULT&lang=en",
				new HttpEntity<>(order, customerHeaders), ReadableOrderConfirmation.class);
		assertEquals(OK, checkout.getStatusCode());
		assertNotNull(checkout.getBody().getId());

		ResponseEntity<ReadableOrder> orderReadback = restTemplate.exchange(
				"/api/v1/auth/orders/" + checkout.getBody().getId() + "?store=DEFAULT&lang=en",
				GET, new HttpEntity<>(customerHeaders), ReadableOrder.class);
		assertEquals(OK, orderReadback.getStatusCode());
		assertEquals(checkout.getBody().getId(), orderReadback.getBody().getId());
	}

	@Test
	public void storefrontFiltersProductsByAttributeCriteria() throws Exception {
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
		String sku = "ATTRCRITUAT" + suffix;
		String optionCode = "ATTRCRIT_UAT_COLOR_" + suffix;
		ReadableProduct readableProduct = sampleProduct(sku);
		MerchantStore store = merchantStoreService.getByCode(MerchantStore.DEFAULT_STORE);
		Language english = languageService.getByCode("en");

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

		ResponseEntity<ReadableProductList> matching = queryAttribute(optionCode, "Red");
		assertEquals(OK, matching.getStatusCode());
		assertTrue(matching.getBody().getProducts().stream()
				.map(ReadableProduct::getSku).anyMatch(sku::equals));

		ResponseEntity<ReadableProductList> hostile = queryAttribute(optionCode + " or 1=1", "Red");
		assertEquals(OK, hostile.getStatusCode());
		assertTrue(hostile.getBody().getProducts().isEmpty());

		ResponseEntity<ReadableProductList> twoCriteria = queryAttribute(optionCode, "Red", optionCode, "Red");
		assertEquals(OK, twoCriteria.getStatusCode());
		assertTrue(twoCriteria.getBody().getProducts().stream()
				.map(ReadableProduct::getSku).anyMatch(sku::equals));
	}

	private ResponseEntity<ReadableProductList> queryAttribute(String... pairs) {
		UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/api/v2/products")
				.queryParam("store", MerchantStore.DEFAULT_STORE)
				.queryParam("lang", "en");
		for (int i = 0; i < pairs.length; i += 2) {
			int index = i / 2;
			builder.queryParam("attributeCriteria[" + index + "].attributeCode", pairs[i]);
			builder.queryParam("attributeCriteria[" + index + "].attributeValue", pairs[i + 1]);
		}
		return restTemplate.exchange(builder.build().encode().toUri(), GET, HttpEntity.EMPTY,
				ReadableProductList.class);
	}

	private static HttpHeaders bearer(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return headers;
	}
}
