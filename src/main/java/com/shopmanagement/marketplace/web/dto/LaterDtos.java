package com.shopmanagement.marketplace.web.dto;

import java.math.BigDecimal;

public final class LaterDtos {
  private LaterDtos() {}

  public record ReturnRequest(String externalReturnId, String reason) {}

  public record SettlementRequest(
      String externalSettlementId,
      String periodStart,
      String periodEnd,
      BigDecimal grossAmount,
      BigDecimal feeAmount,
      BigDecimal netAmount,
      String currency) {}
}
