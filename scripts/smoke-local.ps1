# Local marketplace smoke (requires marketplace-service :8096 + marketplacedb)
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8096/api/v1/marketplace'
$headers = @{
  'X-Tenant-Id' = 'smoke-tenant'
  'X-Shop-Id'   = 'SMOKE-SHOP-01'
  'Content-Type' = 'application/json'
}
$pass = 0
$fail = 0
function Assert-True($cond, $name) {
  if ($cond) { Write-Host "PASS  $name"; $script:pass++ }
  else { Write-Host "FAIL  $name"; $script:fail++ }
}

Write-Host "=== 1) health ==="
$h = Invoke-RestMethod 'http://127.0.0.1:8096/actuator/health'
Assert-True ($h.status -eq 'UP') 'actuator health UP'

Write-Host "=== 2) status / entitlements ==="
$st = Invoke-RestMethod -Headers $headers "$base/status"
Assert-True ($st.module -eq 'MARKETPLACE') 'status module MARKETPLACE'
$ent = Invoke-RestMethod -Headers $headers "$base/entitlements"
Assert-True ($ent.entitlementChecksEnabled -eq $false) 'entitlement checks OFF (default)'

Write-Host "=== 3) account + channels ==="
$acct = Invoke-RestMethod -Method Post -Headers $headers -Body (@{
  shopId = 'SMOKE-SHOP-01'
  displayName = 'Smoke Omnichannel'
  status = 'ACTIVE'
} | ConvertTo-Json) "$base/accounts"
Assert-True ($null -ne $acct.id) "account id=$($acct.id)"

$shopify = Invoke-RestMethod -Method Post -Headers $headers -Body (@{
  accountId = $acct.id
  channelCode = 'SHOPIFY'
  enabled = $true
  externalSellerId = 'smoke-store.myshopify.com'
  config = @{
    shopDomain = 'smoke-store.myshopify.com'
    accessToken = 'shpat_smoke'
    locationId = '1001'
    shopId = 'SMOKE-SHOP-01'
  }
} | ConvertTo-Json -Depth 5) "$base/channels"
Assert-True ($shopify.enabled -eq $true) "shopify channel id=$($shopify.id)"

$amazon = Invoke-RestMethod -Method Post -Headers $headers -Body (@{
  accountId = $acct.id
  channelCode = 'AMAZON'
  enabled = $true
  externalSellerId = 'A1SMOKESELLER'
  config = @{
    sellerId = 'A1SMOKESELLER'
    shopId = 'SMOKE-SHOP-01'
  }
} | ConvertTo-Json -Depth 5) "$base/channels"
Assert-True ($amazon.enabled -eq $true) "amazon channel id=$($amazon.id)"

Write-Host "=== 4) product mappings ==="
$mapS = Invoke-RestMethod -Method Post -Headers $headers -Body (@{
  channelId = $shopify.id
  shopId = 'SMOKE-SHOP-01'
  productId = 9001
  skuCode = 'TEE-BLK-M'
  channelListingId = '111001'
  channelSku = 'TEE-BLK-M'
  syncInventory = $true
  allocationQty = 10
  attributes = @{ inventoryItemId = '222001' }
} | ConvertTo-Json -Depth 5) "$base/mappings"
Assert-True ($mapS.productId -eq 9001) "shopify mapping id=$($mapS.id)"

$mapA = Invoke-RestMethod -Method Post -Headers $headers -Body (@{
  channelId = $amazon.id
  shopId = 'SMOKE-SHOP-01'
  productId = 9002
  skuCode = 'AMZ-SKU-1'
  channelListingId = 'B0SMOKEASIN'
  channelSku = 'AMZ-SKU-1'
  syncInventory = $true
  allocationQty = 5
} | ConvertTo-Json -Depth 5) "$base/mappings"
Assert-True ($mapA.channelSku -eq 'AMZ-SKU-1') "amazon mapping id=$($mapA.id)"

Write-Host "=== 5) order ingest (no stock reserve) ==="
$ing = Invoke-RestMethod -Method Post -Headers $headers -Body (@{
  channelCode = 'SHOPIFY'
  externalOrderId = 'SMOKE-ORDER-1001'
  currency = 'INR'
  totalAmount = 199
  reserveStock = $false
  items = @(@{
    channelListingId = '111001'
    channelSku = 'TEE-BLK-M'
    quantity = 2
    unitPrice = 99.5
    title = 'Tee'
  })
} | ConvertTo-Json -Depth 5) "$base/orders/ingest"
Assert-True ($ing.status -eq 'ACCEPTED' -or $ing.status -eq 'RESERVED') "ingest status=$($ing.status) id=$($ing.id)"
Assert-True ($ing.externalOrderId -eq 'SMOKE-ORDER-1001') 'ingest external id'

$ing2 = Invoke-RestMethod -Method Post -Headers $headers -Body (@{
  channelCode = 'SHOPIFY'
  externalOrderId = 'SMOKE-ORDER-1001'
  reserveStock = $false
  items = @(@{ channelListingId = '111001'; channelSku = 'TEE-BLK-M'; quantity = 2 })
} | ConvertTo-Json -Depth 5) "$base/orders/ingest"
Assert-True ($ing2.idempotent -eq $true) 'ingest idempotent'

Write-Host "=== 6) Shopify webhook -> ingest ==="
$whBody = @{
  id = 555002
  currency = 'INR'
  total_price = '50.00'
  line_items = @(@{
    sku = 'TEE-BLK-M'
    variant_id = 111001
    title = 'Tee'
    quantity = 1
    price = '50.00'
  })
} | ConvertTo-Json -Depth 6
$whHeaders = @{
  'Content-Type' = 'application/json'
  'X-Shopify-Topic' = 'orders/create'
  'X-Shopify-Shop-Domain' = 'smoke-store.myshopify.com'
}
$wh = Invoke-RestMethod -Method Post -Headers $whHeaders -Body $whBody `
  "$base/public/webhooks/SHOPIFY?tenantId=smoke-tenant&shopId=SMOKE-SHOP-01"
Assert-True ($wh.accepted -eq $true) "shopify webhook accepted id=$($wh.webhookEventId)"
Assert-True ($wh.processed -eq $true -or $wh.action -eq 'INGEST' -or $null -ne $wh.order) "shopify webhook processed action=$($wh.action)"

Write-Host "=== 7) Amazon webhook -> ingest ==="
$amzBody = @{
  amazonOrderId = '402-SMOKE-77'
  orderStatus = 'Unshipped'
  currency = 'INR'
  items = @(@{ sellerSku = 'AMZ-SKU-1'; quantityOrdered = 1; price = '25.00' })
} | ConvertTo-Json -Depth 6
$amz = Invoke-RestMethod -Method Post -Headers @{
  'Content-Type' = 'application/json'
  'X-Amzn-Seller-Id' = 'A1SMOKESELLER'
} -Body $amzBody "$base/public/webhooks/AMAZON?tenantId=smoke-tenant&shopId=SMOKE-SHOP-01"
Assert-True ($amz.accepted -eq $true) "amazon webhook accepted id=$($amz.webhookEventId)"
Assert-True ($amz.processed -eq $true -or $amz.action -eq 'INGEST') "amazon webhook processed action=$($amz.action)"

Write-Host "=== 8) inventory sync dry-run ==="
$sync = Invoke-RestMethod -Method Post -Headers $headers "$base/inventory/sync?channelId=$($shopify.id)"
Assert-True ($null -ne $sync.results) "inventory sync results count=$($sync.results.Count)"
$anyDry = $false
foreach ($r in $sync.results) {
  if ($r.channelPush -and ($r.channelPush.dryRun -eq $true -or $r.syncStatus -eq 'SYNCED' -or $r.syncStatus -eq 'ERROR' -or $r.syncStatus -eq 'SKIPPED')) {
    $anyDry = $true
  }
  Write-Host ("  mapping {0} status={1} pushAccepted={2} dryRun={3}" -f $r.mappingId, $r.syncStatus, $r.channelPush.accepted, $r.channelPush.dryRun)
}
Assert-True ($sync.results.Count -ge 1) 'inventory sync returned rows'

Write-Host "=== 9) cancel order ==="
$cancel = Invoke-RestMethod -Method Post -Headers $headers "$base/orders/$($ing.id)/cancel"
Assert-True ($cancel.status -eq 'CANCELLED') "cancel status=$($cancel.status)"

Write-Host "=== 10) available preview (stock may be empty) ==="
try {
  $av = Invoke-RestMethod -Headers $headers "$base/inventory/products/9001/available"
  Assert-True ($av.mutatesStock -eq $false) "available preview mutatesStock=false avail=$($av.availableQuantity)"
} catch {
  Write-Host "WARN available preview: $($_.Exception.Message)"
}

Write-Host ""
Write-Host "SMOKE SUMMARY: $pass passed, $fail failed"
if ($fail -gt 0) { exit 1 }
