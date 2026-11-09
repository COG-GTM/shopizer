package com.salesmanager.core.model.tax;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;


/**
 * Set of various tax configuration settings saved in MerchantConfiguration
 * @author carl samson
 *
 */
public class TaxConfiguration {
	
	private TaxBasisCalculation taxBasisCalculation = TaxBasisCalculation.SHIPPINGADDRESS;
	
	private boolean collectTaxIfDifferentProvinceOfStoreCountry = true;
	private boolean collectTaxIfDifferentCountryOfStoreCountry = false;

	@SuppressWarnings("unchecked")
	
	public String toJSONString() {
		ObjectNode data = JsonNodeFactory.instance.objectNode();
		data.put("taxBasisCalculation", this.getTaxBasisCalculation().name());
		
		return data.toString();
	}

	public void setTaxBasisCalculation(TaxBasisCalculation taxBasisCalculation) {
		this.taxBasisCalculation = taxBasisCalculation;
	}

	public TaxBasisCalculation getTaxBasisCalculation() {
		return taxBasisCalculation;
	}

	public void setCollectTaxIfDifferentProvinceOfStoreCountry(
			boolean collectTaxIfDifferentProvinceOfStoreCountry) {
		this.collectTaxIfDifferentProvinceOfStoreCountry = collectTaxIfDifferentProvinceOfStoreCountry;
	}

	public boolean isCollectTaxIfDifferentProvinceOfStoreCountry() {
		return collectTaxIfDifferentProvinceOfStoreCountry;
	}

	public void setCollectTaxIfDifferentCountryOfStoreCountry(
			boolean collectTaxIfDifferentCountryOfStoreCountry) {
		this.collectTaxIfDifferentCountryOfStoreCountry = collectTaxIfDifferentCountryOfStoreCountry;
	}

	public boolean isCollectTaxIfDifferentCountryOfStoreCountry() {
		return collectTaxIfDifferentCountryOfStoreCountry;
	}

}
