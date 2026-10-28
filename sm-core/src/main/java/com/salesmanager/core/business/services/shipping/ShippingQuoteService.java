package com.salesmanager.core.business.services.shipping;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.common.generic.SalesManagerEntityService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.shipping.Quote;
import com.salesmanager.core.model.shipping.ShippingSummary;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;

import java.util.List;

/**
 * Saves and retrieves various shipping quotes done by the system
 * @author c.samson
 *
 */
public interface ShippingQuoteService extends SalesManagerEntityService<Long, Quote> {
	
	/**
	 * Find shipping quotes by Order
	 * @param order
	 * @return
	 * @throws ServiceException
	 */
	List<Quote> findByOrder(Order order) throws ServiceException;
	
	
	/**
	 * Each quote asked for a given shopping cart creates individual Quote object
	 * in the table ShippingQuote. This method allows the creation of a ShippingSummary
	 * object to work with in the services and in the api, from a quote issued for the
	 * given shopping cart of the given store, using the stored quote values.
	 */
	ShippingSummary getShippingSummary(Long quoteId, ShoppingCart cart, MerchantStore store) throws ServiceException;

	/**
	 * Loads a quote and checks that it was issued for the given shopping cart
	 * of the given store. Throws a validation {@link ServiceException} otherwise.
	 */
	Quote getQuote(Long quoteId, ShoppingCart cart, MerchantStore store) throws ServiceException;

	/**
	 * Builds a ShippingSummary for checkout: the quote must belong to the cart and store,
	 * and the shipping price is re-quoted for the current cart content and the quote
	 * delivery address. A quote whose option or price no longer matches is rejected.
	 */
	ShippingSummary getCheckoutShippingSummary(Long quoteId, ShoppingCart cart, MerchantStore store, Language language) throws ServiceException;

}
