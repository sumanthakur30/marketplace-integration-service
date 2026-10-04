package com.shopmanagement.marketplace.settlement;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Marketplace fee and bank net. This is not the customer's payment. */
public final class SettlementAmounts {

  private SettlementAmounts() {}

  public record Split(BigDecimal gross, BigDecimal fee, BigDecimal net) {}

  public static Split of(BigDecimal gross, BigDecimal fee, BigDecimal net) {
    BigDecimal safeFee = money(fee, BigDecimal.ZERO);
    BigDecimal safeGross = gross == null ? null : money(gross, null);
    BigDecimal safeNet = net == null ? null : money(net, null);
    if (safeGross == null && safeNet == null) {
      throw new IllegalArgumentException("Settlement amount is required");
    }
    if (safeGross == null) {
      safeGross = safeNet.add(safeFee);
    }
    if (safeNet == null) {
      safeNet = safeGross.subtract(safeFee);
    }
    if (safeGross.signum() < 0 || safeFee.signum() < 0 || safeNet.signum() < 0) {
      throw new IllegalArgumentException("Settlement amounts cannot be negative");
    }
    if (safeFee.compareTo(safeGross) > 0) {
      throw new IllegalArgumentException("Fees cannot be more than the gross amount");
    }
    return new Split(safeGross, safeFee, safeNet);
  }

  private static BigDecimal money(BigDecimal value, BigDecimal fallback) {
    if (value == null) {
      return fallback;
    }
    return value.setScale(2, RoundingMode.HALF_UP);
  }
}
