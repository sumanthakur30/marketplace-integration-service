# Entitlement-ON negative smoke.
# Prerequisites: marketplace running with:
#   MARKETPLACE_ENTITLEMENT_ENABLED=true
#   MARKETPLACE_ENTITLEMENT_FAIL_OPEN=false
#   MARKETPLACE_SHOP_BASE_URL=http://127.0.0.1:18080
# and scripts/mock-shop-module-off.ps1 listening (returns false for MARKETPLACE).
$ErrorActionPreference = 'Stop'
$pass = 0
$fail = 0
function Assert-True($cond, $name) {
  if ($cond) { Write-Host "PASS  $name"; $script:pass++ }
  else { Write-Host "FAIL  $name"; $script:fail++ }
}

$mh = @{
  'X-Tenant-Id'  = '1'
  'X-Shop-Id'    = 'SMOKE-SHOP-01'
  'Content-Type' = 'application/json'
}

$ent = Invoke-RestMethod -Headers $mh http://127.0.0.1:8096/api/v1/marketplace/entitlements
Assert-True ($ent.entitlementChecksEnabled -eq $true) "entitlementChecksEnabled=$($ent.entitlementChecksEnabled)"

try {
  Invoke-WebRequest -Method Post -Headers $mh -Body (@{
      shopId = 'SMOKE-SHOP-01'; displayName = 'Denied'; status = 'ACTIVE'
    } | ConvertTo-Json) -Uri http://127.0.0.1:8096/api/v1/marketplace/accounts -UseBasicParsing | Out-Null
  Assert-True $false 'expected 402 on accounts create'
} catch {
  $code = [int]$_.Exception.Response.StatusCode
  Assert-True ($code -eq 402) "accounts create denied HTTP $code"
}

try {
  Invoke-WebRequest -Method Post -Headers $mh -Body (@{
      channelCode = 'SHOPIFY'; externalOrderId = 'ENT-DENY-1'; reserveStock = $false
      items       = @(@{ channelListingId = '111001'; channelSku = 'TEE-BLK-M'; quantity = 1 })
    } | ConvertTo-Json -Depth 5) -Uri http://127.0.0.1:8096/api/v1/marketplace/orders/ingest -UseBasicParsing | Out-Null
  Assert-True $false 'expected 402 on ingest'
} catch {
  $code = [int]$_.Exception.Response.StatusCode
  Assert-True ($code -eq 402) "ingest denied HTTP $code"
}

Write-Host ""
Write-Host "ENTITLEMENT NEGATIVE SUMMARY: $pass passed, $fail failed"
if ($fail -gt 0) { exit 1 }
