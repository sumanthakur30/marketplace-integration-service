package com.shopmanagement.marketplace.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.marketplace.channel.MarketplaceChannelCode;
import com.shopmanagement.marketplace.channel.amazon.AmazonCallException;
import com.shopmanagement.marketplace.channel.flipkart.FlipkartClient;
import com.shopmanagement.marketplace.channel.flipkart.FlipkartOrderMapper;
import com.shopmanagement.marketplace.domain.MarketplaceChannel;
import com.shopmanagement.marketplace.domain.MarketplaceChannelAudit;
import com.shopmanagement.marketplace.repo.MarketplaceChannelAuditRepository;
import com.shopmanagement.marketplace.repo.MarketplaceChannelRepository;
import com.shopmanagement.marketplace.security.ChannelSecretStore;
import com.shopmanagement.marketplace.security.ChannelSecrets;
import com.shopmanagement.marketplace.support.TenantIds;
import com.shopmanagement.marketplace.web.dto.OrderDtos;

/** Flipkart application credentials and one page of approved shipments. Inventory push stays off. */
@Service
public class MarketplaceFlipkartService {

  private final MarketplaceChannelRepository channelRepository;
  private final MarketplaceChannelAuditRepository auditRepository;
  private final ChannelSecretStore secretStore;
  private final FlipkartClient flipkartClient;
  private final MarketplaceOrderService orderService;

  public MarketplaceFlipkartService(
      MarketplaceChannelRepository channelRepository,
      MarketplaceChannelAuditRepository auditRepository,
      ChannelSecretStore secretStore,
      FlipkartClient flipkartClient,
      MarketplaceOrderService orderService) {
    this.channelRepository = channelRepository;
    this.auditRepository = auditRepository;
    this.secretStore = secretStore;
    this.flipkartClient = flipkartClient;
    this.orderService = orderService;
  }

  @Transactional(noRollbackFor = ResponseStatusException.class)
  public Map<String, Object> connect(Long channelId) {
    MarketplaceChannel channel = requireFlipkart(channelId);
    ChannelSecretStore.SecretRead current = readSecrets(channel);
    String clientId = current.secrets.get("clientId");
    String clientSecret = current.secrets.get("clientSecret");
    if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Store the Flipkart application id and secret before connecting. Flipkart was not called.");
    }
    FlipkartClient.Token token;
    try {
      token = flipkartClient.exchange(clientId, clientSecret);
    } catch (AmazonCallException ex) {
      channel.setConnectionStatus(ex.tokenRejected() ? "TOKEN_EXPIRED" : "ERROR");
      channelRepository.save(channel);
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }
    storeToken(channel, current, token);
    channel.setConnectionStatus("CONNECTED");
    channel.setEnabled(true);
    channelRepository.save(channel);
    audit(channel, "FLIPKART_CONNECTED");
    return Map.of("id", channel.getId(), "connectionStatus", "CONNECTED", "enabled", true, "credentialsConfigured", true);
  }

  public Map<String, Object> pullOrders(Long channelId) {
    MarketplaceChannel channel = requireFlipkart(channelId);
    if (!channel.isEnabled()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Connect Flipkart before pulling orders.");
    }
    ChannelSecretStore.SecretRead current = readSecrets(channel);
    String access = current.secrets.get("accessToken");
    if (access == null || access.isBlank()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Connect Flipkart before pulling orders.");
    }
    String body;
    try {
      body = flipkartClient.approvedShipments(access);
    } catch (AmazonCallException ex) {
      if (!ex.tokenRejected() || current.secrets.get("refreshToken") == null) {
        channel.setConnectionStatus(ex.tokenRejected() ? "TOKEN_EXPIRED" : "ERROR");
        channelRepository.save(channel);
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage());
      }
      access = refresh(channel, current);
      try {
        body = flipkartClient.approvedShipments(access);
      } catch (AmazonCallException retry) {
        channel.setConnectionStatus(retry.tokenRejected() ? "TOKEN_EXPIRED" : "ERROR");
        channelRepository.save(channel);
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, retry.getMessage());
      }
    }
    int ingested = 0;
    int failed = 0;
    List<OrderDtos.IngestRequest> requests = FlipkartOrderMapper.toIngest(body);
    int pulled = Math.min(requests.size(), 20);
    for (int i = 0; i < pulled; i++) {
      try {
        orderService.ingest(requests.get(i));
        ingested++;
      } catch (ResponseStatusException ex) {
        failed++;
      }
    }
    channel.setLastSyncAt(Instant.now());
    channel.setConnectionStatus("CONNECTED");
    channelRepository.save(channel);
    audit(channel, "FLIPKART_ORDERS_PULLED");
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("pulled", pulled);
    out.put("ingested", ingested);
    out.put("failed", failed);
    out.put("moreAvailable", FlipkartOrderMapper.hasMore(body));
    return out;
  }

  private String refresh(MarketplaceChannel channel, ChannelSecretStore.SecretRead current) {
    try {
      FlipkartClient.Token token =
          flipkartClient.refresh(current.secrets.get("clientId"), current.secrets.get("clientSecret"), current.secrets.get("refreshToken"));
      storeToken(channel, current, token);
      channelRepository.save(channel);
      return token.accessToken();
    } catch (AmazonCallException ex) {
      channel.setConnectionStatus("TOKEN_EXPIRED");
      channelRepository.save(channel);
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }
  }

  private void storeToken(MarketplaceChannel channel, ChannelSecretStore.SecretRead current, FlipkartClient.Token token) {
    Map<String, String> incoming = new LinkedHashMap<>();
    incoming.put("accessToken", token.accessToken());
    if (token.refreshToken() != null) {
      incoming.put("refreshToken", token.refreshToken());
    }
    channel.setCredentialsCiphertext(secretStore.write(ChannelSecrets.merge(current.secrets, incoming, false)));
  }

  private ChannelSecretStore.SecretRead readSecrets(MarketplaceChannel channel) {
    ChannelSecretStore.SecretRead current = secretStore.read(channel.getCredentialsCiphertext());
    if (current.unreadable) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Stored credentials could not be read. Flipkart was not called.");
    }
    return current;
  }

  private MarketplaceChannel requireFlipkart(Long channelId) {
    if (channelId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "channel id required");
    }
    MarketplaceChannel channel =
        channelRepository
            .findByIdAndTenantIdAndDeletedAtIsNull(channelId, TenantIds.require())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found"));
    if (!MarketplaceChannelCode.FLIPKART.name().equals(channel.getChannelCode())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "This action is only for a Flipkart channel.");
    }
    return channel;
  }

  private void audit(MarketplaceChannel channel, String action) {
    MarketplaceChannelAudit row = new MarketplaceChannelAudit();
    row.setTenantId(channel.getTenantId());
    row.setShopId(TenantIds.shopOrNull());
    row.setChannelId(channel.getId());
    row.setAction(action);
    String actor = TenantIds.userOrNull();
    row.setActor(actor == null ? "unknown" : actor);
    row.setDetailJson(new LinkedHashMap<>());
    auditRepository.save(row);
  }
}
