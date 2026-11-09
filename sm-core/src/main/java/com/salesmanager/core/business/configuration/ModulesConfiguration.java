package com.salesmanager.core.business.configuration;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.shopizer.modules.shipping.canadapost.autoconfigure.CanadaPostAutoConfiguration;
import com.shopizer.search.autoconfigure.SearchAutoConfiguration;

import com.salesmanager.core.business.modules.search.elasticsearch.ElasticsearchSearchModule;
import com.salesmanager.core.modules.integration.payment.model.PaymentModule;
import com.salesmanager.core.modules.integration.shipping.model.ShippingQuoteModule;

/**
 * Contains injection of external shopizer starter modules
 * @author carlsamson
 * New Way - out of xml config and using spring boot starters
 *
 */
// Boot 3 no longer reads EnableAutoConfiguration from spring.factories used by these legacy starters
@Configuration
@Import({ CanadaPostAutoConfiguration.class, SearchAutoConfiguration.class })
public class ModulesConfiguration {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ModulesConfiguration.class);
	
	
	/**
	 * Goes along with
	 * shipping-canadapost-spring-boot-starter
	 */
    @Autowired
    private ShippingQuoteModule canadapost;
    
    
    /**
     * All living modules exposed here
     */
    @Autowired
    private List<PaymentModule> liveModules;

    /**
     * Elasticsearch 8 search module, used when search.engine=elasticsearch
     * (the opensearch starter's module is used otherwise)
     */
    @Bean
    public ElasticsearchSearchModule elasticsearchSearchModule() {
        return new ElasticsearchSearchModule();
    }

    
    
    


}
