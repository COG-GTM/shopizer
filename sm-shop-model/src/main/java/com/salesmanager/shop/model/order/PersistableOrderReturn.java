package com.salesmanager.shop.model.order;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Return request for specific order products and quantities.
 */
public class PersistableOrderReturn implements Serializable {

	private static final long serialVersionUID = 1L;

	private List<PersistableOrderReturnItem> items = new ArrayList<PersistableOrderReturnItem>();
	private String reason;

	public List<PersistableOrderReturnItem> getItems() {
		return items;
	}

	public void setItems(List<PersistableOrderReturnItem> items) {
		this.items = items;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

}
