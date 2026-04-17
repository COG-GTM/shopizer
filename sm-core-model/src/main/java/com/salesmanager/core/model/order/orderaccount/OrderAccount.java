package com.salesmanager.core.model.order.orderaccount;

import java.util.HashSet;
import java.util.Set;
import java.time.LocalDate;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.TableGenerator;

import com.salesmanager.core.constants.SchemaConstant;
import com.salesmanager.core.model.generic.SalesManagerEntity;
import com.salesmanager.core.model.order.Order;

@Entity
@Table(name = "ORDER_ACCOUNT")
public class OrderAccount extends SalesManagerEntity<Long, OrderAccount> {
private static final long serialVersionUID = -2429388347536330540L;

	@Id
	@Column(name = "ORDER_ACCOUNT_ID", unique = true, nullable = false)
	@TableGenerator(name = "TABLE_GEN", table = "SM_SEQUENCER", pkColumnName = "SEQ_NAME", valueColumnName = "SEQ_COUNT", pkColumnValue = "ORDER_ACCOUNT_ID_NEXT_VALUE", allocationSize = 1)
	@GeneratedValue(strategy = GenerationType.TABLE, generator = "TABLE_GEN")
	private Long id;

	@ManyToOne
	@JoinColumn(name = "ORDER_ID", nullable = false)
	private Order order;
	
	@Column(name = "ORDER_ACCOUNT_START_DATE", nullable = false, length = 0)
	private LocalDate orderAccountStartDate;
	
	@Column(name = "ORDER_ACCOUNT_END_DATE", length = 0)
	private LocalDate orderAccountEndDate;

	@Column(name = "ORDER_ACCOUNT_BILL_DAY", nullable = false)
	private Integer orderAccountBillDay;

	@OneToMany(mappedBy = "orderAccount", cascade = CascadeType.ALL)
	private Set<OrderAccountProduct> orderAccountProducts = new HashSet<OrderAccountProduct>();

	public OrderAccount() {
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Order getOrder() {
		return order;
	}

	public void setOrder(Order order) {
		this.order = order;
	}

	public LocalDate getOrderAccountStartDate() {
		return orderAccountStartDate;
	}

	public void setOrderAccountStartDate(LocalDate orderAccountStartDate) {
		this.orderAccountStartDate = orderAccountStartDate;
	}

	public LocalDate getOrderAccountEndDate() {
		return orderAccountEndDate;
	}

	public void setOrderAccountEndDate(LocalDate orderAccountEndDate) {
		this.orderAccountEndDate = orderAccountEndDate;
	}

	public Integer getOrderAccountBillDay() {
		return orderAccountBillDay;
	}

	public void setOrderAccountBillDay(Integer orderAccountBillDay) {
		this.orderAccountBillDay = orderAccountBillDay;
	}

	public Set<OrderAccountProduct> getOrderAccountProducts() {
		return orderAccountProducts;
	}

	public void setOrderAccountProducts(
			Set<OrderAccountProduct> orderAccountProducts) {
		this.orderAccountProducts = orderAccountProducts;
	}
}
