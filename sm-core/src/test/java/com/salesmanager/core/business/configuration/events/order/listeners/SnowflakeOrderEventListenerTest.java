package com.salesmanager.core.business.configuration.events.order.listeners;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.Before;
import org.junit.Test;

import com.salesmanager.core.business.configuration.events.order.OrderStatusChangedEvent;
import com.salesmanager.core.business.configuration.events.order.SaveOrderEvent;
import com.salesmanager.core.business.modules.integration.snowflake.SnowflakeExportService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.order.orderstatus.OrderStatus;

public class SnowflakeOrderEventListenerTest {

	private SnowflakeExportService exportService;
	private Order order;
	private MerchantStore store;

	@Before
	public void setUp() {
		exportService = mock(SnowflakeExportService.class);
		store = new MerchantStore();
		store.setCode("DEFAULT");
		order = new Order();
		order.setId(1L);
		order.setMerchant(store);
	}

	@Test
	public void delegatesSaveOrderEvent() {
		new SnowflakeOrderEventListener(exportService, true).onApplicationEvent(new SaveOrderEvent(this, order, store));
		verify(exportService).exportOrder(order, store);
	}

	@Test
	public void delegatesStatusChangedEvent() {
		new SnowflakeOrderEventListener(exportService, true).onApplicationEvent(
				new OrderStatusChangedEvent(this, order, OrderStatus.ORDERED, OrderStatus.PROCESSED, store));
		verify(exportService).exportOrderStatusChange(order, OrderStatus.ORDERED, OrderStatus.PROCESSED, store);
	}

	@Test
	public void doesNotExportWhenDisabled() {
		SnowflakeOrderEventListener listener = new SnowflakeOrderEventListener(exportService, false);
		listener.onApplicationEvent(new SaveOrderEvent(this, order, store));
		listener.onApplicationEvent(
				new OrderStatusChangedEvent(this, order, OrderStatus.ORDERED, OrderStatus.PROCESSED, store));
		verifyNoInteractions(exportService);
	}

	@Test
	public void exportFailureDoesNotPropagate() {
		doThrow(new RuntimeException("snowflake down")).when(exportService).exportOrder(order, store);
		new SnowflakeOrderEventListener(exportService, true).onApplicationEvent(new SaveOrderEvent(this, order, store));
	}

}
