package com.shopmanagement.marketplace.channel.amazon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.shopmanagement.marketplace.web.dto.OrderDtos;

class AmazonSpApiTest {

  @Test
  void signsTheAwsGetExample() {
    String authorization =
        AwsSigV4.authorization(
            "GET",
            "/",
            "Action=ListUsers&Version=2010-05-08",
            Map.of("host", "iam.amazonaws.com", "x-amz-date", "20150830T123600Z"),
            "us-east-1",
            "iam",
            "AKIDEXAMPLE",
            "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY",
            "20150830T123600Z");
    assertEquals(
        "GET\n/\nAction=ListUsers&Version=2010-05-08\nhost:iam.amazonaws.com\nx-amz-date:20150830T123600Z\n\nhost;x-amz-date\ne3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        AwsSigV4.canonicalRequest(
            "GET",
            "/",
            "Action=ListUsers&Version=2010-05-08",
            Map.of("host", "iam.amazonaws.com", "x-amz-date", "20150830T123600Z")));
    assertEquals(
        "c4afb1cc5771d871763a393e44b703571b55cc28424d1a5e86da6ed3c154a4b9",
        AwsSigV4.signingKeyHex("wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY", "20150830", "us-east-1", "iam"));
    assertTrue(authorization.startsWith("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20150830/us-east-1/iam/aws4_request, SignedHeaders=host;x-amz-date, Signature="));
  }

  @Test
  void consentUrlCarriesTheAppIdAndStateOnly() {
    String url =
        AmazonOauthState.consentUrl(
            "https://sellercentral.amazon.in/apps/authorize/consent", "amzn1.sellerapps.app", "4.100.abc", true);
    assertTrue(url.contains("application_id=amzn1.sellerapps.app"));
    assertTrue(url.contains("state=4.100.abc"));
    assertTrue(url.contains("version=beta"));
    assertFalse(url.toLowerCase().contains("secret"));
    assertFalse(url.toLowerCase().contains("token"));
  }

  @Test
  void stateMustMatchTheChannelAndTenant() {
    String state = AmazonOauthState.sign(4L, "22", 1_000L, "local-key");
    AmazonOauthState.verify(state, 4L, "22", "local-key", 900L);
    assertThrows(IllegalArgumentException.class, () -> AmazonOauthState.verify(state, 4L, "22", "local-key", 1_001L));
    assertThrows(IllegalArgumentException.class, () -> AmazonOauthState.verify(state, 9L, "22", "local-key", 900L));
    assertThrows(IllegalArgumentException.class, () -> AmazonOauthState.verify(state + "x", 4L, "22", "local-key", 900L));
  }

  @Test
  void mapsUnshippedLinesWithoutBuyerDetails() {
    String orders =
        """
        {"payload":{"Orders":[{"AmazonOrderId":"408-1","OrderStatus":"Unshipped","PurchaseDate":"2026-10-01T00:00:00Z","OrderTotal":{"CurrencyCode":"INR","Amount":"100.00"},"BuyerInfo":{"BuyerEmail":"hidden@amazon"}}],"NextToken":"cursor-1"}}
        """;
    String items =
        """
        {"payload":{"OrderItems":[{"ASIN":"B00","SellerSKU":"SKU-1","Title":"Tea","QuantityOrdered":2,"ItemPrice":{"Amount":"100.00"},"BuyerInfo":{"BuyerCustomizedInfo":"no"}}]}}
        """;
    assertEquals("cursor-1", AmazonOrderMapper.nextToken(orders));
    OrderDtos.IngestRequest request = AmazonOrderMapper.toIngest(orders, Map.of("408-1", items)).get(0);
    assertEquals("408-1", request.externalOrderId());
    assertEquals("INR", request.currency());
    assertEquals("SKU-1", request.items().get(0).channelSku());
    assertEquals("B00", request.items().get(0).channelListingId());
    assertEquals("Unshipped", request.payload().get("externalStatus"));
    assertFalse(request.payload().containsKey("BuyerInfo"));
    assertFalse(request.toString().contains("hidden@amazon"));
  }
}
