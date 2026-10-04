package com.shopmanagement.marketplace.channel.amazon;

/** Amazon HTTP failure. The message never includes a token or response body. */
public class AmazonCallException extends RuntimeException {

  private final int status;
  private final boolean tokenRejected;

  public AmazonCallException(int status, boolean tokenRejected, String message) {
    super(message);
    this.status = status;
    this.tokenRejected = tokenRejected;
  }

  public int status() {
    return status;
  }

  public boolean tokenRejected() {
    return tokenRejected;
  }
}
