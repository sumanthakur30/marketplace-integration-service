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
import jakarta.persistence.Table;

@Entity
@Table(name = "marketplace_channel_audit")
public class MarketplaceChannelAudit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  @Column(name = "shop_id", length = 64)
  private String shopId;

  @Column(name = "channel_id", nullable = false)
  private Long channelId;

  @Column(nullable = false, length = 32)
  private String action;

  @Column(length = 128)
  private String actor;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "detail_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> detailJson = new LinkedHashMap<>();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    createdAt = Instant.now();
    if (detailJson == null) {
      detailJson = new LinkedHashMap<>();
    }
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public void setShopId(String shopId) {
    this.shopId = shopId;
  }

  public void setChannelId(Long channelId) {
    this.channelId = channelId;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public void setActor(String actor) {
    this.actor = actor;
  }

  public void setDetailJson(Map<String, Object> detailJson) {
    this.detailJson = detailJson;
  }
}
