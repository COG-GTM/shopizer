package com.salesmanager.shop.store.api.v1.order;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;

import javax.servlet.http.HttpServletResponse;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.model.order.v0.ReadableOrderList;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.controller.order.facade.OrderFacade;
import com.salesmanager.shop.utils.AuthorizationUtils;

@RunWith(MockitoJUnitRunner.class)
public class OrderPaymentApiCapturableTest {

	private static final String USER = "order-admin";

	@Mock
	private AuthorizationUtils authorizationUtils;

	@Mock
	private OrderFacade orderFacade;

	@Mock
	private HttpServletResponse response;

	@InjectMocks
	private OrderPaymentApi orderPaymentApi;

	@Test
	public void authorizationFailurePropagatesWithoutCallingFacadeOrResponse() throws Exception {
		MerchantStore store = org.mockito.Mockito.mock(MerchantStore.class);
		when(authorizationUtils.authenticatedUser()).thenReturn(USER);
		doThrow(new UnauthorizedException("not authorized")).when(authorizationUtils)
				.authorizeUser(eq(USER), anyList(), same(store));

		try {
			orderPaymentApi.listCapturableOrders(null, null, store, null, null, response);
			fail("Expected authorization failure");
		} catch (UnauthorizedException expected) {
			assertEquals("not authorized", expected.getErrorMessage());
		}

		verifyNoInteractions(orderFacade);
		verify(response, never()).sendError(anyInt(), anyString());
	}

	@Test
	public void missingAuthenticatedUserPropagatesBeforeAuthorization() throws Exception {
		MerchantStore store = org.mockito.Mockito.mock(MerchantStore.class);
		when(authorizationUtils.authenticatedUser()).thenThrow(new UnauthorizedException());

		try {
			orderPaymentApi.listCapturableOrders(null, null, store, null, null, response);
			fail("Expected authorization failure");
		} catch (UnauthorizedException expected) {
			assertEquals("Not authorized", expected.getErrorMessage());
		}

		verifyNoInteractions(orderFacade);
		verify(authorizationUtils, never()).authorizeUser(anyString(), anyList(), any(MerchantStore.class));
		verify(response, never()).sendError(anyInt(), anyString());
	}

	@Test
	public void authorizedRequestPassesExactRolesStoreDatesAndLanguage() throws Exception {
		MerchantStore store = org.mockito.Mockito.mock(MerchantStore.class);
		Language language = org.mockito.Mockito.mock(Language.class);
		LocalDate startDate = LocalDate.of(2020, 1, 2);
		LocalDate endDate = LocalDate.of(2020, 2, 3);
		ReadableOrderList orders = new ReadableOrderList();
		when(authorizationUtils.authenticatedUser()).thenReturn(USER);
		when(orderFacade.getCapturableOrderList(same(store), any(Date.class), any(Date.class), same(language)))
				.thenReturn(orders);

		ReadableOrderList result = orderPaymentApi.listCapturableOrders(startDate, endDate, store, language, null,
				response);

		assertSame(orders, result);
		verify(authorizationUtils).authorizeUser(eq(USER),
				eq(Arrays.asList(Constants.GROUP_SUPERADMIN, Constants.GROUP_ADMIN, Constants.GROUP_ADMIN_ORDER,
						Constants.GROUP_ADMIN_RETAIL)),
				same(store));
		verify(orderFacade).getCapturableOrderList(same(store),
				eq(Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant())),
				eq(Date.from(endDate.atStartOfDay(ZoneId.systemDefault()).toInstant())), same(language));
	}

	@Test
	public void missingDatesDefaultToApproximatelyLastTwentyFourHours() throws Exception {
		MerchantStore store = org.mockito.Mockito.mock(MerchantStore.class);
		Language language = org.mockito.Mockito.mock(Language.class);
		when(authorizationUtils.authenticatedUser()).thenReturn(USER);
		when(orderFacade.getCapturableOrderList(same(store), any(Date.class), any(Date.class), same(language)))
				.thenReturn(new ReadableOrderList());
		long beforeCall = System.currentTimeMillis();

		orderPaymentApi.listCapturableOrders(null, null, store, language, null, response);

		ArgumentCaptor<Date> startDate = ArgumentCaptor.forClass(Date.class);
		ArgumentCaptor<Date> endDate = ArgumentCaptor.forClass(Date.class);
		verify(orderFacade).getCapturableOrderList(same(store), startDate.capture(), endDate.capture(),
				same(language));
		long afterCall = System.currentTimeMillis();
		long dayInMillis = 24L * 60 * 60 * 1000;
		long toleranceInMillis = 60L * 1000;
		assertTrue(Math.abs(startDate.getValue().getTime() - (beforeCall - dayInMillis)) < toleranceInMillis);
		assertTrue(Math.abs(endDate.getValue().getTime() - afterCall) < toleranceInMillis);
	}

	@Test
	public void facadeFailurePreservesServiceUnavailableResponse() throws Exception {
		MerchantStore store = org.mockito.Mockito.mock(MerchantStore.class);
		Language language = org.mockito.Mockito.mock(Language.class);
		when(authorizationUtils.authenticatedUser()).thenReturn(USER);
		when(orderFacade.getCapturableOrderList(same(store), any(Date.class), any(Date.class), same(language)))
				.thenThrow(new RuntimeException("facade failure"));

		assertEquals(null, orderPaymentApi.listCapturableOrders(null, null, store, language, null, response));

		verify(response).sendError(eq(503), anyString());
	}
}
