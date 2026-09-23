package com.salesmanager.core.business.configuration.events.order;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.salesmanager.core.model.order.Order;

/**
 * Aspect class that will trigger an event once an order is processed
 *
 */

@Component
@Aspect
public class PublishOrderAspect {

	private static final Logger LOGGER = LoggerFactory.getLogger(PublishOrderAspect.class);

	private ApplicationEventPublisher eventPublisher;

	@Autowired
	public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
		this.eventPublisher = eventPublisher;
	}

	@Pointcut("@target(org.springframework.stereotype.Service)")
	public void serviceMethods() {
	}

	@Pointcut("execution(* com.salesmanager.core.business.services.order.OrderService.processOrder(com.salesmanager.core.model.order.Order, com.salesmanager.core.model.customer.Customer, java.util.List, com.salesmanager.core.model.order.OrderTotalSummary, com.salesmanager.core.model.payments.Payment, com.salesmanager.core.model.merchant.MerchantStore))")
	public void processOrderMethod() {
	}

	@Pointcut("execution(* com.salesmanager.core.business.services.order.OrderService.processOrder(com.salesmanager.core.model.order.Order, com.salesmanager.core.model.customer.Customer, java.util.List, com.salesmanager.core.model.order.OrderTotalSummary, com.salesmanager.core.model.payments.Payment, com.salesmanager.core.model.payments.Transaction, com.salesmanager.core.model.merchant.MerchantStore))")
	public void processOrderWithTransactionMethod() {
	}

	@Pointcut("serviceMethods() && (processOrderMethod() || processOrderWithTransactionMethod())")
	public void orderCreationMethods() {
	}

	@AfterReturning(value = "orderCreationMethods()", returning = "entity")
	public void createOrderEvent(JoinPoint jp, Object entity) {
		try {
			Order order = (Order) entity;
			eventPublisher.publishEvent(new SaveOrderEvent(eventPublisher, order, order.getMerchant()));
		} catch (Exception e) {
			LOGGER.error("Cannot publish order event", e);
		}
	}

}
