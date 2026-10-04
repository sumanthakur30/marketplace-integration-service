package com.shopmanagement.marketplace.security;

import org.springframework.stereotype.Component;

import com.shopmanagement.marketplace.filter.TenantContextFilter;

@Component
public class MarketplaceAccessGuard {

  public void requireView() {
    MarketplaceAccessCheck.require(
        TenantContextFilter.getCurrentRole(),
        TenantContextFilter.getCurrentPermissions(),
        "MARKETPLACE_VIEW",
        "MARKETPLACE_MANAGE",
        "MARKETPLACE_ORDER_VIEW");
  }

  public void requireManage() {
    MarketplaceAccessCheck.require(
        TenantContextFilter.getCurrentRole(),
        TenantContextFilter.getCurrentPermissions(),
        "MARKETPLACE_MANAGE");
  }

  public void requireConnect() {
    MarketplaceAccessCheck.require(
        TenantContextFilter.getCurrentRole(),
        TenantContextFilter.getCurrentPermissions(),
        "MARKETPLACE_CONNECT",
        "MARKETPLACE_MANAGE");
  }
}
