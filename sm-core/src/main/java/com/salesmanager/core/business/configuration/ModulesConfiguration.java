package com.salesmanager.core.business.configuration;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Configuration;

import com.salesmanager.core.modules.integration.payment.model.PaymentModule;
import com.salesmanager.core.modules.integration.shipping.model.ShippingQuoteModule;
import com.shopizer.modules.shipping.canadapost.autoconfigure.CanadaPostAutoConfiguration;
import com.shopizer.search.autoconfigure.SearchAutoConfiguration;

/**
 * Contains injection of external shopizer starter modules
 * @author carlsamson
 * New Way - out of xml config and using spring boot starters
 *
 *
 * The shopizer starters only register their auto-configuration through the Spring Boot 2
 * spring.factories mechanism, which Spring Boot 3 ignores, so they are imported explicitly.
 */
@Configuration
@ImportAutoConfiguration({CanadaPostAutoConfiguration.class, SearchAutoConfiguration.class})
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

    
    
    


}
