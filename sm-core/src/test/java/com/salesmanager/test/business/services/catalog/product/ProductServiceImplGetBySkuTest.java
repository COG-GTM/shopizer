package com.salesmanager.test.business.services.catalog.product;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.repositories.catalog.product.ProductRepository;
import com.salesmanager.core.business.services.catalog.product.ProductServiceImpl;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;

public class ProductServiceImplGetBySkuTest {

	@Test
	public void resolvesBigIntegerLongAndIntegerIdsForBothOverloads() throws Exception {
		ProductRepository productRepository = mock(ProductRepository.class);
		ProductServiceImpl productService = new ProductServiceImpl(productRepository);
		MerchantStore store = new MerchantStore();
		store.setId(1);
		Language language = new Language();

		for (Number id : List.of(BigInteger.valueOf(7L), Long.valueOf(7L), Integer.valueOf(7))) {
			when(productRepository.findBySku("sku", 1)).thenReturn(Collections.<Object>singletonList(id));
			Product localizedProduct = new Product();
			Product storeProduct = new Product();
			when(productRepository.getById(7L, store, language)).thenReturn(localizedProduct);
			when(productRepository.getById(7L, store)).thenReturn(storeProduct);

			assertSame(localizedProduct, productService.getBySku("sku", store, language));
			assertSame(storeProduct, productService.getBySku("sku", store));
		}
	}

	@Test
	public void throwsServiceExceptionForMissingSkuInBothOverloads() {
		ProductRepository productRepository = mock(ProductRepository.class);
		ProductServiceImpl productService = new ProductServiceImpl(productRepository);
		MerchantStore store = new MerchantStore();
		store.setId(1);
		Language language = new Language();
		when(productRepository.findBySku(anyString(), anyInt())).thenReturn(Collections.emptyList());

		assertThrows(ServiceException.class, () -> productService.getBySku("missing", store, language));
		assertThrows(ServiceException.class, () -> productService.getBySku("missing", store));
	}
}
