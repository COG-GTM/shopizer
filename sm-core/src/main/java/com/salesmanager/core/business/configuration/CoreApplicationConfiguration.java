package com.salesmanager.core.business.configuration;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.shopizer.modules.shipping.canadapost.autoconfigure.CanadaPostAutoConfiguration;
import com.shopizer.search.autoconfigure.SearchAutoConfiguration;

@Configuration
@ComponentScan({"com.salesmanager.core.business"})
@EnableAutoConfiguration
@EnableConfigurationProperties(ApplicationSearchConfiguration.class)
@EnableJpaRepositories(basePackages = "com.salesmanager.core.business.repositories")
@EntityScan(basePackages = "com.salesmanager.core.model")
@EnableTransactionManagement
@ImportResource("classpath:/spring/shopizer-core-context.xml")
// shopizer starters still register through META-INF/spring.factories, which Boot 3 no longer reads for auto-configuration
@Import({CanadaPostAutoConfiguration.class, SearchAutoConfiguration.class})
public class CoreApplicationConfiguration {



}
