package com.shopmanagement.marketplace.web.dto;

public final class AmazonDtos {
  private AmazonDtos() {}

  public record AuthorizeComplete(String code, String state, String sellingPartnerId) {}
}
