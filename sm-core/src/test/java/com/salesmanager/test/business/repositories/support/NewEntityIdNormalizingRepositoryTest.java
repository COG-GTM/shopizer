package com.salesmanager.test.business.repositories.support;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.Serializable;
import java.util.Arrays;

import javax.persistence.EntityManager;

import org.junit.Test;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;

import com.salesmanager.core.business.repositories.support.NewEntityIdNormalizingRepository;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.generic.SalesManagerEntity;
import com.salesmanager.core.model.merchant.MerchantStore;

public class NewEntityIdNormalizingRepositoryTest {

	@Test
	public void normalizesLongZeroIdToPersist() {
		EntityManager entityManager = mock(EntityManager.class);
		Product product = new Product();
		product.setId(0L);

		repository(Product.class, entityManager).save(product);

		assertNull(product.getId());
		verify(entityManager).persist(product);
		verify(entityManager, never()).merge(any());
	}

	@Test
	public void normalizesIntegerZeroIdToPersist() {
		EntityManager entityManager = mock(EntityManager.class);
		MerchantStore store = new MerchantStore();
		store.setId(0);

		repository(MerchantStore.class, entityManager).save(store);

		assertNull(store.getId());
		verify(entityManager).persist(store);
		verify(entityManager, never()).merge(any());
	}

	@Test
	public void preservesNonZeroIdAndMerges() {
		EntityManager entityManager = mock(EntityManager.class);
		Product product = new Product();
		product.setId(5L);

		repository(Product.class, entityManager).save(product);

		assertEquals(Long.valueOf(5L), product.getId());
		verify(entityManager).merge(product);
		verify(entityManager, never()).persist(any());
	}

	@Test
	public void persistsNullId() {
		EntityManager entityManager = mock(EntityManager.class);
		Product product = new Product();

		repository(Product.class, entityManager).save(product);

		assertNull(product.getId());
		verify(entityManager).persist(product);
		verify(entityManager, never()).merge(any());
	}

	@Test
	public void leavesNonSalesManagerEntitiesUntouched() {
		EntityManager entityManager = mock(EntityManager.class);
		TestPojo pojo = new TestPojo();
		pojo.setId(0L);

		repository(TestPojo.class, entityManager).save(pojo);

		assertEquals(Long.valueOf(0L), pojo.getId());
		verify(entityManager).merge(pojo);
		verify(entityManager, never()).persist(any());
	}

	@Test
	public void normalizesAllEntitiesInSaveAll() {
		EntityManager entityManager = mock(EntityManager.class);
		Product first = new Product();
		Product second = new Product();
		first.setId(0L);
		second.setId(0L);

		repository(Product.class, entityManager).saveAll(Arrays.asList(first, second));

		assertNull(first.getId());
		assertNull(second.getId());
		verify(entityManager, times(2)).persist(any());
		verify(entityManager, never()).merge(any());
	}

	@Test
	public void normalizesEntityInSaveAndFlush() {
		EntityManager entityManager = mock(EntityManager.class);
		Product product = new Product();
		product.setId(0L);

		repository(Product.class, entityManager).saveAndFlush(product);

		assertNull(product.getId());
		verify(entityManager).persist(product);
		verify(entityManager).flush();
		verify(entityManager, never()).merge(any());
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private <T, ID extends Serializable> NewEntityIdNormalizingRepository<T, ID> repository(Class<T> type,
			EntityManager entityManager) {
		JpaEntityInformation<T, ID> information = mock(JpaEntityInformation.class);
		when(information.getJavaType()).thenReturn(type);
		when(information.isNew(any())).thenAnswer(invocation -> {
			Object entity = invocation.getArgument(0);
			return entity instanceof SalesManagerEntity && ((SalesManagerEntity<?, ?>) entity).isNew();
		});
		when(entityManager.getDelegate()).thenReturn(new Object());
		return new NewEntityIdNormalizingRepository<>(information, entityManager);
	}

	private static final class TestPojo {
		private Long id;

		private Long getId() {
			return id;
		}

		private void setId(Long id) {
			this.id = id;
		}
	}
}
