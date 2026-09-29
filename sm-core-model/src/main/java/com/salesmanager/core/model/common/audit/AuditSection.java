package com.salesmanager.core.model.common.audit;

import java.io.Serializable;
import java.util.Date;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import org.apache.commons.lang3.StringUtils;

import com.salesmanager.core.utils.CloneUtils;


@Embeddable
public class AuditSection implements Serializable {


  private static final long serialVersionUID = 1L;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "DATE_CREATED")
  private Date dateCreated;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "DATE_MODIFIED")
  private Date dateModified;

  @Column(name = "UPDT_ID", length = 60)
  private String modifiedBy;

  public AuditSection() {}

  public Date getDateCreated() {
    return CloneUtils.clone(dateCreated);
  }

  public void setDateCreated(Date dateCreated) {
    this.dateCreated = CloneUtils.clone(dateCreated);
  }

  public Date getDateModified() {
    return CloneUtils.clone(dateModified);
  }

  public void setDateModified(Date dateModified) {
    this.dateModified = CloneUtils.clone(dateModified);
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
