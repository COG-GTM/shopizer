package com.salesmanager.test.shop.facade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.services.catalog.product.attribute.ProductOptionSetService;
import com.salesmanager.core.business.services.catalog.product.variant.ProductVariantGroupService;
import com.salesmanager.core.business.services.catalog.product.variant.ProductVariantImageService;
import com.salesmanager.core.business.services.catalog.product.variant.ProductVariantService;
import com.salesmanager.core.business.services.catalog.product.variation.ProductVariationService;
import com.salesmanager.core.business.services.content.ContentService;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionSet;
import com.salesmanager.core.model.catalog.product.variant.ProductVariantGroup;
import com.salesmanager.core.model.catalog.product.variation.ProductVariation;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.mapper.catalog.PersistableProductOptionSetMapper;
import com.salesmanager.shop.mapper.catalog.PersistableProductVariationMapper;
import com.salesmanager.shop.mapper.catalog.ReadableProductOptionSetMapper;
import com.salesmanager.shop.mapper.catalog.ReadableProductVariationMapper;
import com.salesmanager.shop.mapper.catalog.product.PersistableProductVariantGroupMapper;
import com.salesmanager.shop.mapper.catalog.product.ReadableProductVariantGroupMapper;
import com.salesmanager.shop.model.catalog.product.attribute.optionset.PersistableProductOptionSet;
import com.salesmanager.shop.model.catalog.product.product.variantGroup.PersistableProductVariantGroup;
import com.salesmanager.shop.model.catalog.product.variation.PersistableProductVariation;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.controller.product.facade.ProductTypeFacade;
import com.salesmanager.shop.store.facade.product.ProductOptionSetFacadeImpl;
import com.salesmanager.shop.store.facade.product.ProductVariationFacadeImpl;
import com.salesmanager.shop.store.facade.product.ProductVariantGroupFacadeImpl;

@RunWith(MockitoJUnitRunner.class)
public class ProductCatalogFacadeIdTest {

	private final MerchantStore store = new MerchantStore();
	private final Language language = new Language();

	@Mock
	private ProductOptionSetService productOptionSetService;
	@Mock
	private PersistableProductOptionSetMapper optionSetMapper;
	@Mock
	private ReadableProductOptionSetMapper readableOptionSetMapper;
	@Mock
	private ProductTypeFacade productTypeFacade;
	@InjectMocks
	private ProductOptionSetFacadeImpl optionSetFacade;

	@Mock
	private ProductVariationService productVariationService;
	@Mock
	private PersistableProductVariationMapper variationMapper;
	@Mock
	private ReadableProductVariationMapper readableVariationMapper;
	@InjectMocks
	private ProductVariationFacadeImpl variationFacade;

	@Mock
	private ProductVariantGroupService productVariantGroupService;
	@Mock
	private ProductVariantService productVariantService;
	@Mock
	private ProductVariantImageService productVariantImageService;
	@Mock
	private PersistableProductVariantGroupMapper variantGroupMapper;
	@Mock
	private ReadableProductVariantGroupMapper readableVariantGroupMapper;
	@Mock
	private ContentService contentService;
	@InjectMocks
	private ProductVariantGroupFacadeImpl variantGroupFacade;

	@Before
	public void setUp() {
		store.setCode("DEFAULT");
	}

	@Test
	public void optionSetUpdateUsesPathIdAndExistingCode() throws Exception {
		ProductOptionSet existing = new ProductOptionSet();
		existing.setId(42L);
		existing.setCode("orig");
		PersistableProductOptionSet request = new PersistableProductOptionSet();
		request.setId(17L);
		request.setCode("client");
		when(productOptionSetService.getById(store, 42L, language)).thenReturn(existing);
		when(optionSetMapper.convert(request, store, language)).thenAnswer(invocation -> {
			PersistableProductOptionSet dto = invocation.getArgument(0);
			ProductOptionSet mapped = new ProductOptionSet();
			mapped.setId(dto.getId());
			mapped.setCode(dto.getCode());
			return mapped;
		});

		optionSetFacade.update(42L, request, store, language);

		ArgumentCaptor<ProductOptionSet> saved = ArgumentCaptor.forClass(ProductOptionSet.class);
		verify(productOptionSetService).save(saved.capture());
		assertEquals(Long.valueOf(42L), saved.getValue().getId());
		assertEquals("orig", saved.getValue().getCode());
		assertSame(store, saved.getValue().getStore());
	}

	@Test
	public void optionSetUpdateRejectsMissingEntity() throws Exception {
		PersistableProductOptionSet request = new PersistableProductOptionSet();
		when(productOptionSetService.getById(store, 42L, language)).thenReturn(null);

		try {
			optionSetFacade.update(42L, request, store, language);
			fail("Expected ResourceNotFoundException");
		} catch (ResourceNotFoundException expected) {
			verify(productOptionSetService, never()).save(any(ProductOptionSet.class));
		}
	}

	@Test
	public void optionSetCreateDoesNotPassBodyIdToService() throws Exception {
		PersistableProductOptionSet request = new PersistableProductOptionSet();
		request.setId(17L);
		request.setCode("new-set");
		when(productOptionSetService.getCode(store, "new-set")).thenReturn(null);
		when(optionSetMapper.convert(request, store, language)).thenReturn(new ProductOptionSet());

		optionSetFacade.create(request, store, language);

		ArgumentCaptor<ProductOptionSet> saved = ArgumentCaptor.forClass(ProductOptionSet.class);
		verify(productOptionSetService).create(saved.capture());
		assertNull(saved.getValue().getId());
	}

	@Test
	public void variationCreateDoesNotPassBodyIdToService() throws Exception {
		PersistableProductVariation request = variation(17L, "new-variation");
		when(productVariationService.getByCode(store, "new-variation")).thenReturn(Optional.empty());
		when(variationMapper.convert(request, store, language)).thenReturn(new ProductVariation());

		variationFacade.create(request, store, language);

		ArgumentCaptor<ProductVariation> saved = ArgumentCaptor.forClass(ProductVariation.class);
		verify(productVariationService).saveOrUpdate(saved.capture());
		assertNull(saved.getValue().getId());
	}

	@Test
	public void variationUpdateUsesPathId() throws Exception {
		ProductVariation existing = new ProductVariation();
		existing.setId(42L);
		PersistableProductVariation request = variation(17L, "changed");
		when(productVariationService.getById(store, 42L, language)).thenReturn(Optional.of(existing));
		when(variationMapper.merge(eq(request), any(ProductVariation.class), eq(store), eq(language)))
				.thenAnswer(invocation -> invocation.getArgument(1));

		variationFacade.update(42L, request, store, language);

		ArgumentCaptor<ProductVariation> saved = ArgumentCaptor.forClass(ProductVariation.class);
		verify(productVariationService).save(saved.capture());
		assertEquals(Long.valueOf(42L), saved.getValue().getId());
		assertSame(store, saved.getValue().getMerchantStore());
	}

	@Test
	public void variantGroupCreateDoesNotPassBodyIdToService() throws Exception {
		PersistableProductVariantGroup request = new PersistableProductVariantGroup();
		request.setId(17L);
		ProductVariantGroup mapped = new ProductVariantGroup();
		when(variantGroupMapper.convert(request, store, language)).thenReturn(mapped);
		when(productVariantGroupService.saveOrUpdate(mapped)).thenReturn(mapped);

		variantGroupFacade.create(request, store, language);

		ArgumentCaptor<ProductVariantGroup> saved = ArgumentCaptor.forClass(ProductVariantGroup.class);
		verify(productVariantGroupService).saveOrUpdate(saved.capture());
		assertNull(saved.getValue().getId());
	}

	@Test
	public void variantGroupUpdateUsesPathId() throws Exception {
		ProductVariantGroup existing = new ProductVariantGroup();
		existing.setId(42L);
		PersistableProductVariantGroup request = new PersistableProductVariantGroup();
		request.setId(17L);
		when(productVariantGroupService.getById(42L, store)).thenReturn(Optional.of(existing));
		when(variantGroupMapper.merge(eq(request), any(ProductVariantGroup.class), eq(store), eq(language)))
				.thenAnswer(invocation -> invocation.getArgument(1));
		when(productVariantGroupService.saveOrUpdate(existing)).thenReturn(existing);

		variantGroupFacade.update(42L, request, store, language);

		ArgumentCaptor<ProductVariantGroup> saved = ArgumentCaptor.forClass(ProductVariantGroup.class);
		verify(productVariantGroupService).saveOrUpdate(saved.capture());
		assertEquals(Long.valueOf(42L), saved.getValue().getId());
	}

	private PersistableProductVariation variation(Long id, String code) {
		PersistableProductVariation variation = new PersistableProductVariation();
		variation.setId(id);
		variation.setCode(code);
		return variation;
	}
}
