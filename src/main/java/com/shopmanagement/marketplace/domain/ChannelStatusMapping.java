package com.shopmanagement.marketplace.domain;

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
@Table(name = "channel_status_mapping")
public class ChannelStatusMapping {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  @Column(name = "channel_id", nullable = false)
  private Long channelId;

  @Column(name = "external_status", nullable = false, length = 64)
  private String externalStatus;

  @Column(name = "internal_status", nullable = false, length = 32)
  private String internalStatus;

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

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public Long getChannelId() {
    return channelId;
  }

  public void setChannelId(Long channelId) {
    this.channelId = channelId;
  }

  public String getExternalStatus() {
    return externalStatus;
  }

  public void setExternalStatus(String externalStatus) {
    this.externalStatus = externalStatus;
  }

  public String getInternalStatus() {
    return internalStatus;
  }

  public void setInternalStatus(String internalStatus) {
    this.internalStatus = internalStatus;
  }
}
