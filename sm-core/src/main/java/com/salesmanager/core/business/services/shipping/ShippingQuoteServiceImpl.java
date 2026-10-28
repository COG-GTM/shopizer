package com.salesmanager.core.business.services.shipping;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.repositories.shipping.ShippingQuoteRepository;
import com.salesmanager.core.business.services.common.generic.SalesManagerEntityServiceImpl;
import com.salesmanager.core.business.services.shoppingcart.ShoppingCartService;
import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.shipping.Quote;
import com.salesmanager.core.model.shipping.ShippingOption;
import com.salesmanager.core.model.shipping.ShippingProduct;
import com.salesmanager.core.model.shipping.ShippingQuote;
import com.salesmanager.core.model.shipping.ShippingSummary;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;

@Service("shippingQuoteService")
public class ShippingQuoteServiceImpl extends SalesManagerEntityServiceImpl<Long, Quote> implements ShippingQuoteService {

	
	private static final Logger LOGGER = LoggerFactory.getLogger(ShippingQuoteServiceImpl.class);
	
	private ShippingQuoteRepository shippingQuoteRepository;
	
	@Inject
	private ShippingService shippingService;

	@Inject
	private ShoppingCartService shoppingCartService;
	
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
	public Quote getQuote(Long quoteId, ShoppingCart cart, MerchantStore store) throws ServiceException {

		Validate.notNull(quoteId, "quoteId must not be null");
		Validate.notNull(cart, "cart must not be null");
		Validate.notNull(store, "store must not be null");

		Quote q = shippingQuoteRepository.findById(quoteId).orElse(null);

		boolean sameCart = q != null && cart.getId() != null && Objects.equals(q.getCartId(), cart.getId());
		boolean sameStore = cart.getMerchantStore() != null && store.getId() != null
				&& Objects.equals(cart.getMerchantStore().getId(), store.getId());
		boolean sameCustomer = q == null || q.getCustomerId() == null
				|| Objects.equals(q.getCustomerId(), cart.getCustomerId());

		if (!sameCart || !sameStore || !sameCustomer) {
			LOGGER.warn("Rejected shipping quote [" + quoteId + "] for cart [" + cart.getId() + "] of store ["
					+ store.getId() + "]");
			throw new ServiceException(ServiceException.EXCEPTION_VALIDATION,
					"Shipping quote [" + quoteId + "] is not valid for this shopping cart", "shipping.quote.invalid");
		}

		return q;
	}

	@Override
	public ShippingSummary getShippingSummary(Long quoteId, ShoppingCart cart, MerchantStore store)
			throws ServiceException {
		return toShippingSummary(getQuote(quoteId, cart, store), store);
	}

	@Override
	public ShippingSummary getCheckoutShippingSummary(Long quoteId, ShoppingCart cart, MerchantStore store,
			Language language) throws ServiceException {

		Quote q = getQuote(quoteId, cart, store);

		List<ShippingProduct> products = shoppingCartService.createShippingProduct(cart);
		if (CollectionUtils.isEmpty(products)) {
			return null;
		}

		ShippingQuote current = shippingService.calculateShippingQuote(cart.getId(), store, copyOf(q.getDelivery()),
				products, language);

		ShippingSummary summary = toShippingSummary(q, store);

		if (current != null && current.isFreeShipping()) {
			summary.setFreeShipping(true);
			summary.setShipping(BigDecimal.ZERO);
			summary.setHandling(null);
			return summary;
		}

		ShippingOption option = null;
		if (current != null && current.getShippingOptions() != null) {
			option = current.getShippingOptions().stream()
					.filter(o -> Objects.equals(o.getShippingModuleCode(), q.getModule())
							&& Objects.equals(o.getOptionCode(), q.getOptionCode()))
					.findFirst().orElse(null);
		}

		if (option == null || !sameAmount(option.getOptionPrice(), q.getPrice())
				|| !sameAmount(current.getHandlingFees(), q.getHandling())) {
			throw new ServiceException(ServiceException.EXCEPTION_VALIDATION, "Shipping quote [" + quoteId
					+ "] is out of date for this shopping cart, request a new shipping quote", "shipping.quote.expired");
		}

		return summary;
	}

	private ShippingSummary toShippingSummary(Quote q, MerchantStore store) throws ServiceException {
		ShippingSummary summary = new ShippingSummary();
		summary.setDeliveryAddress(q.getDelivery());
		summary.setShipping(q.getPrice());
		summary.setShippingModule(q.getModule());
		summary.setShippingOption(q.getOptionName());
		summary.setShippingOptionCode(q.getOptionCode());
		summary.setHandling(q.getHandling());
		summary.setFreeShipping(q.isFreeShipping());
		if (shippingService.hasTaxOnShipping(store)) {
			summary.setTaxOnShipping(true);
		}
		return summary;
	}

	private static boolean sameAmount(BigDecimal a, BigDecimal b) {
		return (a == null ? BigDecimal.ZERO : a).compareTo(b == null ? BigDecimal.ZERO : b) == 0;
	}

	private static Delivery copyOf(Delivery source) {
		Delivery delivery = new Delivery();
		if (source == null) {
			return delivery;
		}
		delivery.setFirstName(source.getFirstName());
		delivery.setLastName(source.getLastName());
		delivery.setCompany(source.getCompany());
		delivery.setAddress(source.getAddress());
		delivery.setCity(source.getCity());
		delivery.setPostalCode(source.getPostalCode());
		delivery.setState(source.getState());
		delivery.setTelephone(source.getTelephone());
		delivery.setCountry(source.getCountry());
		delivery.setZone(source.getZone());
		return delivery;
	}

}
