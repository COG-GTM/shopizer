package com.salesmanager.test.shop.util;

import com.salesmanager.core.business.configuration.events.AsynchronousEventsConfiguration;
import com.salesmanager.shop.application.config.AsyncConfig;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import static org.junit.jupiter.api.Assertions.*;

class VirtualThreadsTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(AsyncConfig.class, AsynchronousEventsConfiguration.class);

    @Test
    void defaultsToPlatformThreads() {
        assertThreadMode(context, false);
    }

    @Test
    void explicitlyDisablesVirtualThreads() {
        assertThreadMode(context.withPropertyValues("spring.threads.virtual.enabled=false"), false);
    }

    @Test
    void enablesVirtualThreadsForAsyncAndEvents() {
        assertThreadMode(context.withPropertyValues("spring.threads.virtual.enabled=true"), true);
    }

    @Test
    void rejectsInvalidThreadMode() {
        context.withPropertyValues("spring.threads.virtual.enabled=invalid")
                .run(application -> assertNotNull(application.getStartupFailure()));
    }

    private void assertThreadMode(ApplicationContextRunner runner, boolean virtual) {
        runner.run(application -> {
            assertNull(application.getStartupFailure());
            ExecutorService executor = application.getBean("shopizerAsyncExecutor", ExecutorService.class);
            assertSame(executor, application.getBean(AsyncConfig.class).getAsyncExecutor());
            assertEquals(virtual, executor.submit(() -> Thread.currentThread().isVirtual())
                    .get(5, TimeUnit.SECONDS));
            CompletableFuture<Boolean> eventThread = new CompletableFuture<>();
            SimpleApplicationEventMulticaster events = application.getBean(
                    "applicationEventMulticaster", SimpleApplicationEventMulticaster.class);
            events.addApplicationListener((ApplicationListener<ProbeEvent>) event ->
                    eventThread.complete(Thread.currentThread().isVirtual()));
            events.multicastEvent(new ProbeEvent(this));
            assertEquals(virtual, eventThread.get(5, TimeUnit.SECONDS));
        });
    }

    private static class ProbeEvent extends ApplicationEvent {
        ProbeEvent(Object source) {
            super(source);
        }
    }
}
