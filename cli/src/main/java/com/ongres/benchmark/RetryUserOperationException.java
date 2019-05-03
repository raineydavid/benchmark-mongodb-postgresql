package com.ongres.benchmark;

public class RetryUserOperationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public RetryUserOperationException(Throwable cause) {
    super(cause);
  }

}
