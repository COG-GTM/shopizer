package com.salesmanager.core.business.services.order;

import java.util.Map;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.order.OrderReturnResult;

public interface OrderReturnService {

	/**
	 * Returns specific order products quantities and refunds the corresponding amount
	 * using the configured payment module. The order status becomes RETURNED when all
	 * ordered quantities have been returned, PARTIALLY_RETURNED otherwise.
	 * @param order
	 * @param customer
	 * @param store
	 * @param returnedQuantitiesByOrderProductId
	 * @param reason
	 * @return OrderReturnResult
	 * @throws ServiceException
	 */
	OrderReturnResult returnOrderProducts(Order order, Customer customer, MerchantStore store,
			Map<Long, Integer> returnedQuantitiesByOrderProductId, String reason) throws ServiceException;

}
