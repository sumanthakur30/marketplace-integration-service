package com.shopmanagement.marketplace.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.marketplace.channel.MarketplaceChannelCode;
import com.shopmanagement.marketplace.channel.amazon.AmazonCallException;
import com.shopmanagement.marketplace.channel.amazon.AmazonOauthState;
import com.shopmanagement.marketplace.channel.amazon.AmazonOrderMapper;
import com.shopmanagement.marketplace.channel.amazon.AmazonSpApiClient;
import com.shopmanagement.marketplace.config.MarketplaceProperties;
import com.shopmanagement.marketplace.domain.MarketplaceChannel;
import com.shopmanagement.marketplace.domain.MarketplaceChannelAudit;
import com.shopmanagement.marketplace.repo.MarketplaceChannelAuditRepository;
import com.shopmanagement.marketplace.repo.MarketplaceChannelRepository;
import com.shopmanagement.marketplace.security.ChannelSecretStore;
import com.shopmanagement.marketplace.security.ChannelSecrets;
import com.shopmanagement.marketplace.support.TenantIds;
import com.shopmanagement.marketplace.web.dto.OrderDtos;

/** Amazon Login with Amazon consent and one page of unshipped order pull. Inventory push is unchanged. */
@Service
public class MarketplaceAmazonService {

  private static final int PAGE = 20;

  private final MarketplaceProperties properties;
  private final MarketplaceChannelRepository channelRepository;
  private final MarketplaceChannelAuditRepository auditRepository;
  private final ChannelSecretStore secretStore;
  private final AmazonSpApiClient amazonClient;
  private final MarketplaceOrderService orderService;
  private final String credentialsKey;

  public MarketplaceAmazonService(
      MarketplaceProperties properties,
      MarketplaceChannelRepository channelRepository,
      MarketplaceChannelAuditRepository auditRepository,
      ChannelSecretStore secretStore,
      AmazonSpApiClient amazonClient,
      MarketplaceOrderService orderService,
      @Value("${marketplace.credentials.key:change-me-marketplace-credentials-key}") String credentialsKey) {
    this.properties = properties;
    this.channelRepository = channelRepository;
    this.auditRepository = auditRepository;
    this.secretStore = secretStore;
    this.amazonClient = amazonClient;
    this.orderService = orderService;
    this.credentialsKey = credentialsKey;
  }

  @Transactional
  public Map<String, Object> authorizeUrl(Long channelId) {
    MarketplaceChannel channel = requireAmazon(channelId);
    String applicationId = properties.getAmazon().getApplicationId();
    if (applicationId == null || applicationId.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Amazon application id is not configured on this server. No Amazon call was made.");
    }
    long expiry = Instant.now().getEpochSecond() + AmazonOauthState.TTL_SECONDS;
    String state = AmazonOauthState.sign(channel.getId(), channel.getTenantId(), expiry, credentialsKey);
    String url =
        AmazonOauthState.consentUrl(
            properties.getAmazon().getAuthorizeUrl(), applicationId.trim(), state, properties.getAmazon().isDraftConsent());
    audit(channel, "AMAZON_AUTHORIZE_URL", Map.of());
    return Map.of("url", url);
  }

  @Transactional(noRollbackFor = ResponseStatusException.class)
  public Map<String, Object> completeAuthorization(Long channelId, String code, String state, String sellingPartnerId) {
    MarketplaceChannel channel = requireAmazon(channelId);
    if (code == null || code.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amazon authorization code is required");
    }
    try {
      AmazonOauthState.verify(state, channel.getId(), channel.getTenantId(), credentialsKey, Instant.now().getEpochSecond());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
    if (!properties.getAmazon().lwaReady()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Amazon Login with Amazon is not configured on this server. No token was stored.");
    }
    ChannelSecretStore.SecretRead current = secretStore.read(channel.getCredentialsCiphertext());
    if (current.unreadable) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Stored credentials could not be read. Amazon was not called.");
    }
    String refresh;
    try {
      refresh = amazonClient.exchangeRefreshToken(code.trim());
    } catch (AmazonCallException ex) {
      channel.setConnectionStatus(ex.tokenRejected() ? "TOKEN_EXPIRED" : "ERROR");
      channelRepository.save(channel);
      audit(channel, "AMAZON_AUTHORIZE_FAILED", Map.of("status", ex.status()));
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }
    Map<String, String> stored =
        ChannelSecrets.merge(current.secrets, Map.of("refreshToken", refresh), false);
    channel.setCredentialsCiphertext(secretStore.write(stored));
    channel.setConfigJson(ChannelSecrets.publicConfig(channel.getConfigJson()));
    String seller = trim(sellingPartnerId, 128);
    if (seller != null) {
      channel.setExternalSellerId(seller);
      Map<String, Object> config = new LinkedHashMap<>(channel.getConfigJson());
      config.put("sellerId", seller);
      channel.setConfigJson(config);
    }
    channel.setConnectionStatus("CONNECTED");
    channel.setEnabled(true);
    MarketplaceChannel saved = channelRepository.save(channel);
    audit(saved, "AMAZON_AUTHORIZED", Map.of("credentialFieldNames", List.of("refreshToken")));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", saved.getId());
    out.put("connectionStatus", saved.getConnectionStatus());
    out.put("enabled", saved.isEnabled());
    out.put("externalSellerId", saved.getExternalSellerId());
    out.put("credentialsConfigured", true);
    return out;
  }

  public Map<String, Object> pullOrders(Long channelId) {
    MarketplaceChannel channel = requireAmazon(channelId);
    if (!channel.isEnabled()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Connect Amazon before pulling orders.");
    }
    if (!properties.getAmazon().signingReady()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Amazon SP-API signing is not configured on this server. No orders were pulled.");
    }
    ChannelSecretStore.SecretRead current = secretStore.read(channel.getCredentialsCiphertext());
    if (current.unreadable) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Stored credentials could not be read. Amazon was not called.");
    }
    String refresh = current.secrets.get("refreshToken");
    if (refresh == null || refresh.isBlank()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Authorize Amazon before pulling orders.");
    }
    String access;
    String ordersJson;
    try {
      access = amazonClient.accessToken(refresh);
      ordersJson = amazonClient.getOrders(access, orderQuery(channel));
    } catch (AmazonCallException ex) {
      channel.setConnectionStatus(ex.tokenRejected() ? "TOKEN_EXPIRED" : "ERROR");
      channelRepository.save(channel);
      audit(channel, "AMAZON_PULL_FAILED", Map.of("status", ex.status()));
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }
    Map<String, String> itemsByOrder = new LinkedHashMap<>();
    List<OrderDtos.IngestRequest> requests;
    try {
      int seen = 0;
      for (String orderId : AmazonOrderMapper.orderIds(ordersJson)) {
        if (seen >= PAGE) {
          break;
        }
        seen++;
        itemsByOrder.put(orderId, amazonClient.getOrderItems(access, orderId));
      }
      requests = AmazonOrderMapper.toIngest(ordersJson, itemsByOrder);
    } catch (AmazonCallException ex) {
      channel.setConnectionStatus(ex.tokenRejected() ? "TOKEN_EXPIRED" : "ERROR");
      channelRepository.save(channel);
      audit(channel, "AMAZON_PULL_FAILED", Map.of("status", ex.status()));
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }
    int ingested = 0;
    int failed = 0;
    int skipped = 0;
    int pulled = Math.min(requests.size(), PAGE);
    for (int i = 0; i < pulled; i++) {
      OrderDtos.IngestRequest request = requests.get(i);
      if (request.items() == null || request.items().isEmpty()) {
        skipped++;
        continue;
      }
      try {
        orderService.ingest(request);
        ingested++;
      } catch (ResponseStatusException ex) {
        failed++;
      }
    }
    String next = AmazonOrderMapper.nextToken(ordersJson);
    Map<String, Object> config = new LinkedHashMap<>(channel.getConfigJson() == null ? Map.of() : channel.getConfigJson());
    boolean more = next != null;
    if (more) {
      config.put("amazonOrderCursor", next);
    } else {
      config.remove("amazonOrderCursor");
      config.put("lastOrderPullAt", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
    }
    channel.setConfigJson(config);
    channel.setLastSyncAt(Instant.now());
    channel.setConnectionStatus("CONNECTED");
    channelRepository.save(channel);
    audit(channel, "AMAZON_ORDERS_PULLED", Map.of("ingested", ingested, "failed", failed));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("pulled", pulled);
    out.put("ingested", ingested);
    out.put("skipped", skipped);
    out.put("failed", failed);
    out.put("moreAvailable", more);
    return out;
  }

  private Map<String, String> orderQuery(MarketplaceChannel channel) {
    Map<String, Object> config = channel.getConfigJson() == null ? Map.of() : channel.getConfigJson();
    Object cursor = config.get("amazonOrderCursor");
    if (cursor != null && !String.valueOf(cursor).isBlank()) {
      return Map.of("NextToken", String.valueOf(cursor));
    }
    String marketplaceId = text(config.get("marketplaceId"));
    if (marketplaceId == null) {
      marketplaceId = properties.getAmazon().getMarketplaceId();
    }
    String createdAfter = text(config.get("lastOrderPullAt"));
    if (createdAfter == null) {
      createdAfter = Instant.now().minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
    }
    Map<String, String> query = new LinkedHashMap<>();
    query.put("MarketplaceIds", marketplaceId);
    query.put("CreatedAfter", createdAfter);
    query.put("OrderStatuses", "Unshipped");
    query.put("MaxResultsPerPage", String.valueOf(PAGE));
    return query;
  }

  private MarketplaceChannel requireAmazon(Long channelId) {
    if (channelId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "channel id required");
    }
    MarketplaceChannel channel =
        channelRepository
            .findByIdAndTenantIdAndDeletedAtIsNull(channelId, TenantIds.require())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found"));
    if (!MarketplaceChannelCode.AMAZON.name().equals(channel.getChannelCode())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "This action is only for an Amazon channel.");
    }
    return channel;
  }

  private void audit(MarketplaceChannel channel, String action, Map<String, Object> detail) {
    MarketplaceChannelAudit row = new MarketplaceChannelAudit();
    row.setTenantId(channel.getTenantId());
    row.setShopId(TenantIds.shopOrNull());
    row.setChannelId(channel.getId());
    row.setAction(action);
    String actor = TenantIds.userOrNull();
    row.setActor(actor == null ? "unknown" : actor);
    row.setDetailJson(new LinkedHashMap<>(detail));
    auditRepository.save(row);
  }

  private static String trim(String value, int max) {
    if (value == null) {
      return null;
    }
    String text = value.trim();
    if (text.isEmpty()) {
      return null;
    }
    return text.length() <= max ? text : text.substring(0, max);
  }

  private static String text(Object value) {
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value).trim();
    return text.isEmpty() || "null".equals(text) ? null : text;
  }
}
