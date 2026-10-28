package com.salesmanager.shop.populator.order.transaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.Before;
import org.junit.Test;

import com.salesmanager.core.business.exception.ConversionException;
import com.salesmanager.core.business.services.catalog.pricing.PricingService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.payments.Payment;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.core.model.payments.TransactionType;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;

public class PersistablePaymentPopulatorTest {

	private PersistablePaymentPopulator populator;
	private PricingService pricingService;
	private MerchantStore store;
	private Language language;

	@Before
	public void setUp() {
		populator = new PersistablePaymentPopulator();
		pricingService = mock(PricingService.class);
		store = mock(MerchantStore.class);
		language = mock(Language.class);
		populator.setPricingService(pricingService);
	}

	@Test
	public void doesNotConvertNullOrBlankAmounts() throws Exception {
		PersistablePayment nullAmount = source();
		Payment nullAmountTarget = populator.populate(nullAmount, null, store, language);
		assertNull(nullAmountTarget.getAmount());
		assertEquals(PaymentType.CREDITCARD, nullAmountTarget.getPaymentType());
		assertEquals(TransactionType.AUTHORIZECAPTURE, nullAmountTarget.getTransactionType());

		PersistablePayment blankAmount = source();
		blankAmount.setAmount("   ");
		Payment blankAmountTarget = populator.populate(blankAmount, null, store, language);
		assertNull(blankAmountTarget.getAmount());
		assertEquals(PaymentType.CREDITCARD, blankAmountTarget.getPaymentType());
		verify(pricingService, never()).getAmount(org.mockito.ArgumentMatchers.any(String.class));
	}

	@Test
	public void convertsSubmittedAmountThroughPricingService() throws Exception {
		PersistablePayment source = source();
		source.setAmount("12.50");
		BigDecimal converted = new BigDecimal("12.50");
		when(pricingService.getAmount("12.50")).thenReturn(converted);

		Payment target = populator.populate(source, null, store, language);

		assertSame(converted, target.getAmount());
		verify(pricingService).getAmount("12.50");
	}

	@Test
	public void leavesDefaultTransactionTypeWhenSourceTypeIsNullOrBlank() throws Exception {
		PersistablePayment nullType = source();
		Payment nullTypeTarget = populator.populate(nullType, null, store, language);
		assertEquals(TransactionType.AUTHORIZECAPTURE, nullTypeTarget.getTransactionType());

		PersistablePayment blankType = source();
		blankType.setTransactionType("  ");
		Payment blankTypeTarget = populator.populate(blankType, null, store, language);
		assertEquals(TransactionType.AUTHORIZECAPTURE, blankTypeTarget.getTransactionType());
	}

	@Test
	public void mapsModulePaymentTypeAndTokenMetadata() throws Exception {
		PersistablePayment source = source();
		source.setPaymentModule("stripe");
		source.setPaymentType("CREDITCARD");
		source.setTransactionType("CAPTURE");
		source.setPaymentToken("tok_test");

		Payment target = populator.populate(source, null, store, language);

		assertEquals("stripe", target.getModuleName());
		assertEquals(PaymentType.CREDITCARD, target.getPaymentType());
		assertEquals(TransactionType.CAPTURE, target.getTransactionType());
		assertEquals("tok_test", target.getPaymentMetaData().get("paymentToken"));
	}

	@Test
	public void wrapsInvalidPaymentTypeInConversionException() throws Exception {
		PersistablePayment source = source();
		source.setPaymentType("BOGUS");

		try {
			populator.populate(source, null, store, language);
			fail("Expected ConversionException");
		} catch (ConversionException e) {
			assertTrue(e.getCause() instanceof IllegalArgumentException);
		}
	}

	private PersistablePayment source() {
		PersistablePayment source = new PersistablePayment();
		source.setPaymentType("CREDITCARD");
		return source;
	}
}
