package com.salesmanager.test.shop.integration.order;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.OK;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.exception.ConversionException;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.order.v1.PersistableAnonymousOrder;
import com.salesmanager.shop.model.order.v1.PersistableOrder;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.shop.populator.order.PersistableOrderApiPopulator;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class AnonymousCheckoutCustomerScopeIntegrationTest extends ServicesTestSupport {

	@Autowired
	private TestRestTemplate rest;
	@Autowired
	private PersistableOrderApiPopulator orderPopulator;
	@Autowired
	private CustomerService customerService;
	@Autowired
	private MerchantStoreService merchantStoreService;
	@Autowired
	private LanguageService languageService;
	@PersistenceContext
	private EntityManager entityManager;

	@Test
	@Transactional
	public void anonymousOrderUsesSubmittedCustomerAndIgnoresTopLevelCustomerId() throws Exception {
		Customer victim = registerCustomer("victim", Constants.DEFAULT_STORE);
		String victimEmail = victim.getEmailAddress();
		PersistableAnonymousOrder request = anonymousOrder(victim.getId());

		Order result = orderPopulator.populate(request, new Order(), defaultStore(), english());

		assertNull(result.getCustomerId());
		assertEquals("attacker@example.com", result.getCustomerEmailAddress());
		assertEquals("Attacker", result.getBilling().getLastName());
		assertEquals("Attacker", result.getDelivery().getLastName());

		entityManager.clear();
		Customer reloadedVictim = customerService.getById(victim.getId());
		assertNotNull(reloadedVictim);
		assertEquals(victimEmail, reloadedVictim.getEmailAddress());
		assertEquals("Victim", reloadedVictim.getBilling().getLastName());
	}

	@Test(expected = ConversionException.class)
	@Transactional
	public void authenticatedOrderRejectsCustomerFromDifferentStore() throws Exception {
		String otherStoreCode = createStore();
		Customer otherStoreCustomer = registerCustomer("foreign", otherStoreCode);
		assertEquals(otherStoreCode, otherStoreCustomer.getMerchantStore().getCode());
		PersistableOrder request = authenticatedOrder(otherStoreCustomer.getId());

		orderPopulator.populate(request, new Order(), defaultStore(), english());
	}

	@Test
	@Transactional
	public void authenticatedOrderAcceptsCustomerFromSameStore() throws Exception {
		Customer customer = registerCustomer("same", Constants.DEFAULT_STORE);
		PersistableOrder request = authenticatedOrder(customer.getId());

		Order result = orderPopulator.populate(request, new Order(), defaultStore(), english());

		assertEquals(customer.getId(), result.getCustomerId());
		assertEquals(customer.getEmailAddress(), result.getCustomerEmailAddress());
	}

	private Customer registerCustomer(String label, String storeCode) {
		String email = "scope-" + label + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress(email);
		customer.setUserName(email);
		customer.setPassword("clear123");
		customer.setGender(CustomerGender.M.name());
		customer.setLanguage("en");
		customer.setStoreCode(storeCode);
		customer.setBilling(address(label.equals("victim") ? "Victim" : "Customer"));
		customer.setDelivery(address(label.equals("victim") ? "Victim" : "Customer"));
		ResponseEntity<String> response = rest.postForEntity("/api/v1/customer/register?store=" + storeCode,
				new HttpEntity<>(customer, jsonHeaders()), String.class);
		assertTrue(response.getStatusCode().is2xxSuccessful());
		Customer persistedCustomer = customerService.getByNick(email);
		assertNotNull(persistedCustomer);
		return persistedCustomer;
	}

	private String createStore() {
		String storeCode = "scope-" + UUID.randomUUID().toString().substring(0, 8);
		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(storeCode);
		store.setName(storeCode);
		store.setCurrency("CAD");
		store.setDefaultLanguage("en");
		store.setEmail(storeCode + "@example.com");
		store.setPhone("444-555-6666");
		store.setSupportedLanguages(Collections.singletonList("en"));

		PersistableAddress address = new PersistableAddress();
		address.setAddress("121212 simple address");
		address.setPostalCode("12345");
		address.setCountry("US");
		address.setCity("FT LD");
		address.setStateProvince("FL");
		store.setAddress(address);

		ResponseEntity<Void> response = rest.exchange("/api/v1/private/store/", POST,
				new HttpEntity<>(store, getHeader()), Void.class);
		assertEquals(OK, response.getStatusCode());
		return storeCode;
	}

	private PersistableAnonymousOrder anonymousOrder(Long customerId) {
		PersistableAnonymousOrder order = new PersistableAnonymousOrder();
		order.setCurrency("USD");
		order.setPayment(payment());
		order.setCustomerId(customerId);
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress("attacker@example.com");
		customer.setGender(CustomerGender.M.name());
		customer.setBilling(address("Attacker"));
		customer.setDelivery(address("Attacker"));
		order.setCustomer(customer);
		return order;
	}

	private PersistableOrder authenticatedOrder(Long customerId) {
		PersistableOrder order = new PersistableOrder();
		order.setCustomerId(customerId);
		order.setCurrency("USD");
		order.setPayment(payment());
		return order;
	}

	private PersistablePayment payment() {
		PersistablePayment payment = new PersistablePayment();
		payment.setPaymentModule("moneyorder");
		payment.setPaymentType("MONEYORDER");
		return payment;
	}

	private MerchantStore defaultStore() throws Exception {
		return merchantStoreService.getByCode(Constants.DEFAULT_STORE);
	}

	private Language english() throws Exception {
		return languageService.getByCode("en");
	}

	private Address address(String lastName) {
		Address address = new Address();
		address.setFirstName("Test");
		address.setLastName(lastName);
		address.setCountry("BE");
		return address;
	}

	private HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
		return headers;
	}
}
