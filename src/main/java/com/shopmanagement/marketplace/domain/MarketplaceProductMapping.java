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
@Table(name = "marketplace_product_mapping")
public class MarketplaceProductMapping {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  @Column(name = "shop_id", nullable = false, length = 64)
  private String shopId;

  @Column(name = "channel_id", nullable = false)
  private Long channelId;

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(name = "sku_code", length = 128)
  private String skuCode;

  @Column(name = "channel_listing_id", nullable = false, length = 128)
  private String channelListingId;

  @Column(name = "channel_sku", length = 128)
  private String channelSku;

  @Column(name = "sync_inventory", nullable = false)
  private boolean syncInventory = true;

  @Column(name = "allocation_qty", precision = 18, scale = 3)
  private BigDecimal allocationQty;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> attributes = new LinkedHashMap<>();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
    if (attributes == null) {
      attributes = new LinkedHashMap<>();
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

  public Long getProductId() {
    return productId;
  }

  public void setProductId(Long productId) {
    this.productId = productId;
  }

  public String getSkuCode() {
    return skuCode;
  }

  public void setSkuCode(String skuCode) {
    this.skuCode = skuCode;
  }

  public String getChannelListingId() {
    return channelListingId;
  }

  public void setChannelListingId(String channelListingId) {
    this.channelListingId = channelListingId;
  }

  public String getChannelSku() {
    return channelSku;
  }

  public void setChannelSku(String channelSku) {
    this.channelSku = channelSku;
  }

  public boolean isSyncInventory() {
    return syncInventory;
  }

  public void setSyncInventory(boolean syncInventory) {
    this.syncInventory = syncInventory;
  }

  public BigDecimal getAllocationQty() {
    return allocationQty;
  }

  public void setAllocationQty(BigDecimal allocationQty) {
    this.allocationQty = allocationQty;
  }

  public Map<String, Object> getAttributes() {
    return attributes;
  }

  public void setAttributes(Map<String, Object> attributes) {
    this.attributes = attributes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(Instant deletedAt) {
    this.deletedAt = deletedAt;
  }
}
