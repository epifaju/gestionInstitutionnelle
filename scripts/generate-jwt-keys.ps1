# Génère une paire RSA 2048 pour JWT RS256 et affiche les variables .env (base64 une ligne).
$ErrorActionPreference = "Stop"

$priv = Join-Path $env:TEMP "gestion-jwt-private.pem"
$pub = Join-Path $env:TEMP "gestion-jwt-public.pem"

openssl genrsa -out $priv 2048 2>$null | Out-Null
openssl rsa -in $priv -pubout -out $pub 2>$null | Out-Null

$privB64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($priv))
$pubB64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($pub))

Remove-Item $priv, $pub -Force -ErrorAction SilentlyContinue

Write-Host "# Coller dans .env (echappez `$ en `$`$ sous Docker Compose si besoin)"
Write-Host "JWT_PRIVATE_KEY_B64=$privB64"
Write-Host "JWT_PUBLIC_KEY_B64=$pubB64"
