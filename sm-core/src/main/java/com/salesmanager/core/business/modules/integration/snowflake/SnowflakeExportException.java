package com.salesmanager.core.business.modules.integration.snowflake;

public class SnowflakeExportException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public SnowflakeExportException(String message, Throwable cause) {
		super(message, cause);
	}

}
