package com.shopizer.modules.shipping.canadapost;

import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.country.Country;
import com.salesmanager.core.model.shipping.*;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.modules.integration.IntegrationException;
import com.shopizer.modules.shipping.canadapost.config.CanadaPostQuoteModuleConfig;
import com.shopizer.modules.shipping.canadapost.module.CanadaPostQuoteModule;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CanadaPostEdgeCaseTest {
    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private final List<String> bodies = new ArrayList<>();
    private final CanadaPostQuoteModule module = new CanadaPostQuoteModule();
    private final IntegrationConfiguration configuration = new IntegrationConfiguration();
    private final MerchantStore store = new MerchantStore();
    private final Delivery delivery = new Delivery();
    private final ShippingOrigin origin = new ShippingOrigin();
    private final ShippingConfiguration shipping = new ShippingConfiguration();
    private final List<PackageDetails> packages = new ArrayList<>();
    private int status = 200;
    private String response = quotes(price("DOM.RP", "Regular Parcel", "12.34", "12.96"));
    private String authorization;

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rate", exchange -> {
            requests.incrementAndGet();
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/vnd.cpc.ship.rate-v4+xml");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        CanadaPostQuoteModuleConfig config = new CanadaPostQuoteModuleConfig();
        config.setDevUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/rate");
        module.setConfig(config);
        configuration.getIntegrationKeys().put("username", "test-user");
        configuration.getIntegrationKeys().put("password", "test:password");
        configuration.getIntegrationKeys().put("account", "1234567890");
        configuration.getIntegrationOptions().put("services-domestic", Arrays.asList("DOM.RP", "DOM.XP"));
        configuration.getIntegrationOptions().put("services-usa", Collections.singletonList("USA.EP"));
        store.setSeizeunitcode("CM");
        store.setWeightunitcode("KG");
        Country country = new Country();
        country.setIsoCode("CA");
        delivery.setCountry(country);
        delivery.setPostalCode(" k1a 0b1 ");
        origin.setPostalCode(" h2b 1a0 ");
        packages.add(parcel());
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void aggregatesEachServiceIndependentlyAcrossThreePackages() throws Exception {
        response = quotes(price("DOM.RP", "Colis régulier", "12.34", "12.96")
                + price("DOM.XP", "Xpresspost", "20.00", "21.00"));
        packages.add(parcel());
        packages.add(parcel());
        List<ShippingOption> options = quote();
        assertEquals(2, options.size());
        ShippingOption regular = option(options, "DOM.RP");
        assertEquals(new BigDecimal("38.26"), regular.getOptionPrice());
        assertEquals("Colis régulier", regular.getOptionName());
        assertEquals(new BigDecimal("62.00"), option(options, "DOM.XP").getOptionPrice());
        assertEquals(3, requests.get());
    }

    @Test
    void includesTaxOnEveryPackageWhenEnabled() throws Exception {
        shipping.setTaxOnShipping(true);
        packages.add(parcel());
        assertEquals(new BigDecimal("25.92"), quote().get(0).getOptionPrice());
        assertEquals(2, requests.get());
    }

    @Test
    void translatesAllCarrierMessagesEvenForServerErrors() {
        status = 500;
        response = "<messages xmlns=\"http://www.canadapost.ca/ws/messages\">"
                + "<message><code>100</code><description>First</description></message>"
                + "<message><code>200</code><description>Second</description></message></messages>";
        IntegrationException error = assertThrows(IntegrationException.class, this::quote);
        assertEquals(99, error.getErrorCode());
        assertTrue(error.getCause() instanceof IntegrationException);
        assertEquals("100 - First 200 - Second ", ((IntegrationException) error.getCause()).getMessageCode());
        assertEquals(1, requests.get());
    }

    @Test
    void rejectsMissingCredentialsBeforeSendingAnything() {
        for (String key : Arrays.asList("username", "password", "account")) {
            String value = configuration.getIntegrationKeys().put(key, " ");
            IntegrationException error = assertThrows(IntegrationException.class, this::quote);
            assertEquals("Canadapost missing configuration key " + key, error.getMessageCode());
            configuration.getIntegrationKeys().put(key, value);
        }
        assertEquals(0, requests.get());
    }

    @Test
    void rejectsUnselectedDomesticAndUsServicesBeforeSendingAnything() {
        configuration.getIntegrationOptions().put("services-domestic", Collections.emptyList());
        assertEquals("Canadapost selected domestic services are missing",
                assertThrows(IntegrationException.class, this::quote).getMessageCode());
        delivery.getCountry().setIsoCode("US");
        configuration.getIntegrationOptions().remove("services-usa");
        assertEquals("Canadapost selected USA services are missing",
                assertThrows(IntegrationException.class, this::quote).getMessageCode());
        assertEquals(0, requests.get());
    }

    @Test
    void escapesXmlAndPreservesBasicAuthPasswordContainingColon() throws Exception {
        configuration.getIntegrationKeys().put("account", "123&<456");
        quote();
        assertEquals("Basic " + Base64.getEncoder().encodeToString(
                "test-user:test:password".getBytes(StandardCharsets.ISO_8859_1)), authorization);
        assertTrue(bodies.get(0).contains("123&amp;&lt;456"));
        assertTrue(bodies.get(0).contains("<origin-postal-code>H2B1A0</origin-postal-code>"));
        assertTrue(bodies.get(0).contains("<postal-code>K1A0B1</postal-code>"));
    }

    @Test
    void rejectsWrongNamespaceAsCode99InsteadOfReturningAnEmptyQuote() {
        response = "<price-quotes xmlns=\"urn:wrong\"/>";
        assertEquals(99, assertThrows(IntegrationException.class, this::quote).getErrorCode());
        assertEquals(1, requests.get());
    }

    private List<ShippingOption> quote() throws IntegrationException {
        return module.getShippingQuotes(new ShippingQuote(), packages, BigDecimal.TEN, delivery,
                origin, store, configuration, null, shipping, Locale.CANADA);
    }

    private static PackageDetails parcel() {
        PackageDetails parcel = new PackageDetails();
        parcel.setShippingWeight(2.5);
        parcel.setShippingHeight(3);
        parcel.setShippingWidth(4);
        parcel.setShippingLength(5);
        return parcel;
    }

    private static ShippingOption option(List<ShippingOption> options, String code) {
        return options.stream().filter(option -> code.equals(option.getOptionCode())).findFirst().orElseThrow();
    }

    private static String quotes(String entries) {
        return "<price-quotes xmlns=\"http://www.canadapost.ca/ws/ship/rate-v4\">" + entries + "</price-quotes>";
    }

    private static String price(String code, String name, String base, String due) {
        return "<price-quote><service-code>" + code + "</service-code><service-name>" + name
                + "</service-name><price-details><base>" + base + "</base><due>" + due
                + "</due></price-details></price-quote>";
    }
}
