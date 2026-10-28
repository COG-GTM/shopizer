package com.salesmanager.test.business.repositories;

import static org.junit.Assert.assertEquals;
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

import com.salesmanager.core.business.repositories.customer.CustomerRepositoryImpl;
import com.salesmanager.core.business.repositories.order.OrderRepositoryImpl;
import com.salesmanager.core.model.customer.CustomerCriteria;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.OrderCriteria;

/**
 * Search filters must stay inside the store predicate: an unparenthesized
 * {@code or} appended after {@code where <store> and ...} escapes the tenant
 * boundary because JPQL binds {@code and} tighter than {@code or}.
 */
public class StoreScopedSearchQueryTest {

	private EntityManager em;
	private MerchantStore store;

	@Before
	public void setUp() {
		em = mock(EntityManager.class);
		Query query = mock(Query.class);
		when(em.createQuery(anyString())).thenReturn(query);
		when(query.getSingleResult()).thenReturn(0L);

		store = new MerchantStore();
		store.setId(1);
		store.setCode("ACME");
	}

	@Test
	public void listOrdersKeepsNameAndPhoneFiltersInsideStore() throws Exception {
		OrderCriteria criteria = new OrderCriteria();
		criteria.setCustomerName("%");
		criteria.setCustomerPhone("%");
		criteria.setEmail("a@b.c");
		criteria.setStatus("ORDERED");

		OrderRepositoryImpl repository = new OrderRepositoryImpl();
		inject(repository, em);
		repository.listOrders(store, criteria);

		assertStoreScoped(capturedQueries(), "o.merchant.code=:mCode");
	}

	@Test
	public void listByStoreKeepsNameFilterInsideStore() throws Exception {
		OrderCriteria criteria = new OrderCriteria();
		criteria.setCustomerName("%");
		criteria.setPaymentMethod("paypal");
		criteria.setCustomerId(5L);

		OrderRepositoryImpl repository = new OrderRepositoryImpl();
		inject(repository, em);
		repository.listByStore(store, criteria);

		assertStoreScoped(capturedQueries(), "o.merchant.id=:mId");
	}

	@Test
	public void customerListByStoreKeepsNameFilterInsideStore() throws Exception {
		CustomerCriteria criteria = new CustomerCriteria();
		criteria.setName("%");
		criteria.setFirstName("a");
		criteria.setLastName("b");

		CustomerRepositoryImpl repository = new CustomerRepositoryImpl();
		inject(repository, em);
		repository.listByStore(store, criteria);

		List<String> queries = capturedQueries();
		assertStoreScoped(queries, "c.merchantStore.id=:mId");
		for (String q : queries) {
			assertFalse(q, q.contains(".."));
		}
	}

	private List<String> capturedQueries() {
		ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
		verify(em, atLeastOnce()).createQuery(jpql.capture());
		return jpql.getAllValues();
	}

	private static void assertStoreScoped(List<String> queries, String storePredicate) {
		assertEquals(2, queries.size());
		for (String q : queries) {
			String where = q.substring(q.indexOf(" where ") + " where ".length());
			int orderBy = where.indexOf(" order by ");
			if (orderBy >= 0) {
				where = where.substring(0, orderBy);
			}
			assertTrue(q, where.trim().startsWith(storePredicate));
			assertTrue(q, where.contains(" or "));
			assertFalse("top-level 'or' escapes the store filter: " + q, hasTopLevelOr(where));
		}
	}

	private static boolean hasTopLevelOr(String where) {
		int depth = 0;
		String lower = where.toLowerCase();
		for (int i = 0; i < lower.length(); i++) {
			char c = lower.charAt(i);
			if (c == '(') {
				depth++;
			} else if (c == ')') {
				depth--;
			} else if (depth == 0 && lower.startsWith(" or ", i)) {
				return true;
			}
		}
		return false;
	}

	private static void inject(Object repository, EntityManager em) throws Exception {
		Field field = repository.getClass().getDeclaredField("em");
		field.setAccessible(true);
		field.set(repository, em);
	}
}
