package com.salesmanager.core.business.modules.order.total;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.kie.api.runtime.KieSession;
import org.kie.internal.io.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmanager.core.business.configuration.DroolsBeanFactory;
import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.catalog.pricing.PricingService;
import com.salesmanager.core.business.services.system.MerchantConfigurationService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.price.FinalPrice;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.OrderSummary;
import com.salesmanager.core.model.order.OrderTotal;
import com.salesmanager.core.model.order.OrderTotalType;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;
import com.salesmanager.core.model.system.MerchantConfiguration;
import com.salesmanager.core.modules.order.total.OrderTotalPostProcessorModule;

@Component
public class PromoCodeCalculatorModule implements OrderTotalPostProcessorModule {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(PromoCodeCalculatorModule.class);
	private static final ObjectMapper MAPPER = new ObjectMapper();
	
	/** MerchantConfiguration key holding the store's promo codes as a JSON array of {@link PromoCode}. */
	public static final String PROMO_CODES_CONFIGURATION = "PROMO_CODES";
	
	private static final String PROMO_RULES = "com/salesmanager/drools/rules/PromoCoupon.drl";
	
	@Autowired
	private DroolsBeanFactory droolsBeanFactory;
	
	@Autowired
	private PricingService pricingService;
	
	@Autowired
	private MerchantConfigurationService merchantConfigurationService;

	private String name;
	private String code;

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return name;
	}

	@Override
	public void setName(String name) {
		this.name = name;

	}

	@Override
	public String getCode() {
		// TODO Auto-generated method stub
		return code;
	}

	@Override
	public void setCode(String code) {
		this.code = code;
	}

	@Override
	public OrderTotal caculateProductPiceVariation(OrderSummary summary, ShoppingCartItem shoppingCartItem,
			Product product, Customer customer, MerchantStore store) throws Exception {
		
		Validate.notNull(summary, "OrderTotalSummary must not be null");
		Validate.notNull(store, "MerchantStore must not be null");
		
		if(StringUtils.isBlank(summary.getPromoCode())) {
			return null;
		}
		
		Date now = new Date();
		PromoCode promoCode = findValidPromoCode(summary.getPromoCode(), store, now);
		if(promoCode == null) {
			return null;
		}
		
		OrderTotalResponse resp = new OrderTotalResponse();
		
		OrderTotalInputParameters inputParameters = new OrderTotalInputParameters();
		inputParameters.setPromoCode(promoCode.getCode());
		inputParameters.setPromoCodeDiscount(promoCode.getDiscount());
		inputParameters.setMerchantStoreCode(store.getCode());
		inputParameters.setDate(now);
		
		KieSession kieSession=droolsBeanFactory.getKieSession(ResourceFactory.newClassPathResource(PROMO_RULES));
		try {
			kieSession.insert(inputParameters);
			kieSession.setGlobal("total",resp);
			kieSession.fireAllRules();
		} finally {
			kieSession.dispose();
		}

		Double discount = resp.getDiscount();
		if(discount == null || discount.isNaN() || discount <= 0 || discount > 1) {
			return null;
		}

		OrderTotal orderTotal = new OrderTotal();
		orderTotal.setOrderTotalCode(Constants.OT_DISCOUNT_TITLE);
		orderTotal.setOrderTotalType(OrderTotalType.SUBTOTAL);
		orderTotal.setTitle(Constants.OT_SUBTOTAL_MODULE_CODE);
		orderTotal.setText(summary.getPromoCode());
		
		//calculate discount that will be added as a negative value
		FinalPrice productPrice = pricingService.calculateProductPrice(product);
		
		BigDecimal reduction = productPrice.getFinalPrice().multiply(BigDecimal.valueOf(discount));
		reduction = reduction.multiply(new BigDecimal(shoppingCartItem.getQuantity()));
		
		orderTotal.setValue(reduction);//discount value
		
		return orderTotal;
	}

	/**
	 * Returns the promo code configured for this store that matches the requested
	 * code and is valid on the given date, or null.
	 */
	PromoCode findValidPromoCode(String requestedCode, MerchantStore store, Date date) throws ServiceException {
		MerchantConfiguration configuration = merchantConfigurationService.getMerchantConfiguration(PROMO_CODES_CONFIGURATION, store);
		if(configuration == null || StringUtils.isBlank(configuration.getValue())) {
			return null;
		}
		
		List<PromoCode> promoCodes;
		try {
			promoCodes = MAPPER.readValue(configuration.getValue(), new TypeReference<List<PromoCode>>() {});
		} catch(Exception e) {
			LOGGER.error("Cannot parse " + PROMO_CODES_CONFIGURATION + " configuration for store " + store.getCode(), e);
			return null;
		}
		if(promoCodes == null) {
			return null;
		}
		
		for(PromoCode promoCode : promoCodes) {
			if(promoCode != null && promoCode.matches(requestedCode) && promoCode.isValidOn(date)) {
				return promoCode;
			}
		}
		return null;
	}

}
