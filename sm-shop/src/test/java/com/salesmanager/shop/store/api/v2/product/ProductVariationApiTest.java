package com.salesmanager.shop.store.api.v2.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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

@ExtendWith(MockitoExtension.class)
class ProductVariationApiTest {

  private static final Long PRODUCT_ID = 123L;
  private static final Long OPTION_ID = 7L;
  private static final Long OPTION_VALUE_ID = 70L;

  @Mock private ProductService productService;
  @Mock private PricingService pricingService;

  @InjectMocks private ProductVariationApi api;

  private final MerchantStore store = store(1, "DEFAULT");
  private final Language language = new Language();

  @Test
  void productOfAnotherStoreIsNotFound() throws Exception {
    when(productService.findOne(PRODUCT_ID, store)).thenReturn(null);
    MockHttpServletResponse response = new MockHttpServletResponse();

    ReadableProductPrice price = api.calculateVariant(PRODUCT_ID, selection(), store, language, response);

    assertNull(price);
    assertEquals(404, response.getStatus());
    verify(productService, never()).getById(anyLong());
    verify(pricingService, never()).calculateProductPrice(any(Product.class), anyList());
  }

  @Test
  void unavailableProductIsNotFound() throws Exception {
    Product product = product(store);
    product.setAvailable(false);
    when(productService.findOne(PRODUCT_ID, store)).thenReturn(product);
    MockHttpServletResponse response = new MockHttpServletResponse();

    ReadableProductPrice price = api.calculateVariant(PRODUCT_ID, selection(), store, language, response);

    assertNull(price);
    assertEquals(404, response.getStatus());
    verify(pricingService, never()).calculateProductPrice(any(Product.class), anyList());
  }

  @Test
  void productOfRequestedStoreIsPriced() throws Exception {
    Product product = product(store);
    when(productService.findOne(PRODUCT_ID, store)).thenReturn(product);
    FinalPrice finalPrice = new FinalPrice();
    finalPrice.setOriginalPrice(new BigDecimal("12.00"));
    finalPrice.setFinalPrice(new BigDecimal("12.00"));
    when(pricingService.calculateProductPrice(any(Product.class), anyList())).thenReturn(finalPrice);
    when(pricingService.getDisplayAmount(new BigDecimal("12.00"), store)).thenReturn("$12.00");
    MockHttpServletResponse response = new MockHttpServletResponse();

    ReadableProductPrice price = api.calculateVariant(PRODUCT_ID, selection(), store, language, response);

    assertNotNull(price);
    assertEquals(200, response.getStatus());
    assertEquals("$12.00", price.getFinalPrice());
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<ProductAttribute>> attributes = ArgumentCaptor.forClass(List.class);
    verify(pricingService).calculateProductPrice(any(Product.class), attributes.capture());
    assertEquals(1, attributes.getValue().size());
    verify(productService, never()).getById(anyLong());
  }

  private static MerchantStore store(Integer id, String code) {
    MerchantStore store = new MerchantStore();
    store.setId(id);
    store.setCode(code);
    return store;
  }

  private static Product product(MerchantStore owner) {
    ProductOption option = new ProductOption();
    option.setId(OPTION_ID);
    ProductOptionValue value = new ProductOptionValue();
    value.setId(OPTION_VALUE_ID);
    ProductAttribute attribute = new ProductAttribute();
    attribute.setProductOption(option);
    attribute.setProductOptionValue(value);
    Set<ProductAttribute> attributes = new HashSet<>();
    attributes.add(attribute);

    Product product = new Product();
    product.setId(PRODUCT_ID);
    product.setMerchantStore(owner);
    product.setAvailable(true);
    product.setAttributes(attributes);
    return product;
  }

  private static ReadableSelectedProductVariant selection() {
    ReadableProductVariantValue v = new ReadableProductVariantValue();
    v.setOption(OPTION_ID);
    v.setValue(OPTION_VALUE_ID);
    ReadableSelectedProductVariant selection = new ReadableSelectedProductVariant();
    selection.setOptions(Collections.singletonList(v));
    return selection;
  }
}
