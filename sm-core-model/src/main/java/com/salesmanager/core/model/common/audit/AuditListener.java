package com.salesmanager.core.model.common.audit;

import java.time.LocalDateTime;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

public class AuditListener {

  @PrePersist
  public void onSave(Object o) {
    if (o instanceof Auditable) {
      Auditable audit = (Auditable) o;
      AuditSection auditSection = audit.getAuditSection();

      auditSection.setDateModified(LocalDateTime.now());
      if (auditSection.getDateCreated() == null) {
        auditSection.setDateCreated(LocalDateTime.now());
      }
      audit.setAuditSection(auditSection);
    }
  }

  @PreUpdate
  public void onUpdate(Object o) {
    if (o instanceof Auditable) {
      Auditable audit = (Auditable) o;
      AuditSection auditSection = audit.getAuditSection();

      auditSection.setDateModified(LocalDateTime.now());
      if (auditSection.getDateCreated() == null) {
        auditSection.setDateCreated(LocalDateTime.now());
      }
      audit.setAuditSection(auditSection);
    }
  }
}
