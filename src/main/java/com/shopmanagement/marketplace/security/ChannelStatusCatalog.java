package com.shopmanagement.marketplace.security;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Editable external → internal status rows. Does not change marketplace_order checks. */
public final class ChannelStatusCatalog {

  public static final Set<String> INTERNAL =
      Set.of(
          "NEW",
          "CONFIRMED",
          "ALLOCATED",
          "PACKED",
          "READY_TO_SHIP",
          "SHIPPED",
          "DELIVERED",
          "CANCELLED",
          "RETURN_REQUESTED",
          "RETURNED",
          "REFUNDED",
          "FAILED");

  private ChannelStatusCatalog() {}

  public record Seed(String externalStatus, String internalStatus) {}

  public static List<Seed> seedFor(String channelCode) {
    String code = channelCode == null ? "" : channelCode.trim().toUpperCase(Locale.ROOT);
    return switch (code) {
      case "AMAZON" ->
          List.of(
              new Seed("Pending", "NEW"),
              new Seed("Unshipped", "CONFIRMED"),
              new Seed("Shipped", "SHIPPED"),
              new Seed("Delivered", "DELIVERED"),
              new Seed("Cancelled", "CANCELLED"),
              new Seed("Refunded", "REFUNDED"));
      case "FLIPKART" ->
          List.of(
              new Seed("Created", "NEW"),
              new Seed("Packed", "PACKED"),
              new Seed("Ready to Ship", "READY_TO_SHIP"),
              new Seed("Shipped", "SHIPPED"),
              new Seed("Delivered", "DELIVERED"),
              new Seed("Cancelled", "CANCELLED"),
              new Seed("Returned", "RETURNED"));
      default -> List.of();
    };
  }

  public static String requireInternal(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("internal status required");
    }
    String code = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    if (!INTERNAL.contains(code)) {
      throw new IllegalArgumentException("Unsupported internal status");
    }
    return code;
  }
}
