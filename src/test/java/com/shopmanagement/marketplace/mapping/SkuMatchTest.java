package com.shopmanagement.marketplace.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class SkuMatchTest {

  @Test
  void exactCodeSelectsTheExistingProduct() {
    SkuMatch.Decision decision =
        SkuMatch.match(
            " vgr-001 ",
            List.of(new SkuMatch.ProductRef(10L, "VGR-001", "8901"), new SkuMatch.ProductRef(11L, "VGR-002", null)));
    assertEquals(10L, decision.productId());
    assertEquals(SkuMatch.EXACT_CODE, decision.rule());
    assertFalse(decision.ambiguous());
    assertFalse((Boolean) SkuMatch.toMap(decision).get("createdProduct"));
  }

  @Test
  void exactBarcodeSelectsTheExistingProduct() {
    SkuMatch.Decision decision =
        SkuMatch.match("8901", List.of(new SkuMatch.ProductRef(10L, "VGR-001", "8901")));
    assertEquals(10L, decision.productId());
    assertEquals(SkuMatch.EXACT_BARCODE, decision.rule());
  }

  @Test
  void twoProductsWithTheSameCodeStayUnmatched() {
    SkuMatch.Decision decision =
        SkuMatch.match(
            "VGR-001",
            List.of(new SkuMatch.ProductRef(10L, "VGR-001", null), new SkuMatch.ProductRef(11L, "VGR-001", null)));
    assertNull(decision.productId());
    assertTrue(decision.ambiguous());
    assertEquals(SkuMatch.AMBIGUOUS, decision.rule());
  }

  @Test
  void codeAndBarcodePointingAtDifferentProductsStayUnmatched() {
    SkuMatch.Decision decision =
        SkuMatch.match(
            "SHARED",
            List.of(
                new SkuMatch.ProductRef(10L, "SHARED", "111"),
                new SkuMatch.ProductRef(11L, "OTHER", "SHARED")));
    assertNull(decision.productId());
    assertTrue(decision.ambiguous());
  }

  @Test
  void unknownSkuDoesNotInventAProduct() {
    SkuMatch.Decision decision =
        SkuMatch.match("MISSING", List.of(new SkuMatch.ProductRef(10L, "VGR-001", "8901")));
    assertNull(decision.productId());
    assertEquals(SkuMatch.NONE, decision.rule());
    assertFalse(decision.ambiguous());
  }
}
