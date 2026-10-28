package com.salesmanager.test.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.repositories.catalog.product.ProductRepository;
import com.salesmanager.core.business.services.catalog.product.ProductServiceImpl;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;

public class ProductSkuNumericIdTest {

	private ProductRepository repository;
	private ProductServiceImpl service;
	private MerchantStore store;
	private Language language;
	private Product product;

	@Before
	public void setUp() {
		repository = mock(ProductRepository.class);
		service = new ProductServiceImpl(repository);
		store = new MerchantStore();
		store.setId(17);
		language = new Language();
		product = new Product();
		product.setId(42L);
		when(repository.getById(42L, store, language)).thenReturn(product);
		when(repository.getById(42L, store)).thenReturn(product);
	}

	@Test
	public void acceptsBigIntegerIds() throws Exception {
		assertLookup(BigInteger.valueOf(42));
	}

	@Test
	public void acceptsLongIds() throws Exception {
		assertLookup(42L);
	}

	@Test
	public void acceptsIntegerIds() throws Exception {
		assertLookup(42);
	}

	@Test
	public void acceptsDecimalIds() throws Exception {
		assertLookup(BigDecimal.valueOf(42));
	}

	@Test
	public void missingSkuPreservesTheServiceExceptionContract() {
		when(repository.findBySku("MISSING", store.getId())).thenReturn(Collections.emptyList());
		assertEquals("Cannot get product with sku [MISSING]",
				assertThrows(ServiceException.class, () -> service.getBySku("MISSING", store, language)).getMessage());
		assertEquals("Cannot get product with sku [MISSING]",
				assertThrows(ServiceException.class, () -> service.getBySku("MISSING", store)).getMessage());
	}

	@Test
	public void repositoryFailuresPreserveTheirCause() {
		RuntimeException failure = new IllegalStateException("database unavailable");
		when(repository.findBySku("SKU", store.getId())).thenThrow(failure);
		assertSame(failure, assertThrows(ServiceException.class, () -> service.getBySku("SKU", store, language)).getCause());
		assertSame(failure, assertThrows(ServiceException.class, () -> service.getBySku("SKU", store)).getCause());
	}

	private void assertLookup(Number id) throws Exception {
		when(repository.findBySku("SKU", store.getId())).thenReturn(Collections.singletonList(id));
		assertSame(product, service.getBySku("SKU", store, language));
		assertSame(product, service.getBySku("SKU", store));
		verify(repository).getById(42L, store, language);
		verify(repository).getById(42L, store);
	}
}
