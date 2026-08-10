package com.shopmanagement.marketplace.filter;

import java.io.IOException;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Binds tenant from gateway header {@code X-Tenant-Id}. Public webhook paths skip auth tenant
 * requirement when marked under {@code /api/v1/marketplace/public/**}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class TenantContextFilter extends OncePerRequestFilter {

  public static final String TENANT_ID_HEADER = "X-Tenant-Id";
  public static final String SHOP_ID_HEADER = "X-Shop-Id";
  public static final String USER_ID_HEADER = "X-User-Id";

  private static final ThreadLocal<String> TENANT = new ThreadLocal<>();
  private static final ThreadLocal<String> SHOP = new ThreadLocal<>();
  private static final ThreadLocal<String> USER = new ThreadLocal<>();

  public static String getCurrentTenantId() {
    return TENANT.get();
  }

  public static String getCurrentShopId() {
    return SHOP.get();
  }

  public static String getCurrentUserId() {
    return USER.get();
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path != null
        && (path.startsWith("/actuator")
            || path.startsWith("/v3/api-docs")
            || path.startsWith("/swagger")
            || path.startsWith("/api/v1/marketplace/public/"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      String tenantId = blankToNull(request.getHeader(TENANT_ID_HEADER));
      if (tenantId == null) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"Missing tenant context header: X-Tenant-Id\"}");
        return;
      }
      TENANT.set(tenantId);
      SHOP.set(blankToNull(request.getHeader(SHOP_ID_HEADER)));
      USER.set(blankToNull(request.getHeader(USER_ID_HEADER)));
      MDC.put("tenantId", tenantId);
      filterChain.doFilter(request, response);
    } finally {
      TENANT.remove();
      SHOP.remove();
      USER.remove();
      MDC.remove("tenantId");
    }
  }

  private static String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
