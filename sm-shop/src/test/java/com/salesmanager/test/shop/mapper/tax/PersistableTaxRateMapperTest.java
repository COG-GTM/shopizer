package com.salesmanager.test.shop.mapper.tax;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;

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
import com.salesmanager.core.model.reference.country.Country;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.reference.zone.Zone;
import com.salesmanager.core.model.tax.taxclass.TaxClass;
import com.salesmanager.core.model.tax.taxrate.TaxRate;
import com.salesmanager.shop.mapper.tax.PersistableTaxRateMapper;
import com.salesmanager.shop.model.tax.PersistableTaxRate;
import com.salesmanager.shop.model.tax.TaxRateDescription;

@RunWith(MockitoJUnitRunner.class)
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
	private Country country;
	private Zone zone;
	private TaxClass taxClass;
	private Language english;

	@Before
	public void setUp() throws Exception {
		store = new MerchantStore();
		store.setCode("DEFAULT");
		country = new Country();
		zone = new Zone();
		taxClass = new TaxClass("DEFAULT");
		english = new Language("en");
		when(countryService.getByCode("US")).thenReturn(country);
		when(zoneService.getByCode("NY")).thenReturn(zone);
		when(taxClassService.getByCode("DEFAULT", store)).thenReturn(taxClass);
		when(languageService.getByCode("en")).thenReturn(english);
	}

	@Test
	public void convertIgnoresClientIdsAndLinksNewDescription() {
		PersistableTaxRate source = source();
		source.setId(99L);
		TaxRateDescription description = new TaxRateDescription();
		description.setId(77L);
		description.setLanguage("en");
		description.setName("English rate");
		description.setDescription("English description");
		source.setDescriptions(Collections.singletonList(description));

		TaxRate result = mapper.convert(source, store, english);

		assertNull(result.getId());
		assertEquals(1, result.getDescriptions().size());
		assertNull(result.getDescriptions().get(0).getId());
		assertSame(result, result.getDescriptions().get(0).getTaxRate());
		assertSame(english, result.getDescriptions().get(0).getLanguage());
	}

	@Test
	public void mergeUpdatesExistingDescriptionInPlaceByLanguage() {
		TaxRate destination = new TaxRate();
		destination.setId(5L);
		com.salesmanager.core.model.tax.taxrate.TaxRateDescription existing =
				new com.salesmanager.core.model.tax.taxrate.TaxRateDescription();
		existing.setId(12L);
		existing.setLanguage(english);
		existing.setName("Old name");
		existing.setDescription("Old description");
		existing.setTaxRate(destination);
		destination.getDescriptions().add(existing);

		PersistableTaxRate source = source();
		source.setId(99L);
		TaxRateDescription description = new TaxRateDescription();
		description.setId(77L);
		description.setLanguage("en");
		description.setName("Updated name");
		description.setDescription("Updated description");
		source.setDescriptions(Collections.singletonList(description));

		TaxRate result = mapper.merge(source, destination, store, english);

		assertSame(destination, result);
		assertEquals(Long.valueOf(5L), result.getId());
		assertEquals(1, result.getDescriptions().size());
		assertSame(existing, result.getDescriptions().get(0));
		assertEquals(Long.valueOf(12L), existing.getId());
		assertEquals("Updated name", existing.getName());
		assertEquals("Updated description", existing.getDescription());
	}

	private PersistableTaxRate source() {
		PersistableTaxRate source = new PersistableTaxRate();
		source.setCode("RATE1");
		source.setCountry("US");
		source.setZone("NY");
		source.setTaxClass("DEFAULT");
		source.setRate(new BigDecimal("5.00"));
		return source;
	}
}
