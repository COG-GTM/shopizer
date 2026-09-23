package com.salesmanager.test.integration.snowflake;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import javax.inject.Inject;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;

import com.salesmanager.core.business.configuration.events.order.SaveOrderEvent;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.order.orderstatus.OrderStatus;
import com.salesmanager.test.common.AbstractSalesManagerCoreTestCase;

/**
 * Boots the H2 test context with the shipped (disabled) Snowflake
 * configuration, no Snowflake datasource must be created and publishing order
 * events must not attempt any export.
 *
 */
public class SnowflakeExportDisabledTest extends AbstractSalesManagerCoreTestCase {

	@Inject
	private ApplicationContext applicationContext;

	@Inject
	private ApplicationEventPublisher eventPublisher;

	@Test
	public void noSnowflakeDatasourceIsCreatedByDefault() {
		assertFalse(applicationContext.containsBean("snowflakeDataSource"));
		assertTrue(applicationContext.containsBean("snowflakeExportService"));
	}

	@Test
	public void publishingOrderEventsIsHarmlessWhenExportIsDisabled() throws Exception {
		MerchantStore store = merchantService.getByCode(MerchantStore.DEFAULT_STORE);

		Order order = new Order();
		order.setId(1L);
		order.setStatus(OrderStatus.ORDERED);
		order.setMerchant(store);

		eventPublisher.publishEvent(new SaveOrderEvent(this, order, store));

		Thread.sleep(500);
	}

}
