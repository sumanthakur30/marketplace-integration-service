package com.shopmanagement.marketplace.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopmanagement.marketplace.config.MarketplaceProperties;
import com.shopmanagement.marketplace.domain.MarketplaceAccount;
import com.shopmanagement.marketplace.domain.MarketplaceChannel;
import com.shopmanagement.marketplace.domain.MarketplaceWebhookEvent;
import com.shopmanagement.marketplace.filter.TenantContextFilter;
import com.shopmanagement.marketplace.repo.MarketplaceAccountRepository;
import com.shopmanagement.marketplace.repo.MarketplaceChannelRepository;
import com.shopmanagement.marketplace.repo.MarketplaceWebhookEventRepository;
import com.shopmanagement.marketplace.webhook.ShopifyOrderWebhookMapper;
import com.shopmanagement.marketplace.webhook.WebhookHmacVerifier;
import com.shopmanagement.marketplace.web.dto.OrderDtos;

@Service
public class MarketplaceWebhookService {

  private static final Logger log = LoggerFactory.getLogger(MarketplaceWebhookService.class);

  private final MarketplaceWebhookEventRepository webhookEventRepository;
  private final MarketplaceChannelRepository channelRepository;
  private final MarketplaceAccountRepository accountRepository;
  private final MarketplaceOrderService orderService;
  private final WebhookHmacVerifier hmacVerifier;
  private final ShopifyOrderWebhookMapper shopifyOrderWebhookMapper;
  private final MarketplaceProperties properties;
  private final ObjectMapper objectMapper;

  public MarketplaceWebhookService(
      MarketplaceWebhookEventRepository webhookEventRepository,
      MarketplaceChannelRepository channelRepository,
      MarketplaceAccountRepository accountRepository,
      MarketplaceOrderService orderService,
      WebhookHmacVerifier hmacVerifier,
      ShopifyOrderWebhookMapper shopifyOrderWebhookMapper,
      MarketplaceProperties properties,
      ObjectMapper objectMapper) {
    this.webhookEventRepository = webhookEventRepository;
    this.channelRepository = channelRepository;
    this.accountRepository = accountRepository;
    this.orderService = orderService;
    this.hmacVerifier = hmacVerifier;
    this.shopifyOrderWebhookMapper = shopifyOrderWebhookMapper;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public Map<String, Object> accept(
      String channelRaw,
      byte[] rawBody,
      Map<String, String> headers,
      String tenantHint,
      String shopHint) {
    String channel = channelRaw == null ? "" : channelRaw.trim().toUpperCase(Locale.ROOT);
    if (channel.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "channel path required");
    }
    String signature = firstHeader(headers, "X-Shopify-Hmac-Sha256", "X-Marketplace-Signature", "X-Hub-Signature-256");
    hmacVerifier.verifyIfRequired(channel, rawBody, signature);

    Map<String, Object> payload = parsePayload(rawBody);
    String eventType = resolveEventType(channel, headers, payload);
    String externalId = resolveExternalId(channel, payload);

    MarketplaceChannel matched = resolveChannel(channel, headers, tenantHint, shopHint, payload);
    String tenantId = matched != null ? matched.getTenantId() : blankToNull(tenantHint);
    String shopId =
        matched != null
            ? resolveShopIdFromAccount(matched)
            : blankToNull(shopHint);

    MarketplaceWebhookEvent event = new MarketplaceWebhookEvent();
    event.setTenantId(tenantId);
    event.setChannelCode(channel);
    event.setEventType(eventType);
    event.setExternalId(externalId);
    event.setPayloadJson(enrichPayload(payload, headers, shopId));
    event.setProcessed(false);
    event = webhookEventRepository.save(event);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("accepted", true);
    result.put("webhookEventId", event.getId());
    result.put("channel", channel);
    result.put("eventType", eventType);
    result.put("externalId", externalId);
    result.put("tenantId", tenantId);
    result.put("processed", false);

    if (properties.getInbound().isAutoProcess() && matched != null && tenantId != null) {
      try {
        Map<String, Object> processed = processEvent(event.getId(), matched, shopId);
        result.putAll(processed);
        result.put("processed", true);
      } catch (Exception ex) {
        log.warn("webhook auto-process failed id={}: {}", event.getId(), ex.toString());
        markError(event.getId(), ex.getMessage());
        result.put("processed", false);
        result.put("processError", ex.getMessage());
      }
    } else if (matched == null) {
      result.put(
          "message",
          "Stored; channel not resolved — set tenantId/shopId query params or match Shopify shop domain on marketplace_channel.external_seller_id");
    }
    return result;
  }

  @Transactional
  public Map<String, Object> processEvent(Long eventId, MarketplaceChannel channel, String shopId) {
    MarketplaceWebhookEvent event =
        webhookEventRepository
            .findById(eventId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Webhook not found"));
    if (event.isProcessed()) {
      return Map.of("webhookEventId", eventId, "alreadyProcessed", true);
    }
    String tenantId = event.getTenantId() != null ? event.getTenantId() : channel.getTenantId();
    String effectiveShopId = shopId != null ? shopId : resolveShopIdFromAccount(channel);
    if (effectiveShopId == null || effectiveShopId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "shopId required to process webhook");
    }

    final Map<String, Object>[] out = new Map[] {new LinkedHashMap<>()};
    TenantContextFilter.runWithTenant(
        tenantId,
        effectiveShopId,
        () -> {
          if ("SHOPIFY".equalsIgnoreCase(event.getChannelCode())) {
            out[0] = processShopify(event);
          } else {
            event.setProcessed(true);
            event.setProcessedAt(Instant.now());
            event.setProcessError("No processor for channel " + event.getChannelCode() + " yet");
            webhookEventRepository.save(event);
            out[0] =
                Map.of(
                    "webhookEventId",
                    event.getId(),
                    "skipped",
                    true,
                    "reason",
                    "CHANNEL_PROCESSOR_PENDING");
          }
        });
    return out[0];
  }

  private Map<String, Object> processShopify(MarketplaceWebhookEvent event) {
    String topic = event.getEventType();
    Map<String, Object> payload = event.getPayloadJson();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("webhookEventId", event.getId());
    try {
      if (shopifyOrderWebhookMapper.isOrderCancelled(topic)) {
        String externalId =
            shopifyOrderWebhookMapper
                .externalOrderId(payload)
                .orElseThrow(() -> new IllegalArgumentException("Missing Shopify order id"));
        Map<String, Object> cancelled = orderService.cancelByExternalOrderId("SHOPIFY", externalId);
        out.put("action", "CANCEL");
        out.put("order", cancelled);
      } else if (shopifyOrderWebhookMapper.isOrderCreateOrUpdate(topic)) {
        OrderDtos.IngestRequest ingest = shopifyOrderWebhookMapper.toIngestRequest(payload);
        Map<String, Object> order = orderService.ingest(ingest);
        out.put("action", "INGEST");
        out.put("order", order);
      } else {
        out.put("action", "IGNORE");
        out.put("topic", topic);
      }
      event.setProcessed(true);
      event.setProcessedAt(Instant.now());
      event.setProcessError(null);
      webhookEventRepository.save(event);
      return out;
    } catch (RuntimeException ex) {
      event.setProcessError(ex.getMessage());
      webhookEventRepository.save(event);
      throw ex;
    }
  }

  private void markError(Long eventId, String message) {
    webhookEventRepository
        .findById(eventId)
        .ifPresent(
            e -> {
              e.setProcessError(message);
              webhookEventRepository.save(e);
            });
  }

  private MarketplaceChannel resolveChannel(
      String channel,
      Map<String, String> headers,
      String tenantHint,
      String shopHint,
      Map<String, Object> payload) {
    if (tenantHint != null && !tenantHint.isBlank()) {
      return channelRepository
          .findByTenantIdAndChannelCodeAndDeletedAtIsNull(tenantHint.trim(), channel)
          .filter(MarketplaceChannel::isEnabled)
          .orElse(null);
    }
    if ("SHOPIFY".equals(channel)) {
      String shopDomain = firstHeader(headers, "X-Shopify-Shop-Domain");
      if (shopDomain == null || shopDomain.isBlank()) {
        shopDomain = stringVal(payload.get("shop_domain"));
      }
      if (shopDomain != null) {
        String domain = shopDomain.trim().toLowerCase(Locale.ROOT);
        List<MarketplaceChannel> candidates =
            channelRepository.findByChannelCodeAndEnabledTrueAndDeletedAtIsNull("SHOPIFY");
        for (MarketplaceChannel c : candidates) {
          if (domainEquals(c.getExternalSellerId(), domain)) {
            return c;
          }
          Object cfgDomain =
              c.getConfigJson() != null ? c.getConfigJson().get("shopDomain") : null;
          if (domainEquals(cfgDomain == null ? null : String.valueOf(cfgDomain), domain)) {
            return c;
          }
        }
      }
    }
    return null;
  }

  private String resolveShopIdFromAccount(MarketplaceChannel channel) {
    if (channel.getConfigJson() != null && channel.getConfigJson().get("shopId") != null) {
      return String.valueOf(channel.getConfigJson().get("shopId"));
    }
    return accountRepository
        .findById(channel.getAccountId())
        .map(MarketplaceAccount::getShopId)
        .orElse(channel.getTenantId());
  }

  private Map<String, Object> parsePayload(byte[] rawBody) {
    if (rawBody == null || rawBody.length == 0) {
      return new LinkedHashMap<>();
    }
    try {
      return objectMapper.readValue(rawBody, new TypeReference<Map<String, Object>>() {});
    } catch (Exception ex) {
      Map<String, Object> wrap = new LinkedHashMap<>();
      wrap.put("raw", new String(rawBody, StandardCharsets.UTF_8));
      wrap.put("parseError", ex.getMessage());
      return wrap;
    }
  }

  private Map<String, Object> enrichPayload(
      Map<String, Object> payload, Map<String, String> headers, String shopId) {
    Map<String, Object> enriched = new LinkedHashMap<>(payload == null ? Map.of() : payload);
    Map<String, String> meta = new LinkedHashMap<>();
    if (headers != null) {
      headers.forEach(
          (k, v) -> {
            if (k != null && k.toLowerCase(Locale.ROOT).startsWith("x-shopify")) {
              meta.put(k, v);
            }
          });
    }
    if (!meta.isEmpty()) {
      enriched.put("_headers", meta);
    }
    if (shopId != null) {
      enriched.put("_resolvedShopId", shopId);
    }
    return enriched;
  }

  private static String resolveEventType(
      String channel, Map<String, String> headers, Map<String, Object> payload) {
    if ("SHOPIFY".equals(channel)) {
      String topic = firstHeader(headers, "X-Shopify-Topic");
      if (topic != null) {
        return topic;
      }
    }
    Object type = payload.get("eventType");
    if (type == null) {
      type = payload.get("topic");
    }
    return type == null ? "UNKNOWN" : String.valueOf(type);
  }

  private String resolveExternalId(String channel, Map<String, Object> payload) {
    if ("SHOPIFY".equals(channel)) {
      return shopifyOrderWebhookMapper.externalOrderId(payload).orElse(null);
    }
    Object id = payload.get("id");
    if (id == null) {
      id = payload.get("externalOrderId");
    }
    return id == null ? null : String.valueOf(id);
  }

  private static String firstHeader(Map<String, String> headers, String... names) {
    if (headers == null) {
      return null;
    }
    for (String name : names) {
      for (Map.Entry<String, String> e : headers.entrySet()) {
        if (e.getKey() != null && e.getKey().equalsIgnoreCase(name) && e.getValue() != null) {
          return e.getValue();
        }
      }
    }
    return null;
  }

  private static boolean domainEquals(String a, String b) {
    if (a == null || b == null) {
      return false;
    }
    return a.trim().equalsIgnoreCase(b.trim());
  }

  private static String blankToNull(String v) {
    return v == null || v.isBlank() ? null : v.trim();
  }

  private static String stringVal(Object raw) {
    return raw == null ? null : String.valueOf(raw);
  }
}
