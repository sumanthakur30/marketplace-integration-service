package com.shopmanagement.marketplace.web.dto;

import java.util.Map;

public final class AccountDtos {
  private AccountDtos() {}

  public record UpsertRequest(
      String shopId, String displayName, String status, Map<String, Object> settings) {}
}
