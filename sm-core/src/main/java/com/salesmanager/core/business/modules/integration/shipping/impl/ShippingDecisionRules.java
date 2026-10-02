package com.salesmanager.core.business.modules.integration.shipping.impl;

/**
 * Selects the shipping module to use for a quote.
 * <ul>
 * <li>Canada, weight &lt; 62 and size &lt; 66: {@code canadapost}</li>
 * <li>Quebec, weight &gt; 62 or size &gt; 66: {@code priceByDistance}</li>
 * </ul>
 */
public final class ShippingDecisionRules {

	private ShippingDecisionRules() {
	}

	public static void apply(ShippingInputParameters input, DecisionResponse decision) {
		boolean canada = "CA".equals(input.getCountry());
		if (canada && input.getWeight() < 62 && input.getSize() < 66) {
			decision.setModuleName("canadapost");
		}
		if (canada && "QC".equals(input.getProvince()) && (input.getWeight() > 62 || input.getSize() > 66)) {
			decision.setModuleName("priceByDistance");
		}
	}

}
