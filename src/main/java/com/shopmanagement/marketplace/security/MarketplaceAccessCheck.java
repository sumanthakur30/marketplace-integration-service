package com.shopmanagement.marketplace.security;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Owner and super admin bypass. Other roles need an explicit marketplace permission. */
public final class MarketplaceAccessCheck {

  private MarketplaceAccessCheck() {}

  public static boolean allowed(String role, String permissionsHeader, String... keys) {
    if (isOwner(role)) {
      return true;
    }
    if (keys == null || keys.length == 0 || permissionsHeader == null || permissionsHeader.isBlank()) {
      return false;
    }
    for (String granted : permissionsHeader.split(",")) {
      String code = granted.trim().toUpperCase(Locale.ROOT);
      if (code.isEmpty()) {
        continue;
      }
      for (String key : keys) {
        if (key != null && code.equals(key.trim().toUpperCase(Locale.ROOT))) {
          return true;
        }
      }
    }
    return false;
  }

  public static void require(String role, String permissionsHeader, String... keys) {
    if (!allowed(role, permissionsHeader, keys)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing marketplace permission");
    }
  }

  static boolean isOwner(String role) {
    if (role == null || role.isBlank()) {
      return false;
    }
    String normalized = role.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    return "SUPER_ADMIN".equals(normalized) || "SHOP_OWNER".equals(normalized);
  }
}
