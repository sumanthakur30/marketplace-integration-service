package com.shopmanagement.marketplace.web.dto;

import java.util.Map;

public final class ChannelDtos {
  private ChannelDtos() {}

  public record UpsertRequest(
      Long accountId,
      String channelCode,
      Boolean enabled,
      String externalSellerId,
      String credentialsRef,
      Map<String, Object> config) {}
}
