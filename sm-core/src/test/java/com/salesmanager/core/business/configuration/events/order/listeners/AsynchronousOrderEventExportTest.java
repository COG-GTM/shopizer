package com.salesmanager.core.business.configuration.events.order.listeners;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.Before;
import org.junit.Test;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import com.salesmanager.core.business.configuration.events.order.OrderStatusChangedEvent;
import com.salesmanager.core.business.configuration.events.order.SaveOrderEvent;
import com.salesmanager.core.business.modules.integration.snowflake.SnowflakeExportService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.order.orderstatus.OrderStatus;

/**
 * Order events are dispatched on the asynchronous multicaster configured by
 * {@code AsynchronousEventsConfiguration}, the export happens off the calling
 * (order transaction) thread.
 *
 */
public class AsynchronousOrderEventExportTest {

	private SnowflakeExportService exportService;
	private MerchantStore store;
	private Order order;

	@Before
	public void setUp() {
		exportService = mock(SnowflakeExportService.class);
		store = new MerchantStore();
		store.setCode("DEFAULT");
		order = new Order();
		order.setId(1L);
		order.setMerchant(store);
	}

	private SimpleApplicationEventMulticaster multicaster(boolean exportEnabled) {
		SimpleApplicationEventMulticaster multicaster = new SimpleApplicationEventMulticaster();
		multicaster.setTaskExecutor(new SimpleAsyncTaskExecutor());
		multicaster.addApplicationListener(new SnowflakeOrderEventListener(exportService, exportEnabled));
		return multicaster;
	}

	@Test
	public void ordersAreExportedAsynchronously() {
		multicaster(true).multicastEvent(new SaveOrderEvent(this, order, store));
		verify(exportService, timeout(5000)).exportOrder(order, store);
	}

	@Test
	public void statusChangesAreExportedAsynchronously() {
		multicaster(true)
				.multicastEvent(new OrderStatusChangedEvent(this, order, OrderStatus.ORDERED, OrderStatus.PROCESSED, store));
		verify(exportService, timeout(5000)).exportOrderStatusChange(order, OrderStatus.ORDERED, OrderStatus.PROCESSED,
				store);
	}

	@Test
	public void nothingIsExportedWhenTheFlagIsDisabled() throws Exception {
		multicaster(false).multicastEvent(new SaveOrderEvent(this, order, store));
		Thread.sleep(500);
		verifyNoInteractions(exportService);
	}

}
