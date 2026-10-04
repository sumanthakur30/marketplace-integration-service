package com.shopmanagement.marketplace.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.marketplace.config.MarketplaceProperties;
import com.shopmanagement.marketplace.domain.MarketplaceChannel;
import com.shopmanagement.marketplace.domain.MarketplaceOrder;
import com.shopmanagement.marketplace.domain.MarketplaceOrderItem;
import com.shopmanagement.marketplace.repo.MarketplaceChannelRepository;
import com.shopmanagement.marketplace.repo.MarketplaceOrderItemRepository;
import com.shopmanagement.marketplace.repo.MarketplaceOrderRepository;
import com.shopmanagement.marketplace.order.ErpOrderRequest;
import com.shopmanagement.marketplace.order.OrderErpClient;
import com.shopmanagement.marketplace.repo.MarketplaceProductMappingRepository;
import com.shopmanagement.marketplace.stock.SellableQty;
import com.shopmanagement.marketplace.stock.StockAvailabilityClient;
import com.shopmanagement.marketplace.support.TenantIds;
import com.shopmanagement.marketplace.web.dto.OrderDtos;

@Service
public class MarketplaceOrderService {

  private static final String UNMAPPED_LINE =
      "A line has no product. Map the SKU before a bill is created.";

  private final MarketplaceChannelRepository channelRepository;
  private final MarketplaceProductMappingRepository mappingRepository;
  private final MarketplaceOrderRepository orderRepository;
  private final MarketplaceOrderItemRepository orderItemRepository;
  private final StockAvailabilityClient stockAvailabilityClient;
  private final OrderErpClient orderErpClient;
  private final MarketplaceProperties properties;

  public MarketplaceOrderService(
      MarketplaceChannelRepository channelRepository,
      MarketplaceProductMappingRepository mappingRepository,
      MarketplaceOrderRepository orderRepository,
      MarketplaceOrderItemRepository orderItemRepository,
      StockAvailabilityClient stockAvailabilityClient,
      OrderErpClient orderErpClient,
      MarketplaceProperties properties) {
    this.channelRepository = channelRepository;
    this.mappingRepository = mappingRepository;
    this.orderRepository = orderRepository;
    this.orderItemRepository = orderItemRepository;
    this.stockAvailabilityClient = stockAvailabilityClient;
    this.orderErpClient = orderErpClient;
    this.properties = properties;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listOrders() {
    return orderRepository.findByTenantIdOrderByIdDesc(TenantIds.require()).stream()
        .map(this::toOrderMap)
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getOrder(Long id) {
    MarketplaceOrder order =
        orderRepository
            .findByIdAndTenantId(id, TenantIds.require())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    Map<String, Object> out = toOrderMap(order);
    out.put(
        "items",
        orderItemRepository.findByOrderIdOrderByIdAsc(order.getId()).stream()
            .map(this::toItemMap)
            .collect(Collectors.toList()));
    return out;
  }

  /**
   * Ingest a marketplace order. Idempotent on (channel, externalOrderId). Optionally reserves stock
   * via stock-service {@code /stock/reserve-batch} (same as POS) — never mutates stock tables here.
   */
  @Transactional
  public Map<String, Object> ingest(OrderDtos.IngestRequest req) {
    String tenantId = TenantIds.require();
    String shopId = requireShopId();
    if (req == null || req.externalOrderId() == null || req.externalOrderId().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "externalOrderId required");
    }
    if (req.channelCode() == null || req.channelCode().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "channelCode required");
    }
    String channelCode = req.channelCode().trim().toUpperCase();
    MarketplaceChannel channel =
        channelRepository
            .findByTenantIdAndChannelCodeAndDeletedAtIsNull(tenantId, channelCode)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Channel not configured: " + channelCode));
    if (!channel.isEnabled()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Channel is disabled: " + channelCode);
    }

    var existing =
        orderRepository.findByChannelIdAndExternalOrderId(channel.getId(), req.externalOrderId().trim());
    if (existing.isPresent()) {
      MarketplaceOrder order = existing.get();
      List<MarketplaceOrderItem> items =
          orderItemRepository.findByOrderIdOrderByIdAsc(order.getId());
      refreshMappedProducts(channel.getId(), items);
      if (order.getErpOrderId() == null && hasUnmapped(items)) {
        markErpError(order, UNMAPPED_LINE);
        order = orderRepository.save(order);
      } else if (shouldBridge(channel) && order.getErpOrderId() == null) {
        if (order.getStockReservationKey() != null && !SellableQty.isFefoKey(order.getStockReservationKey())) {
          markErpError(
              order,
              "Stock was already reserved for this marketplace order, so a second bill was not created.");
        } else {
          linkRetailOrder(order, channel, items);
        }
        order = orderRepository.save(order);
      }
      Map<String, Object> out = toOrderMap(order);
      out.put("idempotent", true);
      out.put(
          "items",
          orderItemRepository.findByOrderIdOrderByIdAsc(order.getId()).stream()
              .map(this::toItemMap)
              .collect(Collectors.toList()));
      return out;
    }

    if (req.items() == null || req.items().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "items required");
    }

    MarketplaceOrder order = new MarketplaceOrder();
    order.setTenantId(tenantId);
    order.setShopId(shopId);
    order.setChannelId(channel.getId());
    order.setExternalOrderId(req.externalOrderId().trim());
    order.setCurrency(req.currency() == null || req.currency().isBlank() ? "INR" : req.currency());
    order.setTotalAmount(req.totalAmount());
    order.setOrderedAt(Instant.now());
    order.setStatus("NEW");
    if (req.payload() != null) {
      order.setPayloadJson(new LinkedHashMap<>(req.payload()));
    }
    order = orderRepository.save(order);

    List<MarketplaceOrderItem> savedItems = new ArrayList<>();
    Map<Long, Integer> reserveQty = new HashMap<>();
    for (OrderDtos.Line line : req.items()) {
      ResolvedLine resolved = resolveLine(channel.getId(), line);
      MarketplaceOrderItem item = new MarketplaceOrderItem();
      item.setTenantId(tenantId);
      item.setOrderId(order.getId());
      item.setMappingId(resolved.mappingId());
      item.setProductId(resolved.productId());
      item.setChannelSku(channelSku(line));
      item.setTitle(line.title());
      item.setQuantity(resolved.quantity());
      item.setUnitPrice(line.unitPrice());
      savedItems.add(orderItemRepository.save(item));
      if (resolved.productId() != null) {
        int qty = resolved.quantity().setScale(0, RoundingMode.UP).intValueExact();
        reserveQty.merge(resolved.productId(), qty, Integer::sum);
      }
    }

    if (hasUnmapped(savedItems)) {
      markErpError(order, UNMAPPED_LINE);
    } else if (shouldBridge(channel)) {
      linkRetailOrder(order, channel, savedItems);
    } else if (shouldReserve(req, reserveQty)) {
      String reservationKey =
          "MP-" + tenantId + "-" + channelCode + "-" + order.getExternalOrderId();
      try {
        stockAvailabilityClient.reserveBatch(tenantId, shopId, reserveQty);
        order.setStockReservationKey(reservationKey);
        order.setStatus("RESERVED");
      } catch (Exception ex) {
        order.setStatus("ERROR");
        Map<String, Object> payload = new LinkedHashMap<>(order.getPayloadJson());
        payload.put("stockError", ex.getMessage());
        order.setPayloadJson(payload);
        orderRepository.save(order);
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Stock reserve failed: " + ex.getMessage());
      }
    } else {
      order.setStatus("ACCEPTED");
    }
    order = orderRepository.save(order);

    Map<String, Object> out = toOrderMap(order);
    out.put("idempotent", false);
    out.put("items", savedItems.stream().map(this::toItemMap).collect(Collectors.toList()));
    out.put("reservedProducts", reserveQty);
    return out;
  }

  private boolean shouldBridge(MarketplaceChannel channel) {
    return properties.getOrders().isBridgeEnabled()
        && ErpOrderRequest.orderSyncEnabled(channel.getConfigJson());
  }

  private boolean shouldReserve(OrderDtos.IngestRequest req, Map<Long, Integer> reserveQty) {
    boolean doReserve =
        req.reserveStock() == null
            ? properties.getStock().isReserveOnIngest()
            : Boolean.TRUE.equals(req.reserveStock());
    return doReserve && reserveQty != null && !reserveQty.isEmpty() && properties.getStock().isEnabled();
  }

  /**
   * One unpaid retail bill. Stock is held with FEFO and committed only when the order ships.
   * order-service is told not to use the POS reserve path.
   */
  private void linkRetailOrder(
      MarketplaceOrder order, MarketplaceChannel channel, List<MarketplaceOrderItem> items) {
    if (order.getErpOrderId() != null) {
      return;
    }
    Long customerId = ErpOrderRequest.customerId(configValue(channel, "defaultCustomerId"));
    if (customerId == null) {
      markErpError(
          order, "Set a default customer id on the sales channel before order sync can create a bill.");
      return;
    }
    for (MarketplaceOrderItem item : items) {
      if (item.getProductId() == null) {
        markErpError(order, UNMAPPED_LINE);
        return;
      }
    }
    if (!holdFefo(order, channel, items)) {
      return;
    }
    try {
      Long erpOrderId =
          orderErpClient.createRetailOrder(
              order.getTenantId(),
              order.getShopId(),
              channel.getId(),
              channel.getChannelCode(),
              order.getExternalOrderId(),
              customerId,
              items);
      order.setErpOrderId(erpOrderId);
      order.setStatus("RESERVED");
      clearErpError(order);
    } catch (RuntimeException ex) {
      String message = ex.getMessage() == null ? "Sales bill was not created" : ex.getMessage();
      markErpError(order, message);
    }
  }

  private boolean holdFefo(
      MarketplaceOrder order, MarketplaceChannel channel, List<MarketplaceOrderItem> items) {
    if (SellableQty.isFefoKey(order.getStockReservationKey())) {
      return true;
    }
    if (order.getStockReservationKey() != null) {
      markErpError(
          order, "Stock was already reserved for this marketplace order, so a second bill was not created.");
      return false;
    }
    if (!properties.getStock().isEnabled()) {
      markErpError(order, "Stock service is off, so no bill was created.");
      return false;
    }
    String key = SellableQty.reservationKey(channel.getId(), order.getExternalOrderId());
    try {
      stockAvailabilityClient.reserveFefo(order.getTenantId(), order.getShopId(), key, quantities(items));
      order.setStockReservationKey(key);
      order.setStatus("RESERVED");
      return true;
    } catch (RuntimeException ex) {
      String message = ex.getMessage() == null ? "FEFO reserve failed" : ex.getMessage();
      if (message.length() > 180) {
        message = message.substring(0, 180);
      }
      markErpError(order, message);
      return false;
    }
  }

  private static Map<Long, Integer> quantities(List<MarketplaceOrderItem> items) {
    Map<Long, Integer> qty = new HashMap<>();
    for (MarketplaceOrderItem item : items) {
      if (item.getProductId() == null || item.getQuantity() == null) {
        continue;
      }
      int units = item.getQuantity().setScale(0, RoundingMode.UP).intValueExact();
      qty.merge(item.getProductId(), units, Integer::sum);
    }
    return qty;
  }

  private static Object configValue(MarketplaceChannel channel, String key) {
    if (channel.getConfigJson() == null) {
      return null;
    }
    return channel.getConfigJson().get(key);
  }

  private static void markErpError(MarketplaceOrder order, String message) {
    order.setStatus("ERROR");
    Map<String, Object> payload =
        order.getPayloadJson() == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(order.getPayloadJson());
    payload.put("erpError", message);
    order.setPayloadJson(payload);
  }

  private static void clearErpError(MarketplaceOrder order) {
    if (order.getPayloadJson() == null || !order.getPayloadJson().containsKey("erpError")) {
      return;
    }
    Map<String, Object> payload = new LinkedHashMap<>(order.getPayloadJson());
    payload.remove("erpError");
    order.setPayloadJson(payload);
  }

  @Transactional
  public Map<String, Object> cancel(Long id) {
    String tenantId = TenantIds.require();
    String shopId = requireShopId();
    MarketplaceOrder order =
        orderRepository
            .findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    if ("CANCELLED".equals(order.getStatus()) || "FULFILLED".equals(order.getStatus())) {
      return toOrderMap(order);
    }
    if (SellableQty.isFefoKey(order.getStockReservationKey()) && properties.getStock().isEnabled()) {
      stockAvailabilityClient.releaseFefo(tenantId, shopId, order.getStockReservationKey());
    } else if ("RESERVED".equals(order.getStatus())
        && order.getStockReservationKey() != null
        && properties.getStock().isEnabled()) {
      Map<Long, Integer> releaseQty = new HashMap<>();
      for (MarketplaceOrderItem item : orderItemRepository.findByOrderIdOrderByIdAsc(order.getId())) {
        if (item.getProductId() == null || item.getQuantity() == null) {
          continue;
        }
        int qty = item.getQuantity().setScale(0, RoundingMode.UP).intValueExact();
        releaseQty.merge(item.getProductId(), qty, Integer::sum);
      }
      if (!releaseQty.isEmpty()) {
        stockAvailabilityClient.releaseBatch(tenantId, shopId, releaseQty);
      }
    }
    order.setStatus("CANCELLED");
    return toOrderMap(orderRepository.save(order));
  }

  @Transactional
  public Map<String, Object> ship(Long id) {
    String tenantId = TenantIds.require();
    String shopId = requireShopId();
    MarketplaceOrder order =
        orderRepository
            .findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    if ("FULFILLED".equals(order.getStatus())) {
      return toOrderMap(order);
    }
    if (order.getErpOrderId() == null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Create the sales bill before shipping.");
    }
    if (!SellableQty.isFefoKey(order.getStockReservationKey())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "This order has no FEFO reservation to commit.");
    }
    if (!properties.getStock().isEnabled()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock service is off, so the reservation was not committed.");
    }
    stockAvailabilityClient.commitFefo(tenantId, shopId, order.getStockReservationKey());
    order.setStatus("FULFILLED");
    clearErpError(order);
    return toOrderMap(orderRepository.save(order));
  }

  @Transactional
  public Map<String, Object> cancelByExternalOrderId(String channelCode, String externalOrderId) {
    String tenantId = TenantIds.require();
    MarketplaceChannel channel =
        channelRepository
            .findByTenantIdAndChannelCodeAndDeletedAtIsNull(tenantId, channelCode.toUpperCase())
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Channel not configured: " + channelCode));
    MarketplaceOrder order =
        orderRepository
            .findByChannelIdAndExternalOrderId(channel.getId(), externalOrderId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order not found: " + externalOrderId));
    return cancel(order.getId());
  }

  @Transactional
  public Map<String, Object> applyMappings(Long id) {
    String tenantId = TenantIds.require();
    MarketplaceOrder order =
        orderRepository
            .findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    MarketplaceChannel channel =
        channelRepository
            .findByIdAndTenantIdAndDeletedAtIsNull(order.getChannelId(), tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found"));
    List<MarketplaceOrderItem> items = orderItemRepository.findByOrderIdOrderByIdAsc(order.getId());
    refreshMappedProducts(channel.getId(), items);
    if (order.getErpOrderId() == null && hasUnmapped(items)) {
      markErpError(order, UNMAPPED_LINE);
    } else if (shouldBridge(channel) && order.getErpOrderId() == null) {
      if (order.getStockReservationKey() != null && !SellableQty.isFefoKey(order.getStockReservationKey())) {
        markErpError(
            order,
            "Stock was already reserved for this marketplace order, so a second bill was not created.");
      } else {
        linkRetailOrder(order, channel, items);
      }
    }
    return toOrderMap(orderRepository.save(order));
  }

  private void refreshMappedProducts(Long channelId, List<MarketplaceOrderItem> items) {
    for (MarketplaceOrderItem item : items) {
      if (item.getProductId() != null || item.getChannelSku() == null || item.getChannelSku().isBlank()) {
        continue;
      }
      String sku = item.getChannelSku().trim();
      var mapping = mappingRepository.findByChannelIdAndChannelSkuAndDeletedAtIsNull(channelId, sku);
      if (mapping.isEmpty()) {
        mapping = mappingRepository.findByChannelIdAndChannelListingIdAndDeletedAtIsNull(channelId, sku);
      }
      if (mapping.isEmpty()) {
        continue;
      }
      item.setMappingId(mapping.get().getId());
      item.setProductId(mapping.get().getProductId());
      orderItemRepository.save(item);
    }
  }

  private static boolean hasUnmapped(List<MarketplaceOrderItem> items) {
    for (MarketplaceOrderItem item : items) {
      if (item.getProductId() == null) {
        return true;
      }
    }
    return false;
  }

  private static String channelSku(OrderDtos.Line line) {
    if (line.channelSku() != null && !line.channelSku().isBlank()) {
      return line.channelSku().trim();
    }
    if (line.channelListingId() != null && !line.channelListingId().isBlank()) {
      return line.channelListingId().trim();
    }
    return null;
  }

  private ResolvedLine resolveLine(Long channelId, OrderDtos.Line line) {
    if (line == null || line.quantity() == null || line.quantity().signum() <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Line quantity must be > 0");
    }
    Long productId = line.productId();
    Long mappingId = null;
    if (line.channelListingId() != null && !line.channelListingId().isBlank()) {
      var mapping =
          mappingRepository.findByChannelIdAndChannelListingIdAndDeletedAtIsNull(
              channelId, line.channelListingId().trim());
      if (mapping.isPresent()) {
        mappingId = mapping.get().getId();
        if (productId == null) {
          productId = mapping.get().getProductId();
        }
      }
    }
    if (productId == null && line.channelSku() != null && !line.channelSku().isBlank()) {
      var mapping =
          mappingRepository.findByChannelIdAndChannelSkuAndDeletedAtIsNull(
              channelId, line.channelSku().trim());
      if (mapping.isPresent()) {
        mappingId = mapping.get().getId();
        productId = mapping.get().getProductId();
      }
    }
    return new ResolvedLine(mappingId, productId, line.quantity());
  }

  private static String requireShopId() {
    String shopId = TenantIds.shopOrNull();
    if (shopId == null || shopId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Shop-Id header required");
    }
    return shopId;
  }

  private Map<String, Object> toOrderMap(MarketplaceOrder o) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", o.getId());
    m.put("tenantId", o.getTenantId());
    m.put("shopId", o.getShopId());
    m.put("channelId", o.getChannelId());
    m.put("externalOrderId", o.getExternalOrderId());
    m.put("status", o.getStatus());
    m.put("currency", o.getCurrency());
    m.put("totalAmount", o.getTotalAmount());
    m.put("stockReservationKey", o.getStockReservationKey());
    m.put("erpOrderId", o.getErpOrderId());
    if (o.getPayloadJson() != null && o.getPayloadJson().get("erpError") != null) {
      m.put("erpError", String.valueOf(o.getPayloadJson().get("erpError")));
    }
    m.put("orderedAt", o.getOrderedAt());
    return m;
  }

  private Map<String, Object> toItemMap(MarketplaceOrderItem i) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", i.getId());
    m.put("mappingId", i.getMappingId());
    m.put("productId", i.getProductId());
    m.put("channelSku", i.getChannelSku());
    m.put("title", i.getTitle());
    m.put("quantity", i.getQuantity());
    m.put("unitPrice", i.getUnitPrice());
    return m;
  }

  private record ResolvedLine(Long mappingId, Long productId, BigDecimal quantity) {}
}
