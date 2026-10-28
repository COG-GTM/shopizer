package com.salesmanager.test.content;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayOutputStream;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.modules.cms.content.StaticContentFileManager;
import com.salesmanager.core.business.repositories.content.ContentRepository;
import com.salesmanager.core.business.services.content.ContentServiceImpl;
import com.salesmanager.core.model.content.*;
import com.salesmanager.core.model.merchant.MerchantStore;

public class ContentServiceValidationTest {
    private ContentServiceImpl service;
    private StaticContentFileManager manager;
    private MerchantStore store;

    @Before
    public void setUp() {
        manager = mock(StaticContentFileManager.class);
        service = new ContentServiceImpl(mock(ContentRepository.class));
        ReflectionTestUtils.setField(service, "contentFileManager", manager);
        store = new MerchantStore();
        store.setCode("DEFAULT");
    }

    @Test
    public void rejectsUnsafeNamesBeforeCallingManager() throws Exception {
        for (String name : new String[] { "../x.png", "..\\x.png", "/etc/x.png", "", ". .", "a:b.png" }) {
            rejected(() -> service.addContentFile("DEFAULT", file(name)));
            rejected(() -> service.removeFile("DEFAULT", FileContentType.IMAGE, name));
            rejected(() -> service.removeFile("DEFAULT", name));
            rejected(() -> service.renameFile("DEFAULT", FileContentType.IMAGE, Optional.empty(), name, "ok.png"));
            rejected(() -> service.renameFile("DEFAULT", FileContentType.IMAGE, Optional.empty(), "ok.png", name));
            rejected(() -> service.addFolder(store, Optional.empty(), name));
            rejected(() -> service.removeFolder(store, Optional.empty(), name));
        }
        verifyNoInteractions(manager);
    }

    @Test
    public void validNamesDelegate() throws Exception {
        InputContentFile input = file("ok.png");
        service.addContentFile("DEFAULT", input);
        verify(manager).addFile("DEFAULT", Optional.empty(), input);
        service.removeFile("DEFAULT", FileContentType.IMAGE, "ok.png");
        verify(manager).removeFile("DEFAULT", FileContentType.IMAGE, "ok.png", Optional.empty());
        service.removeFile("DEFAULT", "other.png");
        verify(manager).removeFile("DEFAULT", FileContentType.IMAGE, "other.png", Optional.empty());
        service.addFolder(store, Optional.empty(), "images");
        verify(manager).addFolder("DEFAULT", "images", Optional.empty());
        service.removeFolder(store, Optional.empty(), "images");
        verify(manager).removeFolder("DEFAULT", "images", Optional.empty());
        OutputContentFile existing = new OutputContentFile();
        existing.setFile(new ByteArrayOutputStream());
        when(manager.getFile("DEFAULT", Optional.empty(), FileContentType.IMAGE, "old.png")).thenReturn(existing);
        service.renameFile("DEFAULT", FileContentType.IMAGE, Optional.empty(), "old.png", "new.png");
        verify(manager).removeFile("DEFAULT", FileContentType.IMAGE, "old.png", Optional.empty());
        verify(manager).addFile(eq("DEFAULT"), eq(Optional.empty()), argThat(f -> "new.png".equals(f.getFileName())));
    }

    @Test
    public void missingRenameSourceDoesNotWrite() throws Exception {
        rejected(() -> service.renameFile("DEFAULT", FileContentType.IMAGE, Optional.empty(), "missing.png", "new.png"));
        verify(manager).getFile("DEFAULT", Optional.empty(), FileContentType.IMAGE, "missing.png");
        verifyNoMoreInteractions(manager);
    }

    private InputContentFile file(String name) {
        InputContentFile file = new InputContentFile();
        file.setFileName(name);
        file.setFileContentType(FileContentType.IMAGE);
        return file;
    }

    private void rejected(Operation operation) throws Exception {
        try {
            operation.run();
            fail("Expected ServiceException");
        } catch (ServiceException expected) {
            assertNotNull(expected.getMessageCode());
        }
    }

    private interface Operation { void run() throws Exception; }
}
