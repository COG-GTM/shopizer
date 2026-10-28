package com.salesmanager.shop.store.facade.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.services.catalog.product.attribute.ProductOptionService;
import com.salesmanager.core.business.services.catalog.product.attribute.ProductOptionValueService;
import com.salesmanager.core.business.services.catalog.product.variation.ProductVariationService;
import com.salesmanager.core.model.catalog.product.attribute.ProductOption;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionValue;
import com.salesmanager.core.model.catalog.product.variation.ProductVariation;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.mapper.catalog.PersistableProductVariationMapper;
import com.salesmanager.shop.model.catalog.product.variation.PersistableProductVariation;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ProductVariationFacadeImplTest {

	private static final long OTHER_STORE_VARIATION_ID = 17L;
	private static final long OWN_VARIATION_ID = 42L;
	private static final long GENERATED_ID = 99L;

	@Mock
	private ProductVariationService productVariationService;

	@Mock
	private ProductOptionService productOptionService;

	@Mock
	private ProductOptionValueService productOptionValueService;

	@Spy
	@InjectMocks
	private PersistableProductVariationMapper persistableProductVariationMapper;

	@InjectMocks
	private ProductVariationFacadeImpl facade;

	private MerchantStore store;
	private Language language;
	private ProductOption option;
	private ProductOptionValue optionValue;

	@Before
	public void setUp() {
		ReflectionTestUtils.setField(facade, "persistableProductVariationMapper", persistableProductVariationMapper);
		store = new MerchantStore();
		store.setId(2);
		store.setCode("GLOBEX");
		language = new Language();
		language.setId(1);
		option = new ProductOption();
		option.setId(5L);
		optionValue = new ProductOptionValue();
		optionValue.setId(6L);
		when(productOptionService.getById(store, 5L)).thenReturn(option);
		when(productOptionValueService.getById(store, 6L)).thenReturn(optionValue);
	}

	private PersistableProductVariation body(Long id) {
		PersistableProductVariation var = new PersistableProductVariation();
		var.setId(id);
		var.setCode("anything");
		var.setOption(5L);
		var.setOptionValue(6L);
		return var;
	}

	@Test
	public void createIgnoresClientSuppliedId() throws Exception {
		when(productVariationService.getByCode(store, "anything")).thenReturn(Optional.empty());
		doAnswer(inv -> {
			((ProductVariation) inv.getArgument(0)).setId(GENERATED_ID);
			return null;
		}).when(productVariationService).save(any(ProductVariation.class));

		Long id = facade.create(body(OTHER_STORE_VARIATION_ID), store, language);

		ArgumentCaptor<ProductVariation> saved = ArgumentCaptor.forClass(ProductVariation.class);
		verify(productVariationService).save(saved.capture());
		verify(productVariationService, never()).saveOrUpdate(any());
		verify(productVariationService, never()).getById(anyLong());
		assertEquals(Long.valueOf(GENERATED_ID), id);
		assertSame(store, saved.getValue().getMerchantStore());
		assertEquals("anything", saved.getValue().getCode());
	}

	@Test
	public void createPersistsEntityWithNullIdEvenWhenBodyHasId() throws Exception {
		when(productVariationService.getByCode(store, "anything")).thenReturn(Optional.empty());
		Long[] idAtSave = new Long[1];
		doAnswer(inv -> {
			idAtSave[0] = ((ProductVariation) inv.getArgument(0)).getId();
			return null;
		}).when(productVariationService).save(any(ProductVariation.class));

		facade.create(body(OTHER_STORE_VARIATION_ID), store, language);

		assertNull(idAtSave[0]);
	}

	@Test
	public void updateUsesPathIdNotBodyId() throws Exception {
		ProductVariation existing = new ProductVariation();
		existing.setId(OWN_VARIATION_ID);
		existing.setMerchantStore(store);
		when(productVariationService.getById(store, OWN_VARIATION_ID, language)).thenReturn(Optional.of(existing));

		facade.update(OWN_VARIATION_ID, body(OTHER_STORE_VARIATION_ID), store, language);

		ArgumentCaptor<ProductVariation> saved = ArgumentCaptor.forClass(ProductVariation.class);
		verify(productVariationService).save(saved.capture());
		assertEquals(Long.valueOf(OWN_VARIATION_ID), saved.getValue().getId());
		assertSame(store, saved.getValue().getMerchantStore());
	}

	@Test(expected = ResourceNotFoundException.class)
	public void updateOfAnotherStoresVariationIsNotFound() throws Exception {
		when(productVariationService.getById(eq(store), eq(OTHER_STORE_VARIATION_ID), eq(language)))
				.thenReturn(Optional.empty());
		try {
			facade.update(OTHER_STORE_VARIATION_ID, body(null), store, language);
		} finally {
			verify(productVariationService, never()).save(any());
		}
	}

	@Test
	public void mapperMergeKeepsDestinationId() {
		ProductVariation destination = new ProductVariation();
		destination.setId(OWN_VARIATION_ID);

		ProductVariation merged = persistableProductVariationMapper.merge(body(OTHER_STORE_VARIATION_ID), destination, store, language);

		assertEquals(Long.valueOf(OWN_VARIATION_ID), merged.getId());
	}

	@Test
	public void mapperConvertIgnoresSourceId() {
		ProductVariation converted = persistableProductVariationMapper.convert(body(OTHER_STORE_VARIATION_ID), store, language);

		assertNull(converted.getId());
	}
}
