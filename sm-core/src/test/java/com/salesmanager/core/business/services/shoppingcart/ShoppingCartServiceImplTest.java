package com.salesmanager.core.business.services.shoppingcart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.NoResultException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.repositories.shoppingcart.ShoppingCartAttributeRepository;
import com.salesmanager.core.business.repositories.shoppingcart.ShoppingCartItemRepository;
import com.salesmanager.core.business.repositories.shoppingcart.ShoppingCartRepository;
import com.salesmanager.core.business.services.catalog.pricing.PricingService;
import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.catalog.product.attribute.ProductAttributeService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.attribute.ProductAttribute;
import com.salesmanager.core.model.catalog.product.price.FinalPrice;
import com.salesmanager.core.model.common.UserContext;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.shipping.ShippingProduct;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;
import com.salesmanager.core.model.shoppingcart.ShoppingCartAttributeItem;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;

@ExtendWith(MockitoExtension.class)
class ShoppingCartServiceImplTest {

	private static final Integer STORE_ID = 1;
	private static final Long CART_ID = 100L;

	@Mock
	private ShoppingCartRepository shoppingCartRepository;
	@Mock
	private ShoppingCartItemRepository shoppingCartItemRepository;
	@Mock
	private ShoppingCartAttributeRepository shoppingCartAttributeRepository;
	@Mock
	private ProductService productService;
	@Mock
	private PricingService pricingService;
	@Mock
	private ProductAttributeService productAttributeService;

	private ShoppingCartServiceImpl service;
	private MerchantStore store;
	private Language english;

	@BeforeEach
	void setUp() {
		service = new ShoppingCartServiceImpl(shoppingCartRepository);
		ReflectionTestUtils.setField(service, "productService", productService);
		ReflectionTestUtils.setField(service, "shoppingCartItemRepository", shoppingCartItemRepository);
		ReflectionTestUtils.setField(service, "shoppingCartAttributeItemRepository", shoppingCartAttributeRepository);
		ReflectionTestUtils.setField(service, "pricingService", pricingService);
		ReflectionTestUtils.setField(service, "productAttributeService", productAttributeService);

		english = new Language("en");
		store = merchantStore(STORE_ID);
	}

	@Nested
	class LineItemPricing {

		@ParameterizedTest(name = "qty {0} x {1} = {2}")
		@CsvSource({
			"1, 19.99, 19.99",
			"3, 19.99, 59.97",
			"0, 19.99, 0.00",
			"1000, 19.99, 19990.00",
			"2147483647, 19.99, 42928198103.53",
			"5, 0.00, 0.00",
			"3, 0.333, 0.999",
			"-2, 19.99, -39.98"
		})
		void subTotalIsItemPriceTimesQuantity(int quantity, String unitPrice, String expectedSubTotal) throws Exception {
			Product product = product(1L, "SKU-1");
			ShoppingCart cart = cartWith(item(product, quantity));
			givenCartFound(cart);
			givenProductFound(product);
			givenPrice(product, finalPrice(unitPrice));

			ShoppingCart result = service.getById(CART_ID, store);

			ShoppingCartItem priced = result.getLineItems().iterator().next();
			assertThat(priced.getItemPrice()).isEqualByComparingTo(unitPrice);
			assertThat(priced.getSubTotal()).isEqualByComparingTo(expectedSubTotal);
		}

		@Test
		void subTotalIsNotRoundedToCurrencyScale() throws Exception {
			Product product = product(1L, "SKU-1");
			ShoppingCart cart = cartWith(item(product, 3));
			givenCartFound(cart);
			givenProductFound(product);
			givenPrice(product, finalPrice("0.333"));

			ShoppingCart result = service.getById(CART_ID, store);

			assertThat(result.getLineItems().iterator().next().getSubTotal()).isEqualTo(new BigDecimal("0.999"));
		}

		@Test
		void itemPriceIsRefreshedFromCurrentPricingNotStalePrice() throws Exception {
			Product product = product(1L, "SKU-1");
			ShoppingCartItem item = item(product, 2);
			item.setItemPrice(new BigDecimal("50.00"));
			item.setSubTotal(new BigDecimal("100.00"));
			ShoppingCart cart = cartWith(item);
			givenCartFound(cart);
			givenProductFound(product);
			givenPrice(product, finalPrice("45.00"));

			ShoppingCart result = service.getById(CART_ID, store);

			ShoppingCartItem priced = result.getLineItems().iterator().next();
			assertThat(priced.getItemPrice()).isEqualByComparingTo("45.00");
			assertThat(priced.getSubTotal()).isEqualByComparingTo("90.00");
		}

		@Test
		void discountedFinalPriceIsUsedAndPriceDetailsKept() throws Exception {
			Product product = product(1L, "SKU-1");
			ShoppingCart cart = cartWith(item(product, 2));
			givenCartFound(cart);
			givenProductFound(product);
			FinalPrice discounted = finalPrice("80.00");
			discounted.setOriginalPrice(new BigDecimal("100.00"));
			discounted.setDiscountedPrice(new BigDecimal("80.00"));
			discounted.setDiscounted(true);
			discounted.setDiscountPercent(20);
			givenPrice(product, discounted);

			ShoppingCart result = service.getById(CART_ID, store);

			ShoppingCartItem priced = result.getLineItems().iterator().next();
			assertThat(priced.getItemPrice()).isEqualByComparingTo("80.00");
			assertThat(priced.getSubTotal()).isEqualByComparingTo("160.00");
			assertThat(priced.getFinalPrice()).isSameAs(discounted);
			assertThat(priced.getFinalPrice().getOriginalPrice()).isEqualByComparingTo("100.00");
		}

		@Test
		void eachLineItemIsPricedIndependently() throws Exception {
			Product shirt = product(1L, "SHIRT");
			Product hat = product(2L, "HAT");
			ShoppingCartItem shirtItem = item(shirt, 2);
			ShoppingCartItem hatItem = item(hat, 5);
			ShoppingCart cart = cartWith(shirtItem, hatItem);
			givenCartFound(cart);
			givenProductFound(shirt);
			givenProductFound(hat);
			givenPrice(shirt, finalPrice("25.50"));
			givenPrice(hat, finalPrice("9.99"));

			service.getById(CART_ID, store);

			assertThat(shirtItem.getSubTotal()).isEqualByComparingTo("51.00");
			assertThat(hatItem.getSubTotal()).isEqualByComparingTo("49.95");
		}

		@Test
		void nullQuantityFailsWithServiceException() throws Exception {
			Product product = product(1L, "SKU-1");
			ShoppingCartItem item = item(product, 1);
			item.setQuantity(null);
			givenCartFound(cartWith(item));
			givenProductFound(product);
			givenPrice(product, finalPrice("10.00"));

			assertThatThrownBy(() -> service.getById(CART_ID, store)).isInstanceOf(ServiceException.class);
			verify(shoppingCartRepository, never()).delete(any(ShoppingCart.class));
		}

		@Test
		void pricingFailureIsWrappedInServiceException() throws Exception {
			Product product = product(1L, "SKU-1");
			givenCartFound(cartWith(item(product, 1)));
			givenProductFound(product);
			when(pricingService.calculateProductPrice(eq(product), anyList()))
					.thenThrow(new ServiceException("pricing down"));

			assertThatThrownBy(() -> service.getById(CART_ID, store)).isInstanceOf(ServiceException.class);
		}

		@Test
		void refreshedCartIsPersisted() throws Exception {
			Product product = product(1L, "SKU-1");
			ShoppingCart cart = cartWith(item(product, 1));
			givenCartFound(cart);
			givenProductFound(product);
			givenPrice(product, finalPrice("10.00"));

			service.getById(CART_ID, store);

			verify(shoppingCartRepository).saveAndFlush(cart);
		}

		@Test
		void virtualProductFlagIsPropagatedToItem() throws Exception {
			Product ebook = product(1L, "EBOOK");
			ShoppingCartItem item = item(ebook, 1);
			ebook.setProductVirtual(true);
			givenCartFound(cartWith(item));
			givenProductFound(ebook);
			givenPrice(ebook, finalPrice("5.00"));

			service.getById(CART_ID, store);

			assertThat(item.isProductVirtual()).isTrue();
		}
	}

	@Nested
	class Attributes {

		@Test
		void orphanAttributesAreDeletedAndOnlyValidAttributesArePriced() throws Exception {
			Product product = product(1L, "SKU-1");
			ProductAttribute size = productAttribute(10L, product);
			product.setAttributes(new HashSet<>(Collections.singletonList(size)));

			ShoppingCartItem item = item(product, 2);
			ShoppingCartAttributeItem validAttribute = new ShoppingCartAttributeItem(item, 10L);
			ShoppingCartAttributeItem orphanAttribute = new ShoppingCartAttributeItem(item, 99L);
			item.setAttributes(new HashSet<>(Arrays.asList(validAttribute, orphanAttribute)));

			givenCartFound(cartWith(item));
			givenProductFound(product);
			@SuppressWarnings("unchecked")
			ArgumentCaptor<List<ProductAttribute>> pricedAttributes = ArgumentCaptor.forClass(List.class);
			when(pricingService.calculateProductPrice(eq(product), pricedAttributes.capture()))
					.thenReturn(finalPrice("25.00"));

			service.getById(CART_ID, store);

			verify(shoppingCartAttributeRepository).delete(orphanAttribute);
			verify(shoppingCartAttributeRepository, never()).delete(validAttribute);
			assertThat(pricedAttributes.getValue()).containsExactly(size);
			assertThat(validAttribute.getProductAttribute()).isSameAs(size);
			assertThat(item.getItemPrice()).isEqualByComparingTo("25.00");
			assertThat(item.getSubTotal()).isEqualByComparingTo("50.00");
		}

		@Test
		void itemWithoutAttributesIsPricedWithEmptyAttributeList() throws Exception {
			Product product = product(1L, "SKU-1");
			ShoppingCartItem item = item(product, 1);
			givenCartFound(cartWith(item));
			givenProductFound(product);
			@SuppressWarnings("unchecked")
			ArgumentCaptor<List<ProductAttribute>> pricedAttributes = ArgumentCaptor.forClass(List.class);
			when(pricingService.calculateProductPrice(eq(product), pricedAttributes.capture()))
					.thenReturn(finalPrice("10.00"));

			service.getById(CART_ID, store);

			assertThat(pricedAttributes.getValue()).isEmpty();
			assertThat(item.getAttributes()).isNull();
			verifyNoInteractions(shoppingCartAttributeRepository);
		}
	}

	@Nested
	class ObsoleteCarts {

		@Test
		void cartWithNoLineItemsIsDeletedAndNotReturned() throws Exception {
			ShoppingCart cart = cartWith();
			givenCartFound(cart);

			assertThat(service.getById(CART_ID, store)).isNull();
			verify(shoppingCartRepository).delete(cart);
			verifyNoInteractions(pricingService);
		}

		@Test
		void cartWithNullLineItemsIsDeletedAndNotReturned() throws Exception {
			ShoppingCart cart = cartWith();
			cart.setLineItems(null);
			givenCartFound(cart);

			assertThat(service.getById(CART_ID, store)).isNull();
			verify(shoppingCartRepository).delete(cart);
		}

		@Test
		void cartIsObsoleteWhenAProductNoLongerExists() throws Exception {
			Product removed = product(1L, "GONE");
			ShoppingCartItem item = item(removed, 1);
			ShoppingCart cart = cartWith(item);
			givenCartFound(cart);
			when(productService.getBySku("GONE", store, english)).thenReturn(null);

			assertThat(service.getById(CART_ID, store)).isNull();
			assertThat(item.isObsolete()).isTrue();
			verify(shoppingCartRepository).delete(cart);
			verifyNoInteractions(pricingService);
		}

		@Test
		void oneMissingProductMakesWholeCartObsolete() throws Exception {
			Product existing = product(1L, "EXISTS");
			Product removed = product(2L, "GONE");
			ShoppingCart cart = cartWith(item(existing, 1), item(removed, 1));
			givenCartFound(cart);
			givenProductFound(existing);
			givenPrice(existing, finalPrice("10.00"));
			when(productService.getBySku("GONE", store, english)).thenReturn(null);

			assertThat(service.getById(CART_ID, store)).isNull();
			verify(shoppingCartRepository).delete(cart);
		}

		@Test
		void unknownCartIdReturnsNullWithoutDeleting() throws Exception {
			when(shoppingCartRepository.findById(STORE_ID, CART_ID)).thenReturn(null);

			assertThat(service.getById(CART_ID, store)).isNull();
			verify(shoppingCartRepository, never()).delete(any(ShoppingCart.class));
		}
	}

	@Nested
	class LookupByCodeAndCustomer {

		@Test
		void getByCodeReturnsPricedCart() throws Exception {
			Product product = product(1L, "SKU-1");
			ShoppingCart cart = cartWith(item(product, 4));
			when(shoppingCartRepository.findByCode(STORE_ID, "abc")).thenReturn(cart);
			givenProductFound(product);
			givenPrice(product, finalPrice("2.50"));

			ShoppingCart result = service.getByCode("abc", store);

			assertThat(result).isSameAs(cart);
			assertThat(result.getLineItems().iterator().next().getSubTotal()).isEqualByComparingTo("10.00");
		}

		@Test
		void getByCodeReturnsNullWhenNoResult() throws Exception {
			when(shoppingCartRepository.findByCode(STORE_ID, "missing")).thenThrow(new NoResultException());

			assertThat(service.getByCode("missing", store)).isNull();
		}

		@Test
		void getByCodeWrapsUnexpectedErrors() {
			when(shoppingCartRepository.findByCode(STORE_ID, "boom")).thenThrow(new IllegalStateException("boom"));

			assertThatThrownBy(() -> service.getByCode("boom", store)).isInstanceOf(ServiceException.class);
		}

		@Test
		void getShoppingCartSkipsCartsAlreadyConvertedToOrders() throws Exception {
			Customer customer = customer(7L);
			Product product = product(1L, "SKU-1");
			ShoppingCart ordered = cartWith(item(product, 1));
			ordered.setOrderId(55L);
			ShoppingCart open = cartWith(item(product, 2));
			when(shoppingCartRepository.findByCustomer(7L)).thenReturn(Arrays.asList(ordered, open));
			givenProductFound(product);
			givenPrice(product, finalPrice("3.00"));

			ShoppingCart result = service.getShoppingCart(customer, store);

			assertThat(result).isSameAs(open);
			assertThat(result.getLineItems().iterator().next().getSubTotal()).isEqualByComparingTo("6.00");
		}

		@Test
		void getShoppingCartReturnsNullWhenAllCartsAreOrdered() throws Exception {
			ShoppingCart ordered = cartWith(item(product(1L, "SKU-1"), 1));
			ordered.setOrderId(55L);
			when(shoppingCartRepository.findByCustomer(7L)).thenReturn(Collections.singletonList(ordered));

			assertThat(service.getShoppingCart(customer(7L), store)).isNull();
			verifyNoInteractions(productService, pricingService);
		}

		@Test
		void getShoppingCartDeletesEmptyCart() throws Exception {
			ShoppingCart empty = cartWith();
			when(shoppingCartRepository.findByCustomer(7L)).thenReturn(Collections.singletonList(empty));

			assertThat(service.getShoppingCart(customer(7L), store)).isNull();
			verify(shoppingCartRepository).delete(empty);
		}
	}

	@Nested
	class PopulateShoppingCartItem {

		@Test
		void newItemHasQuantityOneAndCurrentPrice() throws Exception {
			Product product = product(1L, "SKU-1");
			when(pricingService.calculateProductPrice(product)).thenReturn(finalPrice("12.34"));

			ShoppingCartItem item = service.populateShoppingCartItem(product, store);

			assertThat(item.getQuantity()).isEqualTo(1);
			assertThat(item.getSku()).isEqualTo("SKU-1");
			assertThat(item.getProductId()).isEqualTo(1L);
			assertThat(item.getItemPrice()).isEqualByComparingTo("12.34");
		}

		@Test
		void nullProductIsRejected() {
			assertThatThrownBy(() -> service.populateShoppingCartItem(null, store))
					.isInstanceOf(NullPointerException.class).hasMessageContaining("Product");
		}

		@Test
		void productWithoutStoreIsRejected() {
			Product product = product(1L, "SKU-1");
			product.setMerchantStore(null);

			assertThatThrownBy(() -> service.populateShoppingCartItem(product, store))
					.isInstanceOf(NullPointerException.class).hasMessageContaining("merchantStore");
		}

		@Test
		void nullStoreIsRejected() {
			assertThatThrownBy(() -> service.populateShoppingCartItem(product(1L, "SKU-1"), null))
					.isInstanceOf(NullPointerException.class).hasMessageContaining("MerchantStore");
		}
	}

	@Nested
	class CreateShippingProduct {

		@Test
		void onlyPhysicalShippableItemsAreShippedWithTheirQuantityAndPrice() throws Exception {
			Product shippable = product(1L, "BOX");
			shippable.setProductShipeable(true);
			Product virtual = product(2L, "EBOOK");
			virtual.setProductVirtual(true);
			virtual.setProductShipeable(true);
			Product notShippable = product(3L, "SERVICE");
			notShippable.setProductShipeable(false);

			ShoppingCartItem box = item(shippable, 4);
			FinalPrice boxPrice = finalPrice("7.00");
			box.setFinalPrice(boxPrice);
			ShoppingCart cart = cartWith(box, item(virtual, 1), item(notShippable, 2));

			List<ShippingProduct> shipping = service.createShippingProduct(cart);

			assertThat(shipping).hasSize(1);
			assertThat(shipping.get(0).getProduct()).isSameAs(shippable);
			assertThat(shipping.get(0).getQuantity()).isEqualTo(4);
			assertThat(shipping.get(0).getFinalPrice()).isSameAs(boxPrice);
		}

		@Test
		void returnsNullWhenNothingIsShippable() throws Exception {
			Product virtual = product(1L, "EBOOK");
			virtual.setProductVirtual(true);

			assertThat(service.createShippingProduct(cartWith(item(virtual, 3)))).isNull();
		}
	}

	@Nested
	class SaveOrUpdate {

		@Test
		void newCartIsPersisted() throws Exception {
			ShoppingCart cart = cartWith();

			service.saveOrUpdate(cart);

			verify(shoppingCartRepository).saveAndFlush(cart);
		}

		@Test
		void ipAddressIsCapturedFromUserContext() throws Exception {
			ShoppingCart cart = cartWith();
			try (UserContext context = UserContext.create()) {
				context.setIpAddress("10.0.0.1");
				service.saveOrUpdate(cart);
			}

			assertThat(cart.getIpAddress()).isEqualTo("10.0.0.1");
		}

		@Test
		void nullCartIsRejected() {
			assertThatThrownBy(() -> service.saveOrUpdate(null)).isInstanceOf(NullPointerException.class);
		}

		@Test
		void cartWithoutStoreIsRejected() {
			ShoppingCart cart = cartWith();
			cart.setMerchantStore(null);

			assertThatThrownBy(() -> service.saveOrUpdate(cart)).isInstanceOf(NullPointerException.class);
			verifyNoInteractions(shoppingCartRepository);
		}
	}

	@Nested
	class MergeShoppingCarts {

		@Test
		void sameCustomerWithTwoNonEmptyCartsKeepsUserCartUntouched() throws Exception {
			Product product = product(1L, "SKU-1");
			ShoppingCart userCart = cartWith(item(product, 2));
			userCart.setCustomerId(7L);
			ShoppingCart sessionCart = cartWith(item(product, 3));
			sessionCart.setCustomerId(7L);

			ShoppingCart merged = service.mergeShoppingCarts(userCart, sessionCart, store);

			assertThat(merged).isSameAs(userCart);
			assertThat(merged.getLineItems().iterator().next().getQuantity()).isEqualTo(2);
			verify(shoppingCartRepository, never()).saveAndFlush(any());
			verify(shoppingCartRepository, never()).delete(any(ShoppingCart.class));
		}

		@Test
		void duplicateItemWithAttributesHasQuantitiesSummed() throws Exception {
			Product product = product(1L, "SKU-1");
			ShoppingCartItem userItem = item(product, 2);
			userItem.addAttributes(new ShoppingCartAttributeItem(userItem, 10L));
			ShoppingCart userCart = cartWith(userItem);
			ShoppingCart sessionCart = cartWith(item(product, 3));
			givenProductFound(product);
			when(pricingService.calculateProductPrice(product)).thenReturn(finalPrice("10.00"));

			ShoppingCart merged = service.mergeShoppingCarts(userCart, sessionCart, store);

			assertThat(merged.getLineItems()).hasSize(1);
			assertThat(userItem.getQuantity()).isEqualTo(5);
			verify(shoppingCartRepository).saveAndFlush(userCart);
			verify(shoppingCartRepository).delete(sessionCart);
		}

		@Test
		void newSessionItemIsAddedWithSessionQuantityAndCurrentPrice() throws Exception {
			Product existing = product(1L, "SKU-1");
			Product added = product(2L, "SKU-2");
			ShoppingCart userCart = cartWith(item(existing, 1));
			ShoppingCart sessionCart = cartWith(item(added, 4));
			givenProductFound(added);
			when(pricingService.calculateProductPrice(added)).thenReturn(finalPrice("8.25"));

			ShoppingCart merged = service.mergeShoppingCarts(userCart, sessionCart, store);

			assertThat(merged.getLineItems()).hasSize(2);
			ShoppingCartItem mergedItem = merged.getLineItems().stream()
					.filter(i -> "SKU-2".equals(i.getSku())).findFirst().get();
			assertThat(mergedItem.getQuantity()).isEqualTo(4);
			assertThat(mergedItem.getItemPrice()).isEqualByComparingTo("8.25");
			assertThat(mergedItem.getShoppingCart()).isSameAs(userCart);
		}

		@Test
		void duplicateItemWithoutAttributesIsAddedAsSeparateLine() throws Exception {
			Product product = product(1L, "SKU-1");
			ShoppingCartItem userItem = item(product, 2);
			ShoppingCart userCart = cartWith(userItem);
			ShoppingCart sessionCart = cartWith(item(product, 3));
			givenProductFound(product);
			when(pricingService.calculateProductPrice(product)).thenReturn(finalPrice("10.00"));

			ShoppingCart merged = service.mergeShoppingCarts(userCart, sessionCart, store);

			assertThat(merged.getLineItems()).hasSize(2);
			assertThat(userItem.getQuantity()).isEqualTo(2);
		}

		@Test
		void emptySessionCartOnlyPersistsUserCartAndRemovesSessionCart() throws Exception {
			ShoppingCart userCart = cartWith(item(product(1L, "SKU-1"), 1));
			ShoppingCart sessionCart = cartWith();

			ShoppingCart merged = service.mergeShoppingCarts(userCart, sessionCart, store);

			assertThat(merged.getLineItems()).hasSize(1);
			verify(shoppingCartRepository).saveAndFlush(userCart);
			verify(shoppingCartRepository).delete(sessionCart);
			verifyNoInteractions(productService);
		}

		@Test
		void unknownSessionProductAbortsMerge() throws Exception {
			ShoppingCart userCart = cartWith();
			ShoppingCart sessionCart = cartWith(item(product(1L, "GONE"), 1));
			when(productService.getBySku("GONE", store, english)).thenReturn(null);

			assertThatThrownBy(() -> service.mergeShoppingCarts(userCart, sessionCart, store))
					.hasMessageContaining("does not exist");
			verify(shoppingCartRepository, never()).saveAndFlush(any());
			verify(shoppingCartRepository, never()).delete(any(ShoppingCart.class));
		}

		@Test
		void productFromAnotherStoreAbortsMerge() throws Exception {
			Product foreign = product(1L, "FOREIGN");
			foreign.setMerchantStore(merchantStore(2));
			ShoppingCart userCart = cartWith();
			ShoppingCart sessionCart = cartWith(item(foreign, 1));
			when(productService.getBySku("FOREIGN", store, english)).thenReturn(foreign);

			assertThatThrownBy(() -> service.mergeShoppingCarts(userCart, sessionCart, store))
					.hasMessageContaining("does not belong to merchant");
			verify(shoppingCartRepository, never()).saveAndFlush(any());
		}

		@Test
		@Disabled("Known defect: ShoppingCartServiceImpl.mergeShoppingCarts never resets duplicateFound, "
				+ "so every session item after the first duplicate is dropped")
		void itemsAfterADuplicateAreStillMerged() throws Exception {
			Product duplicated = product(1L, "SKU-1");
			Product other = product(2L, "SKU-2");
			ShoppingCartItem userItem = item(duplicated, 1);
			userItem.addAttributes(new ShoppingCartAttributeItem(userItem, 10L));
			ShoppingCart userCart = cartWith(userItem);
			ShoppingCart sessionCart = cartWith(item(duplicated, 1), item(other, 1));
			givenProductFound(duplicated);
			givenProductFound(other);
			when(pricingService.calculateProductPrice(any(Product.class))).thenReturn(finalPrice("1.00"));

			ShoppingCart merged = service.mergeShoppingCarts(userCart, sessionCart, store);

			assertThat(merged.getLineItems()).extracting(ShoppingCartItem::getSku)
					.containsExactlyInAnyOrder("SKU-1", "SKU-2");
		}
	}

	@Nested
	class DeleteShoppingCartItem {

		@Test
		void attributesAreDeletedBeforeTheItem() {
			ShoppingCartItem item = item(product(1L, "SKU-1"), 1);
			ShoppingCartAttributeItem attribute = new ShoppingCartAttributeItem(item, 10L);
			attribute.setId(500L);
			item.setAttributes(new HashSet<>(Collections.singletonList(attribute)));
			when(shoppingCartItemRepository.findOne(9L)).thenReturn(item);

			service.deleteShoppingCartItem(9L);

			verify(shoppingCartAttributeRepository).deleteById(500L);
			verify(shoppingCartItemRepository).deleteById(9L);
			assertThat(item.getAttributes()).isEmpty();
		}

		@Test
		void missingItemIsIgnored() {
			when(shoppingCartItemRepository.findOne(9L)).thenReturn(null);

			service.deleteShoppingCartItem(9L);

			verify(shoppingCartItemRepository, never()).deleteById(any());
			verifyNoInteractions(shoppingCartAttributeRepository);
		}
	}

	private void givenCartFound(ShoppingCart cart) {
		when(shoppingCartRepository.findById(STORE_ID, CART_ID)).thenReturn(cart);
	}

	private void givenProductFound(Product product) throws ServiceException {
		when(productService.getBySku(product.getSku(), store, english)).thenReturn(product);
	}

	private void givenPrice(Product product, FinalPrice price) throws ServiceException {
		when(pricingService.calculateProductPrice(eq(product), anyList())).thenReturn(price);
	}

	private ShoppingCart cartWith(ShoppingCartItem... items) {
		ShoppingCart cart = new ShoppingCart();
		cart.setId(CART_ID);
		cart.setMerchantStore(store);
		Set<ShoppingCartItem> lineItems = new HashSet<>();
		for (ShoppingCartItem item : items) {
			item.setShoppingCart(cart);
			lineItems.add(item);
		}
		cart.setLineItems(lineItems);
		return cart;
	}

	private ShoppingCartItem item(Product product, int quantity) {
		ShoppingCartItem item = new ShoppingCartItem(product);
		item.setQuantity(quantity);
		return item;
	}

	private Product product(Long id, String sku) {
		Product product = new Product();
		product.setId(id);
		product.setSku(sku);
		product.setMerchantStore(store);
		return product;
	}

	private static ProductAttribute productAttribute(Long id, Product product) {
		ProductAttribute attribute = new ProductAttribute();
		attribute.setId(id);
		attribute.setProduct(product);
		return attribute;
	}

	private static FinalPrice finalPrice(String amount) {
		FinalPrice price = new FinalPrice();
		price.setFinalPrice(new BigDecimal(amount));
		price.setOriginalPrice(new BigDecimal(amount));
		return price;
	}

	private MerchantStore merchantStore(Integer id) {
		MerchantStore merchantStore = new MerchantStore();
		merchantStore.setId(id);
		merchantStore.setDefaultLanguage(english);
		return merchantStore;
	}

	private static Customer customer(Long id) {
		Customer customer = new Customer();
		customer.setId(id);
		return customer;
	}
}
