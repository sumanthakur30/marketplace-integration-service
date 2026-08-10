package com.shopmanagement.marketplace.domain;

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
@Table(name = "marketplace_channel")
public class MarketplaceChannel {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "channel_code", nullable = false, length = 32)
  private String channelCode;

  @Column(nullable = false)
  private boolean enabled;

  @Column(name = "external_seller_id", length = 128)
  private String externalSellerId;

  @Column(name = "credentials_ref", length = 256)
  private String credentialsRef;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "config_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> configJson = new LinkedHashMap<>();

  @Column(name = "last_sync_at")
  private Instant lastSyncAt;

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
    if (configJson == null) {
      configJson = new LinkedHashMap<>();
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

  public Long getAccountId() {
    return accountId;
  }

  public void setAccountId(Long accountId) {
    this.accountId = accountId;
  }

  public String getChannelCode() {
    return channelCode;
  }

  public void setChannelCode(String channelCode) {
    this.channelCode = channelCode;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getExternalSellerId() {
    return externalSellerId;
  }

  public void setExternalSellerId(String externalSellerId) {
    this.externalSellerId = externalSellerId;
  }

  public String getCredentialsRef() {
    return credentialsRef;
  }

  public void setCredentialsRef(String credentialsRef) {
    this.credentialsRef = credentialsRef;
  }

  public Map<String, Object> getConfigJson() {
    return configJson;
  }

  public void setConfigJson(Map<String, Object> configJson) {
    this.configJson = configJson;
  }

  public Instant getLastSyncAt() {
    return lastSyncAt;
  }

  public void setLastSyncAt(Instant lastSyncAt) {
    this.lastSyncAt = lastSyncAt;
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
