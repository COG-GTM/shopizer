package com.salesmanager.core.model.reference.country;

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
import com.salesmanager.core.model.reference.language.Language;

@Entity
@Table(name = "COUNTRY_DESCRIPTION", uniqueConstraints={
	@UniqueConstraint(columnNames={
			"COUNTRY_ID",
			"LANGUAGE_ID"
		})
	}
)
@TableGenerator(name = "description_gen", table = "SM_SEQUENCER", pkColumnName = "SEQ_NAME", valueColumnName = "SEQ_COUNT", pkColumnValue = "country_description_seq", allocationSize = SchemaConstant.DESCRIPTION_ID_ALLOCATION_SIZE, initialValue = SchemaConstant.DESCRIPTION_ID_START_VALUE)
//@SequenceGenerator(name = "description_gen", sequenceName = "country_description_seq", allocationSize = SchemaConstant.DESCRIPTION_ID_SEQUENCE_START)
public class CountryDescription extends Description {
	private static final long serialVersionUID = 1L;
	
	@JsonIgnore
	@ManyToOne(targetEntity = Country.class)
	@JoinColumn(name = "COUNTRY_ID", nullable = false)
	private Country country;
	
	public CountryDescription() {
	}
	
	public CountryDescription(Language language, String name) {
		this.setLanguage(language);
		this.setName(name);
	}
	
	public Country getCountry() {
		return country;
	}

	public void setCountry(Country country) {
		this.country = country;
	}
	
}
