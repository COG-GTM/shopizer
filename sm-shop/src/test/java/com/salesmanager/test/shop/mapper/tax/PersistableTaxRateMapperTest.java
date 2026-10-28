package com.salesmanager.test.shop.mapper.tax;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.services.reference.country.CountryService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.business.services.reference.zone.ZoneService;
import com.salesmanager.core.business.services.tax.TaxClassService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.tax.taxrate.TaxRate;
import com.salesmanager.shop.mapper.tax.PersistableTaxRateMapper;
import com.salesmanager.shop.model.tax.PersistableTaxRate;
import com.salesmanager.shop.model.tax.TaxRateDescription;

@RunWith(MockitoJUnitRunner.Silent.class)
public class PersistableTaxRateMapperTest {

	@Mock
	private CountryService countryService;
	@Mock
	private ZoneService zoneService;
	@Mock
	private LanguageService languageService;
	@Mock
	private TaxClassService taxClassService;

	@InjectMocks
	private PersistableTaxRateMapper mapper;

	private MerchantStore store;
	private Language en;

	@Before
	public void setUp() throws Exception {
		store = new MerchantStore();
		store.setId(2);
		store.setCode("ACME");
		en = new Language("en");
		en.setId(1);
		when(languageService.getByCode("en")).thenReturn(en);
	}

	private static PersistableTaxRate request(Long id, Long descriptionId) {
		PersistableTaxRate source = new PersistableTaxRate();
		source.setId(id);
		source.setCode("X");
		source.setRate(BigDecimal.ZERO);
		source.setCountry("CA");
		source.setZone("QC");
		source.setTaxClass("DEFAULT");
		TaxRateDescription description = new TaxRateDescription();
		description.setId(descriptionId);
		description.setLanguage("en");
		description.setName("name");
		source.getDescriptions().add(description);
		return source;
	}

	@Test
	public void convertIgnoresClientSuppliedIds() {
		TaxRate model = mapper.convert(request(12L, 34L), store, en);

		assertNull(model.getId());
		assertEquals(1, model.getDescriptions().size());
		assertNull(model.getDescriptions().get(0).getId());
		assertSame(store, model.getMerchantStore());
	}

	@Test
	public void mergeKeepsExistingIdsAndMatchesDescriptionsByLanguage() {
		TaxRate existing = new TaxRate();
		existing.setId(5L);
		existing.setMerchantStore(store);
		com.salesmanager.core.model.tax.taxrate.TaxRateDescription owned = new com.salesmanager.core.model.tax.taxrate.TaxRateDescription();
		owned.setId(50L);
		owned.setLanguage(en);
		owned.setTaxRate(existing);
		existing.getDescriptions().add(owned);

		TaxRate model = mapper.merge(request(12L, 34L), existing, store, en);

		assertEquals(Long.valueOf(5L), model.getId());
		assertEquals(1, model.getDescriptions().size());
		assertEquals(Long.valueOf(50L), model.getDescriptions().get(0).getId());
		assertEquals("name", model.getDescriptions().get(0).getName());
	}

	@Test
	public void mergeDoesNotAdoptForeignDescriptionIdForNewLanguage() {
		TaxRate existing = new TaxRate();
		existing.setId(5L);
		existing.setMerchantStore(store);

		TaxRate model = mapper.merge(request(null, 34L), existing, store, en);

		assertEquals(Long.valueOf(5L), model.getId());
		assertEquals(1, model.getDescriptions().size());
		assertNull(model.getDescriptions().get(0).getId());
	}
}
