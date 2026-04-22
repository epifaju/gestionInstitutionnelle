param(
  [string]$EnvFile = "$(Resolve-Path (Join-Path $PSScriptRoot "..\\.env"))"
)

if (-not (Test-Path $EnvFile)) {
  throw "Fichier .env introuvable: $EnvFile"
}

Get-Content $EnvFile | ForEach-Object {
  $line = $_.Trim()
  if (-not $line -or $line.StartsWith("#")) { return }
  $idx = $line.IndexOf("=")
  if ($idx -lt 1) { return }
  $key = $line.Substring(0, $idx).Trim()
  $val = $line.Substring($idx + 1)
  if ($val.Length -ge 2 -and $val.StartsWith('"') -and $val.EndsWith('"')) {
    $val = $val.Substring(1, $val.Length - 2)
  }
  # N'écrase pas une variable déjà définie dans la session
  if (-not [string]::IsNullOrEmpty($key) -and [string]::IsNullOrEmpty(${env:$key})) {
    Set-Item -Path ("Env:{0}" -f $key) -Value $val
  }
}

Write-Host "Variables chargées depuis $EnvFile (si absentes de la session)."

# Maven utilise JAVA_HOME ; forcer JDK 21 si présent (le projet compile en release 21)
$jdk21 = "C:\Program Files\Java\jdk-21.0.10"
if (Test-Path (Join-Path $jdk21 "bin\javac.exe")) {
  $env:JAVA_HOME = $jdk21
  if (-not ($env:Path -like "*$jdk21\\bin*")) {
    $env:Path = "$jdk21\\bin;$env:Path"
  }
  Write-Host "JAVA_HOME défini sur $jdk21"
}

Write-Host "Lancement du backend..."

Set-Location $PSScriptRoot
mvn clean spring-boot:run

