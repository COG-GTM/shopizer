package com.salesmanager.test.shop.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.salesmanager.core.business.configuration.events.AsynchronousEventsConfiguration;
import com.salesmanager.shop.application.config.AsyncConfig;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

class AsyncDispatchIntegrationTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void asyncProxyAndEventListenerExecuteOffCallerAndSurviveTaskFailure(boolean virtual) {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
                .withUserConfiguration(AsyncConfig.class, AsynchronousEventsConfiguration.class, Probes.class)
                .withPropertyValues("spring.threads.virtual.enabled=" + virtual)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    Probe probe = context.getBean(Probe.class);
                    Thread caller = Thread.currentThread();
                    Thread worker = probe.thread().get(5, TimeUnit.SECONDS);
                    assertNotSame(caller, worker);
                    assertEquals(virtual, worker.isVirtual());
                    assertThrows(java.util.concurrent.ExecutionException.class,
                            () -> probe.fail().get(5, TimeUnit.SECONDS));
                    assertEquals(virtual, probe.thread().get(5, TimeUnit.SECONDS).isVirtual());
                    CompletableFuture<Thread> received = new CompletableFuture<>();
                    context.publishEvent(new ProbeEvent(received));
                    Thread listener = received.get(5, TimeUnit.SECONDS);
                    assertNotSame(caller, listener);
                    assertEquals(virtual, listener.isVirtual());
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class Probes {
        @Bean Probe probe() { return new Probe(); }
    }

    static class Probe {
        @Async
        public CompletableFuture<Thread> thread() {
            return CompletableFuture.completedFuture(Thread.currentThread());
        }

        @Async
        public CompletableFuture<Void> fail() {
            throw new IllegalStateException("expected probe failure");
        }

        @EventListener
        public void receive(ProbeEvent event) { event.result.complete(Thread.currentThread()); }
    }

    record ProbeEvent(CompletableFuture<Thread> result) { }
}
