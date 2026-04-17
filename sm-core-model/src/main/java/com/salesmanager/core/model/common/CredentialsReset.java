package com.salesmanager.core.model.common;


import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDate;

@Embeddable
public class CredentialsReset {
	
	@Column (name ="RESET_CREDENTIALS_REQ", length=256)
	private String credentialsRequest;

	@Column(name = "RESET_CREDENTIALS_EXP")
	private LocalDate credentialsRequestExpiry = LocalDate.now();

	public String getCredentialsRequest() {
		return credentialsRequest;
	}

	public void setCredentialsRequest(String credentialsRequest) {
		this.credentialsRequest = credentialsRequest;
	}

	public LocalDate getCredentialsRequestExpiry() {
		return credentialsRequestExpiry;
	}

	public void setCredentialsRequestExpiry(LocalDate credentialsRequestExpiry) {
		this.credentialsRequestExpiry = credentialsRequestExpiry;
	}

}
