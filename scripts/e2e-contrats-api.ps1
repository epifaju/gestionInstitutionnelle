#Requires -Version 5.1
<#
.SYNOPSIS
  Tests API manuels (E2E) du module contrats / échéances RH + quelques checks de non-régression.

.DESCRIPTION
  - Charge scripts/e2e-contrats.env s'il existe (copier depuis e2e-contrats.example.env).
  - Utilise curl.exe (Windows) pour lire le code HTTP et le corps JSON.
  - Prérequis : backend démarré, migrations appliquées (V21 = comptes rh/financier/employé + salarié fixture).

.EXAMPLE
  .\scripts\e2e-contrats-api.ps1
  $env:API_BASE = "http://localhost/api/v1"; .\scripts\e2e-contrats-api.ps1
#>

$ErrorActionPreference = "Stop"

function Load-DotEnv {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return }
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line -match '^\s*#' -or $line -eq "") { return }
        $i = $line.IndexOf("=")
        if ($i -lt 1) { return }
        $name = $line.Substring(0, $i).Trim()
        $val = $line.Substring($i + 1).Trim()
        [Environment]::SetEnvironmentVariable($name, $val, "Process")
    }
}

$envPath = Join-Path $PSScriptRoot "e2e-contrats.env"
Load-DotEnv $envPath

$apiBase = if ($env:API_BASE) { $env:API_BASE.TrimEnd("/") } else { "http://localhost:8080/api/v1" }
$salarieId = if ($env:E2E_SALARIE_ID) { $env:E2E_SALARIE_ID } else { "c0e2e0e0-0000-4000-8000-000000000001" }

$rhEmail = if ($env:E2E_RH_EMAIL) { $env:E2E_RH_EMAIL } else { "rh@test.com" }
$rhPass = if ($env:E2E_RH_PASSWORD) { $env:E2E_RH_PASSWORD } else { "AdminTest123!" }
$adminEmail = if ($env:E2E_ADMIN_EMAIL) { $env:E2E_ADMIN_EMAIL } else { "admin@test.com" }
$adminPass = if ($env:E2E_ADMIN_PASSWORD) { $env:E2E_ADMIN_PASSWORD } else { "AdminTest123!" }
$finEmail = if ($env:E2E_FINANCIER_EMAIL) { $env:E2E_FINANCIER_EMAIL } else { "financier@test.com" }
$finPass = if ($env:E2E_FINANCIER_PASSWORD) { $env:E2E_FINANCIER_PASSWORD } else { "AdminTest123!" }
$empEmail = if ($env:E2E_EMPLOYE_EMAIL) { $env:E2E_EMPLOYE_EMAIL } else { "employe@test.com" }
$empPass = if ($env:E2E_EMPLOYE_PASSWORD) { $env:E2E_EMPLOYE_PASSWORD } else { "AdminTest123!" }

function Assert-True {
    param([bool]$Cond, [string]$Msg)
    if (-not $Cond) { throw "ECHEC: $Msg" }
    Write-Host "  OK $Msg" -ForegroundColor Green
}

function Invoke-CurlRaw {
    param(
        [ValidateSet("GET", "POST", "PUT")]
        [string]$Method,
        [string]$Url,
        [string]$Token = $null,
        [string]$JsonBody = $null,
        [string[]]$FormArgs = $null
    )
    $tmp = [System.IO.Path]::GetTempFileName()
    try {
        $curlArgs = @("-sS", "-o", $tmp, "-w", "%{http_code}", "-X", $Method, $Url)
        if ($Token) {
            $curlArgs += "-H"
            $curlArgs += "Authorization: Bearer $Token"
        }
        if ($FormArgs) {
            $curlArgs += $FormArgs
        } elseif ($null -ne $JsonBody -and $JsonBody -ne "") {
            $curlArgs += "-H"
            $curlArgs += "Content-Type: application/json"
            $curlArgs += "-d"
            $curlArgs += $JsonBody
        }
        $codeStr = & curl.exe @curlArgs 2>&1
        if ($LASTEXITCODE -ne 0) { throw "curl failed: $codeStr" }
        $code = [int]$codeStr
        $body = [System.IO.File]::ReadAllText($tmp)
        return @{ StatusCode = $code; Content = $body }
    }
    finally {
        Remove-Item $tmp -ErrorAction SilentlyContinue
    }
}

function Get-AccessToken {
    param([string]$Email, [string]$Password)
    $loginUrl = "$apiBase/auth/login"
    $payload = (@{ email = $Email; password = $Password } | ConvertTo-Json -Compress)
    $r = Invoke-CurlRaw -Method POST -Url $loginUrl -JsonBody $payload
    Assert-True ($r.StatusCode -eq 200) "login $Email -> HTTP $($r.StatusCode)"
    $obj = $r.Content | ConvertFrom-Json
    Assert-True (($obj.success -eq $true) -or ($obj.success -eq "True")) "login $Email -> success"
    return [string]$obj.data.accessToken
}

Write-Host "`n=== E2E contrats RH (API_BASE=$apiBase) ===`n" -ForegroundColor Cyan

# --- Non-régression légère ---
Write-Host "-- Non-régression (admin) --" -ForegroundColor Yellow
$tokAdmin = Get-AccessToken $adminEmail $adminPass
$r = Invoke-CurlRaw -Method GET -Url "$apiBase/rh/salaries?page=0&size=5" -Token $tokAdmin
Assert-True ($r.StatusCode -eq 200) "GET /rh/salaries -> 200"

$today = (Get-Date).ToString("yyyy-MM-dd")
$tomorrow = (Get-Date).AddDays(1).ToString("yyyy-MM-dd")
$congeBody = (@{
        salarieId = $salarieId
        typeConge = "MALADIE"
        dateDebut = $today
        dateFin   = $tomorrow
        commentaire = "e2e-contrats-api.ps1"
    } | ConvertTo-Json -Compress)
$r = Invoke-CurlRaw -Method POST -Url "$apiBase/rh/conges?draft=true" -Token $tokAdmin -JsonBody $congeBody
Assert-True ($r.StatusCode -eq 200) "POST /rh/conges?draft=true -> 200"

$year = (Get-Date).Year
$r = Invoke-CurlRaw -Method GET -Url "$apiBase/rh/paie/$salarieId/$year" -Token $tokAdmin
Assert-True (($r.StatusCode -eq 200) -or ($r.StatusCode -eq 404)) "GET /rh/paie/{salarie}/{annee} -> $($r.StatusCode) (200 ou 404 si aucune paie)"

# --- Création CDD + échéance ---
Write-Host "`n-- Contrat CDD + échéance FIN_CDD --" -ForegroundColor Yellow
$debut = (Get-Date).AddDays(-10).ToString("yyyy-MM-dd")
$fin60 = (Get-Date).AddDays(60).ToString("yyyy-MM-dd")
$contratBody = (@{
        typeContrat         = "CDD"
        dateDebutContrat    = $debut
        dateFinContrat      = $fin60
        dateFinPeriodeEssai = $null
        dureeEssaiMois      = $null
        numeroContrat       = $null
        intitulePoste       = "E2E Poste"
        motifCdd            = "Remplacement"
        conventionCollective = $null
    } | ConvertTo-Json -Compress)

$r = Invoke-CurlRaw -Method POST -Url "$apiBase/rh/contrats/salaries/$salarieId" -Token $tokAdmin -JsonBody $contratBody
Assert-True ($r.StatusCode -eq 201) "POST contrat CDD -> 201"
$contrat = ($r.Content | ConvertFrom-Json).data
$contratId = [string]$contrat.id
Assert-True ($contratId.Length -gt 10) "contrat.id présent"

$r = Invoke-CurlRaw -Method GET -Url "$apiBase/rh/contrats/echeances?salarieId=$salarieId&page=0&size=50" -Token $tokAdmin
Assert-True ($r.StatusCode -eq 200) "GET echeances par salarie -> 200"
$echeances = ($r.Content | ConvertFrom-Json).data.content
$finCdd = @($echeances | Where-Object { $_.typeEcheance -eq "FIN_CDD" -and $_.dateEcheance -eq $fin60 })
Assert-True ($finCdd.Count -ge 1) "échéance FIN_CDD avec date $fin60 trouvée"

# --- Renouvellement (rôle RH uniquement) ---
Write-Host "`n-- Renouvellement CDD (RH) --" -ForegroundColor Yellow
$tokRh = Get-AccessToken $rhEmail $rhPass
$nouvelleFin = (Get-Date).AddDays(120).ToString("yyyy-MM-dd")
$renBody = (@{
        nouvelleDateFin = $nouvelleFin
        motif           = "Prolongation E2E"
        commentaire     = $null
    } | ConvertTo-Json -Compress)
$r = Invoke-CurlRaw -Method POST -Url "$apiBase/rh/contrats/$contratId/renouveler" -Token $tokRh -JsonBody $renBody
Assert-True ($r.StatusCode -eq 200) "POST renouveler -> 200"
$nouveau = ($r.Content | ConvertFrom-Json).data
Assert-True ([bool]$nouveau.actif) "nouveau contrat actif"
Assert-True ($nouveau.renouvellementNumero -eq 1) "renouvellementNumero = 1"

$r = Invoke-CurlRaw -Method GET -Url "$apiBase/rh/contrats/echeances?salarieId=$salarieId&page=0&size=50" -Token $tokAdmin
$echeances2 = ($r.Content | ConvertFrom-Json).data.content
$annulee = @($echeances2 | Where-Object { $_.typeEcheance -eq "FIN_CDD" -and $_.dateEcheance -eq $fin60 -and $_.statut -eq "ANNULEE" })
Assert-True ($annulee.Count -ge 1) "ancienne échéance FIN_CDD ANNULEE"
$nouvelleEch = @($echeances2 | Where-Object { $_.typeEcheance -eq "FIN_CDD" -and $_.dateEcheance -eq $nouvelleFin })
Assert-True ($nouvelleEch.Count -ge 1) "nouvelle échéance FIN_CDD à $nouvelleFin"

# --- Permissions ---
Write-Host "`n-- Permissions --" -ForegroundColor Yellow
$tokEmp = Get-AccessToken $empEmail $empPass
$r = Invoke-CurlRaw -Method GET -Url "$apiBase/rh/contrats?page=0&size=5" -Token $tokEmp
Assert-True ($r.StatusCode -eq 403) "EMPLOYE GET /rh/contrats -> 403"

$tokFin = Get-AccessToken $finEmail $finPass
$r = Invoke-CurlRaw -Method GET -Url "$apiBase/rh/contrats/echeances/dashboard" -Token $tokFin
Assert-True ($r.StatusCode -eq 200) "FINANCIER GET echeances/dashboard -> 200"

$future = (Get-Date).AddDays(40).ToString("yyyy-MM-dd")
$echBody = (@{
        salarieId    = $salarieId
        contratId    = $null
        typeEcheance = "AUTRE"
        titre        = "E2E manuelle"
        description  = $null
        dateEcheance = $future
        priorite     = 2
        responsableId = $null
    } | ConvertTo-Json -Compress)
$r = Invoke-CurlRaw -Method POST -Url "$apiBase/rh/contrats/echeances" -Token $tokFin -JsonBody $echBody
Assert-True ($r.StatusCode -eq 403) "FINANCIER POST /echeances -> 403"

# --- Visite médicale (création avec date réalisée + périodicité) ---
Write-Host "`n-- Visite médicale --" -ForegroundColor Yellow
$dr = (Get-Date).AddDays(-1).ToString("yyyy-MM-dd")
$visBody = (@{
        typeVisite       = "PERIODIQUE"
        datePlanifiee    = $dr
        dateRealisee     = $dr
        medecin          = $null
        centreMedical    = $null
        resultat         = "APTE"
        restrictions     = $null
        periodiciteMois  = 12
    } | ConvertTo-Json -Compress)
$r = Invoke-CurlRaw -Method POST -Url "$apiBase/rh/contrats/salaries/$salarieId/visites" -Token $tokAdmin -JsonBody $visBody
Assert-True ($r.StatusCode -eq 201) "POST visite -> 201"
$vis = ($r.Content | ConvertFrom-Json).data
$expectedProch = ([datetime]::Parse($dr)).AddMonths(12).ToString("yyyy-MM-dd")
Assert-True ([string]$vis.prochaineVisite -eq $expectedProch) "prochaineVisite = dateRealisee + 12 mois ($expectedProch)"

$r = Invoke-CurlRaw -Method GET -Url "$apiBase/rh/contrats/echeances?salarieId=$salarieId&type=VISITE_MEDICALE&page=0&size=20" -Token $tokAdmin
$vmList = @((($r.Content | ConvertFrom-Json).data.content) | Where-Object { $_.dateEcheance -eq $expectedProch })
Assert-True ($vmList.Count -ge 1) "échéance VISITE_MEDICALE à dateEcheance=$expectedProch (alignée sur prochaineVisite)"

# --- Titre de séjour (multipart) ---
Write-Host "`n-- Titre de séjour (multipart) --" -ForegroundColor Yellow
$exp100 = (Get-Date).AddDays(100).ToString("yyyy-MM-dd")
$dataJson = (@{
        typeDocument        = "TITRE_SEJOUR"
        numeroDocument      = "E2E-TS-001"
        paysEmetteur        = "FR"
        dateEmission        = (Get-Date).AddDays(-200).ToString("yyyy-MM-dd")
        dateExpiration      = $exp100
        autoriteEmettrice   = "Préfecture"
    } | ConvertTo-Json -Compress)
$jsonPart = [System.IO.Path]::GetTempFileName()
try {
    [System.IO.File]::WriteAllText($jsonPart, $dataJson, [System.Text.UTF8Encoding]::new($false))
    $form = @("-F", "data=@${jsonPart};type=application/json")
    $r = Invoke-CurlRaw -Method POST -Url "$apiBase/rh/contrats/salaries/$salarieId/titres-sejour" -Token $tokAdmin -FormArgs $form
}
finally {
    Remove-Item $jsonPart -ErrorAction SilentlyContinue
}
Assert-True ($r.StatusCode -eq 201) "POST titres-sejour -> 201"

$attenduAlerte = (Get-Date).AddDays(10).ToString("yyyy-MM-dd")
$r = Invoke-CurlRaw -Method GET -Url "$apiBase/rh/contrats/echeances?salarieId=$salarieId&type=TITRE_SEJOUR&page=0&size=20" -Token $tokAdmin
$tsList = @((($r.Content | ConvertFrom-Json).data.content) | Where-Object { $_.dateEcheance -eq $attenduAlerte })
Assert-True ($tsList.Count -ge 1) "échéance TITRE_SEJOUR à dateEcheance=$attenduAlerte (expiration - 90j)"
$tsEch = $tsList[0]
Assert-True ($tsEch.statut -eq "EN_ALERTE") "statut EN_ALERTE (fenêtre 10j < 30j)"

Write-Host "`n=== Tous les tests API scriptés sont passés ===`n" -ForegroundColor Cyan
Write-Host "À faire manuellement : UI (/rh/contrats, badges), WebSocket congés, exécution du scheduler (cron ou test unitaire)." -ForegroundColor DarkGray
