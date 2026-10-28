package com.salesmanager.test.shop.integration.order;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.OK;

import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

import javax.inject.Inject;
import javax.persistence.EntityManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.boot.test.web.client.TestRestTemplate;

import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.order.OrderService;
import com.salesmanager.core.business.services.reference.country.CountryService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.ProductCriteria;
import com.salesmanager.core.model.catalog.product.ProductList;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.catalog.product.description.ProductDescription;
import com.salesmanager.core.model.common.Billing;
import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.customer.CustomerCriteria;
import com.salesmanager.core.model.customer.CustomerGender;
import com.salesmanager.core.model.customer.CustomerList;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.order.OrderCriteria;
import com.salesmanager.core.model.order.OrderList;
import com.salesmanager.core.model.order.orderstatus.OrderStatus;
import com.salesmanager.core.model.reference.country.Country;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.references.PersistableAddress;
import com.salesmanager.shop.model.store.PersistableMerchantStore;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@SpringBootTest(classes = ShopApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public class StoreScopedSearchIntegrationTest extends ServicesTestSupport {

	@Inject
	private TestRestTemplate restTemplate;

	@Inject
	private CustomerService customerService;

	@Inject
	private MerchantStoreService merchantStoreService;

	@Inject
	private OrderService orderService;

	@Inject
	private ProductService productService;

	@Inject
	private CountryService countryService;

	@Inject
	private LanguageService languageService;

	@Inject
	private EntityManager entityManager;

	private String suffix;
	private String otherStoreCode;
	private MerchantStore defaultStore;
	private MerchantStore otherStore;
	private Customer defaultCustomer;
	private Customer otherCustomer;
	private Order defaultOrder;
	private Order otherOrder;
	private Country country;
	private Language language;

	@Before
	public void seedOrdersAndCustomers() throws Exception {
		suffix = UUID.randomUUID().toString().replace("-", "");
		otherStoreCode = "scope" + suffix.substring(0, 12);
		defaultStore = merchantStoreService.getByCode(MerchantStore.DEFAULT_STORE);
		language = languageService.getByCode("en");
		country = countryService.getByCode("US");

		createOtherStore();
		otherStore = merchantStoreService.getByCode(otherStoreCode);
		assertNotNull(otherStore);

		defaultCustomer = createCustomer(defaultStore, "default", "Default" + suffix, "Store" + suffix);
		otherCustomer = createCustomer(otherStore, "other", "Other" + suffix, "Tenant" + suffix);
		defaultOrder = createOrder(defaultStore, defaultCustomer, "Default" + suffix, "Store" + suffix);
		otherOrder = createOrder(otherStore, otherCustomer, "Other" + suffix, "Tenant" + suffix);
	}

	@Test
	public void orderServiceNameSearchReturnsOnlyDefaultStoreOrders() {
		OrderCriteria criteria = new OrderCriteria();
		criteria.setCustomerName("%");
		criteria.setMaxCount(10000);

		OrderList orders = orderService.listByStore(defaultStore, criteria);

		assertNotNull(orders.getOrders());
		assertTrue(orders.getOrders().stream().anyMatch(order -> order.getId().equals(defaultOrder.getId())));
		assertFalse(orders.getOrders().stream().anyMatch(order -> order.getId().equals(otherOrder.getId())));
		assertEquals(orders.getTotalCount(), orders.getOrders().size());

		criteria.setPageSize(10000);
		criteria.setLegacyPagination(false);
		OrderList restSearchOrders = orderService.getOrders(criteria, defaultStore);
		assertNotNull(restSearchOrders.getOrders());
		assertTrue(restSearchOrders.getOrders().stream()
				.anyMatch(order -> order.getId().equals(defaultOrder.getId())));
		assertFalse(restSearchOrders.getOrders().stream()
				.anyMatch(order -> order.getId().equals(otherOrder.getId())));
	}

	@Test
	public void customerNameAndFirstNameFiltersStayWithinDefaultStore() {
		CustomerCriteria nameCriteria = new CustomerCriteria();
		nameCriteria.setName("%");
		nameCriteria.setMaxCount(10000);
		CustomerList customers = customerService.getListByStore(defaultStore, nameCriteria);

		assertNotNull(customers.getCustomers());
		assertTrue(customers.getCustomers().stream()
				.anyMatch(customer -> customer.getId().equals(defaultCustomer.getId())));
		assertFalse(customers.getCustomers().stream()
				.anyMatch(customer -> customer.getId().equals(otherCustomer.getId())));
		assertEquals(customers.getTotalCount(), customers.getCustomers().size());

		CustomerCriteria firstNameCriteria = new CustomerCriteria();
		firstNameCriteria.setFirstName(defaultCustomer.getBilling().getFirstName());
		CustomerList firstNameMatches = customerService.getListByStore(defaultStore, firstNameCriteria);

		assertTrue(firstNameMatches.getCustomers().stream()
				.anyMatch(customer -> customer.getId().equals(defaultCustomer.getId())));
		assertFalse(firstNameMatches.getCustomers().stream()
				.anyMatch(customer -> customer.getId().equals(otherCustomer.getId())));
	}

	@Test
	public void unavailableProductCountDoesNotIncludeOtherStores() throws Exception {
		Product otherStoreProduct = createUnavailableProductInOtherStore();

		ProductCriteria criteria = new ProductCriteria();
		criteria.setAvailable(false);
		criteria.setOrigin(ProductCriteria.ORIGIN_ADMIN);
		criteria.setLanguage(language.getCode());
		criteria.setMaxCount(10000);
		ProductList result = productService.listByStore(defaultStore, language, criteria);

		Long expectedCount = entityManager.createQuery(
				"select count(distinct p) from Product p join p.descriptions pd "
						+ "where p.merchantStore.id=:storeId and "
						+ "(p.available=false or p.dateAvailable>:date) and pd.language.code=:language",
				Long.class)
				.setParameter("storeId", defaultStore.getId())
				.setParameter("date", new Date())
				.setParameter("language", language.getCode())
				.getSingleResult();

		assertEquals(expectedCount.longValue(), result.getTotalCount());
		assertFalse(result.getProducts().stream()
				.anyMatch(product -> product.getId().equals(otherStoreProduct.getId())));
	}

	private void createOtherStore() {
		PersistableAddress address = new PersistableAddress();
		address.setAddress("123 Tenant Street");
		address.setPostalCode("12345");
		address.setCountry(country.getIsoCode());
		address.setCity("Test City");
		address.setStateProvince("CA");

		PersistableMerchantStore store = new PersistableMerchantStore();
		store.setCode(otherStoreCode);
		store.setCurrency(defaultStore.getCurrency().getCode());
		store.setDefaultLanguage(language.getCode());
		store.setEmail("scope-" + suffix + "@example.test");
		store.setName(otherStoreCode);
		store.setPhone("555-" + suffix.substring(0, 8));
		store.setSupportedLanguages(Arrays.asList(language.getCode()));
		store.setAddress(address);

		ResponseEntity<Void> response = restTemplate.exchange("/api/v1/private/store/", POST,
				new HttpEntity<>(store, getHeader()), Void.class);
		assertEquals(OK, response.getStatusCode());
	}

	private Customer createCustomer(MerchantStore store, String prefix, String firstName, String lastName)
			throws Exception {
		Customer customer = new Customer();
		customer.setMerchantStore(store);
		customer.setDefaultLanguage(language);
		String customerCode = prefix + suffix.substring(0, 8);
		customer.setEmailAddress(customerCode + "@x.io");
		customer.setNick(customerCode);
		customer.setPassword("password");
		customer.setGender(CustomerGender.M);
		customer.setBilling(address(firstName, lastName, "555-" + suffix.substring(0, 8)));
		customer.setDelivery(delivery(firstName, lastName, "555-" + suffix.substring(0, 8)));
		customerService.create(customer);
		return customer;
	}

	private Order createOrder(MerchantStore store, Customer customer, String firstName, String lastName)
			throws Exception {
		Order order = new Order();
		order.setMerchant(store);
		order.setCustomerId(customer.getId());
		order.setCustomerEmailAddress(customer.getEmailAddress());
		order.setStatus(OrderStatus.ORDERED);
		order.setDatePurchased(new Date());
		order.setLastModified(new Date());
		order.setCurrency(store.getCurrency());
		order.setBilling(address(firstName, lastName, "555-" + suffix.substring(0, 8)));
		order.setDelivery(delivery(firstName, lastName, "555-" + suffix.substring(0, 8)));
		orderService.create(order);
		return order;
	}

	private Billing address(String firstName, String lastName, String phone) {
		Billing billing = new Billing();
		billing.setFirstName(firstName);
		billing.setLastName(lastName);
		billing.setTelephone(phone);
		billing.setCountry(country);
		return billing;
	}

	private Delivery delivery(String firstName, String lastName, String phone) {
		Delivery delivery = new Delivery();
		delivery.setFirstName(firstName);
		delivery.setLastName(lastName);
		delivery.setTelephone(phone);
		delivery.setCountry(country);
		return delivery;
	}

	private Product createUnavailableProductInOtherStore() throws Exception {
		Product product = new Product();
		product.setMerchantStore(otherStore);
		product.setSku("leak" + suffix);
		product.setAvailable(false);
		product.setDateAvailable(new Date(System.currentTimeMillis() + 86400000));

		ProductDescription description = new ProductDescription();
		description.setName("Unavailable " + suffix);
		description.setLanguage(language);
		description.setProduct(product);
		product.getDescriptions().add(description);

		ProductAvailability availability = new ProductAvailability();
		availability.setProduct(product);
		availability.setMerchantStore(otherStore);
		availability.setProductDateAvailable(new Date());
		availability.setProductQuantity(1);
		product.getAvailabilities().add(availability);

		productService.create(product);
		return product;
	}

}
