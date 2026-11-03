package com.salesmanager.shop.store.facade.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.business.services.content.ContentService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.model.content.Content;
import com.salesmanager.core.model.content.ContentType;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.content.box.PersistableContentBox;
import com.salesmanager.shop.model.content.common.ContentDescription;
import com.salesmanager.shop.model.content.page.PersistableContentPage;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.utils.FilePathUtils;
import com.salesmanager.shop.utils.ImageFilePath;

@RunWith(MockitoJUnitRunner.class)
public class ContentFacadeImplTest {

  @Mock private ContentService contentService;
  @Mock private LanguageService languageService;
  @Mock private ImageFilePath imageUtils;
  @Mock private FilePathUtils fileUtils;

  @InjectMocks private ContentFacadeImpl contentFacade;

  private Language english;

  @Before
  public void setUp() throws Exception {
    english = new Language();
    english.setCode("en");
    when(languageService.getByCode("en")).thenReturn(english);
  }

  @Test
  public void saveContentPageClearsRequestIdAndCreatesPage() throws Exception {
    MerchantStore store = new MerchantStore();
    PersistableContentPage page = page("new-page");
    page.setId(99L);
    when(contentService.getByCode("new-page", store)).thenReturn(null);

    contentFacade.saveContentPage(page, store, english);

    ArgumentCaptor<Content> contentCaptor = ArgumentCaptor.forClass(Content.class);
    verify(contentService).saveOrUpdate(contentCaptor.capture());
    Content savedContent = contentCaptor.getValue();
    assertNull(savedContent.getId());
    assertSame(store, savedContent.getMerchantStore());
    assertEquals(ContentType.PAGE, savedContent.getContentType());
    assertEquals("new-page", savedContent.getCode());
    assertNull(page.getId());
  }

  @Test
  public void saveContentBoxClearsRequestIdAndCreatesBox() throws Exception {
    MerchantStore store = new MerchantStore();
    PersistableContentBox box = box("new-box");
    box.setId(99L);
    when(contentService.getByCode("new-box", store)).thenReturn(null);

    contentFacade.saveContentBox(box, store, english);

    ArgumentCaptor<Content> contentCaptor = ArgumentCaptor.forClass(Content.class);
    verify(contentService).saveOrUpdate(contentCaptor.capture());
    Content savedContent = contentCaptor.getValue();
    assertNull(savedContent.getId());
    assertSame(store, savedContent.getMerchantStore());
    assertEquals(ContentType.BOX, savedContent.getContentType());
    assertEquals("new-box", savedContent.getCode());
    assertNull(box.getId());
  }

  @Test
  public void saveContentPageRejectsAnExistingCode() throws Exception {
    MerchantStore store = new MerchantStore();
    PersistableContentPage page = page("existing-page");
    when(contentService.getByCode("existing-page", store)).thenReturn(new Content());

    assertServiceRuntimeException(() -> contentFacade.saveContentPage(page, store, english));

    verify(contentService, never()).saveOrUpdate(org.mockito.ArgumentMatchers.any(Content.class));
  }

  @Test
  public void saveContentBoxRejectsAnExistingCode() throws Exception {
    MerchantStore store = new MerchantStore();
    PersistableContentBox box = box("existing-box");
    when(contentService.getByCode("existing-box", store)).thenReturn(new Content());

    assertServiceRuntimeException(() -> contentFacade.saveContentBox(box, store, english));

    verify(contentService, never()).saveOrUpdate(org.mockito.ArgumentMatchers.any(Content.class));
  }

  @Test
  public void updateContentPageSavesTheScopedEntity() throws Exception {
    MerchantStore store = new MerchantStore();
    Content existing = existingContent(5L, store);
    PersistableContentPage page = page("updated-page");
    page.setId(99L);
    when(contentService.getById(5L, store)).thenReturn(existing);

    contentFacade.updateContentPage(5L, page, store, english);

    ArgumentCaptor<Content> contentCaptor = ArgumentCaptor.forClass(Content.class);
    verify(contentService).saveOrUpdate(contentCaptor.capture());
    assertSame(existing, contentCaptor.getValue());
    assertEquals(Long.valueOf(5L), contentCaptor.getValue().getId());
  }

  @Test
  public void updateContentBoxSavesTheScopedEntity() throws Exception {
    MerchantStore store = new MerchantStore();
    Content existing = existingContent(5L, store);
    PersistableContentBox box = box("updated-box");
    box.setId(99L);
    when(contentService.getById(5L, store)).thenReturn(existing);

    contentFacade.updateContentBox(5L, box, store, english);

    ArgumentCaptor<Content> contentCaptor = ArgumentCaptor.forClass(Content.class);
    verify(contentService).saveOrUpdate(contentCaptor.capture());
    assertSame(existing, contentCaptor.getValue());
    assertEquals(Long.valueOf(5L), contentCaptor.getValue().getId());
  }

  @Test
  public void updateContentPageRejectsAnIdOutsideTheStore() throws Exception {
    MerchantStore store = new MerchantStore();
    PersistableContentPage page = page("missing-page");
    when(contentService.getById(5L, store)).thenReturn(null);

    assertServiceRuntimeException(() -> contentFacade.updateContentPage(5L, page, store, english));

    verify(contentService, never()).saveOrUpdate(org.mockito.ArgumentMatchers.any(Content.class));
  }

  @Test
  public void updateContentBoxRejectsAnIdOutsideTheStore() throws Exception {
    MerchantStore store = new MerchantStore();
    PersistableContentBox box = box("missing-box");
    when(contentService.getById(5L, store)).thenReturn(null);

    assertServiceRuntimeException(() -> contentFacade.updateContentBox(5L, box, store, english));

    verify(contentService, never()).saveOrUpdate(org.mockito.ArgumentMatchers.any(Content.class));
  }

  private static PersistableContentPage page(String code) {
    PersistableContentPage page = new PersistableContentPage();
    page.setCode(code);
    page.setDescriptions(Collections.singletonList(description(code)));
    return page;
  }

  private static PersistableContentBox box(String code) {
    PersistableContentBox box = new PersistableContentBox();
    box.setCode(code);
    box.setDescriptions(Collections.singletonList(description(code)));
    return box;
  }

  private static ContentDescription description(String name) {
    ContentDescription description = new ContentDescription();
    description.setLanguage("en");
    description.setName(name);
    description.setFriendlyUrl(name);
    description.setDescription(name + " description");
    return description;
  }

  private static Content existingContent(Long id, MerchantStore store) {
    Content content = new Content();
    content.setId(id);
    content.setMerchantStore(store);
    return content;
  }

  private static void assertServiceRuntimeException(CheckedRunnable action) throws Exception {
    try {
      action.run();
      fail("Expected ServiceRuntimeException");
    } catch (ServiceRuntimeException expected) {
      // expected
    }
  }

  @FunctionalInterface
  private interface CheckedRunnable {
    void run() throws Exception;
  }
}
