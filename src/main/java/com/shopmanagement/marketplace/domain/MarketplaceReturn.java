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
@Table(name = "marketplace_return")
public class MarketplaceReturn {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  @Column(name = "order_id", nullable = false)
  private Long orderId;

  @Column(name = "external_return_id", length = 128)
  private String externalReturnId;

  @Column(nullable = false, length = 32)
  private String status = "OPEN";

  @Column(columnDefinition = "text")
  private String reason;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> payloadJson = new LinkedHashMap<>();

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

  public Long getId() { return id; }
  public String getTenantId() { return tenantId; }
  public void setTenantId(String tenantId) { this.tenantId = tenantId; }
  public Long getOrderId() { return orderId; }
  public void setOrderId(Long orderId) { this.orderId = orderId; }
  public String getExternalReturnId() { return externalReturnId; }
  public void setExternalReturnId(String externalReturnId) { this.externalReturnId = externalReturnId; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }
  public Map<String, Object> getPayloadJson() { return payloadJson; }
  public void setPayloadJson(Map<String, Object> payloadJson) { this.payloadJson = payloadJson; }
}
