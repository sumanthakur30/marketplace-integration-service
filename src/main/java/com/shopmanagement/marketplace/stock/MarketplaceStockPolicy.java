package com.shopmanagement.marketplace.stock;

import java.util.Locale;
import java.util.Map;

/**
 * Amazon sale stock policy. A confirmed sale holds available stock. Shipping commits that hold.
 * A cancellation before commit releases the hold. A refund does not put goods back on the shelf.
 */
public final class MarketplaceStockPolicy {

  private MarketplaceStockPolicy() {}

  public static String externalStatus(Map<String, Object> payload) {
    if (payload == null) {
      return "";
    }
    Object value = payload.get("externalStatus");
    if (value == null) {
      return "";
    }
    return String.valueOf(value).trim().toUpperCase(Locale.ROOT).replace(" ", "");
  }

  /** Sale is real enough to reduce available quantity. Pending is not included. */
  public static boolean holdsStock(String externalStatus) {
    return "UNSHIPPED".equals(externalStatus)
        || "PARTIALLYSHIPPED".equals(externalStatus)
        || "SHIPPED".equals(externalStatus);
  }

  /** Physical quantity leaves the shelf only after Amazon marks the order shipped. */
  public static boolean commitsStock(String externalStatus) {
    return "SHIPPED".equals(externalStatus);
  }

  /** Cancel before the goods leave. Amazon spells this Canceled. */
  public static boolean releasesStock(String externalStatus) {
    return "CANCELED".equals(externalStatus) || "CANCELLED".equals(externalStatus);
  }
}
