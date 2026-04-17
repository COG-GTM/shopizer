package com.salesmanager.core.model.order.orderaccount;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.TableGenerator;

import com.salesmanager.core.constants.SchemaConstant;
import com.salesmanager.core.model.order.orderproduct.OrderProduct;

@Entity
@Table (name="ORDER_ACCOUNT_PRODUCT" )
public class OrderAccountProduct implements Serializable {
	private static final long serialVersionUID = -7437197293537758668L;

	@Id
	@Column (name="ORDER_ACCOUNT_PRODUCT_ID")
	@TableGenerator(name = "TABLE_GEN", table = "SM_SEQUENCER", pkColumnName = "SEQ_NAME", valueColumnName = "SEQ_COUNT",
		pkColumnValue = "ORDERACCOUNTPRODUCT_SEQ_NEXT_VAL", allocationSize = 1)
	@GeneratedValue(strategy = GenerationType.TABLE, generator = "TABLE_GEN")
	private Long orderAccountProductId;

	@ManyToOne
	@JoinColumn(name = "ORDER_ACCOUNT_ID" , nullable=false)
	private OrderAccount orderAccount;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ORDER_PRODUCT_ID" , nullable=false)
	private OrderProduct orderProduct;

	@Column (name="ORDER_ACCOUNT_PRODUCT_ST_DT" , length=0 , nullable=false)
	private LocalDate orderAccountProductStartDate;

	@Column (name="ORDER_ACCOUNT_PRODUCT_END_DT", length=0)
	private LocalDate orderAccountProductEndDate;

	@Column (name="ORDER_ACCOUNT_PRODUCT_EOT"  , length=0 )
	private LocalDateTime orderAccountProductEot;

	@Column (name="ORDER_ACCOUNT_PRODUCT_ACCNT_DT"  , length=0 )
	private LocalDate orderAccountProductAccountedDate;

	@Column (name="ORDER_ACCOUNT_PRODUCT_L_ST_DT"  , length=0 )
	private LocalDateTime orderAccountProductLastStatusDate;

	@Column (name="ORDER_ACCOUNT_PRODUCT_L_TRX_ST" , nullable=false )
	private Integer orderAccountProductLastTransactionStatus;

	@Column (name="ORDER_ACCOUNT_PRODUCT_PM_FR_TY" , nullable=false )
	private Integer orderAccountProductPaymentFrequencyType;

	@Column (name="ORDER_ACCOUNT_PRODUCT_STATUS" , nullable=false )
	private Integer orderAccountProductStatus;

	public OrderAccountProduct() {
	}

	public Long getOrderAccountProductId() {
		return orderAccountProductId;
	}

	public void setOrderAccountProductId(Long orderAccountProductId) {
		this.orderAccountProductId = orderAccountProductId;
	}

	public OrderAccount getOrderAccount() {
		return orderAccount;
	}

	public void setOrderAccount(OrderAccount orderAccount) {
		this.orderAccount = orderAccount;
	}

	public OrderProduct getOrderProduct() {
		return orderProduct;
	}

	public void setOrderProduct(OrderProduct orderProduct) {
		this.orderProduct = orderProduct;
	}

	public LocalDate getOrderAccountProductStartDate() {
		return orderAccountProductStartDate;
	}

	public void setOrderAccountProductStartDate(LocalDate orderAccountProductStartDate) {
		this.orderAccountProductStartDate = orderAccountProductStartDate;
	}

	public LocalDate getOrderAccountProductEndDate() {
		return orderAccountProductEndDate;
	}

	public void setOrderAccountProductEndDate(LocalDate orderAccountProductEndDate) {
		this.orderAccountProductEndDate = orderAccountProductEndDate;
	}

	public LocalDateTime getOrderAccountProductEot() {
		return orderAccountProductEot;
	}

	public void setOrderAccountProductEot(LocalDateTime orderAccountProductEot) {
		this.orderAccountProductEot = orderAccountProductEot;
	}

	public LocalDate getOrderAccountProductAccountedDate() {
		return orderAccountProductAccountedDate;
	}

	public void setOrderAccountProductAccountedDate(
			LocalDate orderAccountProductAccountedDate) {
		this.orderAccountProductAccountedDate = orderAccountProductAccountedDate;
	}

	public LocalDateTime getOrderAccountProductLastStatusDate() {
		return orderAccountProductLastStatusDate;
	}

	public void setOrderAccountProductLastStatusDate(
			LocalDateTime orderAccountProductLastStatusDate) {
		this.orderAccountProductLastStatusDate = orderAccountProductLastStatusDate;
	}

	public Integer getOrderAccountProductLastTransactionStatus() {
		return orderAccountProductLastTransactionStatus;
	}

	public void setOrderAccountProductLastTransactionStatus(
			Integer orderAccountProductLastTransactionStatus) {
		this.orderAccountProductLastTransactionStatus = orderAccountProductLastTransactionStatus;
	}

	public Integer getOrderAccountProductPaymentFrequencyType() {
		return orderAccountProductPaymentFrequencyType;
	}

	public void setOrderAccountProductPaymentFrequencyType(
			Integer orderAccountProductPaymentFrequencyType) {
		this.orderAccountProductPaymentFrequencyType = orderAccountProductPaymentFrequencyType;
	}

	public Integer getOrderAccountProductStatus() {
		return orderAccountProductStatus;
	}

	public void setOrderAccountProductStatus(Integer orderAccountProductStatus) {
		this.orderAccountProductStatus = orderAccountProductStatus;
	}
}
