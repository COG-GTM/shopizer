package com.salesmanager.core.modules.integration.shipping.canadapost;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CanadaPostConfiguration {

	@Bean
	public CanadaPostQuoteModuleConfig canadaPostQuoteModuleConfig(
			@Value("${module.shipping.canadapost.prodUrl:https://soa-gw.canadapost.ca/rs/ship/price}") String prodUrl,
			@Value("${module.shipping.canadapost.devUrl:https://ct.soa-gw.canadapost.ca/rs/ship/price}") String devUrl) {
		CanadaPostQuoteModuleConfig config = new CanadaPostQuoteModuleConfig();
		config.setProdUrl(prodUrl);
		config.setDevUrl(devUrl);
		return config;
	}

	@Bean(name = "canadapost")
	public CanadaPostQuoteModule canadapost(CanadaPostQuoteModuleConfig canadaPostQuoteModuleConfig) {
		CanadaPostQuoteModule module = new CanadaPostQuoteModule();
		module.setConfig(canadaPostQuoteModuleConfig);
		return module;
	}
}
