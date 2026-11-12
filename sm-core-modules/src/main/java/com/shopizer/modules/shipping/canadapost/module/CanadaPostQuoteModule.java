package com.shopizer.modules.shipping.canadapost.module;

import ca.canadapost.cpcdp.rating.generated.messages.Messages;
import ca.canadapost.cpcdp.rating.generated.rating.MailingScenario;
import ca.canadapost.cpcdp.rating.generated.rating.PriceQuotes;
import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.country.Country;
import com.salesmanager.core.model.shipping.PackageDetails;
import com.salesmanager.core.model.shipping.ShippingConfiguration;
import com.salesmanager.core.model.shipping.ShippingOption;
import com.salesmanager.core.model.shipping.ShippingOrigin;
import com.salesmanager.core.model.shipping.ShippingQuote;
import com.salesmanager.core.model.system.CustomIntegrationConfiguration;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.core.modules.integration.IntegrationException;
import com.salesmanager.core.modules.integration.shipping.model.ShippingQuoteModule;
import com.shopizer.modules.shipping.canadapost.config.CanadaPostQuoteModuleConfig;
import jakarta.xml.bind.JAXBContext;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

public class CanadaPostQuoteModule implements ShippingQuoteModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(CanadaPostQuoteModule.class);
    private static final String CANADA_CODE = "CA";
    private static final String US_CODE = "US";
    private static final Double CM_MULTIPLIER = 2.54;
    private static final Double KG_MULTIPLIER = 0.453592;
    private static final String KG = "KG";
    private static final String CM = "CM";
    private static final String PROD_ENV = "PROD";
    private CanadaPostQuoteModuleConfig config;

    public CanadaPostQuoteModuleConfig getConfig() {
        return this.config;
    }

    public void setConfig(CanadaPostQuoteModuleConfig config) {
        this.config = config;
    }

    public void validateModuleConfiguration(IntegrationConfiguration integrationConfiguration, MerchantStore store) throws IntegrationException {
        ArrayList<String> errorFields = null;
        Map<String, String> keys = integrationConfiguration.getIntegrationKeys();
        if (keys == null) {
            IntegrationException ex = new IntegrationException(100);
            ex.setErrorFields(errorFields);
            throw ex;
        }
        if (StringUtils.isBlank(keys.get("username"))) {
            errorFields = new ArrayList<String>();
            errorFields.add("username");
        }
        if (StringUtils.isBlank(keys.get("password"))) {
            errorFields = new ArrayList<>();
            errorFields.add("password");
        }
        if (StringUtils.isBlank(keys.get("account"))) {
            errorFields = new ArrayList<>();
            errorFields.add("account");
        }
        if (StringUtils.isBlank(keys.get("apikey"))) {
            errorFields = new ArrayList<>();
            errorFields.add("apikey");
        }
        if (errorFields != null) {
            IntegrationException ex = new IntegrationException(100);
            ex.setErrorFields(errorFields);
            throw ex;
        }
    }

    public CustomIntegrationConfiguration getCustomModuleConfiguration(MerchantStore store) throws IntegrationException {
        return null;
    }

    public List<ShippingOption> getShippingQuotes(ShippingQuote quote, List<PackageDetails> packages, BigDecimal orderTotal, Delivery delivery, ShippingOrigin origin, MerchantStore store, IntegrationConfiguration configuration, IntegrationModule module, ShippingConfiguration shippingConfiguration, Locale locale) throws IntegrationException {
        MailingScenario.Services service;
        Validate.notNull(store, "MerchantStore is null");
        Validate.notNull(shippingConfiguration, "Module Canadapost is not configured");
        Validate.notNull(packages, "Packages are null");
        Validate.notNull(delivery, "Delivery is null");
        Validate.notNull(origin, "Origin is null");
        Validate.notNull(origin.getPostalCode(), "Origin postal code is null");
        if (StringUtils.isBlank(delivery.getPostalCode())) {
            return null;
        }
        Country country = delivery.getCountry();
        if (!country.getIsoCode().equals(US_CODE) && !country.getIsoCode().equals(CANADA_CODE)) {
            throw new IntegrationException("Canadapost Not configured for shipping in country " + country.getIsoCode());
        }
        Map<String, String> keys = configuration.getIntegrationKeys();
        Map<String, List<String>> options = configuration.getIntegrationOptions();
        if (keys == null) {
            throw new IntegrationException("Canadapost missing configuration keys");
        }
        if (options == null) {
            throw new IntegrationException("Canadapost missing configuration options");
        }
        if (StringUtils.isBlank(keys.get("username"))) {
            throw new IntegrationException("Canadapost missing configuration key username");
        }
        if (StringUtils.isBlank(keys.get("password"))) {
            throw new IntegrationException("Canadapost missing configuration key password");
        }
        if (StringUtils.isBlank(keys.get("account"))) {
            throw new IntegrationException("Canadapost missing configuration key account");
        }
        String username = keys.get("username");
        String password = keys.get("password");
        String client = keys.get("account");
        List<String> domesticServices = options.get("services-domestic");
        List<String> intlServices = options.get("services-intl");
        List<String> usaServices = options.get("services-usa");
        String env = configuration.getEnvironment();
        String link = this.getConfig().getDevUrl();
        if (PROD_ENV.equals(env)) {
            link = this.getConfig().getProdUrl();
        }
        HashMap<String, ShippingOption> allOptions = new HashMap<String, ShippingOption>();
        MailingScenario mailingScenario = new MailingScenario();
        mailingScenario.setCustomerNumber(client);
        RestTemplate serviceClient = new RestTemplate();
        serviceClient.setErrorHandler(new ResponseErrorHandler() {
            public boolean hasError(ClientHttpResponse response) { return false; }
            public void handleError(java.net.URI url, org.springframework.http.HttpMethod method, ClientHttpResponse response) { }
        });
        mailingScenario.setOriginPostalCode(origin.getPostalCode().replaceAll("\\s+", "").toUpperCase());
        MailingScenario.Destination destination = new MailingScenario.Destination();
        if (delivery.getCountry().getIsoCode().equals(CANADA_CODE)) {
            if (domesticServices == null || domesticServices.size() == 0) {
                throw new IntegrationException("Canadapost selected domestic services are missing");
            }
            MailingScenario.Destination.Domestic domestic = new MailingScenario.Destination.Domestic();
            domestic.setPostalCode(delivery.getPostalCode().replaceAll("\\s+", "").toUpperCase());
            destination.setDomestic(domestic);
            service = new MailingScenario.Services();
            for (String domesticService : domesticServices) {
                service.getServiceCodes().add(domesticService);
            }
            mailingScenario.setServices(service);
        } else if (delivery.getCountry().getIsoCode().equals(US_CODE)) {
            if (usaServices == null || usaServices.size() == 0) {
                throw new IntegrationException("Canadapost selected USA services are missing");
            }
            MailingScenario.Destination.UnitedStates unitedStates = new MailingScenario.Destination.UnitedStates();
            unitedStates.setZipCode(delivery.getPostalCode().trim());
            destination.setUnitedStates(unitedStates);
            service = new MailingScenario.Services();
            for (String usaService : usaServices) {
                service.getServiceCodes().add(usaService);
            }
            mailingScenario.setServices(service);
        } else {
            if (intlServices == null || intlServices.size() == 0) {
                throw new IntegrationException("Canadapost selected International services are missing");
            }
            MailingScenario.Destination.International international = new MailingScenario.Destination.International();
            international.setCountryCode(delivery.getCountry().getIsoCode());
            destination.setInternational(international);
            service = new MailingScenario.Services();
            for (String intlService : intlServices) {
                service.getServiceCodes().add(intlService);
            }
            mailingScenario.setServices(service);
        }
        mailingScenario.setDestination(destination);
        for (PackageDetails packageDetail : packages) {
            MailingScenario.ParcelCharacteristics parcelCharacteristics = new MailingScenario.ParcelCharacteristics();
            parcelCharacteristics.setWeight(new BigDecimal(packageDetail.getShippingWeight()));
            MailingScenario.ParcelCharacteristics.Dimensions dimensions = new MailingScenario.ParcelCharacteristics.Dimensions();
            this.convertPackageSizeAndWeight(store, packageDetail);
            dimensions.setHeight(new BigDecimal(packageDetail.getShippingHeight()));
            dimensions.setWidth(new BigDecimal(packageDetail.getShippingWidth()));
            dimensions.setLength(new BigDecimal(packageDetail.getShippingLength()));
            parcelCharacteristics.setDimensions(dimensions);
            mailingScenario.setParcelCharacteristics(parcelCharacteristics);
            InputStream respIS;
            try {
                StringWriter xml = new StringWriter();
                JAXBContext.newInstance(MailingScenario.class).createMarshaller().marshal(mailingScenario, xml);
                HttpHeaders headers = new HttpHeaders();
                headers.setBasicAuth(username, password);
                headers.set("Accept", "application/vnd.cpc.ship.rate-v4+xml");
                headers.set("Content-Type", "application/vnd.cpc.ship.rate-v4+xml");
                headers.set("Accept-Language", "en-CA");
                ResponseEntity<byte[]> response = serviceClient.exchange(link, HttpMethod.POST,
                        new HttpEntity<>(xml.toString().getBytes(StandardCharsets.UTF_8), headers), byte[].class);
                byte[] body = response.getBody();
                respIS = new ByteArrayInputStream(body == null ? new byte[0] : body);
            } catch (jakarta.xml.bind.JAXBException e) {
                throw new IllegalStateException(e);
            }
            try {
                JAXBContext jc = JAXBContext.newInstance(PriceQuotes.class, Messages.class);
                Object entity = jc.createUnmarshaller().unmarshal(respIS);
                if (entity instanceof PriceQuotes) {
                    PriceQuotes priceQuotes = (PriceQuotes)entity;
                    for (PriceQuotes.PriceQuote aPriceQuote : priceQuotes.getPriceQuotes()) {
                        ShippingOption option = allOptions.get(aPriceQuote.getServiceCode());
                        if (option == null) {
                            BigDecimal cost = aPriceQuote.getPriceDetails().getBase();
                            if (shippingConfiguration.isTaxOnShipping()) {
                                cost = aPriceQuote.getPriceDetails().getDue();
                            }
                            option = new ShippingOption();
                            option.setOptionCode(aPriceQuote.getServiceCode());
                            option.setOptionPrice(cost);
                            option.setOptionName(aPriceQuote.getServiceName());
                            allOptions.put(aPriceQuote.getServiceCode(), option);
                        } else {
                            BigDecimal due = option.getOptionPrice().add(aPriceQuote.getPriceDetails().getDue());
                            option.setOptionPrice(due);
                        }
                        LOGGER.debug("Service Name: " + aPriceQuote.getServiceName());
                        LOGGER.debug("Price: $" + aPriceQuote.getPriceDetails().getBase());
                    }
                    continue;
                }
                Messages messageData = (Messages)entity;
                StringBuilder messageBuilder = new StringBuilder();
                for (Messages.Message aMessage : messageData.getMessage()) {
                    LOGGER.debug("Error Code: " + aMessage.getCode());
                    LOGGER.debug("Error Msg: " + aMessage.getDescription());
                    messageBuilder.append(aMessage.getCode()).append(" - ");
                    messageBuilder.append(aMessage.getDescription()).append(" ");
                }
                IntegrationException ex = new IntegrationException(messageBuilder.toString());
                ex.setErrorCode(99);
                ex.setMessageCode(messageBuilder.toString());
                throw ex;
            }
            catch (Exception e) {
                IntegrationException ex = new IntegrationException(e);
                ex.setErrorCode(99);
                throw ex;
            }
        }
        ArrayList<ShippingOption> shippingOptions = new ArrayList<ShippingOption>(allOptions.values());
        return shippingOptions;
    }

    private void convertPackageSizeAndWeight(MerchantStore store, PackageDetails pack) {
        BigDecimal multiplier;
        if (!CM.equals(store.getSeizeunitcode())) {
            multiplier = new BigDecimal(CM_MULTIPLIER);
            pack.setShippingHeight(new BigDecimal(pack.getShippingHeight()).multiply(multiplier).setScale(0, RoundingMode.CEILING).doubleValue());
            pack.setShippingWidth(new BigDecimal(pack.getShippingWidth()).multiply(multiplier).setScale(0, RoundingMode.CEILING).doubleValue());
            pack.setShippingLength(new BigDecimal(pack.getShippingLength()).multiply(multiplier).setScale(0, RoundingMode.CEILING).doubleValue());
        }
        if (!KG.equals(store.getWeightunitcode())) {
            multiplier = new BigDecimal(KG_MULTIPLIER);
            pack.setShippingWeight(new BigDecimal(pack.getShippingWeight()).multiply(multiplier).setScale(0, RoundingMode.CEILING).doubleValue());
        }
    }
}
