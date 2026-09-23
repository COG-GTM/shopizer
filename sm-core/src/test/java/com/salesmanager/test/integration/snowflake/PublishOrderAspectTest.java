package com.salesmanager.test.integration.snowflake;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.aspectj.lang.annotation.Pointcut;
import org.junit.Test;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import com.salesmanager.core.business.configuration.events.order.PublishOrderAspect;
import com.salesmanager.core.business.configuration.events.order.SaveOrderEvent;
import com.salesmanager.core.business.services.order.OrderService;
import com.salesmanager.core.business.services.order.OrderServiceImpl;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;

/**
 * Verifies the aspect pointcuts match the {@link OrderService} order creation
 * methods and that a {@link SaveOrderEvent} is published for a created order.
 *
 */
public class PublishOrderAspectTest {

	private final List<ApplicationEvent> published = new ArrayList<ApplicationEvent>();

	private PublishOrderAspect aspect() {
		PublishOrderAspect aspect = new PublishOrderAspect();
		aspect.setEventPublisher(new ApplicationEventPublisher() {
			@Override
			public void publishEvent(Object event) {
				published.add((ApplicationEvent) event);
			}
		});
		return aspect;
	}

	private boolean matches(String pointcutMethodName, Method target) throws Exception {
		Method pointcutMethod = PublishOrderAspect.class.getMethod(pointcutMethodName);
		AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
		pointcut.setExpression(pointcutMethod.getAnnotation(Pointcut.class).value());
		return pointcut.matches(target, OrderServiceImpl.class);
	}

	@Test
	public void pointcutsMatchOrderServiceProcessOrderMethods() throws Exception {
		int matched = 0;
		for (Method method : OrderService.class.getMethods()) {
			if (!"processOrder".equals(method.getName())) {
				continue;
			}
			if (matches("processOrderMethod", method) || matches("processOrderWithTransactionMethod", method)) {
				matched++;
			}
		}
		assertEquals(2, matched);
	}

	@Test
	public void publishesSaveOrderEventForCreatedOrder() {
		MerchantStore store = new MerchantStore();
		store.setCode("DEFAULT");

		Order order = new Order();
		order.setId(5L);
		order.setMerchant(store);

		aspect().createOrderEvent(null, order);

		assertEquals(1, published.size());
		assertTrue(published.get(0) instanceof SaveOrderEvent);
		assertEquals(order, ((SaveOrderEvent) published.get(0)).getOrder());
		assertEquals(store, ((SaveOrderEvent) published.get(0)).getStore());
	}

}
