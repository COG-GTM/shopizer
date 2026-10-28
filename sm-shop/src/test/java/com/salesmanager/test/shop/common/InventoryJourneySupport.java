package com.salesmanager.test.shop.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.order.OrderService;
import com.salesmanager.core.business.services.payments.PaymentService;
import com.salesmanager.core.business.services.shoppingcart.ShoppingCartService;
import com.salesmanager.core.business.services.system.EmailService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.OrderSummary;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.store.controller.customer.facade.CustomerFacade;
import com.salesmanager.shop.utils.EmailTemplatesUtils;

public class InventoryJourneySupport extends ServicesTestSupport {

	@Autowired private EmailService emailService;
	@Autowired private CustomerFacade customerFacade;
	@Autowired private EmailTemplatesUtils emailTemplatesUtils;
	@Autowired protected ObjectMapper mapper;
	@Autowired protected JdbcTemplate jdbc;
	@Autowired protected MerchantStoreService merchantStoreService;
	@Autowired protected CustomerService customerService;
	@Autowired protected ShoppingCartService shoppingCartService;
	@Autowired protected OrderService orderService;
	@Autowired protected PaymentService paymentService;

	@BeforeEach
	void suppressExternalEmail() {
		EmailService stub = mock(EmailService.class);
		ReflectionTestUtils.setField(customerFacade, "emailService", stub);
		ReflectionTestUtils.setField(emailTemplatesUtils, "emailService", stub);
	}

	@AfterEach
	void restoreEmailService() {
		ReflectionTestUtils.setField(customerFacade, "emailService", emailService);
		ReflectionTestUtils.setField(emailTemplatesUtils, "emailService", emailService);
	}

	protected String unique(String prefix) {
		return prefix + UUID.randomUUID().toString().replace("-", "");
	}

	protected JsonNode json(ResponseEntity<String> response, int expected) throws Exception {
		assertEquals(expected, response.getStatusCodeValue(), response.getBody());
		assertNotNull(response.getBody());
		return mapper.readTree(response.getBody());
	}

	protected ResponseEntity<String> request(String path, HttpMethod method, Object body, HttpHeaders headers) {
		return testRestTemplate.exchange(path, method, new HttpEntity<>(body, headers), String.class);
	}

	protected HttpHeaders customerHeaders() throws Exception {
		String email = unique("buyer") + "@example.com";
		ObjectNode customer = mapper.createObjectNode();
		customer.put("emailAddress", email).put("password", "Password1").put("repeatPassword", "Password1")
				.put("firstName", "Test").put("lastName", "Buyer").put("language", "en");
		ObjectNode billing = customer.putObject("billing");
		billing.put("firstName", "Test").put("lastName", "Buyer").put("country", "CA").put("zone", "QC")
				.put("address", "1 Test Street").put("city", "Montreal").put("postalCode", "H2H2H2");
		customer.set("delivery", billing.deepCopy());
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		JsonNode registered = json(request("/api/v1/customer/register", HttpMethod.POST, customer, headers), 200);
		assertNotNull(registered.get("token"));
		ObjectNode credentials = mapper.createObjectNode().put("username", email).put("password", "Password1");
		JsonNode login = json(request("/api/v1/customer/login", HttpMethod.POST, credentials, headers), 200);
		headers.setBearerAuth(login.get("token").asText());
		json(request("/api/v1/auth/customer/profile", HttpMethod.GET, null, headers), 200);
		return headers;
	}

	protected ReadableProduct stockedProduct(String prefix, int quantity) {
		ReadableProduct product = sampleProduct(unique(prefix));
		jdbc.update("update SALESMANAGER.PRODUCT_AVAILABILITY set QUANTITY=? where PRODUCT_ID=?", quantity, product.getId());
		assertEquals(quantity, stock(product.getId()));
		return product;
	}

	protected int stock(Long productId) {
		return jdbc.queryForObject("select QUANTITY from SALESMANAGER.PRODUCT_AVAILABILITY where PRODUCT_ID=? and PRODUCT_VARIANT is null", Integer.class, productId);
	}

	protected JsonNode cart(String sku, int quantity, HttpHeaders headers) throws Exception {
		return json(request("/api/v1/cart", HttpMethod.POST,
				mapper.createObjectNode().put("product", sku).put("quantity", quantity), headers), 201);
	}

	protected ResponseEntity<String> checkout(String code, HttpHeaders headers) throws Exception {
		MerchantStore store = merchantStoreService.getByCode("DEFAULT");
		IntegrationConfiguration configuration = new IntegrationConfiguration();
		configuration.setModuleCode("moneyorder");
		configuration.setActive(true);
		configuration.getIntegrationKeys().put("address", "1 Test Street, Montreal");
		paymentService.savePaymentModuleConfiguration(configuration, store);
		JsonNode profile = json(request("/api/v1/auth/customer/profile", HttpMethod.GET, null, headers), 200);
		Customer customer = customerService.getById(profile.get("id").asLong());
		ShoppingCart cart = shoppingCartService.getByCode(code, store);
		OrderSummary summary = new OrderSummary();
		summary.setProducts(new ArrayList<>(cart.getLineItems()));
		String amount = orderService.caculateOrderTotal(summary, customer, store, store.getDefaultLanguage()).getTotal().toPlainString();
		ObjectNode order = mapper.createObjectNode().put("currency", store.getCurrency().getCode()).put("customerAgreement", true);
		order.putObject("payment").put("paymentModule", "moneyorder").put("paymentType", "MONEYORDER")
				.put("transactionType", "AUTHORIZECAPTURE").put("amount", amount);
		return request("/api/v1/auth/cart/" + code + "/checkout", HttpMethod.POST, order, headers);
	}
}
