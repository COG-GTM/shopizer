package com.salesmanager.test.shop.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.salesmanager.core.business.services.catalog.product.attribute.ProductOptionService;
import com.salesmanager.core.business.services.catalog.product.attribute.ProductOptionValueService;
import com.salesmanager.core.business.services.catalog.product.type.ProductTypeService;
import com.salesmanager.core.business.services.catalog.product.variant.ProductVariantImageService;
import com.salesmanager.core.business.services.catalog.product.variant.ProductVariantService;
import com.salesmanager.core.model.catalog.product.attribute.ProductOption;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionSet;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionValue;
import com.salesmanager.core.model.catalog.product.variant.ProductVariantGroup;
import com.salesmanager.core.model.catalog.product.variation.ProductVariation;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.mapper.catalog.PersistableProductOptionSetMapper;
import com.salesmanager.shop.mapper.catalog.PersistableProductVariationMapper;
import com.salesmanager.shop.mapper.catalog.product.PersistableProductVariantGroupMapper;
import com.salesmanager.shop.model.catalog.product.attribute.optionset.PersistableProductOptionSet;
import com.salesmanager.shop.model.catalog.product.product.variantGroup.PersistableProductVariantGroup;
import com.salesmanager.shop.model.catalog.product.variation.PersistableProductVariation;

/**
 * Persistable mappers must never copy a client supplied id onto the entity,
 * otherwise a create request could overwrite a row owned by another store.
 */
@ExtendWith(MockitoExtension.class)
public class PersistableMapperIdTest {

	private static final Long FOREIGN_ID = 17L;

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
	private PersistableProductVariationMapper variationMapper;

	@InjectMocks
	private PersistableProductOptionSetMapper optionSetMapper;

	@InjectMocks
	private PersistableProductVariantGroupMapper variantGroupMapper;

	private final MerchantStore store = new MerchantStore();
	private final Language language = new Language();

	@Test
	public void variationConvertIgnoresBodyId() {
		when(productOptionService.getById(any(MerchantStore.class), anyLong())).thenReturn(new ProductOption());
		when(productOptionValueService.getById(any(MerchantStore.class), anyLong())).thenReturn(new ProductOptionValue());

		PersistableProductVariation source = new PersistableProductVariation();
		source.setId(FOREIGN_ID);
		source.setCode("code");
		source.setOption(1L);
		source.setOptionValue(2L);

		ProductVariation variation = variationMapper.convert(source, store, language);
		assertNull(variation.getId());

		ProductVariation existing = new ProductVariation();
		existing.setId(5L);
		assertEquals(Long.valueOf(5L), variationMapper.merge(source, existing, store, language).getId());
	}

	@Test
	public void optionSetConvertIgnoresBodyId() {
		PersistableProductOptionSet source = new PersistableProductOptionSet();
		source.setId(FOREIGN_ID);
		source.setCode("code");
		source.setOption(1L);

		ProductOptionSet optionSet = optionSetMapper.convert(source, store, language);
		assertNull(optionSet.getId());
	}

	@Test
	public void variantGroupConvertIgnoresBodyId() {
		when(productVariantService.getByIds(anyList(), any(MerchantStore.class))).thenReturn(new ArrayList<>());

		PersistableProductVariantGroup source = new PersistableProductVariantGroup();
		source.setId(FOREIGN_ID);
		source.setproductVariants(Collections.singletonList(1L));

		ProductVariantGroup group = variantGroupMapper.convert(source, store, language);
		assertNull(group.getId());
		verify(productVariantImageService, never()).listByProductVariantGroup(anyLong(), any(MerchantStore.class));
	}
}
