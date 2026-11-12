package com.salesmanager.shop.application.config;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

  private static final int EXECUTOR_SERVICE_NUMBER_THREADS = 5;

  @Value("${spring.threads.virtual.enabled:false}")
  private boolean virtualThreadsEnabled;

  @Bean(destroyMethod = "close")
  public ExecutorService shopizerAsyncExecutor() {
    return virtualThreadsEnabled ? Executors.newVirtualThreadPerTaskExecutor()
        : Executors.newFixedThreadPool(EXECUTOR_SERVICE_NUMBER_THREADS);
  }

  @Override
  public Executor getAsyncExecutor() {
    return shopizerAsyncExecutor();
  }

  @Override
  public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return new SimpleAsyncUncaughtExceptionHandler();
  }
}
