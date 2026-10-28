package com.salesmanager.test.business.availability;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.repositories.catalog.product.availability.PageableProductAvailabilityRepository;
import com.salesmanager.core.business.repositories.catalog.product.availability.ProductAvailabilityRepository;
import com.salesmanager.core.business.services.catalog.product.availability.ProductAvailabilityService;
import com.salesmanager.core.business.services.catalog.product.availability.ProductAvailabilityServiceImpl;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.merchant.MerchantStore;

/**
 * Pure unit tests (no Spring context) for the store-scoped SKU lookups.
 */
public class ProductAvailabilityServiceStoreScopeTest {

	private ProductAvailabilityRepository repository;
	private PageableProductAvailabilityRepository pageableRepository;
	private ProductAvailabilityServiceImpl service;
	private MerchantStore store;

	@Before
	public void setUp() {
		repository = mock(ProductAvailabilityRepository.class);
		pageableRepository = mock(PageableProductAvailabilityRepository.class);
		service = new ProductAvailabilityServiceImpl(repository);
		ReflectionTestUtils.setField(service, "pageableProductAvailabilityRepository", pageableRepository);
		store = new MerchantStore();
		store.setCode("STOREA");
	}

	@Test
	public void pagedGetBySkuPassesStoreCodeAndPageRequest() {
		Page<ProductAvailability> expected = new PageImpl<>(Collections.singletonList(new ProductAvailability()));
		when(pageableRepository.getBySku("SKU-1", "STOREA", PageRequest.of(2, 5))).thenReturn(expected);

		Page<ProductAvailability> result = service.getBySku("SKU-1", store, 2, 5);

		assertSame(expected, result);
		verify(pageableRepository).getBySku("SKU-1", "STOREA", PageRequest.of(2, 5));
	}

	@Test
	public void listGetBySkuPassesStoreCode() {
		List<ProductAvailability> expected = Collections.singletonList(new ProductAvailability());
		when(repository.getBySku("SKU-1", "STOREA")).thenReturn(expected);

		assertSame(expected, service.getBySku("SKU-1", store));
		verify(repository).getBySku("SKU-1", "STOREA");
	}

	@Test
	public void nullStoreIsRejectedBeforeAnyQuery() {
		try {
			service.getBySku("SKU-1", null, 0, 10);
		} catch (NullPointerException expected) {
			// Validate.notNull
		}
		try {
			service.getBySku("SKU-1", (MerchantStore) null);
		} catch (NullPointerException expected) {
			// Validate.notNull
		}
		verify(pageableRepository, never()).getBySku(anyString(), anyString(), any(Pageable.class));
		verify(repository, never()).getBySku(anyString(), anyString());
	}

	@Test
	public void everyGetBySkuOverloadRequiresAStore() {
		assertAllGetBySkuOverloadsTake(ProductAvailabilityService.class, MerchantStore.class);
		assertAllGetBySkuOverloadsTake(ProductAvailabilityRepository.class, String.class);
		assertAllGetBySkuOverloadsTake(PageableProductAvailabilityRepository.class, String.class);
	}

	private static void assertAllGetBySkuOverloadsTake(Class<?> type, Class<?> storeType) {
		int found = 0;
		for (Method method : type.getMethods()) {
			if (!"getBySku".equals(method.getName())) {
				continue;
			}
			found++;
			List<Class<?>> params = Arrays.asList(method.getParameterTypes());
			assertTrue(type.getSimpleName() + "." + method + " must take a store argument",
					params.size() >= 2 && params.get(1).equals(storeType));
		}
		assertTrue(type.getSimpleName() + " should expose getBySku", found > 0);
		if (type.equals(ProductAvailabilityService.class)) {
			assertEquals(2, found);
		}
	}
}
