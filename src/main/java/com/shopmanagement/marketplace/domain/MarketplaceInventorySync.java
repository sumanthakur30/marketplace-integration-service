package com.shopmanagement.marketplace.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "marketplace_inventory_sync")
public class MarketplaceInventorySync {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  @Column(name = "mapping_id", nullable = false)
  private Long mappingId;

  @Column(name = "physical_qty", nullable = false, precision = 18, scale = 3)
  private BigDecimal physicalQty = BigDecimal.ZERO;

  @Column(name = "reserved_qty", nullable = false, precision = 18, scale = 3)
  private BigDecimal reservedQty = BigDecimal.ZERO;

  @Column(name = "available_qty", nullable = false, precision = 18, scale = 3)
  private BigDecimal availableQty = BigDecimal.ZERO;

  @Column(name = "allocated_qty", nullable = false, precision = 18, scale = 3)
  private BigDecimal allocatedQty = BigDecimal.ZERO;

  @Column(name = "channel_qty", precision = 18, scale = 3)
  private BigDecimal channelQty;

  @Column(name = "sync_status", nullable = false, length = 32)
  private String syncStatus = "PENDING";

  @Column(name = "last_error")
  private String lastError;

  @Column(name = "synced_at")
  private Instant syncedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
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

  public Long getMappingId() {
    return mappingId;
  }

  public void setMappingId(Long mappingId) {
    this.mappingId = mappingId;
  }

  public BigDecimal getPhysicalQty() {
    return physicalQty;
  }

  public void setPhysicalQty(BigDecimal physicalQty) {
    this.physicalQty = physicalQty;
  }

  public BigDecimal getReservedQty() {
    return reservedQty;
  }

  public void setReservedQty(BigDecimal reservedQty) {
    this.reservedQty = reservedQty;
  }

  public BigDecimal getAvailableQty() {
    return availableQty;
  }

  public void setAvailableQty(BigDecimal availableQty) {
    this.availableQty = availableQty;
  }

  public BigDecimal getAllocatedQty() {
    return allocatedQty;
  }

  public void setAllocatedQty(BigDecimal allocatedQty) {
    this.allocatedQty = allocatedQty;
  }

  public BigDecimal getChannelQty() {
    return channelQty;
  }

  public void setChannelQty(BigDecimal channelQty) {
    this.channelQty = channelQty;
  }

  public String getSyncStatus() {
    return syncStatus;
  }

  public void setSyncStatus(String syncStatus) {
    this.syncStatus = syncStatus;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String lastError) {
    this.lastError = lastError;
  }

  public Instant getSyncedAt() {
    return syncedAt;
  }

  public void setSyncedAt(Instant syncedAt) {
    this.syncedAt = syncedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
