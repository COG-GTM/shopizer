package com.salesmanager.core.business.modules.order.total;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Promo code (coupon) configured for a single merchant store. Stored as a JSON
 * array in the store's MerchantConfiguration under
 * {@link PromoCodeCalculatorModule#PROMO_CODES_CONFIGURATION}.
 *
 * <pre>
 * [{"code":"SPRING10","discount":0.10,"startDate":"2026-03-01","endDate":"2026-03-31","active":true}]
 * </pre>
 *
 * discount is a fraction of the item price in (0, 1]; dates are UTC and endDate is inclusive.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PromoCode {

	private String code;
	private Double discount;
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "UTC")
	private Date startDate;
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "UTC")
	private Date endDate;
	private boolean active = true;

	public boolean matches(String promoCode) {
		return code != null && promoCode != null && code.trim().equalsIgnoreCase(promoCode.trim());
	}

	public boolean isValidOn(Date date) {
		if (!active || discount == null || discount.isNaN() || discount <= 0 || discount > 1) {
			return false;
		}
		if (startDate == null || endDate == null) {
			return false;
		}
		Date endExclusive = new Date(endDate.getTime() + 24L * 60 * 60 * 1000);
		return !date.before(startDate) && date.before(endExclusive);
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public Double getDiscount() {
		return discount;
	}

	public void setDiscount(Double discount) {
		this.discount = discount;
	}

	public Date getStartDate() {
		return startDate;
	}

	public void setStartDate(Date startDate) {
		this.startDate = startDate;
	}

	public Date getEndDate() {
		return endDate;
	}

	public void setEndDate(Date endDate) {
		this.endDate = endDate;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

}
