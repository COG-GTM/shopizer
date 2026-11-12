package com.salesmanager.core.model.reference.zone;

import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.salesmanager.core.constants.SchemaConstant;
import com.salesmanager.core.model.generic.SalesManagerEntity;
import com.salesmanager.core.model.reference.country.Country;

@Entity
@Table(name = "ZONE")
public class Zone extends SalesManagerEntity<Long, Zone> {
  private static final long serialVersionUID = 1L;

  @Id
  @Column(name = "ZONE_ID")
  @TableGenerator(name = "TABLE_GEN", table = "SM_SEQUENCER", pkColumnName = "SEQ_NAME",
      valueColumnName = "SEQ_COUNT", pkColumnValue = "ZONE_SEQ_NEXT_VAL")
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "TABLE_GEN")
  private Long id;

  @JsonIgnore
  @OneToMany(mappedBy = "zone", cascade = CascadeType.ALL)
  private List<ZoneDescription> descriptions = new ArrayList<ZoneDescription>();

  @JsonIgnore
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "COUNTRY_ID", nullable = false)
  private Country country;

  @Transient
  private String name;



  @Column(name = "ZONE_CODE", unique = true, nullable = false)
  private String code;

  public Zone() {}

  public Zone(Country country, String name, String code) {
    this.setCode(code);
    this.setCountry(country);
    this.setCode(name);
  }

  public Country getCountry() {
    return country;
  }

  public void setCountry(Country country) {
    this.country = country;
  }



  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  @Override
  public Long getId() {
    return id;
  }

  @Override
  public void setId(Long id) {
    this.id = id;
  }

  public List<ZoneDescription> getDescriptions() {
    return descriptions;
  }

  public void setDescriptons(List<ZoneDescription> descriptions) {
    this.descriptions = descriptions;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

}
