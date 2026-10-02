package com.salesmanager.core.business.modules.integration.shipping.impl;

/**
 * Custom shipping price based on the distance in kilometers. Rules are evaluated
 * in order and the last matching rule wins.
 */
public final class PriceByDistanceRules {

	private PriceByDistanceRules() {
	}

	public static void apply(ShippingInputParameters input, DecisionResponse decision) {
		if (input.getDistance() <= 530) {
			decision.setCustomPrice("75");
		}
		if (input.getDistance() <= 3550) {
			decision.setCustomPrice("140");
		}
	}

}
