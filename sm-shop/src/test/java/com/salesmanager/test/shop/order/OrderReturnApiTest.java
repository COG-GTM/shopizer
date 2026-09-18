package com.salesmanager.test.shop.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.orderstatus.OrderStatus;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.order.PersistableOrderReturn;
import com.salesmanager.shop.model.order.PersistableOrderReturnItem;
import com.salesmanager.shop.model.order.ReadableOrderReturn;
import com.salesmanager.shop.model.order.ReadableOrderReturnItem;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.RestApiException;
import com.salesmanager.shop.store.api.exception.RestErrorHandler;
import com.salesmanager.shop.store.api.v1.order.OrderPaymentApi;
import com.salesmanager.shop.store.controller.order.facade.OrderFacade;
import com.salesmanager.shop.utils.AuthorizationUtils;

/**
 * Standalone MockMvc tests of the order return endpoint. The existing tests of this source set
 * are integration tests requiring a running database, those tests only exercise the controller.
 */
@ExtendWith(MockitoExtension.class)
public class OrderReturnApiTest {

	private static final String RETURN_URL = "/api/v1/private/orders/100/return";

	@Mock
	private OrderFacade orderFacade;

	@Mock
	private AuthorizationUtils authorizationUtils;

	@InjectMocks
	private OrderPaymentApi orderPaymentApi;

	private MockMvc mockMvc;
	private ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	public void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(orderPaymentApi)
				.setControllerAdvice(new RestErrorHandler())
				.setCustomArgumentResolvers(new StoreArgumentResolver(), new LanguageArgumentResolver())
				.build();
	}

	@Test
	public void partialReturnIsAccepted() throws Exception {

		ReadableOrderReturnItem item = new ReadableOrderReturnItem();
		item.setOrderProductId(10L);
		item.setQuantity(2);
		item.setAmount(new BigDecimal("20.00"));

		ReadableOrderReturn readable = new ReadableOrderReturn();
		readable.setItems(Arrays.asList(item));
		readable.setRefundedAmount(new BigDecimal("20.00"));
		readable.setOrderStatus(OrderStatus.PARTIALLY_RETURNED.name());

		when(orderFacade.returnOrder(eq(100L), any(PersistableOrderReturn.class), any(MerchantStore.class),
				any(Language.class))).thenReturn(readable);

		mockMvc.perform(post(RETURN_URL).contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(returnRequest(10L, 2))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.refundedAmount").value(20.00))
				.andExpect(jsonPath("$.orderStatus").value(OrderStatus.PARTIALLY_RETURNED.name()))
				.andExpect(jsonPath("$.items[0].orderProductId").value(10))
				.andExpect(jsonPath("$.items[0].quantity").value(2))
				.andExpect(jsonPath("$.items[0].amount").value(20.00));

	}

	@Test
	public void missingOrderReturnsNotFound() throws Exception {

		when(orderFacade.returnOrder(anyLong(), any(PersistableOrderReturn.class), any(MerchantStore.class),
				any(Language.class))).thenThrow(new ResourceNotFoundException("Order id [100] not found"));

		mockMvc.perform(post(RETURN_URL).contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(returnRequest(10L, 2))))
				.andExpect(status().isNotFound());

	}

	@Test
	public void emptyItemListReturnsBadRequest() throws Exception {

		when(orderFacade.returnOrder(anyLong(), any(PersistableOrderReturn.class), any(MerchantStore.class),
				any(Language.class)))
						.thenThrow(new RestApiException("400", "At least one item is required for a return request"));

		mockMvc.perform(post(RETURN_URL).contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new PersistableOrderReturn())))
				.andExpect(status().isBadRequest());

	}

	@Test
	public void nonPositiveQuantityReturnsBadRequest() throws Exception {

		when(orderFacade.returnOrder(anyLong(), any(PersistableOrderReturn.class), any(MerchantStore.class),
				any(Language.class))).thenThrow(new RestApiException("400",
						"Returned quantity must be greater than 0 for order product id [10]"));

		mockMvc.perform(post(RETURN_URL).contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(returnRequest(10L, 0))))
				.andExpect(status().isBadRequest());

	}

	private PersistableOrderReturn returnRequest(Long orderProductId, int quantity) {
		PersistableOrderReturnItem item = new PersistableOrderReturnItem();
		item.setOrderProductId(orderProductId);
		item.setQuantity(quantity);

		PersistableOrderReturn request = new PersistableOrderReturn();
		request.setItems(Arrays.asList(item));
		request.setReason("damaged");
		return request;
	}

	private static class StoreArgumentResolver implements HandlerMethodArgumentResolver {

		@Override
		public boolean supportsParameter(MethodParameter parameter) {
			return MerchantStore.class.equals(parameter.getParameterType());
		}

		@Override
		public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
				NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
			MerchantStore store = new MerchantStore();
			store.setCode(MerchantStore.DEFAULT_STORE);
			return store;
		}

	}

	private static class LanguageArgumentResolver implements HandlerMethodArgumentResolver {

		@Override
		public boolean supportsParameter(MethodParameter parameter) {
			return Language.class.equals(parameter.getParameterType());
		}

		@Override
		public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
				NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
			return new Language("en");
		}

	}

}
