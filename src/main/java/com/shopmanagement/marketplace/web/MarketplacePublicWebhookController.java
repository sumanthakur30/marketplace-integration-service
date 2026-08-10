package com.shopmanagement.marketplace.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.marketplace.service.MarketplaceWebhookService;

/**
 * Public marketplace webhooks (gateway allows without JWT). Persists {@code
 * marketplace_webhook_event}; Shopify orders/* auto-ingest/cancel when channel is resolved.
 */
@RestController
@RequestMapping("/api/v1/marketplace/public")
public class MarketplacePublicWebhookController {

  private final MarketplaceWebhookService webhookService;

  public MarketplacePublicWebhookController(MarketplaceWebhookService webhookService) {
    this.webhookService = webhookService;
  }

  @PostMapping(value = "/webhooks/{channel}", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String, Object> ingest(
      @PathVariable String channel,
      @RequestBody(required = false) byte[] rawBody,
      @RequestHeader Map<String, String> headers,
      @RequestParam(required = false) String tenantId,
      @RequestParam(required = false) String shopId) {
    Map<String, String> normalized = new LinkedHashMap<>();
    if (headers != null) {
      normalized.putAll(headers);
    }
    return webhookService.accept(channel, rawBody, normalized, tenantId, shopId);
  }
}
