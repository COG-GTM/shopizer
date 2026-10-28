package com.salesmanager.shop.store.api.v2.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import com.salesmanager.core.business.services.catalog.pricing.PricingService;
import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.attribute.ProductAttribute;
import com.salesmanager.core.model.catalog.product.attribute.ProductOption;
import com.salesmanager.core.model.catalog.product.attribute.ProductOptionValue;
import com.salesmanager.core.model.catalog.product.price.FinalPrice;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.product.ReadableProductPrice;
import com.salesmanager.shop.model.catalog.product.attribute.ReadableProductVariantValue;
import com.salesmanager.shop.model.catalog.product.attribute.ReadableSelectedProductVariant;

/**
 * Edge cases of the store-scoped variant price calculation (PR #212) that complement
 * {@link ProductVariationApiTest}.
 */
@ExtendWith(MockitoExtension.class)
class ProductVariationApiStoreScopeTest {

  private static final Long PRODUCT_ID = 456L;

  @Mock private ProductService productService;
  @Mock private PricingService pricingService;

  @InjectMocks private ProductVariationApi api;

  private final MerchantStore store = store(5, "STORE5");
  private final Language language = new Language();

  @Test
  void lookupUsesTheRequestStoreInstance() throws Exception {
    when(productService.findOne(eq(PRODUCT_ID), any(MerchantStore.class))).thenReturn(null);

    api.calculateVariant(PRODUCT_ID, selection(1L, 10L), store, language, new MockHttpServletResponse());

    ArgumentCaptor<MerchantStore> storeCaptor = ArgumentCaptor.forClass(MerchantStore.class);
    verify(productService).findOne(eq(PRODUCT_ID), storeCaptor.capture());
    assertSame(store, storeCaptor.getValue());
    verify(productService, never()).getById(anyLong());
  }

  @Test
  void notFoundResponseCarriesCorrectedMessage() throws Exception {
    when(productService.findOne(PRODUCT_ID, store)).thenReturn(null);
    MockHttpServletResponse response = new MockHttpServletResponse();

    api.calculateVariant(PRODUCT_ID, selection(1L, 10L), store, language, response);

    assertEquals(404, response.getStatus());
    assertEquals("Product not found for id " + PRODUCT_ID, response.getErrorMessage());
  }

  @Test
  void unavailableProductIsNotFoundEvenWithoutOptions() throws Exception {
    Product product = product(1L, 10L);
    product.setAvailable(false);
    when(productService.findOne(PRODUCT_ID, store)).thenReturn(product);
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertNull(api.calculateVariant(PRODUCT_ID, null, store, language, response));

    assertEquals(404, response.getStatus());
  }

  @Test
  void nullBodyOnAvailableProductReturnsNullWithoutPricing() throws Exception {
    when(productService.findOne(PRODUCT_ID, store)).thenReturn(product(1L, 10L));
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertNull(api.calculateVariant(PRODUCT_ID, null, store, language, response));

    assertEquals(200, response.getStatus());
    verify(pricingService, never()).calculateProductPrice(any(Product.class), anyList());
  }

  @Test
  void emptyOptionsReturnNullWithoutPricing() throws Exception {
    when(productService.findOne(PRODUCT_ID, store)).thenReturn(product(1L, 10L));
    ReadableSelectedProductVariant empty = new ReadableSelectedProductVariant();
    empty.setOptions(new ArrayList<>());
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertNull(api.calculateVariant(PRODUCT_ID, empty, store, language, response));

    assertEquals(200, response.getStatus());
    verify(pricingService, never()).calculateProductPrice(any(Product.class), anyList());
  }

  @Test
  void unmatchedOptionsArePricedWithNoAttributes() throws Exception {
    Product product = product(1L, 10L);
    when(productService.findOne(PRODUCT_ID, store)).thenReturn(product);
    FinalPrice finalPrice = new FinalPrice();
    finalPrice.setOriginalPrice(new BigDecimal("9.99"));
    finalPrice.setFinalPrice(new BigDecimal("9.99"));
    when(pricingService.calculateProductPrice(any(Product.class), anyList())).thenReturn(finalPrice);
    when(pricingService.getDisplayAmount(new BigDecimal("9.99"), store)).thenReturn("$9.99");

    ReadableProductPrice price =
        api.calculateVariant(PRODUCT_ID, selection(2L, 20L), store, language, new MockHttpServletResponse());

    assertNotNull(price);
    assertEquals("$9.99", price.getFinalPrice());
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<ProductAttribute>> attributes = ArgumentCaptor.forClass(List.class);
    verify(pricingService).calculateProductPrice(eq(product), attributes.capture());
    assertTrue(attributes.getValue().isEmpty());
  }

  @Test
  void productOwnedByParentStoreReturnedByScopedLookupIsPriced() throws Exception {
    MerchantStore parent = store(1, "DEFAULT");
    store.setParent(parent);
    Product product = product(1L, 10L);
    product.setMerchantStore(parent);
    when(productService.findOne(PRODUCT_ID, store)).thenReturn(product);
    FinalPrice finalPrice = new FinalPrice();
    finalPrice.setOriginalPrice(new BigDecimal("5.00"));
    finalPrice.setFinalPrice(new BigDecimal("5.00"));
    when(pricingService.calculateProductPrice(any(Product.class), anyList())).thenReturn(finalPrice);
    when(pricingService.getDisplayAmount(new BigDecimal("5.00"), store)).thenReturn("$5.00");

    ReadableProductPrice price =
        api.calculateVariant(PRODUCT_ID, selection(1L, 10L), store, language, new MockHttpServletResponse());

    assertNotNull(price);
    assertEquals("$5.00", price.getFinalPrice());
  }

  private static MerchantStore store(Integer id, String code) {
    MerchantStore s = new MerchantStore();
    s.setId(id);
    s.setCode(code);
    return s;
  }

  private Product product(Long optionId, Long valueId) {
    ProductOption option = new ProductOption();
    option.setId(optionId);
    ProductOptionValue value = new ProductOptionValue();
    value.setId(valueId);
    ProductAttribute attribute = new ProductAttribute();
    attribute.setProductOption(option);
    attribute.setProductOptionValue(value);
    Set<ProductAttribute> attributes = new HashSet<>();
    attributes.add(attribute);

    Product product = new Product();
    product.setId(PRODUCT_ID);
    product.setMerchantStore(store);
    product.setAvailable(true);
    product.setAttributes(attributes);
    return product;
  }

  private static ReadableSelectedProductVariant selection(Long optionId, Long valueId) {
    ReadableProductVariantValue v = new ReadableProductVariantValue();
    v.setOption(optionId);
    v.setValue(valueId);
    ReadableSelectedProductVariant selection = new ReadableSelectedProductVariant();
    selection.setOptions(Collections.singletonList(v));
    return selection;
  }
}
