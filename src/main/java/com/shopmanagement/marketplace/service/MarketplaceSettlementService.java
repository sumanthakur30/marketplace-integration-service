package com.shopmanagement.marketplace.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.marketplace.domain.MarketplaceChannel;
import com.shopmanagement.marketplace.domain.MarketplaceSettlement;
import com.shopmanagement.marketplace.repo.MarketplaceChannelRepository;
import com.shopmanagement.marketplace.repo.MarketplaceSettlementRepository;
import com.shopmanagement.marketplace.settlement.SettlementAmounts;
import com.shopmanagement.marketplace.support.TenantIds;
import com.shopmanagement.marketplace.web.dto.LaterDtos;

/** Records marketplace fees and the bank net. Customer bills stay unpaid. */
@Service
public class MarketplaceSettlementService {

  private final MarketplaceChannelRepository channelRepository;
  private final MarketplaceSettlementRepository settlementRepository;

  public MarketplaceSettlementService(
      MarketplaceChannelRepository channelRepository, MarketplaceSettlementRepository settlementRepository) {
    this.channelRepository = channelRepository;
    this.settlementRepository = settlementRepository;
  }

  @Transactional
  public Map<String, Object> record(Long channelId, LaterDtos.SettlementRequest body) {
    String tenantId = TenantIds.require();
    MarketplaceChannel channel =
        channelRepository
            .findByIdAndTenantIdAndDeletedAtIsNull(channelId, tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found"));
    if (body == null || body.externalSettlementId() == null || body.externalSettlementId().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "external settlement id is required");
    }
    String external = body.externalSettlementId().trim();
    if (external.length() > 128) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "external settlement id is too long");
    }
    var existing = settlementRepository.findByChannelIdAndExternalSettlementId(channel.getId(), external);
    if (existing.isPresent()) {
      return toMap(existing.get(), true);
    }
    SettlementAmounts.Split split;
    try {
      split = SettlementAmounts.of(body.grossAmount(), body.feeAmount(), body.netAmount());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
    MarketplaceSettlement row = new MarketplaceSettlement();
    row.setTenantId(tenantId);
    row.setChannelId(channel.getId());
    row.setExternalSettlementId(external);
    row.setPeriodStart(date(body.periodStart(), "period start"));
    row.setPeriodEnd(date(body.periodEnd(), "period end"));
    row.setAmount(split.net());
    String currency = body.currency() == null || body.currency().isBlank() ? "INR" : body.currency().trim();
    if (currency.length() > 8) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currency is too long");
    }
    row.setCurrency(currency);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("grossAmount", split.gross());
    payload.put("feeAmount", split.fee());
    payload.put("netAmount", split.net());
    payload.put("customerPaymentUnchanged", true);
    row.setPayloadJson(payload);
    return toMap(settlementRepository.save(row), false);
  }

  private static LocalDate date(String raw, String label) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(raw.trim());
    } catch (DateTimeParseException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " must be YYYY-MM-DD");
    }
  }

  private static Map<String, Object> toMap(MarketplaceSettlement row, boolean idempotent) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", row.getId());
    out.put("channelId", row.getChannelId());
    out.put("externalSettlementId", row.getExternalSettlementId());
    out.put("netAmount", row.getAmount());
    out.put("currency", row.getCurrency());
    out.put("idempotent", idempotent);
    out.put("customerPaymentUnchanged", true);
    return out;
  }
}
