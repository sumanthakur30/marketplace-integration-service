package com.shopmanagement.marketplace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "marketplace")
public class MarketplaceProperties {

  private final Entitlement entitlement = new Entitlement();
  private final Stock stock = new Stock();
  private final Orders orders = new Orders();
  private final Shop shop = new Shop();
  private final Channels channels = new Channels();
  private final AmazonApi amazon = new AmazonApi();
  private final Inbound inbound = new Inbound();

  public Entitlement getEntitlement() {
    return entitlement;
  }

  public Stock getStock() {
    return stock;
  }

  public Orders getOrders() {
    return orders;
  }

  public Shop getShop() {
    return shop;
  }

  public Channels getChannels() {
    return channels;
  }

  public AmazonApi getAmazon() {
    return amazon;
  }

  public Inbound getInbound() {
    return inbound;
  }

  public static class Entitlement {
    private boolean enabled;
    private String baseUrl = "http://localhost:8182";
    private String flag = "FEATURE_MARKETPLACE";
    private String amazonFlag = "FEATURE_MARKETPLACE_AMAZON";
    private String flipkartFlag = "FEATURE_MARKETPLACE_FLIPKART";
    private String shopifyFlag = "FEATURE_MARKETPLACE_SHOPIFY";
    private String websiteFlag = "FEATURE_MARKETPLACE_WEBSITE";
    private String moduleCode = "MARKETPLACE";
    private boolean failOpen;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getFlag() {
      return flag;
    }

    public void setFlag(String flag) {
      this.flag = flag;
    }

    public String getAmazonFlag() {
      return amazonFlag;
    }

    public void setAmazonFlag(String amazonFlag) {
      this.amazonFlag = amazonFlag;
    }

    public String getFlipkartFlag() {
      return flipkartFlag;
    }

    public void setFlipkartFlag(String flipkartFlag) {
      this.flipkartFlag = flipkartFlag;
    }

    public String getShopifyFlag() {
      return shopifyFlag;
    }

    public void setShopifyFlag(String shopifyFlag) {
      this.shopifyFlag = shopifyFlag;
    }

    public String getWebsiteFlag() {
      return websiteFlag;
    }

    public void setWebsiteFlag(String websiteFlag) {
      this.websiteFlag = websiteFlag;
    }

    public String getModuleCode() {
      return moduleCode;
    }

    public void setModuleCode(String moduleCode) {
      this.moduleCode = moduleCode;
    }

    public boolean isFailOpen() {
      return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
      this.failOpen = failOpen;
    }
  }

  public static class Stock {
    private boolean enabled = true;
    /** When true, marketplace order ingest calls stock-service reserve-batch (same path as POS). */
    private boolean reserveOnIngest = true;
    private String baseUrl = "http://localhost:8082";
    private boolean failOpen = true;
    private String internalApiKey = "";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public boolean isReserveOnIngest() {
      return reserveOnIngest;
    }

    public void setReserveOnIngest(boolean reserveOnIngest) {
      this.reserveOnIngest = reserveOnIngest;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public boolean isFailOpen() {
      return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
      this.failOpen = failOpen;
    }

    public String getInternalApiKey() {
      return internalApiKey;
    }

    public void setInternalApiKey(String internalApiKey) {
      this.internalApiKey = internalApiKey;
    }
  }

  public static class Shop {
    private String baseUrl = "http://localhost:8080";
    private String internalApiKey = "";

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getInternalApiKey() {
      return internalApiKey;
    }

    public void setInternalApiKey(String internalApiKey) {
      this.internalApiKey = internalApiKey;
    }
  }

  public static class Channels {
    private final ChannelToggle amazon = new ChannelToggle();
    private final ChannelToggle flipkart = new ChannelToggle();
    private final ChannelToggle shopify = new ChannelToggle();
    private final ChannelToggle website = new ChannelToggle();

    public ChannelToggle getAmazon() {
      return amazon;
    }

    public ChannelToggle getFlipkart() {
      return flipkart;
    }

    public ChannelToggle getShopify() {
      return shopify;
    }

    public ChannelToggle getWebsite() {
      return website;
    }
  }

  public static class ChannelToggle {
    private boolean enabled;
    /** When true, inventory push does not call the remote API (safe default). */
    private boolean dryRun = true;
    private String apiVersion = "2024-10";
    /** Amazon bridge URL for quantity updates (optional). */
    private String inventoryEndpoint = "";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public boolean isDryRun() {
      return dryRun;
    }

    public void setDryRun(boolean dryRun) {
      this.dryRun = dryRun;
    }

    public String getApiVersion() {
      return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
      this.apiVersion = apiVersion;
    }

    public String getInventoryEndpoint() {
      return inventoryEndpoint;
    }

    public void setInventoryEndpoint(String inventoryEndpoint) {
      this.inventoryEndpoint = inventoryEndpoint;
    }
  }

  public static class Orders {
    /** When true, ingest may create one retail order. The channel flag orderSyncEnabled still defaults off. */
    private boolean bridgeEnabled = true;
    private String baseUrl = "http://localhost:8083";

    public boolean isBridgeEnabled() {
      return bridgeEnabled;
    }

    public void setBridgeEnabled(boolean bridgeEnabled) {
      this.bridgeEnabled = bridgeEnabled;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }
  }

  /** Platform Selling Partner API app. Seller refresh tokens stay on the channel, not here. */
  public static class AmazonApi {
    private String applicationId = "";
    private String lwaClientId = "";
    private String lwaClientSecret = "";
    private String awsAccessKeyId = "";
    private String awsSecretAccessKey = "";
    private String region = "eu-west-1";
    private String endpoint = "https://sellingpartnerapi-eu.amazon.com";
    private String tokenUrl = "https://api.amazon.com/auth/o2/token";
    private String authorizeUrl = "https://sellercentral.amazon.in/apps/authorize/consent";
    private String marketplaceId = "A21TJRUUN4KGV";
    private boolean draftConsent = true;

    public String getApplicationId() {
      return applicationId;
    }

    public void setApplicationId(String applicationId) {
      this.applicationId = applicationId;
    }

    public String getLwaClientId() {
      return lwaClientId;
    }

    public void setLwaClientId(String lwaClientId) {
      this.lwaClientId = lwaClientId;
    }

    public String getLwaClientSecret() {
      return lwaClientSecret;
    }

    public void setLwaClientSecret(String lwaClientSecret) {
      this.lwaClientSecret = lwaClientSecret;
    }

    public String getAwsAccessKeyId() {
      return awsAccessKeyId;
    }

    public void setAwsAccessKeyId(String awsAccessKeyId) {
      this.awsAccessKeyId = awsAccessKeyId;
    }

    public String getAwsSecretAccessKey() {
      return awsSecretAccessKey;
    }

    public void setAwsSecretAccessKey(String awsSecretAccessKey) {
      this.awsSecretAccessKey = awsSecretAccessKey;
    }

    public String getRegion() {
      return region;
    }

    public void setRegion(String region) {
      this.region = region;
    }

    public String getEndpoint() {
      return endpoint;
    }

    public void setEndpoint(String endpoint) {
      this.endpoint = endpoint;
    }

    public String getTokenUrl() {
      return tokenUrl;
    }

    public void setTokenUrl(String tokenUrl) {
      this.tokenUrl = tokenUrl;
    }

    public String getAuthorizeUrl() {
      return authorizeUrl;
    }

    public void setAuthorizeUrl(String authorizeUrl) {
      this.authorizeUrl = authorizeUrl;
    }

    public String getMarketplaceId() {
      return marketplaceId;
    }

    public void setMarketplaceId(String marketplaceId) {
      this.marketplaceId = marketplaceId;
    }

    public boolean isDraftConsent() {
      return draftConsent;
    }

    public void setDraftConsent(boolean draftConsent) {
      this.draftConsent = draftConsent;
    }

    public boolean lwaReady() {
      return text(lwaClientId) && text(lwaClientSecret);
    }

    public boolean signingReady() {
      return lwaReady() && text(awsAccessKeyId) && text(awsSecretAccessKey) && text(endpoint);
    }

    private static boolean text(String value) {
      return value != null && !value.isBlank();
    }
  }

  public static class Inbound {
    private boolean signingEnabled;
    private String hmacSecret = "";
    /** Prefer for Shopify; falls back to hmacSecret. */
    private String shopifyHmacSecret = "";
    private String amazonHmacSecret = "";
    private boolean autoProcess = true;

    public boolean isSigningEnabled() {
      return signingEnabled;
    }

    public void setSigningEnabled(boolean signingEnabled) {
      this.signingEnabled = signingEnabled;
    }

    public String getHmacSecret() {
      return hmacSecret;
    }

    public void setHmacSecret(String hmacSecret) {
      this.hmacSecret = hmacSecret;
    }

    public String getShopifyHmacSecret() {
      return shopifyHmacSecret;
    }

    public void setShopifyHmacSecret(String shopifyHmacSecret) {
      this.shopifyHmacSecret = shopifyHmacSecret;
    }

    public String getAmazonHmacSecret() {
      return amazonHmacSecret;
    }

    public void setAmazonHmacSecret(String amazonHmacSecret) {
      this.amazonHmacSecret = amazonHmacSecret;
    }

    public boolean isAutoProcess() {
      return autoProcess;
    }

    public void setAutoProcess(boolean autoProcess) {
      this.autoProcess = autoProcess;
    }
  }
}
