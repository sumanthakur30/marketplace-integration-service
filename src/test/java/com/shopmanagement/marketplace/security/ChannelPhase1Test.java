package com.shopmanagement.marketplace.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

class ChannelPhase1Test {

  @Test
  void cipherRoundTripDoesNotKeepPlaintext() {
    ChannelCredentialCipher cipher = new ChannelCredentialCipher("phase1-test-key-value-32b!!!!");
    String blob = cipher.encrypt("{\"accessToken\":\"secret-token-value\"}");
    assertTrue(blob.startsWith(ChannelCredentialCipher.PREFIX));
    assertFalse(blob.contains("secret-token-value"));
    assertEquals("{\"accessToken\":\"secret-token-value\"}", cipher.decrypt(blob));
  }

  @Test
  void blankSecretDoesNotWipeStoredValue() {
    Map<String, String> existing = new LinkedHashMap<>();
    existing.put("accessToken", "kept");
    Map<String, Object> incoming = new LinkedHashMap<>();
    incoming.put("accessToken", "  ");
    incoming.put("clientId", "new-id");
    Map<String, String> merged = ChannelSecrets.merge(existing, incoming, false);
    assertEquals("kept", merged.get("accessToken"));
    assertEquals("new-id", merged.get("clientId"));

    Map<String, Object> config = new LinkedHashMap<>();
    config.put("accessToken", "leak");
    config.put("shopDomain", "demo.myshopify.com");
    assertFalse(ChannelSecrets.publicConfig(config).containsKey("accessToken"));
    assertEquals("demo.myshopify.com", ChannelSecrets.publicConfig(config).get("shopDomain"));
    assertEquals("leak", ChannelSecrets.extractSecrets(config).get("accessToken"));
  }

  @Test
  void clearSecretsDropsStoredValues() {
    Map<String, String> existing = new LinkedHashMap<>();
    existing.put("accessToken", "kept");
    Map<String, String> cleared = ChannelSecrets.merge(existing, Map.<String, Object>of("clientId", ""), true);
    assertTrue(cleared.isEmpty());
  }

  @Test
  void statusSeedAndWhitelist() {
    assertEquals("NEW", ChannelStatusCatalog.seedFor("AMAZON").get(0).internalStatus());
    assertEquals("PACKED", ChannelStatusCatalog.seedFor("FLIPKART").get(1).internalStatus());
    assertEquals("READY_TO_SHIP", ChannelStatusCatalog.requireInternal("ready to ship"));
    assertThrows(IllegalArgumentException.class, () -> ChannelStatusCatalog.requireInternal("NOT_A_STATUS"));
  }

  @Test
  void syncIntervalRejectsUnknownValue() {
    assertThrows(
        ResponseStatusException.class,
        () -> ChannelSettings.normalize(Map.of(), Map.<String, Object>of("syncIntervalMinutes", 7)));
    Map<String, Object> settings =
        ChannelSettings.normalize(Map.of(), Map.<String, Object>of("shopDomain", "shop.example"));
    assertEquals(0, settings.get("syncIntervalMinutes"));
    assertEquals(Boolean.FALSE, settings.get("orderSyncEnabled"));
    assertEquals("shop.example", settings.get("shopDomain"));
  }

  @Test
  void ownerBypassesAndStaffNeedAGrant() {
    assertTrue(MarketplaceAccessCheck.allowed("SHOP OWNER", null, "MARKETPLACE_VIEW"));
    assertFalse(MarketplaceAccessCheck.allowed("SHOP_EMPLOYEE", null, "MARKETPLACE_VIEW"));
    assertTrue(MarketplaceAccessCheck.allowed("SHOP_EMPLOYEE", "MANAGE_ORDERS,MARKETPLACE_CONNECT", "MARKETPLACE_CONNECT"));
    assertFalse(MarketplaceAccessCheck.allowed("SHOP_EMPLOYEE", "MARKETPLACE_VIEW", "MARKETPLACE_MANAGE"));
  }

  @Test
  void secretStoreRoundTrip() {
    ChannelSecretStore store =
        new ChannelSecretStore(new ChannelCredentialCipher("phase1-test-key-value-32b!!!!"), new ObjectMapper());
    String blob = store.write(Map.of("accessToken", "abc"));
    assertFalse(blob.contains("abc"));
    assertEquals("abc", store.read(blob).secrets.get("accessToken"));
    Map<String, Object> adapter = store.adapterConfig(Map.<String, Object>of("shopDomain", "demo"), blob);
    assertEquals("abc", adapter.get("accessToken"));
    assertEquals("demo", adapter.get("shopDomain"));
  }
}
