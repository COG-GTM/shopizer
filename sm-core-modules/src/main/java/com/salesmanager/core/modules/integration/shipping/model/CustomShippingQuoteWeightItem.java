package com.salesmanager.core.modules.integration.shipping.model;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;


public class CustomShippingQuoteWeightItem extends CustomShippingQuoteItem {
	
	private int maximumWeight;
	
	private String priceText;

	public String getPriceText() {
		return priceText;
	}

	public void setPriceText(String priceText) {
		this.priceText = priceText;
	}

	public void setMaximumWeight(int maximumWeight) {
		this.maximumWeight = maximumWeight;
	}

	public int getMaximumWeight() {
		return maximumWeight;
	}

	@SuppressWarnings("unchecked")
	public String toJSONString() {
		ObjectNode data = JsonNodeFactory.instance.objectNode();
		data.put("price", super.getPrice());
		data.put("maximumWeight", this.getMaximumWeight());
		
		return data.toString();
	}



}
