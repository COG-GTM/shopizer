package com.salesmanager.test.shop.integration.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.core.business.repositories.customer.CustomerRepository;
import com.salesmanager.core.business.repositories.user.GroupRepository;
import com.salesmanager.core.business.repositories.user.UserRepository;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.user.Group;
import com.salesmanager.core.model.user.GroupType;
import com.salesmanager.core.model.user.User;
import com.salesmanager.shop.application.ShopApplication;
import com.salesmanager.shop.model.catalog.category.Category;
import com.salesmanager.shop.model.catalog.category.PersistableCategory;
import com.salesmanager.shop.model.catalog.product.product.PersistableProduct;
import com.salesmanager.shop.model.catalog.product.product.ProductSpecification;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.model.customer.address.Address;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class JoinTableMappingIntegrationTest extends ServicesTestSupport {

	@Inject
	private CustomerRepository customerRepository;

	@Inject
	private UserRepository userRepository;

	@Inject
	private GroupRepository groupRepository;

	@Inject
	private PlatformTransactionManager transactionManager;

	@Test
	public void productCategoryJoinCanBeCreatedAndDeletedThroughRest() {
		String suffix = Long.toString(System.nanoTime());
		PersistableCategory category = category("join-cat-" + suffix);
		ResponseEntity<PersistableCategory> categoryResponse = testRestTemplate.postForEntity(
				"/api/v1/private/category?store=DEFAULT", new HttpEntity<>(category, getHeader()),
				PersistableCategory.class);
		assertEquals(CREATED, categoryResponse.getStatusCode());
		assertNotNull(categoryResponse.getBody());

		String sku = "join-product-" + suffix;
		PersistableProduct product = product(sku);
		product.setSku(sku);
		product.setAvailable(true);
		product.setPrice(java.math.BigDecimal.TEN);
		product.setQuantity(5);
		product.setCategories(new ArrayList<Category>());
		ProductSpecification specification = new ProductSpecification();
		specification.setManufacturer(
				com.salesmanager.core.model.catalog.product.manufacturer.Manufacturer.DEFAULT_MANUFACTURER);
		product.setProductSpecifications(specification);
		ResponseEntity<Entity> productResponse = testRestTemplate.postForEntity(
				"/api/v1/private/product?store=DEFAULT", new HttpEntity<>(product, getHeader()), Entity.class);
		assertEquals(CREATED, productResponse.getStatusCode());
		assertNotNull(productResponse.getBody());

		String associationPath = "/api/v1/private/product/" + productResponse.getBody().getId()
				+ "/category/" + categoryResponse.getBody().getId() + "?store=DEFAULT";
		assertEquals(CREATED, testRestTemplate.exchange(associationPath, POST, new HttpEntity<>(getHeader()),
				String.class).getStatusCode());
		JsonNode attached = testRestTemplate.exchange("/api/v2/product/" + sku, GET,
				new HttpEntity<>(getHeader()), JsonNode.class).getBody();
		assertNotNull(attached);
		assertTrue(attached.path("categories").toString().contains(categoryResponse.getBody().getId().toString()));

		assertEquals(OK, testRestTemplate.exchange(associationPath, DELETE, new HttpEntity<>(getHeader()),
				String.class).getStatusCode());
		JsonNode detached = testRestTemplate.exchange("/api/v2/product/" + sku, GET,
				new HttpEntity<>(getHeader()), JsonNode.class).getBody();
		assertNotNull(detached);
		assertTrue(!detached.path("categories").toString().contains(categoryResponse.getBody().getId().toString()));
	}

	@Test
	public void customerAndAdminGroupMembershipChangesSurviveNewTransactions() {
		String suffix = Long.toString(System.nanoTime());
		PersistableCustomer customer = new PersistableCustomer();
		customer.setEmailAddress("join-customer-" + suffix + "@example.com");
		customer.setPassword("clear123");
		customer.setLanguage("en");
		customer.setStoreCode("DEFAULT");
		Address billing = new Address();
		billing.setFirstName("Join");
		billing.setLastName("Customer");
		billing.setCountry("BE");
		customer.setBilling(billing);
		ResponseEntity<ReadableCustomer> customerResponse = testRestTemplate.postForEntity(
				"/api/v1/private/customer?store=DEFAULT", new HttpEntity<>(customer, getHeader()),
				ReadableCustomer.class);
		assertEquals(OK, customerResponse.getStatusCode());
		assertNotNull(customerResponse.getBody());

		Group customerGroup = new Group("join-customer-" + suffix);
		customerGroup.setGroupType(GroupType.CUSTOMER);
		groupRepository.save(customerGroup);
		Group adminGroup = new Group("join-admin-" + suffix);
		adminGroup.setGroupType(GroupType.ADMIN);
		groupRepository.save(adminGroup);

		TransactionTemplate transactions = new TransactionTemplate(transactionManager);
		Long customerId = customerResponse.getBody().getId();
		List<Integer> originalAdminGroupIds = transactions.execute(status -> {
			User admin = userRepository.findByUserName("admin@shopizer.com");
			assertNotNull(admin);
			return admin.getGroups().stream().map(Group::getId).collect(Collectors.toList());
		});

		try {
			transactions.execute(status -> {
				Customer savedCustomer = customerRepository.findById(customerId).orElseThrow();
				savedCustomer.getGroups().add(customerGroup);
				customerRepository.save(savedCustomer);
				User admin = userRepository.findByUserName("admin@shopizer.com");
				admin.getGroups().add(adminGroup);
				userRepository.save(admin);
				return null;
			});

			Boolean customerHasGroup = transactions.execute(status -> customerRepository.findById(customerId)
					.orElseThrow().getGroups().stream().anyMatch(group -> group.getId().equals(customerGroup.getId())));
			Boolean adminHasGroup = transactions.execute(status -> userRepository.findByUserName("admin@shopizer.com")
					.getGroups().stream().anyMatch(group -> group.getId().equals(adminGroup.getId())));
			assertTrue(customerHasGroup);
			assertTrue(adminHasGroup);
		} finally {
			transactions.execute(status -> {
				Customer savedCustomer = customerRepository.findById(customerId).orElseThrow();
				savedCustomer.getGroups().removeIf(group -> group.getId().equals(customerGroup.getId()));
				customerRepository.save(savedCustomer);
				User admin = userRepository.findByUserName("admin@shopizer.com");
				admin.getGroups().removeIf(group -> group.getId().equals(adminGroup.getId()));
				admin.getGroups().clear();
				for (Integer groupId : originalAdminGroupIds) {
					groupRepository.findById(groupId).ifPresent(admin.getGroups()::add);
				}
				userRepository.save(admin);
				return null;
			});
			groupRepository.delete(customerGroup);
			groupRepository.delete(adminGroup);
		}

		Boolean customerGroupRemoved = transactions.execute(status -> customerRepository.findById(customerId)
				.orElseThrow().getGroups().stream().noneMatch(group -> group.getId().equals(customerGroup.getId())));
		assertTrue(customerGroupRemoved);
	}
}
