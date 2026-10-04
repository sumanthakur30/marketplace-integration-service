package com.shopmanagement.marketplace.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientResponseException;

import com.shopmanagement.marketplace.domain.MarketplaceOrderItem;

class ErpOrderRequestTest {

  @Test
  void orderSyncStaysOffUntilTheChannelTurnsItOn() {
    assertFalse(ErpOrderRequest.orderSyncEnabled(Map.of()));
    assertFalse(ErpOrderRequest.orderSyncEnabled(null));
    assertTrue(ErpOrderRequest.orderSyncEnabled(Map.of("orderSyncEnabled", true)));
  }

  @Test
  void missingCustomerDoesNotInventAnId() {
    assertNull(ErpOrderRequest.customerId(null));
    assertNull(ErpOrderRequest.customerId(""));
    assertNull(ErpOrderRequest.customerId("abc"));
    assertEquals(15L, ErpOrderRequest.customerId("15"));
  }

  @Test
  void billStaysUnpaidAndUsesTheExistingProduct() {
    MarketplaceOrderItem item = new MarketplaceOrderItem();
    item.setProductId(88L);
    item.setQuantity(new BigDecimal("2"));
    item.setUnitPrice(new BigDecimal("10.50"));
    item.setTitle("Listed item");

    Map<String, Object> body = ErpOrderRequest.body(15L, "AMAZON", "408-123", List.of(item));

    assertEquals(15L, body.get("customerId"));
    assertEquals("AMAZON", body.get("orderChannel"));
    assertEquals("PAY_LATER", body.get("paymentMethod"));
    assertEquals("UNPAID", body.get("paymentStatus"));
    assertEquals(0d, body.get("paidAmount"));
    assertFalse(body.containsKey("user"));
    @SuppressWarnings("unchecked")
    Map<String, Object> line = ((List<Map<String, Object>>) body.get("items")).get(0);
    assertEquals(88L, line.get("productId"));
    assertEquals(2, line.get("quantity"));
    assertFalse(line.containsKey("createProduct"));
  }

  @Test
  void sameExternalOrderKeepsTheSameIdempotencyKey() {
    String first = ErpOrderRequest.idempotencyKey(4L, "408-123");
    String second = ErpOrderRequest.idempotencyKey(4L, "408-123");
    assertEquals(first, second);
    assertTrue(first.length() <= 128);
    assertTrue(ErpOrderRequest.idempotencyKey(4L, "x".repeat(200)).length() <= 128);
  }

  @Test
  void errorTextDropsSecrets() {
    RestClientResponseException ex =
        new RestClientResponseException(
            "bad",
            400,
            "Bad Request",
            null,
            "{\"message\":\"access token leaked\"}".getBytes(),
            null);
    assertEquals("Sales bill was not created (400)", OrderErpClient.safeMessage(ex));
  }
}
