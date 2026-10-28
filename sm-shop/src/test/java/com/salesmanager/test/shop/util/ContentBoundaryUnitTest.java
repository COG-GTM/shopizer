package com.salesmanager.test.shop.util;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.ResponseStatus;
import com.salesmanager.core.business.services.content.ContentService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.store.api.exception.*;
import com.salesmanager.shop.store.facade.content.ContentFacadeImpl;
import com.salesmanager.shop.utils.FileNameUtils;

public class ContentBoundaryUnitTest {
    @Test
    public void rejectsUnsafeFileNames() {
        FileNameUtils utils = new FileNameUtils();
        for (String name : new String[] { null, "", "../../evil.png", "..\\evil.png", "/etc/x.png", "x.png:stream" }) {
            assertFalse(String.valueOf(name), utils.validFileName(name));
        }
        assertTrue(utils.validFileName("ok-test.png"));
        assertTrue(utils.validFileName("商品.jpg"));
    }

    @Test
    public void handlesRuntimeExceptionWithoutCauseAsBadRequest() throws Exception {
        ErrorEntity error = new RestErrorHandler().handleServiceException(new ServiceRuntimeException("Invalid file name"));
        assertNotNull(error);
        assertTrue(error.getMessage().contains("Invalid file name"));
        assertEquals(HttpStatus.BAD_REQUEST, RestErrorHandler.class
                .getMethod("handleServiceException", ServiceRuntimeException.class)
                .getAnnotation(ResponseStatus.class).value());
    }

    @Test
    public void handlesRuntimeExceptionWithCause() {
        ErrorEntity error = new RestErrorHandler().handleServiceException(
                new ServiceRuntimeException("400", "Upload rejected", new IllegalArgumentException("unsafe path")));
        assertEquals("400", error.getErrorCode());
        assertTrue(error.getMessage().contains("Upload rejected"));
        assertTrue(error.getMessage().contains("unsafe path"));
    }

    @Test
    public void rejectsUnknownAndNullContentTypesWithoutDelegation() {
        ContentService service = mock(ContentService.class);
        ContentFacadeImpl facade = new ContentFacadeImpl();
        ReflectionTestUtils.setField(facade, "contentService", service);
        for (String type : new String[] { "UNKNOWN", "", null }) {
            try {
                facade.delete(new MerchantStore(), "ok.png", type);
                fail("Accepted " + type);
            } catch (ServiceRuntimeException expected) {
                assertEquals("Invalid content type [" + type + "]", expected.getErrorMessage());
            }
        }
        verifyNoInteractions(service);
    }
}
