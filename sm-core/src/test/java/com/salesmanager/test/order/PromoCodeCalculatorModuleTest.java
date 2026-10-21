package com.salesmanager.test.order;

import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.configuration.DroolsBeanFactory;
import com.salesmanager.core.business.modules.order.total.PromoCodeCalculatorModule;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.OrderSummary;

public class PromoCodeCalculatorModuleTest {

	private PromoCodeCalculatorModule module;

	@Before
	public void setUp() {
		module = new PromoCodeCalculatorModule();
		ReflectionTestUtils.setField(module, "droolsBeanFactory", new DroolsBeanFactory());
	}

	@Test
	public void returnsNullForBlankPromoCode() throws Exception {
		OrderSummary summary = new OrderSummary();
		summary.setPromoCode(" ");

		assertNull(module.caculateProductPiceVariation(summary, null, null, null, new MerchantStore()));
	}

	@Test
	public void returnsNullForExpiredPromoCode() throws Exception {
		OrderSummary summary = new OrderSummary();
		summary.setPromoCode("Test1234");

		assertNull(module.caculateProductPiceVariation(summary, null, null, null, new MerchantStore()));
	}
}
