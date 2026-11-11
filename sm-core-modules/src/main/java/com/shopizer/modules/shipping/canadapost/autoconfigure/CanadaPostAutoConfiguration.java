package com.shopizer.modules.shipping.canadapost.autoconfigure;

import com.shopizer.modules.shipping.canadapost.config.CanadaPostQuoteModuleConfig;
import com.shopizer.modules.shipping.canadapost.module.CanadaPostQuoteModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(value={CanadaPostQuoteModule.class})
public class CanadaPostAutoConfiguration {
    Logger logger = LoggerFactory.getLogger(CanadaPostAutoConfiguration.class);
    @Value("${module.shipping.canadapost.prodUrl:https://soa-gw.canadapost.ca/rs/ship/price}")
    private String prodUrl = "https://soa-gw.canadapost.ca/rs/ship/price";
    @Value("${module.shipping.canadapost.devUrl:https://ct.soa-gw.canadapost.ca/rs/ship/price}")
    private String devUrl = "https://ct.soa-gw.canadapost.ca/rs/ship/price";

    @Bean
    public CanadaPostQuoteModuleConfig moduleConfig() {
        this.logger.info("autoconfigure [" + this.getClass().getName() + "]");
        CanadaPostQuoteModuleConfig moduleConfig = new CanadaPostQuoteModuleConfig();
        moduleConfig.setDevUrl(this.devUrl);
        moduleConfig.setProdUrl(this.prodUrl);
        return moduleConfig;
    }

    @Bean
    public CanadaPostQuoteModule canadapost() {
        this.logger.info("instantiate ported canadapost bean [" + CanadaPostQuoteModule.class.getName() + "]");
        CanadaPostQuoteModule canadapost = new CanadaPostQuoteModule();
        canadapost.setConfig(this.moduleConfig());
        return canadapost;
    }
}
