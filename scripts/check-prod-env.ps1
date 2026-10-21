# Vérifie qu'un fichier .env est apte à une mise en prod.
# Usage: .\scripts\check-prod-env.ps1 [-EnvFile .\.env]
param(
  [string]$EnvFile = (Join-Path (Split-Path $PSScriptRoot -Parent) ".env")
)

$ErrorActionPreference = "Stop"
$err = 0
function Fail($msg) { Write-Host "ERREUR: $msg" -ForegroundColor Red; $script:err = 1 }
function Ok($msg) { Write-Host "OK: $msg" -ForegroundColor Green }

if (-not (Test-Path $EnvFile)) {
  Fail "Fichier introuvable: $EnvFile"
  exit 1
}

$vars = @{}
Get-Content $EnvFile | ForEach-Object {
  $line = $_.Trim()
  if ($line -eq "" -or $line.StartsWith("#")) { return }
  $i = $line.IndexOf("=")
  if ($i -lt 1) { return }
  $k = $line.Substring(0, $i).Trim()
  $v = $line.Substring($i + 1).Trim().Trim('"').Trim("'")
  $vars[$k] = $v
}

function Require($name) {
  if (-not $vars.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($vars[$name])) {
    Fail "$name est vide"
  } else {
    Ok "$name renseigne"
  }
}

function ForbidDefault($name, $bad) {
  if ($vars.ContainsKey($name) -and $vars[$name] -eq $bad) {
    Fail "$name utilise encore la valeur d'exemple ($bad)"
  }
}

Require "DB_NAME"
Require "DB_USER"
Require "DB_PASSWORD"
ForbidDefault "DB_PASSWORD" "gestion"

Require "JWT_PRIVATE_KEY_B64"
Require "JWT_PUBLIC_KEY_B64"

Require "MINIO_ROOT_USER"
Require "MINIO_ROOT_PASSWORD"
ForbidDefault "MINIO_ROOT_PASSWORD" "minio12345"
ForbidDefault "MINIO_ROOT_USER" "minio"

Require "FRONTEND_URL"
if (-not ($vars["FRONTEND_URL"] -like "https://*")) {
  Fail "FRONTEND_URL doit commencer par https:// (actuel: $($vars['FRONTEND_URL']))"
} else {
  Ok "FRONTEND_URL en HTTPS"
}

if ($vars["REFRESH_COOKIE_SECURE"] -ne "true") {
  Fail "REFRESH_COOKIE_SECURE doit etre true en HTTPS"
} else {
  Ok "REFRESH_COOKIE_SECURE=true"
}

$root = Split-Path $PSScriptRoot -Parent
$certDir = Join-Path $root "nginx\certs"
if (-not (Test-Path (Join-Path $certDir "fullchain.pem")) -or -not (Test-Path (Join-Path $certDir "privkey.pem"))) {
  Fail "Certificats manquants: nginx/certs/fullchain.pem et privkey.pem"
} else {
  Ok "Certificats TLS presents"
}

if ($err -ne 0) {
  Write-Host ""
  Write-Host "Corrigez les erreurs avant: docker compose -f docker-compose.prod.yml up -d --build" -ForegroundColor Red
  exit 1
}

Write-Host ""
Write-Host "Environnement prod OK."
