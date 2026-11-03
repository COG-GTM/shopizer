package com.salesmanager.test.shop.mapper.tax;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.tax.taxclass.TaxClass;
import com.salesmanager.shop.mapper.tax.PersistableTaxClassMapper;
import com.salesmanager.shop.model.tax.PersistableTaxClass;

public class PersistableTaxClassMapperTest {

	private final PersistableTaxClassMapper mapper = new PersistableTaxClassMapper();

	@Test
	public void convertDoesNotCopyClientIdAndSetsStoreAndFields() {
		MerchantStore store = new MerchantStore();
		store.setCode("DEFAULT");
		PersistableTaxClass source = new PersistableTaxClass();
		source.setId(99L);
		source.setCode("CLASS99");
		source.setName("Class title");

		TaxClass result = mapper.convert(source, store, null);

		assertNull(result.getId());
		assertSame(store, result.getMerchantStore());
		assertEquals("CLASS99", result.getCode());
		assertEquals("Class title", result.getTitle());
	}

	@Test
	public void mergePreservesDestinationIdAndSetsPassedStore() {
		MerchantStore store = new MerchantStore();
		store.setCode("DEFAULT");
		TaxClass destination = new TaxClass("OLD");
		destination.setId(5L);
		PersistableTaxClass source = new PersistableTaxClass();
		source.setId(99L);
		source.setCode("CLASS99");
		source.setName("Class title");

		TaxClass result = mapper.merge(source, destination, store, null);

		assertSame(destination, result);
		assertEquals(Long.valueOf(5L), result.getId());
		assertSame(store, result.getMerchantStore());
		assertEquals("CLASS99", result.getCode());
		assertEquals("Class title", result.getTitle());
	}
}
