# Prépare un environnement prod local (R1) : JWT, certs auto-signés, .env.prod
# Usage: .\scripts\ops\prepare-local-prod.ps1
# Prérequis: Docker (image alpine/openssl)
$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$EnvProd = Join-Path $Root ".env.prod"
$CertDir = Join-Path $Root "nginx\certs"
$Work = Join-Path $env:TEMP "gestion-prod-prep"
New-Item -ItemType Directory -Force -Path $Work, $CertDir | Out-Null

function New-Secret([int]$len = 28) {
  $chars = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
  -join ((1..$len) | ForEach-Object { $chars[(Get-Random -Maximum $chars.Length)] })
}

Write-Host "==> Pull alpine/openssl"
docker pull alpine/openssl:latest | Out-Null

Write-Host "==> Certificats auto-signes"
docker run --rm -v "${CertDir}:/certs" alpine/openssl:latest `
  req -x509 -nodes -newkey rsa:2048 `
  -keyout /certs/privkey.pem -out /certs/fullchain.pem -days 825 `
  -subj "/CN=localhost"
if (-not (Test-Path (Join-Path $CertDir "fullchain.pem"))) { throw "certs manquants" }

Write-Host "==> Cles JWT RS256"
docker run --rm -v "${Work}:/out" alpine/openssl:latest genrsa -out /out/jwt-private.pem 2048
docker run --rm -v "${Work}:/out" alpine/openssl:latest rsa -in /out/jwt-private.pem -pubout -out /out/jwt-public.pem
# Convertir en PKCS#8 pour Spring
docker run --rm -v "${Work}:/out" alpine/openssl:latest pkcs8 -topk8 -nocrypt -in /out/jwt-private.pem -out /out/jwt-private-pkcs8.pem

$privPem = [IO.File]::ReadAllText((Join-Path $Work "jwt-private-pkcs8.pem"))
$pubPem = [IO.File]::ReadAllText((Join-Path $Work "jwt-public.pem"))
$privB64 = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($privPem.Trim() + "`n"))
$pubB64 = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pubPem.Trim() + "`n"))

Remove-Item $Work -Recurse -Force -ErrorAction SilentlyContinue

$dbPass = New-Secret 28
$minioUser = "minioadmin"
$minioPass = New-Secret 28

Write-Host "==> Ecriture .env.prod"
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
$envBody = @"
DB_NAME=gestion
DB_USER=gestion
DB_PASSWORD=$dbPass
JWT_PRIVATE_KEY_B64=$privB64
JWT_PUBLIC_KEY_B64=$pubB64
MINIO_ROOT_USER=$minioUser
MINIO_ROOT_PASSWORD=$minioPass
MINIO_BUCKET=gestion
MINIO_PUBLIC_ENDPOINT=https://localhost
FRONTEND_URL=https://localhost
REFRESH_COOKIE_SECURE=true
EMAIL_ENABLED=false
EMAIL_HEALTH_ENABLED=false
"@
[IO.File]::WriteAllText($EnvProd, $envBody, $utf8NoBom)

Write-Host "OK: $EnvProd"
Write-Host "Verifier: .\scripts\check-prod-env.ps1 -EnvFile .\.env.prod"
