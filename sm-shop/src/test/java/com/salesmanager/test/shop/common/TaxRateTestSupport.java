package com.salesmanager.test.shop.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.tax.PersistableTaxRate;
import com.salesmanager.shop.model.tax.ReadableTaxRate;
import com.salesmanager.shop.model.tax.TaxRateDescription;

public abstract class TaxRateTestSupport extends ServicesTestSupport {

	protected PersistableTaxRate rate(String language, String name) {
		PersistableTaxRate request = new PersistableTaxRate();
		request.setCode("tax" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
		request.setCountry("US");
		request.setZone("NY");
		request.setTaxClass("DEFAULT");
		request.setStore("DEFAULT");
		request.setPriority(0);
		request.setRate(new BigDecimal("9.25"));
		TaxRateDescription description = new TaxRateDescription();
		description.setLanguage(language);
		description.setName(name);
		request.getDescriptions().add(description);
		return request;
	}

	protected Long create(PersistableTaxRate request, HttpHeaders headers) {
		ResponseEntity<Entity> response = testRestTemplate.postForEntity("/api/v1/private/tax/rate",
				new HttpEntity<>(request, headers), Entity.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertNotNull(response.getBody().getId());
		return response.getBody().getId();
	}

	protected ReadableTaxRate read(Long id, String language, HttpHeaders headers) {
		ResponseEntity<ReadableTaxRate> response = testRestTemplate.exchange(
				"/api/v1/private/tax/rate/" + id + "?lang=" + language, HttpMethod.GET,
				new HttpEntity<>(headers), ReadableTaxRate.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		return response.getBody();
	}
}
