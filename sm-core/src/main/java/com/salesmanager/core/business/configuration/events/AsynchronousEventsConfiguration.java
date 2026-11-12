package com.salesmanager.core.business.configuration.events;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.beans.factory.annotation.Value;

/**
 * Events will be asynchronous (in a different thread)
 * @author carlsamson
 *
 */
@Configuration
public class AsynchronousEventsConfiguration {
	
	   @Bean(name = "applicationEventMulticaster")
	   public ApplicationEventMulticaster simpleApplicationEventMulticaster(
	       @Value("${spring.threads.virtual.enabled:false}") boolean virtualThreadsEnabled) {
	       SimpleApplicationEventMulticaster eventMulticaster
	         = new SimpleApplicationEventMulticaster();
	        
	       SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
	       executor.setVirtualThreads(virtualThreadsEnabled);
	       eventMulticaster.setTaskExecutor(executor);
	       return eventMulticaster;
	   }

}
