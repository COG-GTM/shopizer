package com.salesmanager.test.shop.integration.cart;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.api.io.Resource;
import org.kie.api.runtime.KieSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.salesmanager.core.business.configuration.DroolsBeanFactory;
import com.salesmanager.core.business.modules.order.total.PromoCodeCalculatorModule;
import com.salesmanager.core.business.services.shoppingcart.ShoppingCartService;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;
import com.salesmanager.shop.model.catalog.product.ReadableProduct;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
public class PromoCodeExpiryIntegrationTest extends ServicesTestSupport {

	@Autowired
	private PromoCodeCalculatorModule promoCodeCalculatorModule;

	private DroolsBeanFactory originalDroolsBeanFactory;

	@Before
	public void stubPromoRules() {
		originalDroolsBeanFactory = (DroolsBeanFactory) ReflectionTestUtils.getField(
				promoCodeCalculatorModule, "droolsBeanFactory");
		DroolsBeanFactory droolsBeanFactory = mock(DroolsBeanFactory.class);
		when(droolsBeanFactory.getKieSession(any(Resource.class))).thenReturn(mock(KieSession.class));
		ReflectionTestUtils.setField(promoCodeCalculatorModule, "droolsBeanFactory", droolsBeanFactory);
	}

	@After
	public void restorePromoRules() {
		ReflectionTestUtils.setField(promoCodeCalculatorModule, "droolsBeanFactory", originalDroolsBeanFactory);
	}

	@Autowired
	private ShoppingCartService shoppingCartService;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	public void expiredPromoIsHiddenAndCanBeResubmitted() throws Exception {
		expireAndResubmit(Date.from(Instant.now().minus(Duration.ofDays(2))));
	}

	@Test
	public void missingPromoDateIsHiddenAndCanBeResubmitted() throws Exception {
		expireAndResubmit(null);
	}

	private void expireAndResubmit(Date added) throws Exception {
		ReadableProduct product = sampleProduct("promo-" + UUID.randomUUID());
		PersistableShoppingCartItem item = new PersistableShoppingCartItem();
		item.setProduct(product.getSku());
		item.setQuantity(1);
		item.setPromoCode("ONEDAY");
		ResponseEntity<ReadableShoppingCart> created = testRestTemplate.postForEntity(
				"/api/v1/cart", item, ReadableShoppingCart.class);
		assertEquals(CREATED, created.getStatusCode());
		String code = created.getBody().getCode();
		assertEquals("ONEDAY", read(code).getPromoCode());

		Long id = created.getBody().getId();
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);
		transaction.executeWithoutResult(status -> {
			ShoppingCart cart = shoppingCartService.getById(id);
			cart.setPromoAdded(added);
			try {
				shoppingCartService.saveOrUpdate(cart);
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		});
		assertNull(read(code).getPromoCode());
		transaction.executeWithoutResult(status ->
				assertNull(shoppingCartService.getById(id).getPromoCode()));

		long before = System.currentTimeMillis();
		ResponseEntity<ReadableShoppingCart> updated = testRestTemplate.exchange(
				"/api/v1/cart/" + code, HttpMethod.PUT, new HttpEntity<>(item), ReadableShoppingCart.class);
		assertEquals(CREATED, updated.getStatusCode());
		assertEquals("ONEDAY", read(code).getPromoCode());
		transaction.executeWithoutResult(status -> {
			ShoppingCart refreshed = shoppingCartService.getById(id);
			assertEquals("ONEDAY", refreshed.getPromoCode());
			assertNotNull(refreshed.getPromoAdded());
			assertTrue(refreshed.getPromoAdded().getTime() >= before);
			assertTrue(refreshed.getPromoAdded().getTime() <= System.currentTimeMillis());
		});
	}

	private ReadableShoppingCart read(String code) {
		ResponseEntity<ReadableShoppingCart> response = testRestTemplate.getForEntity(
				"/api/v1/cart/" + code, ReadableShoppingCart.class);
		assertEquals(OK, response.getStatusCode());
		return response.getBody();
	}
}
