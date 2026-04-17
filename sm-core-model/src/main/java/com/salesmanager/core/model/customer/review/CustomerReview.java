package com.salesmanager.core.model.customer.review;

import java.util.HashSet;
import java.util.Set;
import java.time.LocalDateTime;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.UniqueConstraint;

import com.salesmanager.core.constants.SchemaConstant;
import com.salesmanager.core.model.common.audit.AuditListener;
import com.salesmanager.core.model.common.audit.AuditSection;
import com.salesmanager.core.model.common.audit.Auditable;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.generic.SalesManagerEntity;

@Entity
@EntityListeners(value = AuditListener.class)
@Table(name = "CUSTOMER_REVIEW", uniqueConstraints={
		@UniqueConstraint(columnNames={
				"CUSTOMERS_ID",
				"REVIEWED_CUSTOMER_ID"
			})
		}
)
public class CustomerReview extends SalesManagerEntity<Long, CustomerReview> implements Auditable {
	private static final long serialVersionUID = 1L;

	@Id
	@Column(name = "CUSTOMER_REVIEW_ID", unique=true, nullable=false)
	@TableGenerator(name = "TABLE_GEN", table = "SM_SEQUENCER", pkColumnName = "SEQ_NAME", valueColumnName = "SEQ_COUNT",
	pkColumnValue = "CUSTOMER_REVIEW_SEQ_NEXT_VAL", allocationSize = 1)
	@GeneratedValue(strategy = GenerationType.TABLE, generator = "TABLE_GEN")
	private Long id;
	
	@Embedded
	private AuditSection audit = new AuditSection();
	
	@Column(name = "REVIEWS_RATING")
	private Double reviewRating;
	
	@Column(name = "REVIEWS_READ")
	private Long reviewRead;
	
	@Column(name = "REVIEW_DATE")
	private LocalDateTime reviewDate;
	
	@Column(name = "STATUS")
	private Integer status;

	@ManyToOne
	@JoinColumn(name="CUSTOMERS_ID")
	private Customer customer;
	

	
	@OneToOne
	@JoinColumn(name="REVIEWED_CUSTOMER_ID")
	private Customer reviewedCustomer;

	public Customer getReviewedCustomer() {
		return reviewedCustomer;
	}

	public void setReviewedCustomer(Customer reviewedCustomer) {
		this.reviewedCustomer = reviewedCustomer;
	}

	@OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, mappedBy = "customerReview")
	private Set<CustomerReviewDescription> descriptions = new HashSet<CustomerReviewDescription>();
	
	public CustomerReview() {
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Double getReviewRating() {
		return reviewRating;
	}

	public void setReviewRating(Double reviewRating) {
		this.reviewRating = reviewRating;
	}

	public Long getReviewRead() {
		return reviewRead;
	}

	public void setReviewRead(Long reviewRead) {
		this.reviewRead = reviewRead;
	}

	public Integer getStatus() {
		return status;
	}

	public void setStatus(Integer status) {
		this.status = status;
	}

	public Customer getCustomer() {
		return customer;
	}

	public void setCustomer(Customer customer) {
		this.customer = customer;
	}


	public Set<CustomerReviewDescription> getDescriptions() {
		return descriptions;
	}

	public void setDescriptions(Set<CustomerReviewDescription> descriptions) {
		this.descriptions = descriptions;
	}
	
	@Override
	public AuditSection getAuditSection() {
		return audit;
	}
	
	@Override
	public void setAuditSection(AuditSection audit) {
		this.audit = audit;
	}
	
	public LocalDateTime getReviewDate() {
		return reviewDate;
	}

	public void setReviewDate(LocalDateTime reviewDate) {
		this.reviewDate = reviewDate;
	}

}
