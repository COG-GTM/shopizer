package com.salesmanager.core.modules.integration.shipping.canadapost;

import java.io.ByteArrayInputStream;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

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
import com.salesmanager.core.modules.integration.shipping.canadapost.xml.CanadaPostMessage;
import com.salesmanager.core.modules.integration.shipping.canadapost.xml.CanadaPostRatingXml;
import com.salesmanager.core.modules.integration.shipping.canadapost.xml.MailingScenario;
import com.salesmanager.core.modules.integration.shipping.canadapost.xml.PriceQuote;
import com.salesmanager.core.modules.integration.shipping.model.ShippingQuoteModule;

public class CanadaPostQuoteModule implements ShippingQuoteModule {

	private static final String CANADA_CODE = "CA";
	private static final String US_CODE = "US";
	private static final Double CM_MULTIPLIER = 2.54D;
	private static final Double KG_MULTIPLIER = 0.453592D;
	private static final String KG = "KG";
	private static final String CM = "CM";
	private static final String PROD_ENV = "PROD";
	private static final String RATE_MEDIA_TYPE = "application/vnd.cpc.ship.rate-v4+xml";

	private final CanadaPostRatingXml ratingXml = new CanadaPostRatingXml();
	private CanadaPostQuoteModuleConfig config;
	private RestTemplate restTemplate;

	public CanadaPostQuoteModule() {
		setRestTemplate(new RestTemplate());
	}

	public CanadaPostQuoteModuleConfig getConfig() {
		return config;
	}

	public void setConfig(CanadaPostQuoteModuleConfig config) {
		this.config = config;
	}

	public RestTemplate getRestTemplate() {
		return restTemplate;
	}

	public void setRestTemplate(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
		this.restTemplate.setErrorHandler(new ResponseErrorHandler() {
			@Override
			public boolean hasError(ClientHttpResponse response) {
				return false;
			}

			@Override
			public void handleError(ClientHttpResponse response) {
			}
		});
	}

	@Override
	public void validateModuleConfiguration(IntegrationConfiguration integrationConfiguration,
			MerchantStore store) throws IntegrationException {
		List<String> errorFields = null;
		Map<String, String> keys = integrationConfiguration.getIntegrationKeys();
		if (keys == null) {
			IntegrationException ex = new IntegrationException(IntegrationException.ERROR_VALIDATION_SAVE);
			ex.setErrorFields(errorFields);
			throw ex;
		}
		if (StringUtils.isBlank(keys.get("username"))) {
			errorFields = new ArrayList<String>();
			errorFields.add("username");
		}
		if (StringUtils.isBlank(keys.get("password"))) {
			errorFields = new ArrayList<String>();
			errorFields.add("password");
		}
		if (StringUtils.isBlank(keys.get("account"))) {
			errorFields = new ArrayList<String>();
			errorFields.add("account");
		}
		if (StringUtils.isBlank(keys.get("apikey"))) {
			errorFields = new ArrayList<String>();
			errorFields.add("apikey");
		}
		if (errorFields != null) {
			IntegrationException ex = new IntegrationException(IntegrationException.ERROR_VALIDATION_SAVE);
			ex.setErrorFields(errorFields);
			throw ex;
		}
	}

	@Override
	public CustomIntegrationConfiguration getCustomModuleConfiguration(MerchantStore store)
			throws IntegrationException {
		return null;
	}

	@Override
	public List<ShippingOption> getShippingQuotes(ShippingQuote quote, List<PackageDetails> packages,
			BigDecimal orderTotal, Delivery delivery, ShippingOrigin origin, MerchantStore store,
			IntegrationConfiguration configuration, IntegrationModule module,
			ShippingConfiguration shippingConfiguration, Locale locale) throws IntegrationException {
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
		if (!(country.getIsoCode().equals("US") || country.getIsoCode().equals("CA"))) {
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

		String link = config.getDevUrl();
		if (PROD_ENV.equals(configuration.getEnvironment())) {
			link = config.getProdUrl();
		}

		Map<String, ShippingOption> allOptions = new HashMap<String, ShippingOption>();
		MailingScenario mailingScenario = new MailingScenario();
		mailingScenario.setCustomerNumber(client);
		mailingScenario.setOriginPostalCode(origin.getPostalCode().replaceAll("\\s+", "").toUpperCase());
		MailingScenario.Destination destination = new MailingScenario.Destination();
		if (delivery.getCountry().getIsoCode().equals(CANADA_CODE)) {
			if (domesticServices == null || domesticServices.size() == 0) {
				throw new IntegrationException("Canadapost selected domestic services are missing");
			}
			destination.setDomesticPostalCode(delivery.getPostalCode().replaceAll("\\s+", "").toUpperCase());
			mailingScenario.setServiceCodes(domesticServices);
		} else if (delivery.getCountry().getIsoCode().equals(US_CODE)) {
			if (usaServices == null || usaServices.size() == 0) {
				throw new IntegrationException("Canadapost selected USA services are missing");
			}
			destination.setUnitedStatesZipCode(delivery.getPostalCode().trim());
			mailingScenario.setServiceCodes(usaServices);
		} else {
			if (intlServices == null || intlServices.size() == 0) {
				throw new IntegrationException("Canadapost selected International services are missing");
			}
			destination.setInternationalCountryCode(delivery.getCountry().getIsoCode());
			mailingScenario.setServiceCodes(intlServices);
		}
		mailingScenario.setDestination(destination);

		for (PackageDetails packageDetail : packages) {
			MailingScenario.ParcelCharacteristics parcelCharacteristics =
					new MailingScenario.ParcelCharacteristics();
			parcelCharacteristics.setWeight(new BigDecimal(packageDetail.getShippingWeight()));
			MailingScenario.Dimensions dimensions = new MailingScenario.Dimensions();
			convertPackageSizeAndWeight(store, packageDetail);
			dimensions.setHeight(new BigDecimal(packageDetail.getShippingHeight()));
			dimensions.setWidth(new BigDecimal(packageDetail.getShippingWidth()));
			dimensions.setLength(new BigDecimal(packageDetail.getShippingLength()));
			parcelCharacteristics.setDimensions(dimensions);
			mailingScenario.setParcelCharacteristics(parcelCharacteristics);

			try {
				HttpHeaders headers = new HttpHeaders();
				headers.setAccept(java.util.Collections.singletonList(MediaType.parseMediaType(RATE_MEDIA_TYPE)));
				headers.setContentType(MediaType.parseMediaType(RATE_MEDIA_TYPE));
				headers.set("Accept-Language", "en-CA");
				headers.setBasicAuth(username, password);
				HttpEntity<String> request = new HttpEntity<String>(ratingXml.write(mailingScenario), headers);
				byte[] responseBody = restTemplate.exchange(link, HttpMethod.POST, request, byte[].class).getBody();
				CanadaPostRatingXml.Result result = ratingXml.parse(new ByteArrayInputStream(
						responseBody == null ? new byte[0] : responseBody));
				if (result.isMessages()) {
					StringBuilder messageBuilder = new StringBuilder();
					for (CanadaPostMessage message : result.getMessages()) {
						messageBuilder.append(message.getCode()).append(" - ");
						messageBuilder.append(message.getDescription()).append(" ");
					}
					IntegrationException ex = new IntegrationException(messageBuilder.toString());
					ex.setErrorCode(IntegrationException.TRANSACTION_EXCEPTION);
					ex.setMessageCode(messageBuilder.toString());
					throw ex;
				}
				for (PriceQuote priceQuote : result.getPriceQuotes()) {
					ShippingOption option = allOptions.get(priceQuote.getServiceCode());
					if (option == null) {
						BigDecimal cost = priceQuote.getBase();
						if (shippingConfiguration.isTaxOnShipping()) {
							cost = priceQuote.getDue();
						}
						option = new ShippingOption();
						option.setOptionCode(priceQuote.getServiceCode());
						option.setOptionPrice(cost);
						option.setOptionName(priceQuote.getServiceName());
						allOptions.put(priceQuote.getServiceCode(), option);
					} else {
						BigDecimal due = option.getOptionPrice().add(priceQuote.getDue());
						option.setOptionPrice(due);
					}
				}
			} catch (IntegrationException e) {
				throw e;
			} catch (Exception e) {
				IntegrationException ex = new IntegrationException(e);
				ex.setErrorCode(IntegrationException.TRANSACTION_EXCEPTION);
				throw ex;
			}
		}

		return new ArrayList<ShippingOption>(allOptions.values());
	}

	private void convertPackageSizeAndWeight(MerchantStore store, PackageDetails pack) {
		if (!CM.equals(store.getSeizeunitcode())) {
			BigDecimal multiplier = new BigDecimal(CM_MULTIPLIER.doubleValue());
			pack.setShippingHeight(new BigDecimal(pack.getShippingHeight()).multiply(multiplier)
					.setScale(0, RoundingMode.CEILING).doubleValue());
			pack.setShippingWidth(new BigDecimal(pack.getShippingWidth()).multiply(multiplier)
					.setScale(0, RoundingMode.CEILING).doubleValue());
			pack.setShippingLength(new BigDecimal(pack.getShippingLength()).multiply(multiplier)
					.setScale(0, RoundingMode.CEILING).doubleValue());
		}
		if (!KG.equals(store.getWeightunitcode())) {
			BigDecimal multiplier = new BigDecimal(KG_MULTIPLIER.doubleValue());
			pack.setShippingWeight(new BigDecimal(pack.getShippingWeight()).multiply(multiplier)
					.setScale(0, RoundingMode.CEILING).doubleValue());
		}
	}
}
