package com.salesmanager.test.shop.tax;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.services.reference.country.CountryService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.business.services.reference.zone.ZoneService;
import com.salesmanager.core.business.services.tax.TaxClassService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.tax.taxclass.TaxClass;
import com.salesmanager.core.model.tax.taxrate.TaxRate;
import com.salesmanager.core.model.tax.taxrate.TaxRateDescription;
import com.salesmanager.shop.mapper.tax.PersistableTaxClassMapper;
import com.salesmanager.shop.mapper.tax.PersistableTaxRateMapper;
import com.salesmanager.shop.model.tax.PersistableTaxClass;
import com.salesmanager.shop.model.tax.PersistableTaxRate;

public class TaxMapperIdentityTest {

	private final MerchantStore store = new MerchantStore(1, "DEFAULT", "Default");
	private final Language english = new Language();
	private PersistableTaxRateMapper mapper;
	private TaxClassService taxClasses;

	@Before
	public void setUp() throws Exception {
		english.setCode("en");
		mapper = new PersistableTaxRateMapper();
		taxClasses = mock(TaxClassService.class);
		LanguageService languages = mock(LanguageService.class);
		when(languages.getByCode("en")).thenReturn(english);
		ReflectionTestUtils.setField(mapper, "countryService", mock(CountryService.class));
		ReflectionTestUtils.setField(mapper, "zoneService", mock(ZoneService.class));
		ReflectionTestUtils.setField(mapper, "languageService", languages);
		ReflectionTestUtils.setField(mapper, "taxClassService", taxClasses);
	}

	@Test
	public void classConvertIgnoresBodyIdAndBodyStore() {
		PersistableTaxClass body = classBody();
		TaxClass result = new PersistableTaxClassMapper().convert(body, store, english);
		assertNull(result.getId());
		assertSame(store, result.getMerchantStore());
		assertEquals("NEW", result.getCode());
		assertEquals("New class", result.getTitle());
		assertEquals(Long.valueOf(999L), body.getId());
	}

	@Test
	public void classMergePreservesDestinationIdEvenWhenBodyIdIsNull() {
		TaxClass target = new TaxClass();
		target.setId(17L);
		PersistableTaxClass body = classBody();
		body.setId(null);

		assertSame(target, new PersistableTaxClassMapper().merge(body, target, store, english));
		assertEquals(Long.valueOf(17L), target.getId());
		assertEquals("New class", target.getTitle());
	}

	@Test
	public void rateConvertGeneratesNewIdentitiesAndResolvesClassInCallerStore() throws Exception {
		PersistableTaxRate body = rateBody();
		TaxClass ownClass = new TaxClass();
		when(taxClasses.getByCode("STANDARD", store)).thenReturn(ownClass);

		TaxRate result = mapper.convert(body, store, english);

		assertNull(result.getId());
		assertNull(result.getDescriptions().get(0).getId());
		assertSame(result, result.getDescriptions().get(0).getTaxRate());
		assertSame(ownClass, result.getTaxClass());
		assertSame(store, result.getMerchantStore());
		assertEquals(new BigDecimal("7.5"), result.getTaxRate());
		verify(taxClasses).getByCode("STANDARD", store);
	}

	@Test
	public void rateMergeRetainsExistingLanguageRowAndItsIdentity() {
		TaxRate target = new TaxRate();
		target.setId(17L);
		TaxRateDescription existing = new TaxRateDescription();
		existing.setId(23L);
		existing.setLanguage(english);
		existing.setTaxRate(target);
		target.getDescriptions().add(existing);

		assertSame(target, mapper.merge(rateBody(), target, store, english));
		assertEquals(Long.valueOf(17L), target.getId());
		assertEquals(1, target.getDescriptions().size());
		assertSame(existing, target.getDescriptions().get(0));
		assertEquals(Long.valueOf(23L), existing.getId());
		assertEquals("New rate", existing.getName());
	}

	@Test
	public void addingLanguageNeverUsesRateOrDescriptionRequestId() {
		TaxRate target = new TaxRate();
		target.setId(17L);
		mapper.merge(rateBody(), target, store, english);

		assertEquals(Long.valueOf(17L), target.getId());
		assertEquals(1, target.getDescriptions().size());
		assertNull(target.getDescriptions().get(0).getId());
		assertSame(english, target.getDescriptions().get(0).getLanguage());
	}

	private PersistableTaxClass classBody() {
		PersistableTaxClass body = new PersistableTaxClass();
		body.setId(999L);
		body.setStore("OTHER");
		body.setCode("NEW");
		body.setName("New class");
		return body;
	}

	private PersistableTaxRate rateBody() {
		PersistableTaxRate body = new PersistableTaxRate();
		body.setId(999L);
		body.setStore("OTHER");
		body.setCode("NEW");
		body.setTaxClass("STANDARD");
		body.setRate(new BigDecimal("7.5"));
		com.salesmanager.shop.model.tax.TaxRateDescription desc =
				new com.salesmanager.shop.model.tax.TaxRateDescription();
		desc.setId(888L);
		desc.setLanguage("en");
		desc.setName("New rate");
		body.getDescriptions().add(desc);
		return body;
	}
}
