package com.salesmanager.shop.model.catalog.product;

import java.io.Serializable;

public class PersistableProductReview extends ProductReviewEntity implements
		Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	// required on the private (admin) route; set from the principal on the customer route
	private Long customerId;
	public Long getCustomerId() {
		return customerId;
	}
	public void setCustomerId(Long customerId) {
		this.customerId = customerId;
	}
	


}
