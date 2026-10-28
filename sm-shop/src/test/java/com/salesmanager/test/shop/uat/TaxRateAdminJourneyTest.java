package com.salesmanager.test.shop.uat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.salesmanager.shop.model.tax.PersistableTaxRate;
import com.salesmanager.shop.model.tax.ReadableTaxRate;
import com.salesmanager.test.shop.common.TaxRateTestSupport;

@RunWith(SpringRunner.class)
public class TaxRateAdminJourneyTest extends TaxRateTestSupport {

	@Test
	public void adminCreatesAndEditsIndependentRatesEvenWithClientSuppliedIds() {
		HttpHeaders headers = getHeader();
		PersistableTaxRate original = rate("en", "Original");
		Long originalId = create(original, headers);
		ReadableTaxRate before = read(originalId, "en", headers);
		PersistableTaxRate second = rate("en", "Independent");
		second.setId(originalId);
		second.getDescriptions().get(0).setId(before.getDescription().getId());

		Long secondId = create(second, headers);

		assertNotEquals(originalId, secondId);
		ReadableTaxRate created = read(secondId, "en", headers);
		assertNotEquals(before.getDescription().getId(), created.getDescription().getId());
		assertEquals("Independent", created.getDescription().getName());
		second.setId(originalId);
		second.setRate(new BigDecimal("7.50"));
		second.getDescriptions().get(0).setName("Updated");
		ResponseEntity<Void> updated = testRestTemplate.exchange("/api/v1/private/tax/rate/" + secondId,
				HttpMethod.PUT, new HttpEntity<>(second, headers), Void.class);
		assertEquals(HttpStatus.OK, updated.getStatusCode());
		ReadableTaxRate after = read(secondId, "en", headers);
		assertEquals(created.getDescription().getId(), after.getDescription().getId());
		assertEquals("Updated", after.getDescription().getName());
		assertEquals(0, new BigDecimal(after.getRate()).compareTo(new BigDecimal("7.50")));
		ReadableTaxRate untouched = read(originalId, "en", headers);
		assertEquals(original.getCode(), untouched.getCode());
		assertEquals(before.getDescription().getId(), untouched.getDescription().getId());
		assertEquals("Original", untouched.getDescription().getName());
		assertEquals(0, new BigDecimal(untouched.getRate()).compareTo(original.getRate()));
	}

	@Test
	public void taxManagementRequiresAdminAuthentication() {
		String url = "/api/v1/private/tax/rates";
		assertEquals(HttpStatus.OK, testRestTemplate.exchange(url, HttpMethod.GET,
				new HttpEntity<>(getHeader()), String.class).getStatusCode());
		assertDenied(testRestTemplate.getForEntity(url, String.class));
	}

	private void assertDenied(ResponseEntity<?> response) {
		assertTrue("Expected 401/403, got " + response.getStatusCode(),
				response.getStatusCode() == HttpStatus.UNAUTHORIZED || response.getStatusCode() == HttpStatus.FORBIDDEN);
	}
}
