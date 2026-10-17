package com.shopmanagement.marketplace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shopmanagement.marketplace.config.MarketplaceProperties;
import com.shopmanagement.marketplace.domain.MarketplaceChannel;
import com.shopmanagement.marketplace.domain.MarketplaceOrder;
import com.shopmanagement.marketplace.domain.MarketplaceOrderItem;
import com.shopmanagement.marketplace.domain.MarketplaceProductMapping;
import com.shopmanagement.marketplace.filter.TenantContextFilter;
import com.shopmanagement.marketplace.order.OrderErpClient;
import com.shopmanagement.marketplace.repo.MarketplaceChannelRepository;
import com.shopmanagement.marketplace.repo.MarketplaceOrderItemRepository;
import com.shopmanagement.marketplace.repo.MarketplaceOrderRepository;
import com.shopmanagement.marketplace.repo.MarketplaceProductMappingRepository;
import com.shopmanagement.marketplace.stock.StockAvailabilityClient;
import com.shopmanagement.marketplace.web.dto.OrderDtos;

@ExtendWith(MockitoExtension.class)
class MarketplaceOrderServiceTest {

  @Mock private MarketplaceChannelRepository channelRepository;
  @Mock private MarketplaceProductMappingRepository mappingRepository;
  @Mock private MarketplaceOrderRepository orderRepository;
  @Mock private MarketplaceOrderItemRepository orderItemRepository;
  @Mock private StockAvailabilityClient stockAvailabilityClient;
  @Mock private OrderErpClient orderErpClient;

  private MarketplaceOrderService service;
  private MarketplaceChannel channel;
  private final List<MarketplaceOrder> stored = new ArrayList<>();

  @BeforeEach
  void setUp() {
    MarketplaceProperties properties = new MarketplaceProperties();
    properties.getStock().setEnabled(true);
    properties.getOrders().setBridgeEnabled(false);
    service =
        new MarketplaceOrderService(
            channelRepository,
            mappingRepository,
            orderRepository,
            orderItemRepository,
            stockAvailabilityClient,
            orderErpClient,
            properties);
    channel = new MarketplaceChannel();
    channel.setId(4L);
    channel.setChannelCode("AMAZON");
    channel.setEnabled(true);
    channel.setConfigJson(Map.of());
    lenient()
        .when(channelRepository.findByTenantIdAndChannelCodeAndDeletedAtIsNull("tenant-a", "AMAZON"))
        .thenReturn(Optional.of(channel));
    lenient().when(orderRepository.save(any())).thenAnswer(invocation -> {
      MarketplaceOrder order = invocation.getArgument(0);
      if (order.getId() == null) {
        order.setId(20L);
        stored.clear();
        stored.add(order);
      }
      return order;
    });
    when(orderItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    lenient().when(orderItemRepository.findByOrderIdOrderByIdAsc(20L)).thenAnswer(invocation -> {
      if (stored.isEmpty() || stored.get(0).getId() == null) {
        return List.of();
      }
      MarketplaceOrderItem item = new MarketplaceOrderItem();
      item.setProductId(77L);
      item.setChannelSku("EXO006.1");
      item.setQuantity(BigDecimal.ONE);
      return List.of(item);
    });
    lenient()
        .when(mappingRepository.findByChannelIdAndChannelListingIdAndDeletedAtIsNull(any(), any()))
        .thenReturn(Optional.empty());
    lenient()
        .when(mappingRepository.findByChannelIdAndChannelSkuAndDeletedAtIsNull(any(), any()))
        .thenReturn(Optional.empty());
  }

  @Test
  void amazonSaleForTheCurrentShopReservesOnceAndARepeatDoesNot() {
    mapped("EXO006.1", 77L);
    when(orderRepository.findByChannelIdAndExternalOrderId(4L, "ORDER-1"))
        .thenReturn(Optional.empty())
        .thenAnswer(invocation -> Optional.of(stored.get(0)));

    TenantContextFilter.runWithTenant("tenant-a", "ART-01", () -> service.ingest(sale("Unshipped")));
    TenantContextFilter.runWithTenant("tenant-a", "ART-01", () -> service.ingest(sale("Unshipped")));

    verify(stockAvailabilityClient, times(1)).reserveFefo(eq("tenant-a"), eq("ART-01"), eq("fefo-4-ORDER-1"), eq(Map.of(77L, 1)));
    verify(stockAvailabilityClient, never()).commitFefo(any(), any(), any());
    assertEquals("ART-01", stored.get(0).getShopId());
    assertEquals("RESERVED", stored.get(0).getStatus());
  }

  @Test
  void unmappedSkuDoesNotReserveStock() {
    when(mappingRepository.findByChannelIdAndChannelSkuAndDeletedAtIsNull(4L, "UNKNOWN")).thenReturn(Optional.empty());
    when(orderRepository.findByChannelIdAndExternalOrderId(4L, "ORDER-1")).thenReturn(Optional.empty());

    TenantContextFilter.runWithTenant("tenant-a", "ART-01", () -> service.ingest(sale("Unshipped", "UNKNOWN")));

    verify(stockAvailabilityClient, never()).reserveFefo(any(), any(), any(), any());
    assertEquals("ERROR", stored.get(0).getStatus());
  }

  @Test
  void anotherShopReservesItsOwnStock() {
    mapped("EXO006.1", 77L);
    when(channelRepository.findByTenantIdAndChannelCodeAndDeletedAtIsNull("tenant-b", "AMAZON"))
        .thenReturn(Optional.of(channel));
    when(orderRepository.findByChannelIdAndExternalOrderId(4L, "ORDER-2")).thenReturn(Optional.empty());

    TenantContextFilter.runWithTenant("tenant-b", "OTHER-01", () -> service.ingest(sale("Unshipped", "EXO006.1", "ORDER-2")));

    verify(stockAvailabilityClient).reserveFefo(eq("tenant-b"), eq("OTHER-01"), any(), eq(Map.of(77L, 1)));
    verify(stockAvailabilityClient, never()).reserveFefo(eq("tenant-a"), eq("ART-01"), any(), any());
  }

  @Test
  void cancelBeforeAHoldDoesNotReleaseStock() {
    when(orderRepository.findByChannelIdAndExternalOrderId(4L, "ORDER-1")).thenReturn(Optional.empty());

    TenantContextFilter.runWithTenant("tenant-a", "ART-01", () -> service.ingest(sale("Canceled", "UNKNOWN")));

    verify(stockAvailabilityClient, never()).releaseFefo(any(), any(), any());
  }

  @Test
  void cancelAfterAHoldReleasesOnce() {
    mapped("EXO006.1", 77L);
    when(orderRepository.findByChannelIdAndExternalOrderId(4L, "ORDER-1"))
        .thenReturn(Optional.empty())
        .thenAnswer(invocation -> Optional.of(stored.get(0)));

    TenantContextFilter.runWithTenant("tenant-a", "ART-01", () -> service.ingest(sale("Unshipped")));
    TenantContextFilter.runWithTenant("tenant-a", "ART-01", () -> service.ingest(sale("Canceled")));
    TenantContextFilter.runWithTenant("tenant-a", "ART-01", () -> service.ingest(sale("Canceled")));

    verify(stockAvailabilityClient, times(1)).releaseFefo("tenant-a", "ART-01", "fefo-4-ORDER-1");
    assertEquals("CANCELLED", stored.get(0).getStatus());
  }

  @Test
  void shippedOrderCommitsTheHoldOnce() {
    mapped("EXO006.1", 77L);
    when(orderRepository.findByChannelIdAndExternalOrderId(4L, "ORDER-1"))
        .thenReturn(Optional.empty())
        .thenAnswer(invocation -> Optional.of(stored.get(0)));

    TenantContextFilter.runWithTenant("tenant-a", "ART-01", () -> service.ingest(sale("Shipped")));
    TenantContextFilter.runWithTenant("tenant-a", "ART-01", () -> service.ingest(sale("Shipped")));

    verify(stockAvailabilityClient, times(1)).reserveFefo(eq("tenant-a"), eq("ART-01"), eq("fefo-4-ORDER-1"), eq(Map.of(77L, 1)));
    verify(stockAvailabilityClient, times(1)).commitFefo("tenant-a", "ART-01", "fefo-4-ORDER-1");
    assertEquals("FULFILLED", stored.get(0).getStatus());
  }

  private void mapped(String sku, long productId) {
    MarketplaceProductMapping mapping = new MarketplaceProductMapping();
    mapping.setId(9L);
    mapping.setProductId(productId);
    when(mappingRepository.findByChannelIdAndChannelSkuAndDeletedAtIsNull(4L, sku)).thenReturn(Optional.of(mapping));
  }

  private static OrderDtos.IngestRequest sale(String status) {
    return sale(status, "EXO006.1", "ORDER-1");
  }

  private static OrderDtos.IngestRequest sale(String status, String sku) {
    return sale(status, sku, "ORDER-1");
  }

  private static OrderDtos.IngestRequest sale(String status, String sku, String orderId) {
    return new OrderDtos.IngestRequest(
        "AMAZON",
        orderId,
        "INR",
        BigDecimal.TEN,
        null,
        List.of(new OrderDtos.Line(sku, sku, null, "Pillow", BigDecimal.ONE, BigDecimal.TEN)),
        Map.of("externalStatus", status));
  }
}
