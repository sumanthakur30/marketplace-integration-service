# Stock reserve smoke (requires marketplace :8096 + stock-service :8082)
# stock-service requires numeric X-Tenant-Id.
$ErrorActionPreference = 'Stop'
$pass = 0
$fail = 0
function Assert-True($cond, $name) {
  if ($cond) { Write-Host "PASS  $name"; $script:pass++ }
  else { Write-Host "FAIL  $name"; $script:fail++ }
}

$sh = @{
  'X-Tenant-Id'  = '1'
  'X-Shop-Id'    = 'SMOKE-SHOP-01'
  'Content-Type' = 'application/json'
}
$mh = @{
  'X-Tenant-Id'  = '1'
  'X-Shop-Id'    = 'SMOKE-SHOP-01'
  'Content-Type' = 'application/json'
}

Write-Host '=== reserve smoke: health ==='
Assert-True ((Invoke-RestMethod http://127.0.0.1:8082/actuator/health).status -eq 'UP') 'stock UP'
Assert-True ((Invoke-RestMethod http://127.0.0.1:8096/actuator/health).status -eq 'UP') 'marketplace UP'

Write-Host '=== ensure stock + marketplace mapping ==='
try {
  $c0 = Invoke-RestMethod -Headers $sh 'http://127.0.0.1:8082/stock/check?productId=9001'
} catch {
  $c0 = $null
}
if (-not $c0 -or [int]$c0.availableQuantity -lt 5) {
  Invoke-RestMethod -Method Post -Headers $sh -Body (@{
      productId   = 9001
      quantity    = 100
      productName = 'Smoke Tee'
    } | ConvertTo-Json) http://127.0.0.1:8082/stock/init | Out-Null
  $c0 = Invoke-RestMethod -Headers $sh 'http://127.0.0.1:8082/stock/check?productId=9001'
}
Write-Host ("before reserved={0} avail={1}" -f $c0.reserved, $c0.availableQuantity)

$acct = Invoke-RestMethod -Method Post -Headers $mh -Body (@{
    shopId      = 'SMOKE-SHOP-01'
    displayName = 'Reserve Smoke'
    status      = 'ACTIVE'
  } | ConvertTo-Json) http://127.0.0.1:8096/api/v1/marketplace/accounts

$channels = Invoke-RestMethod -Headers $mh 'http://127.0.0.1:8096/api/v1/marketplace/channels'
$ch = @($channels) | Where-Object { $_.channelCode -eq 'SHOPIFY' } | Select-Object -First 1
if (-not $ch) {
  $ch = Invoke-RestMethod -Method Post -Headers $mh -Body (@{
      accountId        = $acct.id
      channelCode      = 'SHOPIFY'
      enabled          = $true
      externalSellerId = 'reserve-store.myshopify.com'
      config           = @{
        shopDomain  = 'reserve-store.myshopify.com'
        accessToken = 'x'
        locationId  = '1'
        shopId      = 'SMOKE-SHOP-01'
      }
    } | ConvertTo-Json -Depth 5) http://127.0.0.1:8096/api/v1/marketplace/channels
}

$maps = Invoke-RestMethod -Headers $mh "http://127.0.0.1:8096/api/v1/marketplace/mappings?channelId=$($ch.id)"
$map = @($maps) | Where-Object { $_.productId -eq 9001 } | Select-Object -First 1
if (-not $map) {
  $map = Invoke-RestMethod -Method Post -Headers $mh -Body (@{
      channelId        = $ch.id
      shopId           = 'SMOKE-SHOP-01'
      productId        = 9001
      skuCode          = 'TEE-BLK-M'
      channelListingId = '111001'
      channelSku       = 'TEE-BLK-M'
      syncInventory    = $true
      allocationQty    = 10
      attributes       = @{ inventoryItemId = '222001' }
    } | ConvertTo-Json -Depth 5) http://127.0.0.1:8096/api/v1/marketplace/mappings
}

Write-Host '=== ingest with reserveStock=true ==='
$oid = 'SMOKE-RESERVE-' + [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$ing = Invoke-RestMethod -Method Post -Headers $mh -Body (@{
    channelCode     = 'SHOPIFY'
    externalOrderId = $oid
    currency        = 'INR'
    totalAmount     = 99.5
    reserveStock    = $true
    items           = @(@{
        channelListingId = '111001'
        channelSku       = 'TEE-BLK-M'
        quantity         = 2
        unitPrice        = 49.75
      })
  } | ConvertTo-Json -Depth 5) http://127.0.0.1:8096/api/v1/marketplace/orders/ingest

Assert-True ($ing.status -eq 'RESERVED') "ingest RESERVED status=$($ing.status) id=$($ing.id)"
Assert-True ($null -ne $ing.stockReservationKey) "reservationKey=$($ing.stockReservationKey)"

$c1 = Invoke-RestMethod -Headers $sh 'http://127.0.0.1:8082/stock/check?productId=9001'
Assert-True ([int]$c1.reserved -ge ([int]$c0.reserved + 2)) ("stock reserved $($c0.reserved)->$($c1.reserved)")

Write-Host '=== cancel releases reserve ==='
$cancel = Invoke-RestMethod -Method Post -Headers $mh "http://127.0.0.1:8096/api/v1/marketplace/orders/$($ing.id)/cancel"
Assert-True ($cancel.status -eq 'CANCELLED') 'cancel after reserve'
$c2 = Invoke-RestMethod -Headers $sh 'http://127.0.0.1:8082/stock/check?productId=9001'
Assert-True ([int]$c2.reserved -le [int]$c0.reserved) ("stock released $($c1.reserved)->$($c2.reserved)")

Write-Host ""
Write-Host "RESERVE SMOKE SUMMARY: $pass passed, $fail failed"
if ($fail -gt 0) { exit 1 }
