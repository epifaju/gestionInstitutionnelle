# R3 — Bootstrap ADMIN métier sur stack prod locale.
# 1) Réactive temporairement admin@test.com
# 2) Login fixture → crée admin@localhost
# 3) Redémarre backend (fixtures re-désactivés)
# Usage: .\scripts\ops\bootstrap-admin.ps1 [-EnvFile .\.env.prod]
param(
  [string]$EnvFile = (Join-Path (Split-Path $PSScriptRoot -Parent | Split-Path -Parent) ".env.prod"),
  [string]$NewEmail = "admin@localhost",
  [string]$NewPassword = "ChangeMeProd2026!"
)
$ErrorActionPreference = "Stop"
$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location $Root

Write-Host "==> Reactivation temporaire admin@test.com"
docker compose -f docker-compose.prod.yml --env-file $EnvFile exec -T postgres `
  psql -U gestion -d gestion -c "UPDATE utilisateurs SET actif = true WHERE lower(email) = lower('admin@test.com');"

Set-Content -Path "$env:TEMP\login-fixture.json" -Value '{"email":"admin@test.com","password":"AdminTest123!"}' -Encoding ascii
$login = curl.exe -sk -X POST https://localhost/api/v1/auth/login `
  -H "Content-Type: application/json" `
  --data-binary "@$env:TEMP\login-fixture.json"
Write-Host "Login response: $login"
if ($login -notmatch '"accessToken"') {
  # Cookie-based auth: extract from Set-Cookie via -c cookie jar
  $cookieJar = "$env:TEMP\gestion-cookies.txt"
  curl.exe -sk -c $cookieJar -X POST https://localhost/api/v1/auth/login `
    -H "Content-Type: application/json" `
    --data-binary "@$env:TEMP\login-fixture.json" -o "$env:TEMP\login-out.json"
  Get-Content "$env:TEMP\login-out.json"
} else {
  $cookieJar = $null
}

$createBody = @{
  email = $NewEmail
  password = $NewPassword
  nom = "Admin"
  prenom = "Prod"
  role = "ADMIN"
} | ConvertTo-Json -Compress
Set-Content -Path "$env:TEMP\create-admin.json" -Value $createBody -Encoding utf8

Write-Host "==> Creation $NewEmail"
if ($cookieJar) {
  curl.exe -sk -b $cookieJar -X POST https://localhost/api/v1/admin/users `
    -H "Content-Type: application/json" `
    --data-binary "@$env:TEMP\create-admin.json" -w "`nHTTP:%{http_code}`n"
} else {
  $token = ($login | ConvertFrom-Json).data.accessToken
  if (-not $token) { $token = ($login | ConvertFrom-Json).accessToken }
  curl.exe -sk -X POST https://localhost/api/v1/admin/users `
    -H "Content-Type: application/json" `
    -H "Authorization: Bearer $token" `
    --data-binary "@$env:TEMP\create-admin.json" -w "`nHTTP:%{http_code}`n"
}

Write-Host "==> Restart backend (fixtures off)"
docker compose -f docker-compose.prod.yml --env-file $EnvFile restart backend
for ($i = 0; $i -lt 40; $i++) {
  $h = curl.exe -sk https://localhost/healthz
  if ($h -match '"status":"UP"') { break }
  Start-Sleep -Seconds 3
}

Write-Host "==> Verif fixture refusee"
curl.exe -sk -X POST https://localhost/api/v1/auth/login `
  -H "Content-Type: application/json" `
  --data-binary "@$env:TEMP\login-fixture.json" -w "`nfixture_HTTP:%{http_code}`n"

Set-Content -Path "$env:TEMP\login-admin.json" -Value (@{email=$NewEmail;password=$NewPassword} | ConvertTo-Json -Compress) -Encoding ascii
Write-Host "==> Verif ADMIN metier"
curl.exe -sk -c "$env:TEMP\admin-cookies.txt" -X POST https://localhost/api/v1/auth/login `
  -H "Content-Type: application/json" `
  --data-binary "@$env:TEMP\login-admin.json" -w "`nadmin_HTTP:%{http_code}`n"

Write-Host "OK bootstrap. Compte: $NewEmail / (mot de passe fourni)"
