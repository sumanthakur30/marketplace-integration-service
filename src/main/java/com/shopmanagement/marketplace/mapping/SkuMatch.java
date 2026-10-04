package com.shopmanagement.marketplace.mapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Picks an existing product when a channel SKU equals one product code or one barcode.
 * More than one match stays unmatched. This class never describes a new product.
 */
public final class SkuMatch {

  public static final String EXACT_CODE = "EXACT_CODE";
  public static final String EXACT_BARCODE = "EXACT_BARCODE";
  public static final String NONE = "NONE";
  public static final String AMBIGUOUS = "AMBIGUOUS";

  private SkuMatch() {}

  public record ProductRef(Long productId, String code, String barcode) {}

  public record Decision(Long productId, String skuCode, String rule, boolean ambiguous) {}

  public static Decision match(String channelSku, List<ProductRef> products) {
    String needle = normalize(channelSku);
    if (needle == null || products == null || products.isEmpty()) {
      return unmatched();
    }
    Long byCode = uniqueProduct(products, needle, true);
    Long byBarcode = uniqueProduct(products, needle, false);
    if (hasDuplicate(products, needle, true) || hasDuplicate(products, needle, false)) {
      return ambiguous();
    }
    if (byCode != null && byBarcode != null && !byCode.equals(byBarcode)) {
      return ambiguous();
    }
    if (byCode != null) {
      return new Decision(byCode, codeOf(products, byCode), EXACT_CODE, false);
    }
    if (byBarcode != null) {
      return new Decision(byBarcode, codeOf(products, byBarcode), EXACT_BARCODE, false);
    }
    return unmatched();
  }

  public static Map<String, Object> toMap(Decision decision) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("productId", decision.productId());
    out.put("skuCode", decision.skuCode());
    out.put("rule", decision.rule());
    out.put("ambiguous", decision.ambiguous());
    out.put("createdProduct", false);
    return out;
  }

  private static Decision unmatched() {
    return new Decision(null, null, NONE, false);
  }

  private static Decision ambiguous() {
    return new Decision(null, null, AMBIGUOUS, true);
  }

  private static Long uniqueProduct(List<ProductRef> products, String needle, boolean code) {
    Long found = null;
    for (ProductRef product : products) {
      if (product == null || product.productId() == null || product.productId() <= 0) {
        continue;
      }
      String value = code ? product.code() : product.barcode();
      if (!needle.equals(normalize(value))) {
        continue;
      }
      if (found != null && !found.equals(product.productId())) {
        return null;
      }
      found = product.productId();
    }
    return found;
  }

  private static boolean hasDuplicate(List<ProductRef> products, String needle, boolean code) {
    Long first = null;
    for (ProductRef product : products) {
      if (product == null || product.productId() == null) {
        continue;
      }
      String value = code ? product.code() : product.barcode();
      if (!needle.equals(normalize(value))) {
        continue;
      }
      if (first != null && !first.equals(product.productId())) {
        return true;
      }
      first = product.productId();
    }
    return false;
  }

  private static String codeOf(List<ProductRef> products, Long productId) {
    for (ProductRef product : products) {
      if (product != null && productId.equals(product.productId()) && product.code() != null) {
        String code = product.code().trim();
        return code.isEmpty() ? null : code;
      }
    }
    return null;
  }

  private static String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    return trimmed.toUpperCase(Locale.ROOT);
  }
}
