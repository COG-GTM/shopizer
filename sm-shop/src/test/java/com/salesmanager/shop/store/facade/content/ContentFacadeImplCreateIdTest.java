package com.salesmanager.shop.store.facade.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.salesmanager.core.business.services.content.ContentService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.model.content.Content;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.content.box.PersistableContentBox;
import com.salesmanager.shop.model.content.page.PersistableContentPage;
import com.salesmanager.shop.store.api.exception.ConstraintException;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;

@ExtendWith(MockitoExtension.class)
class ContentFacadeImplCreateIdTest {

	private static final Long OTHER_STORE_CONTENT_ID = 17L;

	@Mock
	private ContentService contentService;

	@Mock
	private LanguageService languageService;

	@InjectMocks
	private ContentFacadeImpl contentFacade;

	private MerchantStore store;
	private Language language;

	@BeforeEach
	void setUp() throws Exception {
		store = new MerchantStore();
		store.setId(2);
		store.setCode("GLOBEX");
		language = new Language("en");
		language.setId(1);
	}

	@Test
	void createPageWithAnotherStoresIdDoesNotLookUpThatId() throws Exception {
		PersistableContentPage page = page("new-page");
		page.setId(OTHER_STORE_CONTENT_ID);
		when(contentService.getByCode(page.getCode(), store)).thenReturn(null);
		when(languageService.getByCode("en")).thenReturn(language);

		contentFacade.saveContentPage(page, store, language);

		Content saved = captureSaved();
		assertNull(saved.getId());
		verify(contentService, never()).getById(anyLong());
		verify(contentService, never()).getById(anyLong(), any(MerchantStore.class));
	}

	@Test
	void createPageWithDuplicateCodeThrowsConstraintException() throws Exception {
		PersistableContentPage page = page("duplicate-page");
		when(contentService.getByCode(page.getCode(), store)).thenReturn(new Content());

		ServiceRuntimeException exception = assertThrows(ServiceRuntimeException.class,
				() -> contentFacade.saveContentPage(page, store, language));

		assertEquals(ConstraintException.class, exception.getCause().getClass());
		verify(contentService, never()).saveOrUpdate(any(Content.class));
	}

	@Test
	void createBoxWithDuplicateCodeThrowsConstraintException() throws Exception {
		PersistableContentBox box = box("duplicate-box");
		when(contentService.getByCode(box.getCode(), store)).thenReturn(new Content());

		ServiceRuntimeException exception = assertThrows(ServiceRuntimeException.class,
				() -> contentFacade.saveContentBox(box, store, language));

		assertEquals(ConstraintException.class, exception.getCause().getClass());
		verify(contentService, never()).saveOrUpdate(any(Content.class));
	}

	@Test
	void updatePageForAnotherStoresContentDoesNotSave() throws Exception {
		when(contentService.getById(OTHER_STORE_CONTENT_ID, store)).thenReturn(null);

		ServiceRuntimeException exception = assertThrows(ServiceRuntimeException.class,
				() -> contentFacade.updateContentPage(OTHER_STORE_CONTENT_ID, page("foreign-page"), store, language));

		assertEquals(ConstraintException.class, exception.getCause().getClass());
		verify(contentService, never()).saveOrUpdate(any(Content.class));
	}

	@Test
	void updateBoxForAnotherStoresContentDoesNotSave() throws Exception {
		when(contentService.getById(OTHER_STORE_CONTENT_ID, store)).thenReturn(null);

		ServiceRuntimeException exception = assertThrows(ServiceRuntimeException.class,
				() -> contentFacade.updateContentBox(OTHER_STORE_CONTENT_ID, box("foreign-box"), store, language));

		assertEquals(ConstraintException.class, exception.getCause().getClass());
		verify(contentService, never()).saveOrUpdate(any(Content.class));
	}

	@ParameterizedTest
	@ValueSource(longs = { 0L, -1L })
	void createPageWithNonPositiveIdSavesNewEntityWithoutId(long requestId) throws Exception {
		PersistableContentPage page = page("non-positive-" + requestId);
		page.setId(requestId);
		when(contentService.getByCode(page.getCode(), store)).thenReturn(null);
		when(languageService.getByCode("en")).thenReturn(language);

		contentFacade.saveContentPage(page, store, language);

		assertNull(captureSaved().getId());
	}

	private Content captureSaved() throws Exception {
		ArgumentCaptor<Content> captor = ArgumentCaptor.forClass(Content.class);
		verify(contentService).saveOrUpdate(captor.capture());
		return captor.getValue();
	}

	private static PersistableContentPage page(String code) {
		PersistableContentPage page = new PersistableContentPage();
		page.setCode(code);
		page.setDescriptions(Collections.singletonList(description()));
		return page;
	}

	private static PersistableContentBox box(String code) {
		PersistableContentBox box = new PersistableContentBox();
		box.setCode(code);
		box.setDescriptions(Collections.singletonList(description()));
		return box;
	}

	private static com.salesmanager.shop.model.content.common.ContentDescription description() {
		com.salesmanager.shop.model.content.common.ContentDescription description =
				new com.salesmanager.shop.model.content.common.ContentDescription();
		description.setLanguage("en");
		description.setName("Test content");
		description.setTitle("Test content");
		description.setDescription("<p>Test content</p>");
		description.setFriendlyUrl("test-content");
		return description;
	}
}
