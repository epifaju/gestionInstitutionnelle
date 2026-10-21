$ErrorActionPreference = "Stop"
$mainRoot = Resolve-Path "src\main\java"
$testRoot = Resolve-Path "src\test\java"
$mainFiles = Get-ChildItem -Recurse $mainRoot -Filter *.java
$testNames = @(
  Get-ChildItem -Recurse $testRoot -Filter *Test.java |
    ForEach-Object { $_.BaseName -replace 'Test$', '' }
)

$rows = foreach ($f in $mainFiles) {
  $rel = $f.FullName.Substring($mainRoot.Path.Length + 1).Replace('\', '/')
  $base = $f.BaseName
  $module = "other"
  if ($rel -match 'modules/([^/]+)/') { $module = $Matches[1] }
  elseif ($rel -match '^com/app/([^/]+)/') { $module = $Matches[1] }

  $kind = "other"
  if ($rel -match '/service/') { $kind = "service" }
  elseif ($rel -match '/controller/') { $kind = "controller" }
  elseif ($rel -match '/repository/') { $kind = "repository" }
  elseif ($rel -match '/entity/') { $kind = "entity" }
  elseif ($rel -match '/dto/') { $kind = "dto" }
  elseif ($rel -match '/config/') { $kind = "config" }

  [PSCustomObject]@{
    file   = $rel
    base   = $base
    module = $module
    kind   = $kind
    tested = [bool]($testNames -contains $base)
  }
}

New-Item -ItemType Directory -Force -Path "target" | Out-Null
$rows | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 "target\coverage-inventory.json"
Write-Host ("main={0} tests={1} services_tested={2}/{3} controllers_tested={4}/{5}" -f `
  $rows.Count, $testNames.Count, `
  @($rows | Where-Object { $_.kind -eq 'service' -and $_.tested }).Count, `
  @($rows | Where-Object { $_.kind -eq 'service' }).Count, `
  @($rows | Where-Object { $_.kind -eq 'controller' -and $_.tested }).Count, `
  @($rows | Where-Object { $_.kind -eq 'controller' }).Count)
