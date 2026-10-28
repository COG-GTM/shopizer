package com.salesmanager.test.order;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.modules.order.total.PromoCode;

public class PromoCodeTest {

	private TimeZone previousTimeZone;

	@Before
	public void saveDefaultTimeZone() {
		previousTimeZone = TimeZone.getDefault();
	}

	@After
	public void restoreDefaultTimeZone() {
		TimeZone.setDefault(previousTimeZone);
	}

	@Test
	public void matchesIgnoringCaseAndSurroundingWhitespace() {
		PromoCode promoCode = new PromoCode();
		promoCode.setCode(" SPRING10 ");

		assertTrue(promoCode.matches(" spring10 "));
	}

	@Test
	public void doesNotMatchNullCodes() {
		PromoCode promoCode = new PromoCode();
		promoCode.setCode("SPRING10");

		assertFalse(promoCode.matches(null));
		promoCode.setCode(null);
		assertFalse(promoCode.matches("SPRING10"));
	}

	@Test
	public void validityWindowIncludesStartAndEndDates() {
		PromoCode promoCode = validPromoCode();
		Date start = Date.from(Instant.parse("2026-03-01T00:00:00.000Z"));
		Date beforeStart = Date.from(Instant.parse("2026-02-28T23:59:59.999Z"));
		Date end = Date.from(Instant.parse("2026-03-01T23:59:59.999Z"));
		Date afterEnd = Date.from(Instant.parse("2026-03-02T00:00:00.000Z"));

		assertTrue(promoCode.isValidOn(start));
		assertFalse(promoCode.isValidOn(beforeStart));
		assertTrue(promoCode.isValidOn(end));
		assertFalse(promoCode.isValidOn(afterEnd));
	}

	@Test
	public void acceptsDiscountOfOneAndRejectsOutOfRangeDiscounts() {
		PromoCode promoCode = validPromoCode();
		Date date = Date.from(Instant.parse("2026-03-01T12:00:00Z"));
		promoCode.setDiscount(1.0);
		assertTrue(promoCode.isValidOn(date));

		for (Double discount : new Double[] { 0.0, -0.01, 1.01, Double.NaN, null }) {
			promoCode.setDiscount(discount);
			assertFalse(promoCode.isValidOn(date));
		}
	}

	@Test
	public void rejectsInactiveOrUndatedCodes() {
		PromoCode promoCode = validPromoCode();
		Date date = Date.from(Instant.parse("2026-03-01T12:00:00Z"));

		promoCode.setActive(false);
		assertFalse(promoCode.isValidOn(date));

		promoCode.setActive(true);
		promoCode.setStartDate(null);
		assertFalse(promoCode.isValidOn(date));

		promoCode = validPromoCode();
		promoCode.setEndDate(null);
		assertFalse(promoCode.isValidOn(date));
	}

	@Test
	public void deserializesDatesAsUtcAndDefaultsActiveToTrue() throws Exception {
		TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
		String json = "[{\"code\":\"SPRING10\",\"discount\":0.10,"
				+ "\"startDate\":\"2026-03-01\",\"endDate\":\"2026-03-31\","
				+ "\"unknownProperty\":\"ignored\"}]";

		List<PromoCode> promoCodes = new ObjectMapper().readValue(json, new TypeReference<List<PromoCode>>() {});

		assertEquals(1, promoCodes.size());
		assertEquals(Instant.parse("2026-03-01T00:00:00Z").toEpochMilli(),
				promoCodes.get(0).getStartDate().getTime());
		assertEquals(Instant.parse("2026-03-31T00:00:00Z").toEpochMilli(),
				promoCodes.get(0).getEndDate().getTime());
		assertTrue(promoCodes.get(0).isActive());
	}

	private static PromoCode validPromoCode() {
		PromoCode promoCode = new PromoCode();
		promoCode.setCode("SPRING10");
		promoCode.setDiscount(0.10);
		promoCode.setStartDate(Date.from(Instant.parse("2026-03-01T00:00:00Z")));
		promoCode.setEndDate(Date.from(Instant.parse("2026-03-01T00:00:00Z")));
		return promoCode;
	}
}
