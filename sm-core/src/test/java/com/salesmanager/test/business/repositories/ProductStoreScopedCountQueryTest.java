package com.salesmanager.test.business.repositories;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.salesmanager.core.business.repositories.catalog.product.ProductRepositoryImpl;
import com.salesmanager.core.business.repositories.customer.CustomerRepositoryImpl;
import com.salesmanager.core.business.repositories.order.OrderRepositoryImpl;
import com.salesmanager.core.model.catalog.product.ProductCriteria;
import com.salesmanager.core.model.customer.CustomerCriteria;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.OrderCriteria;

public class ProductStoreScopedCountQueryTest {

	private EntityManager em;
	private Query query;
	private MerchantStore store;

	@Before
	public void setUp() {
		em = mock(EntityManager.class);
		query = mock(Query.class);
		when(em.createQuery(anyString())).thenReturn(query);
		when(query.getSingleResult()).thenReturn(0L);

		store = new MerchantStore();
		store.setId(1);
		store.setCode("ACME");
	}

	@Test
	public void unavailableProductCountKeepsAvailabilityOrInsideStorePredicate() throws Exception {
		ProductCriteria criteria = new ProductCriteria();
		criteria.setAvailable(false);
		criteria.setOrigin(ProductCriteria.ORIGIN_ADMIN);

		ProductRepositoryImpl repository = new ProductRepositoryImpl();
		inject(repository);
		repository.listByStore(store, null, criteria);

		String countQuery = capturedQueries().stream()
				.filter(jpql -> jpql.startsWith("select count"))
				.findFirst()
				.orElseThrow(AssertionError::new);
		assertStoreScopedWhere(countQuery, "p.merchantStore.id=:mId");
		assertFalse(countQuery, hasTopLevelOr(whereClause(countQuery)));
	}

	@Test
	public void availableProductCountHasNoTopLevelOr() throws Exception {
		ProductCriteria criteria = new ProductCriteria();
		criteria.setAvailable(true);
		criteria.setOrigin(ProductCriteria.ORIGIN_ADMIN);

		ProductRepositoryImpl repository = new ProductRepositoryImpl();
		inject(repository);
		repository.listByStore(store, null, criteria);

		String countQuery = capturedQueries().stream()
				.filter(jpql -> jpql.startsWith("select count"))
				.findFirst()
				.orElseThrow(AssertionError::new);
		assertStoreScopedWhere(countQuery, "p.merchantStore.id=:mId");
		assertFalse(countQuery, hasTopLevelOr(whereClause(countQuery)));
	}

	@Test
	public void customerFirstNameFilterUsesBillingPath() throws Exception {
		CustomerCriteria criteria = new CustomerCriteria();
		criteria.setFirstName("Ada");

		CustomerRepositoryImpl repository = new CustomerRepositoryImpl();
		inject(repository);
		repository.listByStore(store, criteria);

		for (String jpql : capturedQueries()) {
			assertFalse(jpql, jpql.contains(".."));
			assertTrue(jpql, jpql.contains("c.billing.firstName like:fn"));
			assertStoreScopedWhere(jpql, "c.merchantStore.id=:mId");
		}
	}

	@Test
	public void customerAdditionalFiltersRemainAnded() throws Exception {
		CustomerCriteria criteria = new CustomerCriteria();
		criteria.setFirstName("Ada");
		criteria.setLastName("Lovelace");
		criteria.setEmail("ada@example.test");
		criteria.setCountry("US");

		CustomerRepositoryImpl repository = new CustomerRepositoryImpl();
		inject(repository);
		repository.listByStore(store, criteria);

		for (String jpql : capturedQueries()) {
			assertTrue(jpql, jpql.contains("c.billing.lastName like:ln"));
			assertTrue(jpql, jpql.contains("c.emailAddress like:email"));
			assertTrue(jpql, jpql.contains("c.billing.country.isoCode like:country"));
			assertFalse(jpql, hasTopLevelOr(whereClause(jpql)));
		}
	}

	@Test
	public void orderListByStoreNameFilterIsStoreScoped() throws Exception {
		OrderCriteria criteria = new OrderCriteria();
		criteria.setCustomerName("%");

		OrderRepositoryImpl repository = new OrderRepositoryImpl();
		inject(repository);
		repository.listByStore(store, criteria);

		assertStoreScopedQueries(capturedQueries(), "o.merchant.id=:mId");
	}

	@Test
	public void orderListOrdersNameFilterIsStoreScoped() throws Exception {
		OrderCriteria criteria = new OrderCriteria();
		criteria.setCustomerName("%");

		OrderRepositoryImpl repository = new OrderRepositoryImpl();
		inject(repository);
		repository.listOrders(store, criteria);

		assertStoreScopedQueries(capturedQueries(), "o.merchant.code=:mCode");
	}

	@Test
	public void orderListOrdersPhoneFilterIsStoreScoped() throws Exception {
		OrderCriteria criteria = new OrderCriteria();
		criteria.setCustomerPhone("%");

		OrderRepositoryImpl repository = new OrderRepositoryImpl();
		inject(repository);
		repository.listOrders(store, criteria);

		assertStoreScopedQueries(capturedQueries(), "o.merchant.code=:mCode");
	}

	private void inject(Object repository) throws Exception {
		Field field = repository.getClass().getDeclaredField("em");
		field.setAccessible(true);
		field.set(repository, em);
	}

	private List<String> capturedQueries() {
		ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
		verify(em, atLeastOnce()).createQuery(jpql.capture());
		return jpql.getAllValues();
	}

	private static void assertStoreScopedQueries(List<String> queries, String storePredicate) {
		for (String jpql : queries) {
			assertStoreScopedWhere(jpql, storePredicate);
			assertFalse(jpql, hasTopLevelOr(whereClause(jpql)));
		}
	}

	private static void assertStoreScopedWhere(String jpql, String storePredicate) {
		assertTrue(jpql, whereClause(jpql).trim().startsWith(storePredicate));
	}

	private static String whereClause(String jpql) {
		String where = jpql.substring(jpql.indexOf(" where ") + " where ".length());
		int orderBy = where.indexOf(" order by ");
		return orderBy >= 0 ? where.substring(0, orderBy) : where;
	}

	private static boolean hasTopLevelOr(String where) {
		int depth = 0;
		String lower = where.toLowerCase();
		for (int i = 0; i < lower.length(); i++) {
			char character = lower.charAt(i);
			if (character == '(') {
				depth++;
			} else if (character == ')') {
				depth--;
			} else if (depth == 0 && lower.startsWith(" or ", i)) {
				return true;
			}
		}
		return false;
	}
}
