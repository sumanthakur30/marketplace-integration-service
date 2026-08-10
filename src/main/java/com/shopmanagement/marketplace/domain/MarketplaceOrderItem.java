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
import jakarta.persistence.Table;

@Entity
@Table(name = "marketplace_order_item")
public class MarketplaceOrderItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  @Column(name = "order_id", nullable = false)
  private Long orderId;

  @Column(name = "mapping_id")
  private Long mappingId;

  @Column(name = "product_id")
  private Long productId;

  @Column(name = "channel_sku", length = 128)
  private String channelSku;

  @Column(length = 512)
  private String title;

  @Column(nullable = false, precision = 18, scale = 3)
  private BigDecimal quantity;

  @Column(name = "unit_price", precision = 18, scale = 2)
  private BigDecimal unitPrice;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "line_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> lineJson = new LinkedHashMap<>();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    createdAt = Instant.now();
    if (lineJson == null) {
      lineJson = new LinkedHashMap<>();
    }
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

  public Long getOrderId() {
    return orderId;
  }

  public void setOrderId(Long orderId) {
    this.orderId = orderId;
  }

  public Long getMappingId() {
    return mappingId;
  }

  public void setMappingId(Long mappingId) {
    this.mappingId = mappingId;
  }

  public Long getProductId() {
    return productId;
  }

  public void setProductId(Long productId) {
    this.productId = productId;
  }

  public String getChannelSku() {
    return channelSku;
  }

  public void setChannelSku(String channelSku) {
    this.channelSku = channelSku;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }

  public void setQuantity(BigDecimal quantity) {
    this.quantity = quantity;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public void setUnitPrice(BigDecimal unitPrice) {
    this.unitPrice = unitPrice;
  }

  public Map<String, Object> getLineJson() {
    return lineJson;
  }

  public void setLineJson(Map<String, Object> lineJson) {
    this.lineJson = lineJson;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
