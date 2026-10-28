package com.salesmanager.shop.model.catalog.product;

import java.io.Serializable;

/**
 * Public view of the author of a product review. Only holds data that is
 * safe to show to anonymous visitors (first name and last initial).
 */
public class ReadableProductReviewer implements Serializable {

	private static final long serialVersionUID = 1L;
	private String firstName;
	private String displayName;

	public String getFirstName() {
		return firstName;
	}
	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}
	public String getDisplayName() {
		return displayName;
	}
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

}
