package com.salesmanager.shop.store.api.v1.order;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.salesmanager.shop.constants.Constants;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.order.history.PersistableOrderStatusHistory;
import com.salesmanager.shop.model.order.history.ReadableOrderStatusHistory;
import com.salesmanager.shop.store.controller.order.facade.OrderFacade;
import com.salesmanager.shop.utils.AuthorizationUtils;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Order status history api")
public class OrderStatusHistoryApi {

	@Inject
	private OrderFacade orderFacade;

	@Inject
	private AuthorizationUtils authorizationUtils;

	@RequestMapping(value = { "private/orders/{id}/history" }, method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.OK)
	@ResponseBody
	public List<ReadableOrderStatusHistory> list(@PathVariable final Long id, @Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language) {

		String user = authorizationUtils.authenticatedUser();
		authorizationUtils.authorizeUser(user, Stream.of(Constants.GROUP_SUPERADMIN, Constants.GROUP_ADMIN,
				Constants.GROUP_ADMIN_ORDER, Constants.GROUP_ADMIN_RETAIL).collect(Collectors.toList()), merchantStore);

		return orderFacade.getReadableOrderHistory(id, merchantStore, language);

	}

	@RequestMapping(value = { "private/orders/{id}/history" }, method = RequestMethod.POST)
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Add order history")
	@ResponseBody
	public void create(@PathVariable final Long id, @RequestBody PersistableOrderStatusHistory history,
			@Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language) {

		String user = authorizationUtils.authenticatedUser();
		authorizationUtils.authorizeUser(user, Stream.of(Constants.GROUP_SUPERADMIN, Constants.GROUP_ADMIN,
				Constants.GROUP_ADMIN_ORDER, Constants.GROUP_ADMIN_RETAIL).collect(Collectors.toList()), merchantStore);

		// TODO validate date format

		orderFacade.createOrderStatus(history, id, merchantStore);

	}

}
