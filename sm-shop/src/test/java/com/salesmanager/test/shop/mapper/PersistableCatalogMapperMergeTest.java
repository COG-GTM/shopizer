package com.salesmanager.test.shop.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.services.catalog.product.attribute.ProductOptionService;
import com.salesmanager.core.business.services.catalog.product.attribute.ProductOptionValueService;
import com.salesmanager.core.business.services.catalog.product.type.ProductTypeService;
import com.salesmanager.core.business.services.catalog.product.variant.ProductVariantImageService;
import com.salesmanager.core.business.services.catalog.product.variant.ProductVariantService;
import com.salesmanager.core.model.catalog.product.attribute.ProductOption;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionSet;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionValue;
import com.salesmanager.core.model.catalog.product.variant.ProductVariant;
import com.salesmanager.core.model.catalog.product.variant.ProductVariantGroup;
import com.salesmanager.core.model.catalog.product.variant.ProductVariantImage;
import com.salesmanager.core.model.catalog.product.variation.ProductVariation;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.mapper.catalog.PersistableProductOptionSetMapper;
import com.salesmanager.shop.mapper.catalog.PersistableProductVariationMapper;
import com.salesmanager.shop.mapper.catalog.product.PersistableProductVariantGroupMapper;
import com.salesmanager.shop.model.catalog.product.attribute.optionset.PersistableProductOptionSet;
import com.salesmanager.shop.model.catalog.product.product.variantGroup.PersistableProductVariantGroup;
import com.salesmanager.shop.model.catalog.product.variation.PersistableProductVariation;
import com.salesmanager.shop.store.api.exception.ConversionRuntimeException;

@RunWith(MockitoJUnitRunner.class)
public class PersistableCatalogMapperMergeTest {

	private final MerchantStore store = new MerchantStore();
	private final Language language = new Language();

	@Mock
	private ProductOptionService productOptionService;

	@Mock
	private ProductOptionValueService productOptionValueService;

	@Mock
	private ProductTypeService productTypeService;

	@Mock
	private ProductVariantService productVariantService;

	@Mock
	private ProductVariantImageService productVariantImageService;

	@InjectMocks
	private PersistableProductOptionSetMapper optionSetMapper;

	@InjectMocks
	private PersistableProductVariationMapper variationMapper;

	@InjectMocks
	private PersistableProductVariantGroupMapper variantGroupMapper;

	@Test
	public void variantGroupMergeKeepsDestinationIdAndUsesItsImages() {
		PersistableProductVariantGroup source = new PersistableProductVariantGroup();
		source.setId(17L);
		source.setproductVariants(Collections.singletonList(3L));

		ProductVariant variant = new ProductVariant();
		ProductVariantGroup destination = new ProductVariantGroup();
		destination.setId(5L);
		ProductVariantImage image = new ProductVariantImage();
		when(productVariantService.getByIds(Collections.singletonList(3L), store))
				.thenReturn(Collections.singletonList(variant));
		when(productVariantImageService.listByProductVariantGroup(5L, store))
				.thenReturn(Collections.singletonList(image));

		ProductVariantGroup result = variantGroupMapper.merge(source, destination, store, language);

		assertSame(destination, result);
		assertEquals(Long.valueOf(5L), result.getId());
		assertSame(store, result.getMerchantStore());
		assertSame(Collections.singletonList(image).get(0), result.getImages().get(0));
		assertSame(destination, variant.getProductVariantGroup());
		assertTrue(result.getProductVariants().contains(variant));
		verify(productVariantImageService).listByProductVariantGroup(5L, store);
		verify(productVariantImageService, never()).listByProductVariantGroup(17L, store);
	}

	@Test
	public void optionSetMergeKeepsDestinationIdAndMapsOptionAndValues() {
		PersistableProductOptionSet source = new PersistableProductOptionSet();
		source.setId(17L);
		source.setCode("option-set");
		source.setReadOnly(true);
		source.setOption(2L);
		source.setOptionValues(Collections.singletonList(3L));

		ProductOption option = new ProductOption();
		ProductOptionValue value = new ProductOptionValue();
		ProductOptionSet destination = new ProductOptionSet();
		destination.setId(5L);
		when(productOptionService.getById(store, 2L)).thenReturn(option);
		when(productOptionValueService.getById(store, 3L)).thenReturn(value);

		ProductOptionSet result = optionSetMapper.merge(source, destination, store, language);

		assertSame(destination, result);
		assertEquals(Long.valueOf(5L), result.getId());
		assertEquals("option-set", result.getCode());
		assertTrue(result.isOptionDisplayOnly());
		assertSame(option, result.getOption());
		assertSame(value, result.getValues().get(0));
	}

	@Test
	public void variationConvertMapsCodeAndStore() {
		PersistableProductVariation source = variation("variation", 1L, 2L);
		ProductOption option = new ProductOption();
		ProductOptionValue value = new ProductOptionValue();
		when(productOptionService.getById(store, 1L)).thenReturn(option);
		when(productOptionValueService.getById(store, 2L)).thenReturn(value);

		ProductVariation result = variationMapper.convert(source, store, language);

		assertEquals("variation", result.getCode());
		assertSame(store, result.getMerchantStore());
		assertSame(option, result.getProductOption());
		assertSame(value, result.getProductOptionValue());
	}

	@Test
	public void variationConvertRejectsMissingOption() {
		PersistableProductVariation source = variation("variation", 1L, 2L);
		when(productOptionService.getById(store, 1L)).thenReturn(null);

		assertConversionFailure(source);
	}

	@Test
	public void variationConvertRejectsMissingOptionValue() {
		PersistableProductVariation source = variation("variation", 1L, 2L);
		when(productOptionService.getById(store, 1L)).thenReturn(new ProductOption());
		when(productOptionValueService.getById(store, 2L)).thenReturn(null);

		assertConversionFailure(source);
	}

	private PersistableProductVariation variation(String code, Long option, Long optionValue) {
		PersistableProductVariation source = new PersistableProductVariation();
		source.setCode(code);
		source.setOption(option);
		source.setOptionValue(optionValue);
		return source;
	}

	private void assertConversionFailure(PersistableProductVariation source) {
		try {
			variationMapper.convert(source, store, language);
			fail("Expected ConversionRuntimeException");
		} catch (ConversionRuntimeException expected) {
			assertTrue(expected instanceof ConversionRuntimeException);
		}
	}
}
