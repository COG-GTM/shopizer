package com.salesmanager.test.shop.integration;

import com.salesmanager.core.modules.integration.shipping.model.ShippingQuoteModule;
import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.country.Country;
import com.salesmanager.core.model.shipping.*;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.test.shop.common.ServicesTestSupport;
import com.shopizer.modules.shipping.canadapost.config.CanadaPostQuoteModuleConfig;
import com.shopizer.modules.shipping.canadapost.module.CanadaPostQuoteModule;
import java.util.Map;
import java.util.Collections;
import java.util.Locale;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import com.sun.net.httpserver.HttpServer;
import javax.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class CanadaPostWiringIntegrationTest extends ServicesTestSupport {
    @Autowired private ApplicationContext context;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired @Qualifier("shippingModules") private Map<String, ShippingQuoteModule> shippingModules;

    @Test
    void autoConfigurationIsDiscoveredAndXmlRegistryUsesTheSameSingleton() {
        CanadaPostQuoteModule module = context.getBean("canadapost", CanadaPostQuoteModule.class);
        assertEquals(1, context.getBeansOfType(CanadaPostQuoteModule.class).size());
        assertSame(module, shippingModules.get("canadapost"));
        assertSame(context.getBean("moduleConfig", CanadaPostQuoteModuleConfig.class), module.getConfig());
        assertEquals("https://soa-gw.canadapost.ca/rs/ship/price", module.getConfig().getProdUrl());
        assertEquals("https://ct.soa-gw.canadapost.ca/rs/ship/price", module.getConfig().getDevUrl());
    }

    @Test
    void hibernateStillLoadsAndExecutesH2QueriesAlongsideTheShippingModule() {
        javax.persistence.EntityManager manager = entityManagerFactory.createEntityManager();
        try {
            Number count = (Number) manager.createNativeQuery("SELECT COUNT(*) FROM SALESMANAGER.MERCHANT_STORE")
                    .getSingleResult();
            assertTrue(count.longValue() > 0);
            assertNotNull(context.getBean("canadapost"));
        } finally {
            manager.close();
        }
    }

    @Test
    void springManagedModuleCanMarshalAndReadACarrierQuote() throws Exception {
        CanadaPostQuoteModule module = context.getBean("canadapost", CanadaPostQuoteModule.class);
        String original = module.getConfig().getDevUrl();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rate", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = ("<price-quotes xmlns=\"http://www.canadapost.ca/ws/ship/rate-v4\">"
                    + "<price-quote><service-code>DOM.RP</service-code><service-name>Regular Parcel</service-name>"
                    + "<price-details><base>12.34</base><due>12.96</due></price-details></price-quote></price-quotes>")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/vnd.cpc.ship.rate-v4+xml");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            module.getConfig().setDevUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/rate");
            IntegrationConfiguration configuration = new IntegrationConfiguration();
            configuration.getIntegrationKeys().put("username", "test-user");
            configuration.getIntegrationKeys().put("password", "test-password");
            configuration.getIntegrationKeys().put("account", "1234567890");
            configuration.getIntegrationOptions().put("services-domestic", Collections.singletonList("DOM.RP"));
            MerchantStore store = new MerchantStore();
            store.setSeizeunitcode("CM");
            store.setWeightunitcode("KG");
            Delivery delivery = new Delivery();
            Country country = new Country();
            country.setIsoCode("CA");
            delivery.setCountry(country);
            delivery.setPostalCode("K1A0B1");
            ShippingOrigin origin = new ShippingOrigin();
            origin.setPostalCode("H2B1A0");
            PackageDetails parcel = new PackageDetails();
            parcel.setShippingWeight(2.5);
            parcel.setShippingHeight(3);
            parcel.setShippingWidth(4);
            parcel.setShippingLength(5);
            java.util.List<ShippingOption> options = shippingModules.get("canadapost").getShippingQuotes(
                    new ShippingQuote(), Collections.singletonList(parcel), BigDecimal.TEN, delivery, origin,
                    store, configuration, null, new ShippingConfiguration(), Locale.CANADA);
            assertEquals(1, options.size());
            assertEquals("DOM.RP", options.get(0).getOptionCode());
            assertEquals(new BigDecimal("12.34"), options.get(0).getOptionPrice());
        } finally {
            module.getConfig().setDevUrl(original);
            server.stop(0);
        }
    }
}
