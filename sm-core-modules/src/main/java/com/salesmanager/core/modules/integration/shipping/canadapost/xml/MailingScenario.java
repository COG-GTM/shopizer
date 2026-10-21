package com.salesmanager.core.modules.integration.shipping.canadapost.xml;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class MailingScenario {

	private String customerNumber;
	private ParcelCharacteristics parcelCharacteristics;
	private List<String> serviceCodes = new ArrayList<String>();
	private String originPostalCode;
	private Destination destination;

	public String getCustomerNumber() {
		return customerNumber;
	}

	public void setCustomerNumber(String customerNumber) {
		this.customerNumber = customerNumber;
	}

	public ParcelCharacteristics getParcelCharacteristics() {
		return parcelCharacteristics;
	}

	public void setParcelCharacteristics(ParcelCharacteristics parcelCharacteristics) {
		this.parcelCharacteristics = parcelCharacteristics;
	}

	public List<String> getServiceCodes() {
		return serviceCodes;
	}

	public void setServiceCodes(List<String> serviceCodes) {
		this.serviceCodes = serviceCodes;
	}

	public String getOriginPostalCode() {
		return originPostalCode;
	}

	public void setOriginPostalCode(String originPostalCode) {
		this.originPostalCode = originPostalCode;
	}

	public Destination getDestination() {
		return destination;
	}

	public void setDestination(Destination destination) {
		this.destination = destination;
	}

	public static class ParcelCharacteristics {
		private BigDecimal weight;
		private Dimensions dimensions;

		public BigDecimal getWeight() {
			return weight;
		}

		public void setWeight(BigDecimal weight) {
			this.weight = weight;
		}

		public Dimensions getDimensions() {
			return dimensions;
		}

		public void setDimensions(Dimensions dimensions) {
			this.dimensions = dimensions;
		}
	}

	public static class Dimensions {
		private BigDecimal length;
		private BigDecimal width;
		private BigDecimal height;

		public BigDecimal getLength() {
			return length;
		}

		public void setLength(BigDecimal length) {
			this.length = length;
		}

		public BigDecimal getWidth() {
			return width;
		}

		public void setWidth(BigDecimal width) {
			this.width = width;
		}

		public BigDecimal getHeight() {
			return height;
		}

		public void setHeight(BigDecimal height) {
			this.height = height;
		}
	}

	public static class Destination {
		private String domesticPostalCode;
		private String unitedStatesZipCode;
		private String internationalCountryCode;

		public String getDomesticPostalCode() {
			return domesticPostalCode;
		}

		public void setDomesticPostalCode(String domesticPostalCode) {
			this.domesticPostalCode = domesticPostalCode;
		}

		public String getUnitedStatesZipCode() {
			return unitedStatesZipCode;
		}

		public void setUnitedStatesZipCode(String unitedStatesZipCode) {
			this.unitedStatesZipCode = unitedStatesZipCode;
		}

		public String getInternationalCountryCode() {
			return internationalCountryCode;
		}

		public void setInternationalCountryCode(String internationalCountryCode) {
			this.internationalCountryCode = internationalCountryCode;
		}
	}
}
