package com.shopmanagement.marketplace.service;

import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.marketplace.domain.MarketplaceOrder;
import com.shopmanagement.marketplace.domain.MarketplaceOrderItem;
import com.shopmanagement.marketplace.domain.MarketplaceReturn;
import com.shopmanagement.marketplace.repo.MarketplaceOrderItemRepository;
import com.shopmanagement.marketplace.repo.MarketplaceOrderRepository;
import com.shopmanagement.marketplace.repo.MarketplaceReturnRepository;
import com.shopmanagement.marketplace.stock.SellableQty;
import com.shopmanagement.marketplace.stock.StockAvailabilityClient;
import com.shopmanagement.marketplace.support.TenantIds;

/**
 * Records a marketplace return. Unshipped orders release the FEFO hold. Shipped orders restock
 * through stock-service {@code /stock/add}, the same call a sales return uses. The customer bill
 * is not marked paid or refunded.
 */
@Service
public class MarketplaceReturnService {

  private final MarketplaceOrderRepository orderRepository;
  private final MarketplaceOrderItemRepository orderItemRepository;
  private final MarketplaceReturnRepository returnRepository;
  private final StockAvailabilityClient stockAvailabilityClient;

  public MarketplaceReturnService(
      MarketplaceOrderRepository orderRepository,
      MarketplaceOrderItemRepository orderItemRepository,
      MarketplaceReturnRepository returnRepository,
      StockAvailabilityClient stockAvailabilityClient) {
    this.orderRepository = orderRepository;
    this.orderItemRepository = orderItemRepository;
    this.returnRepository = returnRepository;
    this.stockAvailabilityClient = stockAvailabilityClient;
  }

  @Transactional
  public Map<String, Object> record(Long orderId, String externalReturnId, String reason) {
    String tenantId = TenantIds.require();
    MarketplaceOrder order =
        orderRepository
            .findByIdAndTenantId(orderId, tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    if ("CANCELLED".equals(order.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "A cancelled order cannot be returned.");
    }
    if ("RETURNED".equals(order.getStatus())) {
      String externalEarly = externalReturnId == null || externalReturnId.isBlank() ? "manual-" + order.getId() : externalReturnId.trim();
      var already = returnRepository.findByOrderIdAndExternalReturnId(order.getId(), externalEarly);
      if (already.isPresent()) {
        return toMap(already.get(), true);
      }
      throw new ResponseStatusException(HttpStatus.CONFLICT, "This order was already returned.");
    }
    String external = externalReturnId == null || externalReturnId.isBlank() ? "manual-" + order.getId() : externalReturnId.trim();
    if (external.length() > 128) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "external return id is too long");
    }
    var existing = returnRepository.findByOrderIdAndExternalReturnId(order.getId(), external);
    if (existing.isPresent() && !"OPEN".equals(existing.get().getStatus())) {
      return toMap(existing.get(), true);
    }
    MarketplaceReturn row = existing.orElseGet(MarketplaceReturn::new);
    row.setTenantId(tenantId);
    row.setOrderId(order.getId());
    row.setExternalReturnId(external);
    row.setStatus("OPEN");
    row.setReason(reason == null || reason.isBlank() ? null : reason.trim());
    if (row.getPayloadJson() == null) {
      row.setPayloadJson(new LinkedHashMap<>());
    }
    row = returnRepository.save(row);

    boolean shipped = "FULFILLED".equals(order.getStatus());
    if (shipped && Boolean.TRUE.equals(row.getPayloadJson().get("restockApplied"))) {
      return toMap(row, true);
    }
    if (!shipped && Boolean.TRUE.equals(row.getPayloadJson().get("releaseApplied"))) {
      return toMap(row, true);
    }

    if (shipped) {
      for (MarketplaceOrderItem item : orderItemRepository.findByOrderIdOrderByIdAsc(order.getId())) {
        if (item.getProductId() == null || item.getQuantity() == null) {
          continue;
        }
        int qty = item.getQuantity().setScale(0, RoundingMode.UP).intValue();
        stockAvailabilityClient.addQuantity(order.getTenantId(), order.getShopId(), item.getProductId(), qty);
      }
      row.getPayloadJson().put("restockApplied", true);
      row.setStatus("RESTOCKED");
    } else if (SellableQty.isFefoKey(order.getStockReservationKey())) {
      stockAvailabilityClient.releaseFefo(order.getTenantId(), order.getShopId(), order.getStockReservationKey());
      row.getPayloadJson().put("releaseApplied", true);
      row.setStatus("CLOSED");
    } else {
      row.setStatus("CLOSED");
    }
    order.setStatus("RETURNED");
    orderRepository.save(order);
    return toMap(returnRepository.save(row), false);
  }

  private static Map<String, Object> toMap(MarketplaceReturn row, boolean idempotent) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", row.getId());
    out.put("orderId", row.getOrderId());
    out.put("externalReturnId", row.getExternalReturnId());
    out.put("status", row.getStatus());
    out.put("idempotent", idempotent);
    out.put("customerPaymentUnchanged", true);
    return out;
  }
}
