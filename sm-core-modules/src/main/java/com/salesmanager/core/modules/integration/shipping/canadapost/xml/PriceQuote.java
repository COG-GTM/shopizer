package com.salesmanager.core.modules.integration.shipping.canadapost.xml;

import java.math.BigDecimal;

public class PriceQuote {

	private String serviceCode;
	private String serviceName;
	private BigDecimal base;
	private BigDecimal due;

	public String getServiceCode() {
		return serviceCode;
	}

	public void setServiceCode(String serviceCode) {
		this.serviceCode = serviceCode;
	}

	public String getServiceName() {
		return serviceName;
	}

	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}

	public BigDecimal getBase() {
		return base;
	}

	public void setBase(BigDecimal base) {
		this.base = base;
	}

	public BigDecimal getDue() {
		return due;
	}

	public void setDue(BigDecimal due) {
		this.due = due;
	}
}
