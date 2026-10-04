package com.shopmanagement.marketplace.web.dto;

import java.util.List;
import java.util.Map;

public final class ChannelDtos {
  private ChannelDtos() {}

  public record UpsertRequest(
      Long accountId,
      String channelCode,
      Boolean enabled,
      String externalSellerId,
      String credentialsRef,
      Map<String, Object> config,
      Map<String, Object> secrets,
      Boolean clearSecrets) {}

  public record StatusMappingRow(String externalStatus, String internalStatus) {}

  public record StatusMappingReplaceRequest(List<StatusMappingRow> mappings) {}
}
