package com.salesmanager.shop.model.catalog.product;

import java.io.Serializable;


public class ReadableProductReview extends ProductReviewEntity implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private ReadableProductReviewer customer;
	public ReadableProductReviewer getCustomer() {
		return customer;
	}
	public void setCustomer(ReadableProductReviewer customer) {
		this.customer = customer;
	}

}
