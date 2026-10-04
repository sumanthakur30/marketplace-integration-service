package com.shopmanagement.marketplace.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
@Table(name = "marketplace_settlement")
public class MarketplaceSettlement {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false, length = 64)
  private String tenantId;

  @Column(name = "channel_id", nullable = false)
  private Long channelId;

  @Column(name = "external_settlement_id", length = 128)
  private String externalSettlementId;

  @Column(name = "period_start")
  private LocalDate periodStart;

  @Column(name = "period_end")
  private LocalDate periodEnd;

  @Column(precision = 18, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 8)
  private String currency = "INR";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> payloadJson = new LinkedHashMap<>();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    createdAt = Instant.now();
    if (payloadJson == null) {
      payloadJson = new LinkedHashMap<>();
    }
  }

  public Long getId() { return id; }
  public String getTenantId() { return tenantId; }
  public void setTenantId(String tenantId) { this.tenantId = tenantId; }
  public Long getChannelId() { return channelId; }
  public void setChannelId(Long channelId) { this.channelId = channelId; }
  public String getExternalSettlementId() { return externalSettlementId; }
  public void setExternalSettlementId(String externalSettlementId) { this.externalSettlementId = externalSettlementId; }
  public LocalDate getPeriodStart() { return periodStart; }
  public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }
  public LocalDate getPeriodEnd() { return periodEnd; }
  public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }
  public BigDecimal getAmount() { return amount; }
  public void setAmount(BigDecimal amount) { this.amount = amount; }
  public String getCurrency() { return currency; }
  public void setCurrency(String currency) { this.currency = currency; }
  public Map<String, Object> getPayloadJson() { return payloadJson; }
  public void setPayloadJson(Map<String, Object> payloadJson) { this.payloadJson = payloadJson; }
}
