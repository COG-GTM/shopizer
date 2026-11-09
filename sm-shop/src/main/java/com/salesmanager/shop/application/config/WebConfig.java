package com.salesmanager.shop.application.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.UrlHandlerFilter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Configuration
public class WebConfig implements WebMvcConfigurer {
	
	
    @Autowired
    private MerchantStoreArgumentResolver merchantStoreArgumentResolver;
    
    @Autowired
    private LanguageArgumentResolver languageArgumentResolver;

	
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
        argumentResolvers.add(merchantStoreArgumentResolver);
        argumentResolvers.add(languageArgumentResolver);
    }

    /**
     * Spring Framework 6 no longer matches "/path/" to "/path"; existing API clients rely on trailing slashes.
     */
    @Bean
    public FilterRegistrationBean<UrlHandlerFilter> trailingSlashFilter() {
        FilterRegistrationBean<UrlHandlerFilter> registration = new FilterRegistrationBean<>(
                UrlHandlerFilter.trailingSlashHandler("/**").wrapRequest().build());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
    

}
