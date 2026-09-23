package com.salesmanager.core.business.configuration.events.order.listeners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import com.salesmanager.core.business.configuration.events.order.OrderEvent;
import com.salesmanager.core.business.configuration.events.order.OrderStatusChangedEvent;
import com.salesmanager.core.business.configuration.events.order.SaveOrderEvent;
import com.salesmanager.core.business.modules.integration.snowflake.SnowflakeExportService;

/**
 * Exports order events to Snowflake when the export is configured to do so
 *
 */
@Component
public class SnowflakeOrderEventListener implements ApplicationListener<OrderEvent> {

	private static final Logger LOGGER = LoggerFactory.getLogger(SnowflakeOrderEventListener.class);

	@Autowired
	private SnowflakeExportService snowflakeExportService;

	@Value("${snowflake.export.enabled:false}")
	private boolean exportEnabled;

	public SnowflakeOrderEventListener() {
	}

	SnowflakeOrderEventListener(SnowflakeExportService snowflakeExportService, boolean exportEnabled) {
		this.snowflakeExportService = snowflakeExportService;
		this.exportEnabled = exportEnabled;
	}

	@Override
	public void onApplicationEvent(OrderEvent event) {

		if (!exportEnabled) {
			LOGGER.debug("Snowflake order export is disabled, skipping event [{}]", event.getClass().getSimpleName());
			return;
		}

		try {
			if (event instanceof SaveOrderEvent) {
				snowflakeExportService.exportOrder(event.getOrder(), event.getStore());
			} else if (event instanceof OrderStatusChangedEvent) {
				OrderStatusChangedEvent statusChangedEvent = (OrderStatusChangedEvent) event;
				snowflakeExportService.exportOrderStatusChange(statusChangedEvent.getOrder(),
						statusChangedEvent.getOldStatus(), statusChangedEvent.getNewStatus(),
						statusChangedEvent.getStore());
			}
		} catch (Exception e) {
			LOGGER.error("Cannot export order event to Snowflake", e);
		}

	}

}
