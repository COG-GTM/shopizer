package com.salesmanager.core.model.common.audit;

import java.io.Serializable;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDateTime;

import org.apache.commons.lang3.StringUtils;



@Embeddable
public class AuditSection implements Serializable {


  private static final long serialVersionUID = 1L;

  @Column(name = "DATE_CREATED")
  private LocalDateTime dateCreated;

  @Column(name = "DATE_MODIFIED")
  private LocalDateTime dateModified;

  @Column(name = "UPDT_ID", length = 60)
  private String modifiedBy;

  public AuditSection() {}

  public LocalDateTime getDateCreated() {
    return dateCreated;
  }

  public void setDateCreated(LocalDateTime dateCreated) {
    this.dateCreated = dateCreated;
  }

  public LocalDateTime getDateModified() {
    return dateModified;
  }

  public void setDateModified(LocalDateTime dateModified) {
    this.dateModified = dateModified;
  }

  public String getModifiedBy() {
    return modifiedBy;
  }

  public void setModifiedBy(String modifiedBy) {
	  if(!StringUtils.isBlank(modifiedBy)) {//TODO
		  if(modifiedBy.length()>20) {
			  modifiedBy = modifiedBy.substring(0, 20);
		  }
	  }
    this.modifiedBy = modifiedBy;
  }
}
