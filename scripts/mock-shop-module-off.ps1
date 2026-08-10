# Mock shop-service module probe (MARKETPLACE OFF) on :18080
$ErrorActionPreference = 'Stop'
$prefix = 'http://127.0.0.1:18080/'
$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add($prefix)
$listener.Start()
Write-Host "mock shop listening on $prefix (Ctrl+C to stop)"
while ($listener.IsListening) {
  $ctx = $listener.GetContext()
  $path = $ctx.Request.Url.AbsolutePath
  $code = $ctx.Request.QueryString['code']
  $body = 'false'
  if ($path -match '/modules/enabled' -and $code -eq 'MARKETPLACE') {
    $body = 'false'
  } elseif ($path -eq '/health' -or $path -eq '/actuator/health') {
    $body = '{"status":"UP"}'
  }
  $buf = [Text.Encoding]::UTF8.GetBytes($body)
  $ctx.Response.StatusCode = 200
  $ctx.Response.ContentType = 'application/json'
  $ctx.Response.ContentLength64 = $buf.Length
  $ctx.Response.OutputStream.Write($buf, 0, $buf.Length)
  $ctx.Response.Close()
  Write-Host ("{0} {1}?code={2} -> {3}" -f $ctx.Request.HttpMethod, $path, $code, $body)
}
