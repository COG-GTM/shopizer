package com.salesmanager.core.business.modules.integration.snowflake;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.order.orderstatus.OrderStatus;

/**
 * Outbound export of order data to a Snowflake data warehouse. Implementations
 * must be no-op when {@code snowflake.export.enabled} is false.
 *
 */
public interface SnowflakeExportService {

	void exportOrder(Order order, MerchantStore store);

	void exportOrderStatusChange(Order order, OrderStatus oldStatus, OrderStatus newStatus, MerchantStore store);

}
