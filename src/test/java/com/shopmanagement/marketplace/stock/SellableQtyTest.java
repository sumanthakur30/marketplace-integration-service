package com.shopmanagement.marketplace.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SellableQtyTest {

  @Test
  void bufferComesOffAvailableAndCannotGoBelowZero() {
    assertEquals(7, SellableQty.units(10, 3));
    assertEquals(0, SellableQty.units(2, 5));
    assertEquals(4, SellableQty.units(4.9, 0));
  }

  @Test
  void missingBufferIsZero() {
    assertEquals(0, SellableQty.buffer(null));
    assertEquals(0, SellableQty.buffer("nope"));
    assertEquals(4, SellableQty.buffer(4));
  }

  @Test
  void reservationKeyStaysStableAndShort() {
    String key = SellableQty.reservationKey(9L, "408-1");
    assertEquals(key, SellableQty.reservationKey(9L, "408-1"));
    assertTrue(key.startsWith("fefo-"));
    assertTrue(SellableQty.reservationKey(9L, "x".repeat(200)).length() <= 120);
    assertTrue(SellableQty.isFefoKey(key));
    assertFalse(SellableQty.isFefoKey("MP-1-AMAZON-1"));
  }
}
