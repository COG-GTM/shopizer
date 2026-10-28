package com.salesmanager.shop.store.api.exception;

public class ForbiddenException extends GenericRuntimeException {

  private final static String ERROR_CODE = "403";

  private static final long serialVersionUID = 1L;

  public ForbiddenException() {
    super(ERROR_CODE, "Forbidden");
  }

  public ForbiddenException(String message) {
    super(ERROR_CODE, message);
  }
}
