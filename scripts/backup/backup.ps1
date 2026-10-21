# Backup Postgres + MinIO (Windows) pour docker-compose.prod.yml
# Usage: .\scripts\backup\backup.ps1 [-EnvFile .\.env.prod]
param(
  [string]$EnvFile = (Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) ".env.prod"),
  [string]$OutRoot = (Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) "backups")
)
$ErrorActionPreference = "Stop"
$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location $Root
$stamp = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
$Out = Join-Path $OutRoot $stamp
New-Item -ItemType Directory -Force -Path $Out | Out-Null

$vars = @{}
Get-Content $EnvFile | ForEach-Object {
  $line = $_.Trim()
  if ($line -eq "" -or $line.StartsWith("#")) { return }
  $i = $line.IndexOf("=")
  if ($i -lt 1) { return }
  $vars[$line.Substring(0, $i).Trim()] = $line.Substring($i + 1).Trim()
}
$dbUser = if ($vars["DB_USER"]) { $vars["DB_USER"] } else { "gestion" }
$dbName = if ($vars["DB_NAME"]) { $vars["DB_NAME"] } else { "gestion" }
$bucket = if ($vars["MINIO_BUCKET"]) { $vars["MINIO_BUCKET"] } else { "gestion" }
$minioUser = $vars["MINIO_ROOT_USER"]
$minioPass = $vars["MINIO_ROOT_PASSWORD"]

Write-Host "==> Postgres dump -> $Out\postgres.dump"
docker compose -f docker-compose.prod.yml --env-file $EnvFile exec -T postgres `
  pg_dump -U $dbUser -d $dbName -Fc -f /tmp/gestion.dump
docker compose -f docker-compose.prod.yml --env-file $EnvFile cp postgres:/tmp/gestion.dump "$Out\postgres.dump"
docker compose -f docker-compose.prod.yml --env-file $EnvFile exec -T postgres rm -f /tmp/gestion.dump

Write-Host "==> MinIO mirror -> $Out\minio"
$minioDir = Join-Path $Out "minio"
New-Item -ItemType Directory -Force -Path $minioDir | Out-Null
$net = docker inspect -f "{{range `$k,`$v := .NetworkSettings.Networks}}{{`$k}}{{end}}" (docker compose -f docker-compose.prod.yml --env-file $EnvFile ps -q minio)
# Escape password for URL if needed — use mc alias inside container
docker run --rm --network $net `
  -e "MC_HOST_src=http://${minioUser}:${minioPass}@minio:9000" `
  minio/mc:latest `
  mb --ignore-existing "src/$bucket" | Out-Null
docker run --rm --network $net `
  -e "MC_HOST_src=http://${minioUser}:${minioPass}@minio:9000" `
  -v "${minioDir}:/backup" `
  minio/mc:latest `
  mirror --overwrite "src/$bucket" /backup/

Set-Content -Path (Join-Path $Out "MANIFEST.txt") -Value $stamp
Write-Host "Backup termine: $Out"
Write-Output $Out
