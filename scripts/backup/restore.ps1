# Restore Postgres + MinIO depuis un dossier produit par backup.ps1
# Usage: .\scripts\backup\restore.ps1 -BackupDir backups\<stamp> [-ConfirmRestore]
param(
  [Parameter(Mandatory = $true)][string]$BackupDir,
  [string]$EnvFile = (Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) ".env.prod"),
  [switch]$ConfirmRestore
)
$ErrorActionPreference = "Stop"
$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location $Root
if (-not (Test-Path (Join-Path $BackupDir "postgres.dump"))) {
  throw "postgres.dump manquant dans $BackupDir"
}
if (-not $ConfirmRestore) {
  $c = Read-Host "Tapez RESTORE pour confirmer l'ecrasement"
  if ($c -ne "RESTORE") { Write-Host "Annule."; exit 1 }
}

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

Write-Host "==> Restore Postgres"
docker compose -f docker-compose.prod.yml --env-file $EnvFile cp "$BackupDir\postgres.dump" postgres:/tmp/gestion.dump
docker compose -f docker-compose.prod.yml --env-file $EnvFile exec -T postgres `
  pg_restore -U $dbUser -d $dbName --clean --if-exists /tmp/gestion.dump
docker compose -f docker-compose.prod.yml --env-file $EnvFile exec -T postgres rm -f /tmp/gestion.dump

$minioSrc = Join-Path $BackupDir "minio"
if (Test-Path $minioSrc) {
  Write-Host "==> Restore MinIO"
  $net = docker inspect -f "{{range `$k,`$v := .NetworkSettings.Networks}}{{`$k}}{{end}}" (docker compose -f docker-compose.prod.yml --env-file $EnvFile ps -q minio)
  docker run --rm --network $net `
    -e "MC_HOST_dst=http://${minioUser}:${minioPass}@minio:9000" `
    -v "${minioSrc}:/backup:ro" `
    minio/mc:latest `
    mirror --overwrite /backup/ "dst/$bucket"
}

Write-Host "Restore termine. Redemarrez backend si besoin."
