package com.salesmanager.core.model.customer.attribute;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.UniqueConstraint;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.salesmanager.core.constants.SchemaConstant;
import com.salesmanager.core.model.common.description.Description;

@Entity
@Table(name = "CUSTOMER_OPT_VAL_DESCRIPTION", uniqueConstraints={
	@UniqueConstraint(columnNames={
			"CUSTOMER_OPT_VAL_ID",
			"LANGUAGE_ID"
		})
	}
)

@TableGenerator(name = "description_gen", table = "SM_SEQUENCER", pkColumnName = "SEQ_NAME", valueColumnName = "SEQ_COUNT", pkColumnValue = "customer_option_value_description_seq", allocationSize = SchemaConstant.DESCRIPTION_ID_ALLOCATION_SIZE, initialValue = SchemaConstant.DESCRIPTION_ID_START_VALUE)
//@SequenceGenerator(name = "description_gen", sequenceName = "customer_option_value_description_seq", allocationSize = SchemaConstant.DESCRIPTION_ID_SEQUENCE_START)
public class CustomerOptionValueDescription extends Description {
	private static final long serialVersionUID = 1L;
	
	@JsonIgnore
	@ManyToOne(targetEntity = CustomerOptionValue.class)
	@JoinColumn(name = "CUSTOMER_OPT_VAL_ID")
	private CustomerOptionValue customerOptionValue;
	
	
	public CustomerOptionValueDescription() {
	}

	public CustomerOptionValue getCustomerOptionValue() {
		return customerOptionValue;
	}

	public void setCustomerOptionValue(CustomerOptionValue customerOptionValue) {
		this.customerOptionValue = customerOptionValue;
	}

}
