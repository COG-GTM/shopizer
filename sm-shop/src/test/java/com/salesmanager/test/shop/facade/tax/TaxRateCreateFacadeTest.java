package com.salesmanager.test.shop.facade.tax;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.services.tax.TaxRateService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.tax.taxrate.TaxRate;
import com.salesmanager.shop.mapper.tax.PersistableTaxRateMapper;
import com.salesmanager.shop.model.tax.PersistableTaxRate;
import com.salesmanager.shop.model.tax.TaxRateDescription;
import com.salesmanager.shop.store.api.exception.OperationNotAllowedException;
import com.salesmanager.shop.store.facade.tax.TaxFacadeImpl;

@RunWith(MockitoJUnitRunner.class)
public class TaxRateCreateFacadeTest {

	@Mock
	private TaxRateService service;
	@Mock
	private PersistableTaxRateMapper mapper;
	@InjectMocks
	private TaxFacadeImpl facade;
	private MerchantStore store;
	private Language language;

	@Before
	public void setUp() {
		store = new MerchantStore();
		store.setId(2);
		store.setCode("ACME");
		language = new Language("en");
	}

	@Test
	public void clearsAllClientIdsBeforeInvokingMapper() throws Exception {
		PersistableTaxRate request = request();
		for (long id : new long[] {37L, 0L, -1L}) {
			TaxRateDescription description = new TaxRateDescription();
			description.setId(id);
			request.getDescriptions().add(description);
		}
		TaxRate model = new TaxRate();
		when(mapper.convert(same(request), same(store), same(language))).thenAnswer(invocation -> {
			assertNull(request.getId());
			request.getDescriptions().forEach(description -> assertNull(description.getId()));
			return model;
		});
		when(service.saveOrUpdate(model)).thenAnswer(invocation -> {
			model.setId(99L);
			return model;
		});

		assertEquals(Long.valueOf(99L), facade.createTaxRate(request, store, language).getId());
		verify(mapper).convert(request, store, language);
		verify(service).saveOrUpdate(model);
	}

	@Test
	public void createAllowsAbsentDescriptionsAndClearsRateId() throws Exception {
		PersistableTaxRate request = request();
		request.setDescriptions(null);
		TaxRate model = new TaxRate();
		model.setId(99L);
		when(mapper.convert(request, store, language)).thenReturn(model);
		when(service.saveOrUpdate(model)).thenReturn(model);

		assertEquals(Long.valueOf(99L), facade.createTaxRate(request, store, language).getId());
		assertNull(request.getId());
	}

	@Test
	public void duplicateCodeIsRejectedBeforeMappingOrSaving() throws Exception {
		when(service.getByCode("new-rate", store)).thenReturn(new TaxRate());
		try {
			facade.createTaxRate(request(), store, language);
			fail("Duplicate rate code must not be inserted");
		} catch (OperationNotAllowedException expected) {
			verify(mapper, never()).convert(any(), any(), any());
			verify(service, never()).saveOrUpdate(any());
		}
	}

	private PersistableTaxRate request() {
		PersistableTaxRate request = new PersistableTaxRate();
		request.setCode("new-rate");
		request.setId(12L);
		return request;
	}
}
