package com.salesmanager.test.shop.unit.config;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.application.config.MerchantStoreArgumentResolver;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.controller.store.facade.StoreFacade;
import com.salesmanager.shop.store.controller.user.facade.UserFacade;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

@RunWith(MockitoJUnitRunner.class)
public class MerchantStoreArgumentResolverTest {

    private static final String PRIVATE_URI = "/api/v1/private/stores";

    @Mock
    private StoreFacade storeFacade;

    @Mock
    private UserFacade userFacade;

    @Mock
    private NativeWebRequest webRequest;

    @InjectMocks
    private MerchantStoreArgumentResolver resolver;

    private final MockHttpServletRequest servletRequest = new MockHttpServletRequest();
    private final MerchantStore defaultStore = new MerchantStore();
    private MethodParameter parameter;

    @Before
    public void setUp() throws NoSuchMethodException {
        defaultStore.setCode("DEFAULT");
        servletRequest.setRequestURI(PRIVATE_URI);
        when(webRequest.getNativeRequest(HttpServletRequest.class)).thenReturn(servletRequest);
        when(storeFacade.get("DEFAULT")).thenReturn(defaultStore);
        parameter = new MethodParameter(Handler.class.getMethod("stores", MerchantStore.class), 0);
    }

    @Test
    public void absentStoreParameterUsesDefaultStore() throws Exception {
        when(webRequest.getParameter("store")).thenReturn(null);
        when(userFacade.authorizeStore(defaultStore, PRIVATE_URI)).thenReturn(true);

        assertSame(defaultStore, resolver.resolveArgument(parameter, new ModelAndViewContainer(), webRequest, null));
    }

    @Test(expected = UnauthorizedException.class)
    public void unauthorizedStoreIsRejected() throws Exception {
        when(webRequest.getParameter("store")).thenReturn("DEFAULT");
        when(userFacade.authorizeStore(defaultStore, PRIVATE_URI)).thenReturn(false);

        resolver.resolveArgument(parameter, new ModelAndViewContainer(), webRequest, null);
    }

    @Test
    public void authorizedStoreIsReturned() throws Exception {
        when(webRequest.getParameter("store")).thenReturn("DEFAULT");
        when(userFacade.authorizeStore(defaultStore, PRIVATE_URI)).thenReturn(true);

        assertSame(defaultStore, resolver.resolveArgument(parameter, new ModelAndViewContainer(), webRequest, null));
    }

    @Test
    public void blankStoreParameterUsesDefaultStore() throws Exception {
        when(webRequest.getParameter("store")).thenReturn(" ");
        when(userFacade.authorizeStore(defaultStore, PRIVATE_URI)).thenReturn(true);

        assertSame(defaultStore, resolver.resolveArgument(parameter, new ModelAndViewContainer(), webRequest, null));
    }

    private static class Handler {
        public void stores(MerchantStore store) {
        }
    }
}
