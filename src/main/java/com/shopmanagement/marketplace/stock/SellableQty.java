package com.shopmanagement.marketplace.stock;

/**
 * Channel sellable quantity is on-hand available stock minus the channel buffer.
 * The result is never negative. This does not create stock or a product.
 */
public final class SellableQty {

  private SellableQty() {}

  public static int buffer(Object raw) {
    if (raw == null) {
      return 0;
    }
    if (raw instanceof Number number) {
      return Math.max(0, number.intValue());
    }
    try {
      return Math.max(0, Integer.parseInt(String.valueOf(raw).trim()));
    } catch (NumberFormatException ex) {
      return 0;
    }
  }

  public static int units(double available, int buffer) {
    int onHand = (int) Math.floor(Math.max(0d, available));
    return Math.max(0, onHand - Math.max(0, buffer));
  }

  public static String reservationKey(long channelId, String externalOrderId) {
    String external = externalOrderId == null ? "" : externalOrderId.trim();
    String key = "fefo-" + channelId + "-" + external;
    return key.length() <= 120 ? key : key.substring(0, 120);
  }

  public static boolean isFefoKey(String key) {
    return key != null && key.startsWith("fefo-");
  }
}
