package com.salesmanager.shop.mapper.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.services.catalog.product.attribute.ProductOptionService;
import com.salesmanager.core.business.services.catalog.product.attribute.ProductOptionValueService;
import com.salesmanager.core.model.catalog.product.attribute.ProductOption;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionValue;
import com.salesmanager.core.model.catalog.product.variation.ProductVariation;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.product.variation.PersistableProductVariation;
import com.salesmanager.shop.store.api.exception.ConversionRuntimeException;

@RunWith(MockitoJUnitRunner.class)
public class ProductVariationIdentityTest {

	@Mock private ProductOptionService optionService;
	@Mock private ProductOptionValueService valueService;
	@InjectMocks private PersistableProductVariationMapper mapper;

	private final MerchantStore store = new MerchantStore(2, "IDENTITY", "Identity store");
	private final Language language = new Language();
	private final ProductOption option = new ProductOption();
	private final ProductOptionValue value = new ProductOptionValue();
	private PersistableProductVariation body;

	@Before
	public void setUp() {
		body = new PersistableProductVariation();
		body.setCode("updated");
		body.setOption(5L);
		body.setOptionValue(6L);
	}

	private void assertIdentityPreserved(Long bodyId) {
		when(optionService.getById(store, 5L)).thenReturn(option);
		when(valueService.getById(store, 6L)).thenReturn(value);
		body.setId(bodyId);
		ProductVariation destination = new ProductVariation();
		destination.setId(42L);

		ProductVariation merged = mapper.merge(body, destination, store, language);

		assertSame(destination, merged);
		assertEquals(Long.valueOf(42), merged.getId());
		assertEquals("updated", merged.getCode());
		assertSame(store, merged.getMerchantStore());
		assertSame(option, merged.getProductOption());
		assertSame(value, merged.getProductOptionValue());
		assertEquals(bodyId, body.getId());
	}

	@Test
	public void omittedBodyIdDoesNotClearPersistedIdentity() {
		assertIdentityPreserved(null);
	}

	@Test
	public void zeroBodyIdDoesNotReplacePersistedIdentity() {
		assertIdentityPreserved(0L);
	}

	@Test
	public void negativeBodyIdDoesNotReplacePersistedIdentity() {
		assertIdentityPreserved(-1L);
	}

	@Test
	public void optionMustBelongToTheCurrentStore() {
		assertThrows(ConversionRuntimeException.class, () -> mapper.convert(body, store, language));
		verify(optionService).getById(store, 5L);
	}

	@Test
	public void optionValueMustBelongToTheCurrentStore() {
		when(optionService.getById(store, 5L)).thenReturn(option);
		assertThrows(ConversionRuntimeException.class, () -> mapper.convert(body, store, language));
		verify(valueService).getById(store, 6L);
	}
}
