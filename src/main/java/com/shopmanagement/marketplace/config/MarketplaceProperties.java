package com.shopmanagement.marketplace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "marketplace")
public class MarketplaceProperties {

  private final Entitlement entitlement = new Entitlement();
  private final Stock stock = new Stock();
  private final Shop shop = new Shop();
  private final Channels channels = new Channels();
  private final Inbound inbound = new Inbound();

  public Entitlement getEntitlement() {
    return entitlement;
  }

  public Stock getStock() {
    return stock;
  }

  public Shop getShop() {
    return shop;
  }

  public Channels getChannels() {
    return channels;
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

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }

  public static class Inbound {
    private boolean signingEnabled;
    private String hmacSecret = "";
    /** Prefer for Shopify; falls back to hmacSecret. */
    private String shopifyHmacSecret = "";
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

    public boolean isAutoProcess() {
      return autoProcess;
    }

    public void setAutoProcess(boolean autoProcess) {
      this.autoProcess = autoProcess;
    }
  }
}
