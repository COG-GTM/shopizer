package com.salesmanager.test.shop.tax;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.salesmanager.core.business.services.reference.country.CountryService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.business.services.reference.zone.ZoneService;
import com.salesmanager.core.business.services.tax.TaxClassService;
import com.salesmanager.core.business.services.tax.TaxRateService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.tax.taxclass.TaxClass;
import com.salesmanager.core.model.tax.taxrate.TaxRate;
import com.salesmanager.shop.mapper.tax.PersistableTaxClassMapper;
import com.salesmanager.shop.mapper.tax.PersistableTaxRateMapper;
import com.salesmanager.shop.mapper.tax.ReadableTaxClassMapper;
import com.salesmanager.shop.mapper.tax.ReadableTaxRateMapper;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.tax.PersistableTaxClass;
import com.salesmanager.shop.model.tax.PersistableTaxRate;
import com.salesmanager.shop.model.tax.TaxRateDescription;
import com.salesmanager.shop.store.facade.tax.TaxFacadeImpl;

@RunWith(MockitoJUnitRunner.class)
public class TaxFacadeImplTest {

	@Mock
	private TaxClassService taxClassService;

	@Mock
	private TaxRateService taxRateService;

	@Mock
	private CountryService countryService;

	@Mock
	private ZoneService zoneService;

	@Mock
	private LanguageService languageService;

	@Spy
	private PersistableTaxClassMapper persistableTaxClassMapper = new PersistableTaxClassMapper();

	@Mock
	private ReadableTaxClassMapper readableTaxClassMapper;

	@Mock
	private ReadableTaxRateMapper readableTaxRateMapper;

	@InjectMocks
	private TaxFacadeImpl taxFacade;

	private final Language language = new Language();

	@Before
	public void wireTaxRateMapper() {
		PersistableTaxRateMapper persistableTaxRateMapper = new PersistableTaxRateMapper();
		ReflectionTestUtils.setField(persistableTaxRateMapper, "countryService", countryService);
		ReflectionTestUtils.setField(persistableTaxRateMapper, "zoneService", zoneService);
		ReflectionTestUtils.setField(persistableTaxRateMapper, "languageService", languageService);
		ReflectionTestUtils.setField(persistableTaxRateMapper, "taxClassService", taxClassService);
		ReflectionTestUtils.setField(taxFacade, "persistableTaxRateMapper", persistableTaxRateMapper);
	}

	@Test
	public void createTaxClassIgnoresClientSuppliedId() throws Exception {
		MerchantStore acme = store(1, "ACME");
		PersistableTaxClass body = new PersistableTaxClass();
		body.setId(7L);
		body.setCode("X");
		body.setName("X");

		when(taxClassService.exists("X", acme)).thenReturn(false);
		when(taxClassService.saveOrUpdate(any(TaxClass.class))).thenAnswer(inv -> {
			TaxClass t = inv.getArgument(0);
			assertNull("create must insert, not merge onto an existing id", t.getId());
			t.setId(12L);
			return t;
		});

		Entity created = taxFacade.createTaxClass(body, acme, language);

		ArgumentCaptor<TaxClass> saved = ArgumentCaptor.forClass(TaxClass.class);
		verify(taxClassService).saveOrUpdate(saved.capture());
		assertSame(acme, saved.getValue().getMerchantStore());
		assertEquals("X", saved.getValue().getCode());
		assertEquals(Long.valueOf(12L), created.getId());
	}

	@Test
	public void updateTaxClassWritesOnlyTheOwnedRow() throws Exception {
		MerchantStore acme = store(1, "ACME");
		TaxClass existing = new TaxClass();
		existing.setId(5L);
		existing.setCode("OLD");
		existing.setMerchantStore(acme);
		when(taxClassService.getById(5L)).thenReturn(existing);

		PersistableTaxClass body = new PersistableTaxClass();
		body.setId(7L);
		body.setCode("NEW");
		body.setName("New");

		taxFacade.updateTaxClass(5L, body, acme, language);

		ArgumentCaptor<TaxClass> saved = ArgumentCaptor.forClass(TaxClass.class);
		verify(taxClassService).saveOrUpdate(saved.capture());
		assertSame(existing, saved.getValue());
		assertEquals(Long.valueOf(5L), saved.getValue().getId());
		assertEquals("NEW", saved.getValue().getCode());
	}

	@Test
	public void createTaxRateIgnoresClientSuppliedIds() throws Exception {
		MerchantStore acme = store(1, "ACME");
		PersistableTaxRate body = new PersistableTaxRate();
		body.setId(9L);
		body.setCode("R");
		TaxRateDescription description = new TaxRateDescription();
		description.setId(33L);
		description.setLanguage("en");
		description.setName("Rate");
		body.getDescriptions().add(description);

		when(taxRateService.getByCode("R", acme)).thenReturn(null);
		when(taxRateService.saveOrUpdate(any(TaxRate.class))).thenAnswer(inv -> inv.getArgument(0));

		taxFacade.createTaxRate(body, acme, language);

		ArgumentCaptor<TaxRate> saved = ArgumentCaptor.forClass(TaxRate.class);
		verify(taxRateService).saveOrUpdate(saved.capture());
		assertNull(saved.getValue().getId());
		assertSame(acme, saved.getValue().getMerchantStore());
		assertEquals(1, saved.getValue().getDescriptions().size());
		assertNull(saved.getValue().getDescriptions().get(0).getId());
	}

	@Test
	public void updateTaxRateKeepsTheLoadedRowId() throws Exception {
		MerchantStore acme = store(1, "ACME");
		TaxRate existing = new TaxRate();
		existing.setId(9L);
		existing.setCode("R");
		existing.setMerchantStore(acme);
		when(taxRateService.getById(9L, acme)).thenReturn(existing);

		PersistableTaxRate body = new PersistableTaxRate();
		body.setId(70L);
		body.setCode("R2");

		taxFacade.updateTaxRate(9L, body, acme, language);

		ArgumentCaptor<TaxRate> saved = ArgumentCaptor.forClass(TaxRate.class);
		verify(taxRateService).saveOrUpdate(saved.capture());
		assertSame(existing, saved.getValue());
		assertEquals(Long.valueOf(9L), saved.getValue().getId());
		assertEquals("R2", saved.getValue().getCode());
	}

	private static MerchantStore store(int id, String code) {
		MerchantStore store = new MerchantStore();
		store.setId(id);
		store.setCode(code);
		return store;
	}
}
