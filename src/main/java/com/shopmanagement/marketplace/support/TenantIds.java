package com.shopmanagement.marketplace.support;

import com.shopmanagement.marketplace.filter.TenantContextFilter;

public final class TenantIds {

  private TenantIds() {}

  public static String require() {
    String tenantId = TenantContextFilter.getCurrentTenantId();
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalStateException("Missing tenant context");
    }
    return tenantId;
  }

  public static String shopOrNull() {
    return TenantContextFilter.getCurrentShopId();
  }

  public static String userOrNull() {
    return TenantContextFilter.getCurrentUserId();
  }
}
