package com.shopmanagement.marketplace.service;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.marketplace.channel.MarketplaceChannelCode;
import com.shopmanagement.marketplace.channel.connector.ConnectorOrderMapper;
import com.shopmanagement.marketplace.channel.connector.ConnectorUrl;
import com.shopmanagement.marketplace.domain.MarketplaceChannel;
import com.shopmanagement.marketplace.repo.MarketplaceChannelRepository;
import com.shopmanagement.marketplace.security.ChannelSecretStore;
import com.shopmanagement.marketplace.support.TenantIds;
import com.shopmanagement.marketplace.web.dto.OrderDtos;

/** Pulls orders from a configured partner URL. Field names are data, not a script. */
@Service
public class MarketplaceConnectorService {

  private final MarketplaceChannelRepository channelRepository;
  private final ChannelSecretStore secretStore;
  private final MarketplaceOrderService orderService;
  private final RestClient.Builder restClientBuilder;

  public MarketplaceConnectorService(
      MarketplaceChannelRepository channelRepository,
      ChannelSecretStore secretStore,
      MarketplaceOrderService orderService,
      RestClient.Builder restClientBuilder) {
    this.channelRepository = channelRepository;
    this.secretStore = secretStore;
    this.orderService = orderService;
    this.restClientBuilder = restClientBuilder;
  }

  public Map<String, Object> pullOrders(Long channelId) {
    String tenantId = TenantIds.require();
    MarketplaceChannel channel =
        channelRepository
            .findByIdAndTenantIdAndDeletedAtIsNull(channelId, tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found"));
    String code = channel.getChannelCode();
    if (MarketplaceChannelCode.AMAZON.name().equals(code) || MarketplaceChannelCode.FLIPKART.name().equals(code)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Use the channel's own pull for Amazon and Flipkart.");
    }
    if (!channel.isEnabled()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Connect the channel before pulling orders.");
    }
    Map<String, Object> config = channel.getConfigJson() == null ? Map.of() : channel.getConfigJson();
    String endpoint = text(config.get("apiEndpoint"));
    URI uri;
    try {
      uri = ConnectorUrl.requirePublicHttp(endpoint);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
    ChannelSecretStore.SecretRead secrets = secretStore.read(channel.getCredentialsCiphertext());
    if (secrets.unreadable) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Stored credentials could not be read. The partner was not called.");
    }
    String apiKey = secrets.secrets.get("apiKey");
    String header = text(config.get("authHeader"));
    if (header == null) {
      header = "Authorization";
    }
    String body;
    try {
      var request = restClientBuilder.build().get().uri(uri);
      if (apiKey != null && !apiKey.isBlank()) {
        String value = "Authorization".equalsIgnoreCase(header) && !apiKey.regionMatches(true, 0, "Bearer ", 0, 7)
            ? "Bearer " + apiKey
            : apiKey;
        request = request.header(header, value);
      }
      body = request.retrieve().body(String.class);
    } catch (RestClientResponseException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Partner returned " + ex.getStatusCode().value());
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Partner order pull could not be completed");
    }
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("ordersPath", text(config.get("connectorOrdersPath")));
    fields.put("orderIdField", text(config.get("connectorOrderIdField")));
    fields.put("skuField", text(config.get("connectorSkuField")));
    fields.put("quantityField", text(config.get("connectorQuantityField")));
    fields.put("priceField", text(config.get("connectorPriceField")));
    fields.put("listingField", text(config.get("connectorListingField")));
    List<OrderDtos.IngestRequest> requests = ConnectorOrderMapper.toIngest(body, fields, code);
    int ingested = 0;
    int failed = 0;
    for (OrderDtos.IngestRequest request : requests) {
      try {
        orderService.ingest(request);
        ingested++;
      } catch (ResponseStatusException ex) {
        failed++;
      }
    }
    channel.setLastSyncAt(Instant.now());
    channelRepository.save(channel);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("pulled", requests.size());
    out.put("ingested", ingested);
    out.put("failed", failed);
    return out;
  }

  private static String text(Object value) {
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value).trim();
    return text.isEmpty() || "null".equals(text) ? null : text;
  }
}
