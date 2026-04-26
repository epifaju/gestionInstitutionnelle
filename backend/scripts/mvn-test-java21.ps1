Param(
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]]$MavenArgs
)

$ErrorActionPreference = "Stop"

function Get-JavaMajor([string]$javaExe) {
  try {
    $out = & $javaExe -version 2>&1 | Select-Object -First 1
    if ($out -match '"(\d+)(\.\d+)*') { return [int]$Matches[1] }
  } catch {}
  return $null
}

function Resolve-Jdk21Home() {
  # 0) Try resolving from the current `java` on PATH (java.home).
  try {
    $props = & java -XshowSettings:properties -version 2>&1
    $homeLine = $props | Where-Object { $_ -match '^\s*java\.home\s*=\s*(.+)\s*$' } | Select-Object -First 1
    if ($homeLine -and $homeLine -match '^\s*java\.home\s*=\s*(.+)\s*$') {
      $home = $Matches[1].Trim()
      # If the resolved java.home looks like a JDK 21, prefer it.
      if ($home -match 'jdk-?21') {
        $javaExe = Join-Path $home "bin\java.exe"
        if (Test-Path $javaExe) { return $home }
      }
    }
  } catch {}

  # 1) If JAVA_HOME already points to Java 21, keep it.
  if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    $javaExe = Join-Path $env:JAVA_HOME "bin\java.exe"
    $major = Get-JavaMajor $javaExe
    if ($major -eq 21) { return $env:JAVA_HOME }
  }

  # 2) Common installation locations.
  $candidates = @(
    "C:\Program Files\Java",
    "C:\Program Files\Eclipse Adoptium",
    "C:\Program Files\Microsoft",
    "C:\Program Files\BellSoft"
  ) | Where-Object { Test-Path $_ }

  foreach ($root in $candidates) {
    $jdks =
      Get-ChildItem -Path $root -Directory -ErrorAction SilentlyContinue |
      Where-Object { $_.Name -match 'jdk-?21' } |
      Sort-Object -Property Name -Descending

    foreach ($jdk in $jdks) {
      $javaExe = Join-Path $jdk.FullName "bin\java.exe"
      if (Test-Path $javaExe) {
        $major = Get-JavaMajor $javaExe
        if ($major -eq 21) { return $jdk.FullName }
      }
    }
  }

  return $null
}

$jdkHome = Resolve-Jdk21Home
if (-not $jdkHome) {
  Write-Error "JDK 21 introuvable. Installez un JDK 21 (Temurin/Oracle/etc.) et/ou définissez JAVA_HOME vers ce JDK, puis relancez."
}

$env:JAVA_HOME = $jdkHome
$env:PATH = (Join-Path $env:JAVA_HOME "bin") + ";" + $env:PATH

Write-Host ("Using JAVA_HOME=" + $env:JAVA_HOME)

if (-not $MavenArgs -or $MavenArgs.Count -eq 0) {
  $MavenArgs = @("test")
}

& mvn @MavenArgs
