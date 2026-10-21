package com.salesmanager.core.business.configuration;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.salesmanager.core.modules.integration.shipping.canadapost.CanadaPostConfiguration;
import com.salesmanager.core.modules.integration.payment.model.PaymentModule;
import com.salesmanager.core.modules.integration.shipping.model.ShippingQuoteModule;

/**
 * Contains injection of external shopizer starter modules
 * @author carlsamson
 * New Way - out of xml config and using spring boot starters
 *
 */
@Configuration
@Import(CanadaPostConfiguration.class)
public class ModulesConfiguration {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ModulesConfiguration.class);
	
	
	/**
	 * Canada Post shipping integration from sm-core-modules
	 */
    @Autowired
    private ShippingQuoteModule canadapost;
    
    
    /**
     * All living modules exposed here
     */
    @Autowired
    private List<PaymentModule> liveModules;

    
    
    


}
