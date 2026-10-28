package com.salesmanager.test.shop.common;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

@TestConfiguration
public class TestPropertyPlaceholderConfiguration {

	@Bean
	public static PropertySourcesPlaceholderConfigurer testPropertySourcesPlaceholderConfigurer(
			ConfigurableEnvironment environment) {
		PropertySourcesPlaceholderConfigurer configurer = new PropertySourcesPlaceholderConfigurer();
		configurer.setEnvironment(environment);
		configurer.setLocalOverride(true);
		configurer.setIgnoreUnresolvablePlaceholders(true);
		configurer.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return configurer;
	}
}
