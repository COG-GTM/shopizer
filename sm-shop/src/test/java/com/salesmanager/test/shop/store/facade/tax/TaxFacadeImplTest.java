package com.salesmanager.test.shop.store.facade.tax;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.exception.ServiceException;
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
import com.salesmanager.shop.store.api.exception.OperationNotAllowedException;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.facade.tax.TaxFacadeImpl;

@RunWith(MockitoJUnitRunner.class)
public class TaxFacadeImplTest {

	@Mock
	private TaxClassService taxClassService;
	@Mock
	private TaxRateService taxRateService;
	@Mock
	private PersistableTaxClassMapper persistableTaxClassMapper;
	@Mock
	private ReadableTaxClassMapper readableTaxClassMapper;
	@Mock
	private PersistableTaxRateMapper persistableTaxRateMapper;
	@Mock
	private ReadableTaxRateMapper readableTaxRateMapper;
	@InjectMocks
	private TaxFacadeImpl facade;

	private MerchantStore store;
	private Language language;

	@Before
	public void setUp() {
		store = new MerchantStore();
		store.setId(1);
		store.setCode("DEFAULT");
		language = new Language("en");
	}

	@Test
	public void createTaxClassClearsClientIdBeforeMapping() throws Exception {
		PersistableTaxClass source = new PersistableTaxClass();
		source.setId(42L);
		source.setCode("CLASS1");
		source.setName("Class one");
		TaxClass mapped = new TaxClass("CLASS1");
		TaxClass saved = new TaxClass("CLASS1");
		saved.setId(13L);
		when(taxClassService.exists("CLASS1", store)).thenReturn(false);
		when(persistableTaxClassMapper.convert(any(PersistableTaxClass.class), same(store), same(language)))
				.thenReturn(mapped);
		when(taxClassService.saveOrUpdate(mapped)).thenReturn(saved);

		Entity result = facade.createTaxClass(source, store, language);

		ArgumentCaptor<PersistableTaxClass> captor = ArgumentCaptor.forClass(PersistableTaxClass.class);
		verify(persistableTaxClassMapper).convert(captor.capture(), same(store), same(language));
		assertNull(captor.getValue().getId());
		assertEquals("DEFAULT", captor.getValue().getStore());
		assertEquals(Long.valueOf(13L), result.getId());
		verify(taxClassService).saveOrUpdate(mapped);
	}

	@Test
	public void createExistingTaxClassDoesNotSave() throws Exception {
		PersistableTaxClass source = new PersistableTaxClass();
		source.setId(42L);
		source.setCode("CLASS1");
		when(taxClassService.exists("CLASS1", store)).thenReturn(true);

		try {
			facade.createTaxClass(source, store, language);
			fail("Expected OperationNotAllowedException");
		} catch (OperationNotAllowedException expected) {
			// expected
		}

		verify(taxClassService, never()).saveOrUpdate(any(TaxClass.class));
		verify(persistableTaxClassMapper, never()).convert(any(PersistableTaxClass.class), any(MerchantStore.class),
				any(Language.class));
	}

	@Test
	public void createTaxRateClearsRateAndDescriptionIdsBeforeMapping() throws Exception {
		PersistableTaxRate source = new PersistableTaxRate();
		source.setId(42L);
		source.setCode("RATE1");
		TaxRateDescription english = description(77L, "en");
		TaxRateDescription french = description(78L, "fr");
		source.setDescriptions(Arrays.asList(english, french));
		TaxRate mapped = new TaxRate();
		TaxRate saved = new TaxRate();
		saved.setId(15L);
		when(taxRateService.getByCode("RATE1", store)).thenReturn(null);
		when(persistableTaxRateMapper.convert(any(PersistableTaxRate.class), same(store), same(language)))
				.thenReturn(mapped);
		when(taxRateService.saveOrUpdate(mapped)).thenReturn(saved);

		Entity result = facade.createTaxRate(source, store, language);

		ArgumentCaptor<PersistableTaxRate> captor = ArgumentCaptor.forClass(PersistableTaxRate.class);
		verify(persistableTaxRateMapper).convert(captor.capture(), same(store), same(language));
		assertNull(captor.getValue().getId());
		assertEquals(2, captor.getValue().getDescriptions().size());
		captor.getValue().getDescriptions().forEach(description -> assertNull(description.getId()));
		assertEquals(Long.valueOf(15L), result.getId());
		verify(taxRateService).saveOrUpdate(mapped);
	}

	@Test
	public void updateTaxClassMergesLoadedModelAndSavesSameInstance() throws Exception {
		TaxClass model = new TaxClass("CLASS1");
		model.setId(5L);
		model.setMerchantStore(store);
		PersistableTaxClass source = new PersistableTaxClass();
		source.setId(99L);
		source.setCode("CLASS1");
		when(taxClassService.getById(5L)).thenReturn(model);
		when(persistableTaxClassMapper.merge(source, model, store, language)).thenReturn(model);
		when(taxClassService.saveOrUpdate(model)).thenReturn(model);

		facade.updateTaxClass(5L, source, store, language);

		verify(persistableTaxClassMapper).merge(source, model, store, language);
		verify(taxClassService).saveOrUpdate(same(model));
		verify(persistableTaxClassMapper, never()).convert(any(PersistableTaxClass.class),
				any(MerchantStore.class), any(Language.class));
	}

	@Test
	public void updateTaxClassFromAnotherStoreIsRejectedWithoutSaving() throws Exception {
		MerchantStore otherStore = new MerchantStore();
		otherStore.setCode("OTHER");
		TaxClass model = new TaxClass("CLASS1");
		model.setId(5L);
		model.setMerchantStore(otherStore);
		when(taxClassService.getById(5L)).thenReturn(model);

		try {
			facade.updateTaxClass(5L, new PersistableTaxClass(), store, language);
			fail("Expected UnauthorizedException");
		} catch (UnauthorizedException expected) {
			// expected
		}

		verify(taxClassService, never()).saveOrUpdate(any(TaxClass.class));
	}

	@Test
	public void updateTaxClassWithMissingModelIsNotFound() throws Exception {
		when(taxClassService.getById(5L)).thenReturn(null);

		try {
			facade.updateTaxClass(5L, new PersistableTaxClass(), store, language);
			fail("Expected ResourceNotFoundException");
		} catch (ResourceNotFoundException expected) {
			// expected
		}

		verify(taxClassService, never()).saveOrUpdate(any(TaxClass.class));
	}

	private static TaxRateDescription description(Long id, String code) {
		TaxRateDescription description = new TaxRateDescription();
		description.setId(id);
		description.setLanguage(code);
		description.setName(code + " rate");
		return description;
	}
}
