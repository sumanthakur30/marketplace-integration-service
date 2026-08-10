package com.shopmanagement.marketplace.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "marketplace_order")
public class MarketplaceOrder {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  @Column(name = "shop_id", nullable = false, length = 64)
  private String shopId;

  @Column(name = "channel_id", nullable = false)
  private Long channelId;

  @Column(name = "external_order_id", nullable = false, length = 128)
  private String externalOrderId;

  @Column(nullable = false, length = 32)
  private String status = "NEW";

  @Column(nullable = false, length = 8)
  private String currency = "INR";

  @Column(name = "total_amount", precision = 18, scale = 2)
  private BigDecimal totalAmount;

  @Column(name = "stock_reservation_key", length = 128)
  private String stockReservationKey;

  @Column(name = "erp_order_id")
  private Long erpOrderId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> payloadJson = new LinkedHashMap<>();

  @Column(name = "ordered_at")
  private Instant orderedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
    if (payloadJson == null) {
      payloadJson = new LinkedHashMap<>();
    }
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public String getShopId() {
    return shopId;
  }

  public void setShopId(String shopId) {
    this.shopId = shopId;
  }

  public Long getChannelId() {
    return channelId;
  }

  public void setChannelId(Long channelId) {
    this.channelId = channelId;
  }

  public String getExternalOrderId() {
    return externalOrderId;
  }

  public void setExternalOrderId(String externalOrderId) {
    this.externalOrderId = externalOrderId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public void setTotalAmount(BigDecimal totalAmount) {
    this.totalAmount = totalAmount;
  }

  public String getStockReservationKey() {
    return stockReservationKey;
  }

  public void setStockReservationKey(String stockReservationKey) {
    this.stockReservationKey = stockReservationKey;
  }

  public Long getErpOrderId() {
    return erpOrderId;
  }

  public void setErpOrderId(Long erpOrderId) {
    this.erpOrderId = erpOrderId;
  }

  public Map<String, Object> getPayloadJson() {
    return payloadJson;
  }

  public void setPayloadJson(Map<String, Object> payloadJson) {
    this.payloadJson = payloadJson;
  }

  public Instant getOrderedAt() {
    return orderedAt;
  }

  public void setOrderedAt(Instant orderedAt) {
    this.orderedAt = orderedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
