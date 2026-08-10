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
@Table(name = "marketplace_webhook_event")
public class MarketplaceWebhookEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", length = 64)
  private String tenantId;

  @Column(name = "channel_code", nullable = false, length = 32)
  private String channelCode;

  @Column(name = "event_type", nullable = false, length = 64)
  private String eventType;

  @Column(name = "external_id", length = 128)
  private String externalId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> payloadJson = new LinkedHashMap<>();

  @Column(nullable = false)
  private boolean processed;

  @Column(name = "process_error")
  private String processError;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  @Column(name = "processed_at")
  private Instant processedAt;

  @PrePersist
  void onCreate() {
    if (receivedAt == null) {
      receivedAt = Instant.now();
    }
    if (payloadJson == null) {
      payloadJson = new LinkedHashMap<>();
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

  public String getChannelCode() {
    return channelCode;
  }

  public void setChannelCode(String channelCode) {
    this.channelCode = channelCode;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getExternalId() {
    return externalId;
  }

  public void setExternalId(String externalId) {
    this.externalId = externalId;
  }

  public Map<String, Object> getPayloadJson() {
    return payloadJson;
  }

  public void setPayloadJson(Map<String, Object> payloadJson) {
    this.payloadJson = payloadJson;
  }

  public boolean isProcessed() {
    return processed;
  }

  public void setProcessed(boolean processed) {
    this.processed = processed;
  }

  public String getProcessError() {
    return processError;
  }

  public void setProcessError(String processError) {
    this.processError = processError;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }

  public void setReceivedAt(Instant receivedAt) {
    this.receivedAt = receivedAt;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }

  public void setProcessedAt(Instant processedAt) {
    this.processedAt = processedAt;
  }
}
