package com.salesmanager.core.model.order.filehistory;

import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.UniqueConstraint;

import com.salesmanager.core.model.merchant.MerchantStore;

@Entity
@Table (name="FILE_HISTORY", uniqueConstraints={
		@UniqueConstraint(
			columnNames={
				"MERCHANT_ID",
				"FILE_ID"
			}
		)
	}
)
public class FileHistory implements Serializable {
	private static final long serialVersionUID = 1321251632883237664L;
	
	@Id
	@Column(name = "FILE_HISTORY_ID", unique = true, nullable = false)
	@TableGenerator(name = "TABLE_GEN", table = "SM_SEQUENCER", pkColumnName = "SEQ_NAME", valueColumnName = "SEQ_COUNT",
		pkColumnValue = "FILE_HISTORY_ID_NEXT_VALUE", allocationSize = 1)
	@GeneratedValue(strategy = GenerationType.TABLE, generator = "TABLE_GEN")
	private Long id;
	
	@ManyToOne(targetEntity = MerchantStore.class)
	@JoinColumn(name = "MERCHANT_ID", nullable = false)
	private MerchantStore store;
	
	@Column(name = "FILE_ID")
	private Long fileId;

	@Column ( name="FILESIZE", nullable=false )
	private Integer filesize;
	
	@Column ( name="DATE_ADDED", length=0, nullable=false )
	private LocalDateTime dateAdded;
	
	@Column ( name="DATE_DELETED", length=0 )
	private LocalDateTime dateDeleted;
	
	@Column ( name="ACCOUNTED_DATE", length=0 )
	private LocalDateTime accountedDate;
	
	@Column ( name="DOWNLOAD_COUNT", nullable=false )
	private Integer downloadCount;
	
	public FileHistory() {
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public MerchantStore getStore() {
		return store;
	}

	public void setStore(MerchantStore store) {
		this.store = store;
	}

	public Long getFileId() {
		return fileId;
	}

	public void setFileId(Long fileId) {
		this.fileId = fileId;
	}

	public Integer getFilesize() {
		return filesize;
	}

	public void setFilesize(Integer filesize) {
		this.filesize = filesize;
	}

	public LocalDateTime getDateAdded() {
		return dateAdded;
	}

	public void setDateAdded(LocalDateTime dateAdded) {
		this.dateAdded = dateAdded;
	}

	public LocalDateTime getDateDeleted() {
		return dateDeleted;
	}

	public void setDateDeleted(LocalDateTime dateDeleted) {
		this.dateDeleted = dateDeleted;
	}

	public LocalDateTime getAccountedDate() {
		return accountedDate;
	}

	public void setAccountedDate(LocalDateTime accountedDate) {
		this.accountedDate = accountedDate;
	}

	public Integer getDownloadCount() {
		return downloadCount;
	}

	public void setDownloadCount(Integer downloadCount) {
		this.downloadCount = downloadCount;
	}

}