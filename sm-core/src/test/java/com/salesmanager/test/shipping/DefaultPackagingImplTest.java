package com.salesmanager.test.shipping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.modules.integration.shipping.impl.DefaultPackagingImpl;
import com.salesmanager.core.business.services.shipping.ShippingService;
import com.salesmanager.core.business.services.system.MerchantLogService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.shipping.PackageDetails;
import com.salesmanager.core.model.shipping.ShippingConfiguration;
import com.salesmanager.core.model.shipping.ShippingProduct;

public class DefaultPackagingImplTest {

	@Test
	public void itemPackagingExpandsQuantities() throws Exception {
		DefaultPackagingImpl packaging = new DefaultPackagingImpl();
		Product first = product();
		Product second = product();

		List<PackageDetails> packages = packaging.getItemPackagesDetails(Arrays.asList(shippingProduct(first, 3),
				shippingProduct(second, 2)), new MerchantStore());

		assertEquals(5, packages.size());
	}

	@Test
	public void itemPackagingRejectsHugeQuantityBeforeAllocation() throws Exception {
		DefaultPackagingImpl packaging = new DefaultPackagingImpl();

		try {
			packaging.getItemPackagesDetails(Arrays.asList(shippingProduct(product(), 2_000_000_000)),
					new MerchantStore());
			fail("Expected ServiceException");
		} catch (ServiceException e) {
			assertEquals("Shipping quantity exceeds maximum of 1000 units", e.getMessage());
		}
	}

	@Test
	public void boxPackagingRejectsHugeQuantityBeforeAllocation() throws Exception {
		DefaultPackagingImpl packaging = packagingWithBoxConfiguration();

		try {
			packaging.getBoxPackagesDetails(Arrays.asList(shippingProduct(product(), 2_000_000_000)),
					new MerchantStore());
			fail("Expected ServiceException");
		} catch (ServiceException e) {
			assertEquals("Shipping quantity exceeds maximum of 1000 units", e.getMessage());
		}
	}

	@Test
	public void boxPackagingRejectsMoreThanMaximumBoxes() throws Exception {
		DefaultPackagingImpl packaging = packagingWithBoxConfiguration();

		try {
			packaging.getBoxPackagesDetails(Arrays.asList(shippingProduct(product(), 150)), new MerchantStore());
			fail("Expected ServiceException");
		} catch (ServiceException e) {
			assertEquals("Number of shipping boxes exceeds maximum of 100", e.getMessage());
		}
	}

	@Test
	public void boxPackagingReturnsFiftyBoxes() throws Exception {
		DefaultPackagingImpl packaging = packagingWithBoxConfiguration();

		List<PackageDetails> packages = packaging.getBoxPackagesDetails(
				Arrays.asList(shippingProduct(product(), 50)), new MerchantStore());

		assertEquals(50, packages.size());
	}

	private DefaultPackagingImpl packagingWithBoxConfiguration() throws ServiceException {
		DefaultPackagingImpl packaging = new DefaultPackagingImpl();
		ShippingService shippingService = mock(ShippingService.class);
		MerchantLogService merchantLogService = mock(MerchantLogService.class);
		ReflectionTestUtils.setField(packaging, "shippingService", shippingService);
		ReflectionTestUtils.setField(packaging, "merchantLogService", merchantLogService);

		ShippingConfiguration configuration = new ShippingConfiguration();
		configuration.setBoxWidth(10);
		configuration.setBoxHeight(10);
		configuration.setBoxLength(10);
		configuration.setBoxWeight(1);
		configuration.setMaxWeight(100);
		when(shippingService.getShippingConfiguration(org.mockito.Mockito.any(MerchantStore.class)))
				.thenReturn(configuration);
		return packaging;
	}

	private ShippingProduct shippingProduct(Product product, int quantity) {
		ShippingProduct shippingProduct = new ShippingProduct(product);
		shippingProduct.setQuantity(quantity);
		return shippingProduct;
	}

	private Product product() {
		Product product = new Product();
		product.setProductVirtual(false);
		product.setProductHeight(BigDecimal.TEN);
		product.setProductLength(BigDecimal.TEN);
		product.setProductWidth(BigDecimal.TEN);
		product.setProductWeight(BigDecimal.ONE);
		product.setDescriptions(new HashSet<>());
		return product;
	}
}
