# R5 — Sonde locale /healthz (équivalent cron / UptimeRobot)
# Exit 0 si UP, 1 sinon. Usage: .\scripts\ops\probe-healthz.ps1 [-Url https://localhost/healthz]
param(
  [string]$Url = "https://localhost/healthz",
  [switch]$SkipTlsVerify
)
$ErrorActionPreference = "Stop"
$args = @("-s", "-o", "$env:TEMP\healthz-body.json", "-w", "%{http_code}")
if ($SkipTlsVerify -or $Url -match "localhost") { $args = @("-sk") + $args[1..($args.Length-1)] }
# curl: -sk -o file -w code
$code = & curl.exe -sk -o "$env:TEMP\healthz-body.json" -w "%{http_code}" $Url
$body = Get-Content "$env:TEMP\healthz-body.json" -Raw -ErrorAction SilentlyContinue
Write-Host "HTTP $code body=$body"
if ($code -ne "200" -or $body -notmatch '"status"\s*:\s*"UP"') {
  Write-Host "ALERT: healthz DOWN"
  exit 1
}
Write-Host "OK: healthz UP"
exit 0
