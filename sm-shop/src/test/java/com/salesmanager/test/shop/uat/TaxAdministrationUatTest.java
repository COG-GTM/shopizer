package com.salesmanager.test.shop.uat;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.reference.country.CountryService;
import com.salesmanager.core.business.services.reference.zone.ZoneService;
import com.salesmanager.core.business.services.tax.TaxClassService;
import com.salesmanager.core.business.services.tax.TaxRateService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.tax.taxclass.TaxClass;
import com.salesmanager.core.model.tax.taxrate.TaxRate;
import com.salesmanager.shop.model.tax.PersistableTaxClass;
import com.salesmanager.shop.model.tax.PersistableTaxRate;
import com.salesmanager.shop.model.tax.TaxRateDescription;
import com.salesmanager.test.shop.common.ServicesTestSupport;

@RunWith(SpringRunner.class)
public class TaxAdministrationUatTest extends ServicesTestSupport {

	@Autowired private MerchantStoreService stores;
	@Autowired private TaxClassService classes;
	@Autowired private TaxRateService rates;
	@Autowired private CountryService countries;
	@Autowired private ZoneService zones;

	private MerchantStore owner;
	private MerchantStore foreign;
	private HttpHeaders headers;
	private TaxClass victimClass;
	private TaxRate victimRate;
	private final List<Long> createdClasses = new ArrayList<>();
	private final List<Long> createdRates = new ArrayList<>();

	@Before
	public void fixtures() throws Exception {
		owner = stores.getByCode("DEFAULT");
		foreign = new MerchantStore();
		foreign.setCode("UAT_" + UUID.randomUUID().toString().replace("-", ""));
		foreign.setStorename("Tax isolation fixture");
		foreign.setStorephone("1234567890");
		foreign.setStorecity("Montreal");
		foreign.setStorepostalcode("H1H1H1");
		foreign.setStoreEmailAddress("tax-uat@example.com");
		foreign.setCountry(owner.getCountry());
		foreign.setCurrency(owner.getCurrency());
		foreign.setDefaultLanguage(owner.getDefaultLanguage());
		foreign.getLanguages().add(owner.getDefaultLanguage());
		stores.saveOrUpdate(foreign);
		victimClass = new TaxClass("VICTIM");
		victimClass.setMerchantStore(foreign);
		victimClass = classes.saveOrUpdate(victimClass);
		victimRate = new TaxRate();
		victimRate.setCode("VICTIM");
		victimRate.setMerchantStore(foreign);
		victimRate.setTaxClass(victimClass);
		victimRate.setTaxRate(new BigDecimal("5"));
		victimRate.setCountry(countries.getByCode("US"));
		victimRate.setZone(zones.getByCode("NY"));
		com.salesmanager.core.model.tax.taxrate.TaxRateDescription victimDescription =
				new com.salesmanager.core.model.tax.taxrate.TaxRateDescription();
		victimDescription.setLanguage(owner.getDefaultLanguage());
		victimDescription.setName("Victim rate");
		victimDescription.setTaxRate(victimRate);
		victimRate.getDescriptions().add(victimDescription);
		victimRate = rates.saveOrUpdate(victimRate);
		headers = getHeader();
		assertTrue(headers.getFirst("Authorization").startsWith("Bearer "));
	}

	@After
	public void cleanup() throws Exception {
		for (Long id : createdRates) rates.delete(rates.getById(id, owner));
		for (Long id : createdClasses) classes.delete(classes.getById(id));
		if (victimRate != null) {
			TaxRate persisted = rates.getById(victimRate.getId());
			if (persisted != null) rates.delete(persisted);
		}
		if (victimClass != null) classes.delete(classes.getById(victimClass.getId()));
		if (foreign != null && foreign.getId() != null) stores.delete(foreign);
	}

	@Test
	public void createClassWithForeignIdInsertsAndLeavesVictimUntouched() throws Exception {
		PersistableTaxClass body = classBody();
		body.setId(victimClass.getId());
		body.setStore(foreign.getCode());
		ResponseEntity<JsonNode> response = testRestTemplate.postForEntity(
				"/api/v1/private/tax/class?store=DEFAULT", new HttpEntity<>(body, headers), JsonNode.class);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertTrue(response.getBody().path("id").isIntegralNumber());
		long id = response.getBody().path("id").asLong();
		assertNotEquals(victimClass.getId().longValue(), id);
		createdClasses.add(id);
		JsonNode saved = get("/api/v1/private/tax/class/" + body.getCode() + "?store=DEFAULT");
		assertEquals(id, saved.path("id").asLong());
		assertEquals(body.getName(), saved.path("name").asText());
		assertVictimUnchanged();
	}

	@Test
	public void createRateWithForeignIdInsertsAndUpdateKeepsPathIdentity() throws Exception {
		PersistableTaxRate body = new PersistableTaxRate();
		body.setId(victimRate.getId());
		body.setCode("UR" + suffix());
		body.setCountry("US");
		body.setZone("NY");
		body.setTaxClass("DEFAULT");
		body.setRate(new BigDecimal("7.5"));
		TaxRateDescription description = new TaxRateDescription();
		description.setId(987654L);
		description.setLanguage("en");
		description.setName("New rate");
		body.getDescriptions().add(description);
		ResponseEntity<JsonNode> response = testRestTemplate.postForEntity(
				"/api/v1/private/tax/rate?store=DEFAULT", new HttpEntity<>(body, headers), JsonNode.class);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		long id = response.getBody().path("id").asLong();
		assertTrue(id > 0);
		assertNotEquals(victimRate.getId().longValue(), id);
		createdRates.add(id);
		JsonNode saved = get("/api/v1/private/tax/rate/" + id);
		assertEquals(body.getCode(), saved.path("code").asText());
		assertEquals(7.5, saved.path("rate").asDouble(), 0.0001);
		body.setRate(new BigDecimal("8.5"));
		description.setName("Updated rate");
		ResponseEntity<Void> updated = testRestTemplate.exchange("/api/v1/private/tax/rate/" + id,
				HttpMethod.PUT, new HttpEntity<>(body, headers), Void.class);
		assertEquals(HttpStatus.OK, updated.getStatusCode());
		assertEquals(8.5, get("/api/v1/private/tax/rate/" + id).path("rate").asDouble(), 0.0001);
		assertVictimUnchanged();
	}

	@Test
	public void classUpdateUsesPathIdRatherThanForeignBodyId() throws Exception {
		PersistableTaxClass body = classBody();
		ResponseEntity<JsonNode> created = testRestTemplate.postForEntity("/api/v1/private/tax/class",
				new HttpEntity<>(body, headers), JsonNode.class);
		assertEquals(HttpStatus.OK, created.getStatusCode());
		long id = created.getBody().path("id").asLong();
		createdClasses.add(id);
		body.setId(victimClass.getId());
		body.setName("Updated class");
		ResponseEntity<Void> updated = testRestTemplate.exchange("/api/v1/private/tax/class/" + id,
				HttpMethod.PUT, new HttpEntity<>(body, headers), Void.class);

		assertEquals(HttpStatus.OK, updated.getStatusCode());
		JsonNode saved = get("/api/v1/private/tax/class/" + body.getCode());
		assertEquals(id, saved.path("id").asLong());
		assertEquals("Updated class", saved.path("name").asText());
		assertVictimUnchanged();
	}

	@Test
	public void taxAdministrationRequiresAdminAuthentication() {
		ResponseEntity<String> response = testRestTemplate.getForEntity("/api/v1/private/tax/class", String.class);
		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
		assertTrue(get("/api/v1/private/tax/class").path("items").isArray());
	}

	private JsonNode get(String path) {
		ResponseEntity<JsonNode> response = testRestTemplate.exchange(path, HttpMethod.GET,
				new HttpEntity<>(headers), JsonNode.class);
		assertEquals(path, HttpStatus.OK, response.getStatusCode());
		return response.getBody();
	}

	private void assertVictimUnchanged() {
		JsonNode taxClass = get("/api/v1/private/tax/class/VICTIM?store=" + foreign.getCode());
		assertEquals(victimClass.getId().longValue(), taxClass.path("id").asLong());
		assertEquals("VICTIM", taxClass.path("name").asText());
		JsonNode rate = get("/api/v1/private/tax/rate/" + victimRate.getId() + "?store=" + foreign.getCode());
		assertEquals("VICTIM", rate.path("code").asText());
		assertEquals(5.0, rate.path("rate").asDouble(), 0.0001);
	}

	private PersistableTaxClass classBody() {
		PersistableTaxClass body = new PersistableTaxClass();
		body.setCode("UC" + suffix());
		body.setName("New class");
		return body;
	}

	private String suffix() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
	}
}
