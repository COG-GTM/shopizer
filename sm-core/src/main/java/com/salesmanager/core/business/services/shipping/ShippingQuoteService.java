package com.salesmanager.core.business.services.shipping;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.common.generic.SalesManagerEntityService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
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
	 * object to work with in the services and in the api.
	 * The quote must have been created for the given shopping cart (which must belong
	 * to the given store) and, when a customer with an address is supplied, for that
	 * customer's delivery address; otherwise a validation ServiceException is thrown.
	 * @param quoteId
	 * @param cart shopping cart the quote must belong to
	 * @param customer customer placing the order, may be null
	 * @param store
	 * @return
	 * @throws ServiceException
	 */
	ShippingSummary getShippingSummary(Long quoteId, ShoppingCart cart, Customer customer, MerchantStore store) throws ServiceException;

}
