package com.salesmanager.shop.store.facade.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.salesmanager.core.business.services.content.ContentService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.model.content.Content;
import com.salesmanager.core.model.content.ContentType;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.content.box.PersistableContentBox;
import com.salesmanager.shop.model.content.common.ContentDescription;
import com.salesmanager.shop.model.content.page.PersistableContentPage;

@ExtendWith(MockitoExtension.class)
class ContentFacadeImplTest {

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
		language = new Language("fr");
		language.setId(2);
		when(languageService.getByCode("fr")).thenReturn(language);
	}

	@Test
	void createPageIgnoresIdFromRequestBody() throws Exception {
		PersistableContentPage page = new PersistableContentPage();
		page.setId(OTHER_STORE_CONTENT_ID);
		page.setCode("mallory-page");
		page.setDescriptions(Collections.singletonList(description()));

		contentFacade.saveContentPage(page, store, language);

		Content saved = captureSaved();
		assertNull(saved.getId());
		assertSame(store, saved.getMerchantStore());
		assertEquals(ContentType.PAGE, saved.getContentType());
		assertEquals("mallory-page", saved.getCode());
		verify(contentService, never()).getById(OTHER_STORE_CONTENT_ID);
	}

	@Test
	void createBoxIgnoresIdFromRequestBody() throws Exception {
		PersistableContentBox box = new PersistableContentBox();
		box.setId(OTHER_STORE_CONTENT_ID);
		box.setCode("mallory-box");
		box.setDescriptions(Collections.singletonList(description()));

		contentFacade.saveContentBox(box, store, language);

		Content saved = captureSaved();
		assertNull(saved.getId());
		assertSame(store, saved.getMerchantStore());
		assertEquals(ContentType.BOX, saved.getContentType());
		assertEquals("mallory-box", saved.getCode());
	}

	@Test
	void updatePageKeepsIdOfContentLoadedForStore() throws Exception {
		Content existing = new Content();
		existing.setId(5L);
		existing.setMerchantStore(store);
		when(contentService.getById(5L, store)).thenReturn(existing);

		PersistableContentPage page = new PersistableContentPage();
		page.setId(OTHER_STORE_CONTENT_ID);
		page.setCode("terms");
		page.setDescriptions(Collections.singletonList(description()));

		contentFacade.updateContentPage(5L, page, store, language);

		Content saved = captureSaved();
		assertSame(existing, saved);
		assertEquals(Long.valueOf(5L), saved.getId());
	}

	@Test
	void updateBoxKeepsIdOfContentLoadedForStore() throws Exception {
		Content existing = new Content();
		existing.setId(6L);
		existing.setMerchantStore(store);
		when(contentService.getById(6L, store)).thenReturn(existing);

		PersistableContentBox box = new PersistableContentBox();
		box.setId(OTHER_STORE_CONTENT_ID);
		box.setCode("footer");
		box.setDescriptions(Collections.singletonList(description()));

		contentFacade.updateContentBox(6L, box, store, language);

		Content saved = captureSaved();
		assertSame(existing, saved);
		assertEquals(Long.valueOf(6L), saved.getId());
	}

	private Content captureSaved() throws Exception {
		ArgumentCaptor<Content> captor = ArgumentCaptor.forClass(Content.class);
		verify(contentService).saveOrUpdate(captor.capture());
		return captor.getValue();
	}

	private static ContentDescription description() {
		ContentDescription description = new ContentDescription();
		description.setLanguage("fr");
		description.setName("Conditions");
		description.setTitle("Conditions");
		description.setDescription("<p>texte</p>");
		return description;
	}
}
