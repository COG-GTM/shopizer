package com.salesmanager.test.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Date;
import java.util.UUID;

import org.junit.Test;

import com.salesmanager.core.model.catalog.catalog.Catalog;
import com.salesmanager.core.model.catalog.catalog.CatalogCategoryEntry;
import com.salesmanager.core.model.catalog.category.Category;
import com.salesmanager.core.model.catalog.category.CategoryDescription;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.catalog.product.description.ProductDescription;
import com.salesmanager.core.model.common.Billing;
import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.reference.country.Country;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.reference.zone.Zone;
import com.salesmanager.test.common.AbstractSalesManagerCoreTestCase;

public class NewEntityIdSemanticsTest extends AbstractSalesManagerCoreTestCase {

	@Test
	public void productWithZeroIdIsCreatedAndFoundBySku() throws Exception {
		MerchantStore store = merchantService.getByCode(MerchantStore.DEFAULT_STORE);
		Language language = languageService.getByCode("en");
		String sku = "ab541-" + UUID.randomUUID();
		long count = productService.count();

		Product product = new Product();
		product.setId(0L);
		product.setMerchantStore(store);
		product.setSku(sku);

		ProductDescription description = new ProductDescription();
		description.setProduct(product);
		description.setLanguage(language);
		description.setName(sku);
		product.getDescriptions().add(description);

		ProductAvailability availability = new ProductAvailability();
		availability.setProduct(product);
		availability.setProductQuantity(1);
		availability.setProductDateAvailable(new Date());
		product.getAvailabilities().add(availability);

		productService.create(product);

		assertCreatedId(product.getId());
		assertEquals(count + 1, productService.count().longValue());
		assertNotNull(productService.getById(product.getId()));
		assertEquals(product.getId(), productService.getBySku(sku, store).getId());
		assertEquals(product.getId(), productService.getBySku(sku, store, language).getId());
	}

	@Test
	public void customerWithZeroIdIsCreated() throws Exception {
		MerchantStore store = merchantService.getByCode(MerchantStore.DEFAULT_STORE);
		Country country = countryService.getByCode("US");
		Zone zone = zoneService.getByCode("VT");
		long count = customerService.count();

		Customer customer = new Customer();
		customer.setId(0L);
		customer.setMerchantStore(store);
		customer.setDefaultLanguage(store.getDefaultLanguage());
		customer.setEmailAddress("ab541-" + UUID.randomUUID() + "@example.com");
		customer.setNick("ab541-" + UUID.randomUUID());
		customer.setPassword("password");
		customer.setAnonymous(true);

		Billing billing = new Billing();
		billing.setFirstName("AB");
		billing.setLastName("541");
		billing.setCountry(country);
		billing.setZone(zone);
		customer.setBilling(billing);

		Delivery delivery = new Delivery();
		delivery.setCountry(country);
		delivery.setZone(zone);
		customer.setDelivery(delivery);

		customerService.create(customer);

		assertCreatedId(customer.getId());
		assertEquals(count + 1, customerService.count().longValue());
		assertNotNull(customerService.getById(customer.getId()));
	}

	@Test
	public void orderWithZeroIdIsCreated() throws Exception {
		MerchantStore store = merchantService.getByCode(MerchantStore.DEFAULT_STORE);
		Country country = countryService.getByCode("US");
		Zone zone = zoneService.getByCode("VT");
		long count = orderService.count();

		Order order = new Order();
		order.setId(0L);
		order.setMerchant(store);
		order.setCustomerEmailAddress(UUID.randomUUID() + "@x.io");
		order.setDatePurchased(new Date());
		Billing billing = new Billing();
		billing.setFirstName("AB");
		billing.setLastName("541");
		billing.setCountry(country);
		billing.setZone(zone);
		order.setBilling(billing);

		Delivery delivery = new Delivery();
		delivery.setCountry(country);
		delivery.setZone(zone);
		order.setDelivery(delivery);

		orderService.saveOrUpdate(order);

		assertCreatedId(order.getId());
		assertEquals(count + 1, orderService.count().longValue());
		assertNotNull(orderService.getById(order.getId()));
	}

	@Test
	public void categoryWithZeroIdUsesGeneratedIdInLineage() throws Exception {
		MerchantStore store = merchantService.getByCode(MerchantStore.DEFAULT_STORE);
		Language language = languageService.getByCode("en");
		long count = categoryService.count();

		Category category = new Category();
		category.setId(0L);
		category.setMerchantStore(store);
		category.setCode("ab541-" + UUID.randomUUID());
		CategoryDescription description = new CategoryDescription();
		description.setCategory(category);
		description.setLanguage(language);
		description.setName(category.getCode());
		category.getDescriptions().add(description);
		categoryService.create(category);

		assertCreatedId(category.getId());
		assertEquals(count + 1, categoryService.count().longValue());
		assertNotNull(categoryService.getById(category.getId(), store.getId()));
		assertEquals("/" + category.getId() + "/", category.getLineage());
	}

	@Test
	public void catalogWithZeroIdIsCreated() throws Exception {
		MerchantStore store = merchantService.getByCode(MerchantStore.DEFAULT_STORE);
		long count = catalogService.count();

		Catalog catalog = new Catalog(store);
		catalog.setId(0L);
		catalog.setCode("ab541-" + UUID.randomUUID());
		catalogService.saveOrUpdate(catalog, store);

		assertCreatedId(catalog.getId());
		assertEquals(count + 1, catalogService.count().longValue());
		assertTrue(catalogService.getById(catalog.getId(), store).isPresent());
	}

	@Test
	public void catalogEntryWithZeroIdIsCreated() throws Exception {
		MerchantStore store = merchantService.getByCode(MerchantStore.DEFAULT_STORE);
		Language language = languageService.getByCode("en");
		String code = "ab541-" + UUID.randomUUID();

		Product product = new Product();
		product.setMerchantStore(store);
		product.setSku(code);
		ProductDescription productDescription = new ProductDescription();
		productDescription.setProduct(product);
		productDescription.setLanguage(language);
		productDescription.setName(code);
		product.getDescriptions().add(productDescription);
		ProductAvailability availability = new ProductAvailability();
		availability.setProduct(product);
		availability.setProductQuantity(1);
		availability.setProductDateAvailable(new Date());
		product.getAvailabilities().add(availability);
		productService.create(product);

		Category category = new Category();
		category.setMerchantStore(store);
		category.setCode(code);
		CategoryDescription categoryDescription = new CategoryDescription();
		categoryDescription.setCategory(category);
		categoryDescription.setLanguage(language);
		categoryDescription.setName(code);
		category.getDescriptions().add(categoryDescription);
		categoryService.create(category);

		Catalog catalog = new Catalog(store);
		catalog.setCode(code);
		catalogService.saveOrUpdate(catalog, store);

		long count = catalogEntryService.count();
		CatalogCategoryEntry entry = new CatalogCategoryEntry();
		entry.setId(0L);
		entry.setCategory(category);
		catalogEntryService.add(entry, catalog);

		assertCreatedId(entry.getId());
		assertEquals(count + 1, catalogEntryService.count().longValue());
		assertNotNull(catalogEntryService.getById(entry.getId()));
	}

	private void assertCreatedId(Long id) {
		assertNotNull(id);
		assertTrue(id != 0L);
	}
}
