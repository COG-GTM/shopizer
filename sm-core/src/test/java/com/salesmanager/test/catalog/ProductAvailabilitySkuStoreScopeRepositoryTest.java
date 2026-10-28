package com.salesmanager.test.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;

import org.junit.Test;
import org.springframework.data.domain.Page;

import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.catalog.product.description.ProductDescription;
import com.salesmanager.core.model.catalog.product.price.ProductPrice;
import com.salesmanager.core.model.catalog.product.price.ProductPriceDescription;
import com.salesmanager.core.model.catalog.product.type.ProductType;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.test.common.AbstractSalesManagerCoreTestCase;

/**
 * Store scoping of ProductAvailabilityService.getBySku (paged + list) against H2.
 * Products are only created in dedicated stores so the DEFAULT store catalogue
 * used by other tests is left untouched.
 */
public class ProductAvailabilitySkuStoreScopeRepositoryTest extends AbstractSalesManagerCoreTestCase {

	private static final String STORE_A = "INVSCA";
	private static final String STORE_B = "INVSCB";

	@Test
	public void sameSkuInTwoStoresReturnsOnlyRequestingStoreRows() throws Exception {
		Language en = languageService.getByCode("en");
		MerchantStore a = store(STORE_A);
		MerchantStore b = store(STORE_B);
		String sku = "INV-SHARED-01";

		Product pa = product(a, en, sku, 11, "*");
		Product pb = product(b, en, sku, 22, "*");
		try {
			Page<ProductAvailability> pageA = productAvailabilityService.getBySku(sku, a, 0, 10);
			assertEquals(1, pageA.getTotalElements());
			assertEquals(Integer.valueOf(11), pageA.getContent().get(0).getProductQuantity());
			assertEquals(STORE_A, pageA.getContent().get(0).getMerchantStore().getCode());

			Page<ProductAvailability> pageB = productAvailabilityService.getBySku(sku, b, 0, 10);
			assertEquals(1, pageB.getTotalElements());
			assertEquals(Integer.valueOf(22), pageB.getContent().get(0).getProductQuantity());
			assertEquals(STORE_B, pageB.getContent().get(0).getMerchantStore().getCode());

			List<ProductAvailability> listA = productAvailabilityService.getBySku(sku, a);
			assertEquals(1, listA.size());
			assertEquals(STORE_A, listA.get(0).getMerchantStore().getCode());

			List<ProductAvailability> listB = productAvailabilityService.getBySku(sku, b);
			assertEquals(1, listB.size());
			assertEquals(STORE_B, listB.get(0).getMerchantStore().getCode());
		} finally {
			productService.delete(pa);
			productService.delete(pb);
		}
	}

	@Test
	public void countQueryAndPagingAreStoreScoped() throws Exception {
		Language en = languageService.getByCode("en");
		MerchantStore a = store(STORE_A);
		MerchantStore b = store(STORE_B);
		String sku = "INV-PAGED-01";

		Product pa = product(a, en, sku, 1, "*", "CA");
		Product pb = product(b, en, sku, 2, "*", "US", "FR");
		try {
			Page<ProductAvailability> firstPage = productAvailabilityService.getBySku(sku, a, 0, 1);
			assertEquals(2, firstPage.getTotalElements());
			assertEquals(2, firstPage.getTotalPages());
			assertEquals(1, firstPage.getContent().size());

			Page<ProductAvailability> secondPage = productAvailabilityService.getBySku(sku, a, 1, 1);
			assertEquals(1, secondPage.getContent().size());
			assertEquals(STORE_A, secondPage.getContent().get(0).getMerchantStore().getCode());

			Page<ProductAvailability> all = productAvailabilityService.getBySku(sku, a, 0, 10);
			for (ProductAvailability availability : all.getContent()) {
				assertEquals(STORE_A, availability.getMerchantStore().getCode());
			}

			assertEquals(3, productAvailabilityService.getBySku(sku, b, 0, 10).getTotalElements());
			assertEquals(2, productAvailabilityService.getBySku(sku, a).size());
			assertEquals(3, productAvailabilityService.getBySku(sku, b).size());
		} finally {
			productService.delete(pa);
			productService.delete(pb);
		}
	}

	@Test
	public void skuOwnedByAnotherStoreIsInvisible() throws Exception {
		Language en = languageService.getByCode("en");
		MerchantStore a = store(STORE_A);
		MerchantStore b = store(STORE_B);
		String sku = "INV-ONLY-B-01";

		Product pb = product(b, en, sku, 7, "*");
		try {
			Page<ProductAvailability> fromA = productAvailabilityService.getBySku(sku, a, 0, 10);
			assertEquals(0, fromA.getTotalElements());
			assertTrue(fromA.getContent().isEmpty());
			assertTrue(productAvailabilityService.getBySku(sku, a).isEmpty());

			MerchantStore defaultStore = merchantService.getByCode(MerchantStore.DEFAULT_STORE);
			assertEquals(0, productAvailabilityService.getBySku(sku, defaultStore, 0, 10).getTotalElements());
			assertTrue(productAvailabilityService.getBySku(sku, defaultStore).isEmpty());
		} finally {
			productService.delete(pb);
		}
	}

	@Test
	public void unknownSkuReturnsEmpty() throws Exception {
		MerchantStore a = store(STORE_A);
		assertEquals(0, productAvailabilityService.getBySku("INV-DOES-NOT-EXIST", a, 0, 10).getTotalElements());
		assertTrue(productAvailabilityService.getBySku("INV-DOES-NOT-EXIST", a).isEmpty());
	}

	@Test(expected = NullPointerException.class)
	public void pagedLookupRejectsNullStore() {
		productAvailabilityService.getBySku("ANY", null, 0, 10);
	}

	@Test(expected = NullPointerException.class)
	public void listLookupRejectsNullStore() {
		productAvailabilityService.getBySku("ANY", (MerchantStore) null);
	}

	private MerchantStore store(String code) throws Exception {
		MerchantStore existing = merchantService.getByCode(code);
		if (existing != null) {
			return existing;
		}
		MerchantStore template = merchantService.getByCode(MerchantStore.DEFAULT_STORE);
		MerchantStore store = new MerchantStore();
		store.setCountry(template.getCountry());
		store.setCurrency(template.getCurrency());
		store.setDefaultLanguage(template.getDefaultLanguage());
		store.setInBusinessSince(new Date(System.currentTimeMillis()));
		store.setZone(template.getZone());
		store.setStorename("Inventory scope " + code);
		store.setStorephone("888-888-8888");
		store.setCode(code);
		store.setStorecity("My city");
		store.setStoreaddress("1234 Street address");
		store.setStorepostalcode("H2H-2H2");
		store.setStoreEmailAddress(code.toLowerCase() + "@shopizer.com");
		store.setDomainName("localhost:8080");
		store.setStoreTemplate("december");
		merchantService.create(store);
		return merchantService.getByCode(code);
	}

	private Product product(MerchantStore store, Language en, String sku, int quantity, String... regions)
			throws Exception {
		Product product = new Product();
		product.setSku(sku);
		product.setType(productTypeService.getProductType(ProductType.GENERAL_TYPE));
		product.setMerchantStore(store);

		ProductDescription description = new ProductDescription();
		description.setName(sku + " " + store.getCode());
		description.setLanguage(en);
		description.setProduct(product);
		product.getDescriptions().add(description);

		for (String region : regions) {
			ProductAvailability availability = new ProductAvailability();
			availability.setProductDateAvailable(new Date(System.currentTimeMillis()));
			availability.setProductQuantity(quantity);
			availability.setRegion(region);
			availability.setAvailable(true);
			availability.setProduct(product);
			availability.setMerchantStore(store);

			ProductPrice price = new ProductPrice();
			price.setDefaultPrice(true);
			price.setProductPriceAmount(new BigDecimal("9.99"));
			price.setProductAvailability(availability);

			ProductPriceDescription priceDescription = new ProductPriceDescription();
			priceDescription.setName("Base price");
			priceDescription.setProductPrice(price);
			priceDescription.setLanguage(en);
			price.getDescriptions().add(priceDescription);
			availability.getPrices().add(price);

			product.getAvailabilities().add(availability);
		}
		productService.saveProduct(product);
		return product;
	}
}
