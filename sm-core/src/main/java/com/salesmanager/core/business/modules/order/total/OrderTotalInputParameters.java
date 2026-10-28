package com.salesmanager.core.business.modules.order.total;

import java.util.Date;

/**
 * Input itm to be added to drules engine
 * @author carlsamson
 *
 */
public class OrderTotalInputParameters {
	
	private Double discount;//output value set by engine
	private String totalCode;//output value set by engine
	
	//input parameters
	private long productId;
	private String itemManufacturerCode;
	private String itemCategoryCode;
	private String shippingMethod;
	private String promoCode;
	private Double promoCodeDiscount;//discount configured by the store for promoCode
	private String merchantStoreCode;
	private Date date = new Date();
	
	//might add variation based on other objects such as Customer
	
	public Double getDiscount() {
		return discount;
	}
	public void setDiscount(Double discount) {
		this.discount = discount;
	}
	public String getTotalCode() {
		return totalCode;
	}
	public void setTotalCode(String totalCode) {
		this.totalCode = totalCode;
	}
	public String getItemManufacturerCode() {
		return itemManufacturerCode;
	}
	public void setItemManufacturerCode(String itemManufacturerCode) {
		this.itemManufacturerCode = itemManufacturerCode;
	}
	public String getItemCategoryCode() {
		return itemCategoryCode;
	}
	public void setItemCategoryCode(String itemCategoryCode) {
		this.itemCategoryCode = itemCategoryCode;
	}
	public long getProductId() {
		return productId;
	}
	public void setProductId(long productId) {
		this.productId = productId;
	}
	public String getShippingMethod() {
		return shippingMethod;
	}
	public void setShippingMethod(String shippingMethod) {
		this.shippingMethod = shippingMethod;
	}
	public String getPromoCode() {
		return promoCode;
	}
	public void setPromoCode(String promoCode) {
		this.promoCode = promoCode;
	}
	public Double getPromoCodeDiscount() {
		return promoCodeDiscount;
	}
	public void setPromoCodeDiscount(Double promoCodeDiscount) {
		this.promoCodeDiscount = promoCodeDiscount;
	}
	public String getMerchantStoreCode() {
		return merchantStoreCode;
	}
	public void setMerchantStoreCode(String merchantStoreCode) {
		this.merchantStoreCode = merchantStoreCode;
	}
	public Date getDate() {
		return date;
	}
	public void setDate(Date date) {
		this.date = date;
	}

	


}
