package com.salesmanager.core.business.services.shipping;

import java.util.List;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.repositories.shipping.ShippingQuoteRepository;
import com.salesmanager.core.business.services.common.generic.SalesManagerEntityServiceImpl;
import com.salesmanager.core.model.common.Billing;
import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.shipping.Quote;
import com.salesmanager.core.model.shipping.ShippingSummary;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;

@Service("shippingQuoteService")
public class ShippingQuoteServiceImpl extends SalesManagerEntityServiceImpl<Long, Quote> implements ShippingQuoteService {

	
	private static final Logger LOGGER = LoggerFactory.getLogger(ShippingQuoteServiceImpl.class);
	
	private ShippingQuoteRepository shippingQuoteRepository;
	
	@Inject
	private ShippingService shippingService;
	
	@Inject
	public ShippingQuoteServiceImpl(ShippingQuoteRepository repository) {
		super(repository);
		this.shippingQuoteRepository = repository;
		// TODO Auto-generated constructor stub
	}

	@Override
	public List<Quote> findByOrder(Order order) throws ServiceException {
		Validate.notNull(order,"Order cannot be null");
		return this.shippingQuoteRepository.findByOrder(order.getId());
	}

	@Override
	public ShippingSummary getShippingSummary(Long quoteId, ShoppingCart cart, Customer customer, MerchantStore store) throws ServiceException {
		
		Validate.notNull(quoteId,"quoteId must not be null");
		Validate.notNull(cart,"ShoppingCart must not be null");
		Validate.notNull(store,"MerchantStore must not be null");
		
		Quote q = shippingQuoteRepository.findById(quoteId).orElse(null);
		
		if(q == null) {
			throw invalidQuote(quoteId, "does not exist");
		}
		
		if(cart.getMerchantStore() == null || cart.getMerchantStore().getId() == null
				|| !cart.getMerchantStore().getId().equals(store.getId())) {
			throw invalidQuote(quoteId, "cart does not belong to store " + store.getCode());
		}
		
		if(q.getCartId() == null || !q.getCartId().equals(cart.getId())) {
			throw invalidQuote(quoteId, "does not belong to cart " + cart.getId());
		}
		
		if(q.getOrderId() != null) {
			throw invalidQuote(quoteId, "has already been used");
		}
		
		if(customer != null) {
			if(q.getCustomerId() != null && customer.getId() != null && !q.getCustomerId().equals(customer.getId())) {
				throw invalidQuote(quoteId, "does not belong to customer");
			}
			
			Delivery customerAddress = shippingAddress(customer);
			if(customerAddress != null && !sameAddress(q.getDelivery(), customerAddress)) {
				throw invalidQuote(quoteId, "does not match the delivery address");
			}
		}
		
		ShippingSummary quote = new ShippingSummary();
		quote.setDeliveryAddress(q.getDelivery());
		quote.setShipping(q.getPrice());
		quote.setShippingModule(q.getModule());
		quote.setShippingOption(q.getOptionName());
		quote.setShippingOptionCode(q.getOptionCode());
		quote.setHandling(q.getHandling());
		quote.setFreeShipping(q.isFreeShipping());
		
		if(shippingService.hasTaxOnShipping(store)) {
			quote.setTaxOnShipping(true);
		}
		
		return quote;
		
	}
	
	private ServiceException invalidQuote(Long quoteId, String reason) {
		LOGGER.warn("Rejected shipping quote [" + quoteId + "]: " + reason);
		return new ServiceException(ServiceException.EXCEPTION_VALIDATION, "Invalid shipping quote [" + quoteId + "] " + reason);
	}
	
	/**
	 * Address used to compute shipping quotes for a customer: delivery address,
	 * or billing address when no delivery postal code is set
	 */
	private Delivery shippingAddress(Customer customer) {
		Delivery delivery = customer.getDelivery();
		if(delivery != null && !StringUtils.isBlank(delivery.getPostalCode())) {
			return delivery;
		}
		Billing billing = customer.getBilling();
		if(billing != null && (!StringUtils.isBlank(billing.getPostalCode()) || billing.getCountry() != null)) {
			Delivery address = new Delivery();
			address.setPostalCode(billing.getPostalCode());
			address.setCountry(billing.getCountry());
			return address;
		}
		return delivery;
	}
	
	private boolean sameAddress(Delivery quoted, Delivery address) {
		if(quoted == null) {
			return false;
		}
		String quotedCountry = quoted.getCountry() != null ? quoted.getCountry().getIsoCode() : null;
		String country = address.getCountry() != null ? address.getCountry().getIsoCode() : null;
		return StringUtils.equalsIgnoreCase(quotedCountry, country)
				&& normalizePostalCode(quoted.getPostalCode()).equals(normalizePostalCode(address.getPostalCode()));
	}
	
	private String normalizePostalCode(String postalCode) {
		return StringUtils.deleteWhitespace(StringUtils.defaultString(postalCode)).toUpperCase();
	}


}
