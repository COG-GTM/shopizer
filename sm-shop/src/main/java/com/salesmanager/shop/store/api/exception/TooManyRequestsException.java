package com.salesmanager.shop.store.api.exception;

public class TooManyRequestsException extends GenericRuntimeException {

  private final static String ERROR_CODE = "429";

  private static final long serialVersionUID = 1L;

  public TooManyRequestsException(String message) {
    super(ERROR_CODE, message);
  }
}
