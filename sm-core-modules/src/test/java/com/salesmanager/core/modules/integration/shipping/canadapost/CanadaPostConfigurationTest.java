package com.salesmanager.core.modules.integration.shipping.canadapost;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import com.salesmanager.core.modules.integration.shipping.model.ShippingQuoteModule;

public class CanadaPostConfigurationTest {

	@Test
	public void registersShippingModuleWithDefaultUrls() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		try {
			context.register(CanadaPostConfiguration.class);
			context.refresh();

			CanadaPostQuoteModule module = context.getBean("canadapost", CanadaPostQuoteModule.class);

			assertTrue(module instanceof ShippingQuoteModule);
			assertEquals("https://soa-gw.canadapost.ca/rs/ship/price", module.getConfig().getProdUrl());
			assertEquals("https://ct.soa-gw.canadapost.ca/rs/ship/price", module.getConfig().getDevUrl());
		} finally {
			context.close();
		}
	}

	@Test
	public void appliesConfiguredUrlOverrides() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		try {
			Map<String, Object> properties = new HashMap<String, Object>();
			properties.put("module.shipping.canadapost.prodUrl", "https://prod.example/rates");
			properties.put("module.shipping.canadapost.devUrl", "https://dev.example/rates");
			context.getEnvironment().getPropertySources().addFirst(
					new MapPropertySource("canada-post-test", properties));
			context.register(CanadaPostConfiguration.class);
			context.refresh();

			CanadaPostQuoteModule module = context.getBean("canadapost", CanadaPostQuoteModule.class);

			assertEquals("https://prod.example/rates", module.getConfig().getProdUrl());
			assertEquals("https://dev.example/rates", module.getConfig().getDevUrl());
		} finally {
			context.close();
		}
	}
}
