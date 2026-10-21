package com.salesmanager.test.business.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;

import org.junit.Test;

import com.salesmanager.core.business.utils.ProductPriceUtils;

/**
 * Amount parsing/formatting relies on commons-validator {@code CurrencyValidator} and
 * commons-lang3 {@code StringUtils}; both are bumped by AB-538.
 */
public class ProductPriceUtilsAmountTest {

	private final ProductPriceUtils utils = new ProductPriceUtils();

	@Test
	public void parsesPlainInteger() throws Exception {
		assertEquals(0, new BigDecimal("100").compareTo(utils.getAmount("100")));
	}

	@Test
	public void parsesDecimalAndThousandSeparators() throws Exception {
		assertEquals(0, new BigDecimal("12.5").compareTo(utils.getAmount("12.5")));
		assertEquals(0, new BigDecimal("1345.99").compareTo(utils.getAmount("1,345.99")));
	}

	@Test
	public void rejectsNonNumericNegativeAndOverPreciseAmounts() {
		assertThrows(Exception.class, () -> utils.getAmount("abc"));
		assertThrows(Exception.class, () -> utils.getAmount("-5"));
		assertThrows(Exception.class, () -> utils.getAmount("1.234"));
	}

	@Test
	public void formatsAdminAndStringAmountsWithTwoDecimals() throws Exception {
		assertEquals("1,345.50", utils.getAdminFormatedAmount(null, new BigDecimal("1345.5")));
		assertEquals("7.00", utils.getStringAmount(new BigDecimal("7")));
		assertEquals("", utils.getStringAmount(null));
	}
}
