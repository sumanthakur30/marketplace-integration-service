package com.shopmanagement.marketplace.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public marketplace webhooks (gateway must allow without JWT). Payload is accepted and stored in a
 * later phase; Phase-1 acknowledges only.
 */
@RestController
@RequestMapping("/api/v1/marketplace/public")
public class MarketplacePublicWebhookController {

  @PostMapping("/webhooks/{channel}")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String, Object> ingest(
      @PathVariable String channel, @RequestBody(required = false) Map<String, Object> body) {
    return Map.of(
        "accepted", true,
        "channel", channel == null ? "" : channel.toUpperCase(),
        "stub", true,
        "message", "Webhook received; persistence + HMAC verification in next phase");
  }
}
