package com.shopizer.modules.shipping.canadapost;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.country.Country;
import com.salesmanager.core.model.shipping.*;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.modules.integration.IntegrationException;
import com.shopizer.modules.shipping.canadapost.config.CanadaPostQuoteModuleConfig;
import com.shopizer.modules.shipping.canadapost.module.CanadaPostQuoteModule;
import com.shopizer.modules.shipping.canadapost.autoconfigure.CanadaPostAutoConfiguration;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Node;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

public class CanadaPostContractTest {
    private static final Path DATA = Paths.get(System.getProperty("canadapost.fixtures",
            "src/test/resources/canadapost"));
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

    public static void main(String[] args) throws Exception {
        for (String scenario : scenarios()) run(scenario, true);
    }

    private static List<String> scenarios() {
        return Arrays.asList("domestic", "us", "international", "error", "empty", "imperial", "tax", "multiple", "malformed", "production", "blank-postal", "no-packages", "empty-body");
    }

    @Test public void originalContracts() throws Exception {
        for (String scenario : scenarios()) run(scenario, false);
    }

    private static void run(String scenario, boolean record) throws Exception {
        String fixture = Arrays.asList("domestic", "us", "international", "error", "empty", "malformed")
                .contains(scenario) ? scenario : "domestic";
        byte[] response = "empty-body".equals(scenario) ? new byte[0]
                : Files.readAllBytes(DATA.resolve(fixture + ".xml"));
        List<String> requests = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + "\n"
                + exchange.getRequestHeaders().getFirst("Authorization") + "\n"
                + exchange.getRequestHeaders().getFirst("Accept") + "\n"
                + exchange.getRequestHeaders().getFirst("Content-Type") + "\n"
                + exchange.getRequestHeaders().getFirst("Accept-Language") + "\n" + canonical(request));
            exchange.getResponseHeaders().set("Content-Type", "application/vnd.cpc.ship.rate-v4+xml");
            exchange.sendResponseHeaders("error".equals(scenario) ? 400 : 200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        String result;
        try {
            CanadaPostQuoteModule module = new CanadaPostQuoteModule();
            CanadaPostQuoteModuleConfig config = new CanadaPostQuoteModuleConfig();
            config.setDevUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/rs/ship/price");
            config.setProdUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/prod");
            module.setConfig(config);
            Delivery delivery = new Delivery();
            Country country = new Country();
            country.setIsoCode("international".equals(scenario) ? "GB" : "us".equals(scenario) ? "US" : "CA");
            delivery.setCountry(country);
            delivery.setPostalCode("us".equals(scenario) ? " 90210 " : " k1a 0b1 ");
            if ("blank-postal".equals(scenario)) delivery.setPostalCode(" ");
            ShippingOrigin origin = new ShippingOrigin();
            origin.setPostalCode(" h2b 1a0 ");
            MerchantStore store = new MerchantStore();
            store.setSeizeunitcode("imperial".equals(scenario) ? "IN" : "CM");
            store.setWeightunitcode("imperial".equals(scenario) ? "LB" : "KG");
            ShippingConfiguration shipping = new ShippingConfiguration();
            shipping.setTaxOnShipping("tax".equals(scenario));
            List<PackageDetails> packages = new ArrayList<>();
            for (int i = 0; i < ("multiple".equals(scenario) ? 2 : 1); i++) {
                PackageDetails pack = new PackageDetails();
                pack.setShippingWeight(2.5);
                pack.setShippingHeight(3);
                pack.setShippingWidth(4);
                pack.setShippingLength(5);
                packages.add(pack);
            }
            if ("no-packages".equals(scenario)) packages.clear();
            IntegrationConfiguration integration = configuration();
            if ("production".equals(scenario)) integration.setEnvironment("PROD");
            try {
                List<ShippingOption> options = module.getShippingQuotes(new ShippingQuote(), packages,
                    BigDecimal.TEN, delivery, origin, store, integration, null, shipping, Locale.CANADA);
                if (options != null) options.sort(Comparator.comparing(ShippingOption::getOptionCode));
                result = JSON.writeValueAsString(options);
            } catch (IntegrationException ex) {
                result = JSON.writeValueAsString(Arrays.asList(ex.getErrorCode(), ex.getMessageCode(),
                    ex.getCause() == null ? ex.getMessage() : ex.getCause().getClass().getSimpleName(),
                    ex.getCause() instanceof IntegrationException ? ((IntegrationException) ex.getCause()).getMessageCode() : null));
            }
        } finally {
            server.stop(0);
        }
        Path golden = DATA.resolve(scenario + ".golden.json");
        Path requestGolden = DATA.resolve(scenario + ".request.txt");
        String requestText = String.join("\n---\n", requests);
        if (record) {
            Files.writeString(golden, result + "\n");
            Files.writeString(requestGolden, requestText);
            System.out.println(scenario + ": " + result + " requests=" + requests.size());
        } else {
            assertEquals(scenario, Files.readString(golden).trim(), result);
            assertEquals(scenario + " request", Files.readString(requestGolden), requestText);
        }
    }

    private static String canonical(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return canonical(factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(
                xml.getBytes(StandardCharsets.UTF_8))).getDocumentElement());
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static String canonical(Node node) {
        if (node.getNodeType() == Node.TEXT_NODE) return node.getTextContent().trim();
        StringBuilder out = new StringBuilder("{").append(node.getNamespaceURI()).append("}")
                .append(node.getLocalName()).append("[");
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling())
            out.append(canonical(child));
        return out.append("]").toString();
    }

    private static IntegrationConfiguration configuration() {
        IntegrationConfiguration config = new IntegrationConfiguration();
        config.setEnvironment("TEST");
        config.getIntegrationKeys().put("username", "test-user");
        config.getIntegrationKeys().put("password", "test-password");
        config.getIntegrationKeys().put("account", "1234567890");
        config.getIntegrationKeys().put("apikey", "unused-key");
        config.getIntegrationOptions().put("services-domestic", Arrays.asList("DOM.RP", "DOM.XP"));
        config.getIntegrationOptions().put("services-usa", Arrays.asList("USA.EP"));
        config.getIntegrationOptions().put("services-intl", Arrays.asList("INT.XP"));
        return config;
    }

    @Test public void validationPreservesLastMissingField() throws Exception {
        CanadaPostQuoteModule module = new CanadaPostQuoteModule();
        module.validateModuleConfiguration(configuration(), new MerchantStore());
        for (String field : Arrays.asList("username", "password", "account", "apikey")) {
            IntegrationConfiguration config = configuration();
            config.getIntegrationKeys().put(field, " ");
            try {
                module.validateModuleConfiguration(config, new MerchantStore());
                fail(field);
            } catch (IntegrationException ex) {
                assertEquals(100, ex.getErrorCode());
                assertEquals(Collections.singletonList(field), ex.getErrorFields());
            }
        }
        IntegrationConfiguration config = new IntegrationConfiguration();
        try { module.validateModuleConfiguration(config, new MerchantStore()); fail(); }
        catch (IntegrationException ex) { assertEquals(Collections.singletonList("apikey"), ex.getErrorFields()); }
        config.setIntegrationKeys(null);
        try { module.validateModuleConfiguration(config, new MerchantStore()); fail(); }
        catch (IntegrationException ex) { assertEquals(100, ex.getErrorCode()); assertNull(ex.getErrorFields()); }
    }

    @Test public void beanDefaultsAndPropertyOverrides() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(CanadaPostAutoConfiguration.class)) {
            CanadaPostQuoteModule module = context.getBean("canadapost", CanadaPostQuoteModule.class);
            assertSame(context.getBean("moduleConfig"), module.getConfig());
            assertEquals("https://soa-gw.canadapost.ca/rs/ship/price", module.getConfig().getProdUrl());
            assertEquals("https://ct.soa-gw.canadapost.ca/rs/ship/price", module.getConfig().getDevUrl());
        }
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            Map<String, Object> properties = new HashMap<>();
            properties.put("module.shipping.canadapost.prodUrl", "http://localhost/prod");
            properties.put("module.shipping.canadapost.devUrl", "http://localhost/test");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
            context.register(CanadaPostAutoConfiguration.class);
            context.refresh();
            CanadaPostQuoteModule module = context.getBean("canadapost", CanadaPostQuoteModule.class);
            assertEquals("http://localhost/prod", module.getConfig().getProdUrl());
            assertEquals("http://localhost/test", module.getConfig().getDevUrl());
        }
    }
}
