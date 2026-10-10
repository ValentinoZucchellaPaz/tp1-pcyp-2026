<#
.SYNOPSIS
  Ejecuta barridos de la simulación del TP1 y recolecta tiempos y estados finales.

.DESCRIPTION
  La aplicación siempre lee `config/tp1.properties`, por lo que este script
  respalda el archivo, escribe cada variante, corre el jar N veces, registra
  `durationMillis` y la distribución de estados finales, y finalmente restaura
  la configuración original y regenera la corrida oficial en `resultados/`.

  Salidas:
    tools/analisis/configs/<variante>.properties  (config de cada variante)
    tools/analisis/salidas/resultados.csv         (una fila por corrida)
    tools/analisis/salidas/resumen.md             (tabla Markdown por variante)

.PARAMETER Scenario
  Official (solo la config oficial), Threads (barrido de hilos),
  Delays (barrido de demoras) o All.

.PARAMETER Runs
  Cantidad de corridas por variante (por defecto 5).

.PARAMETER Orders
  Override de la cantidad de órdenes para acortar los barridos (por defecto 500).

.PARAMETER SkipBuild
  No compila; usa el `target/tp1.jar` existente.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\analisis\run-experiments.ps1

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\analisis\run-experiments.ps1 -Scenario All -Runs 5
#>
[CmdletBinding()]
param(
    [ValidateSet('Official', 'Threads', 'Delays', 'All')]
    [string]$Scenario = 'Official',

    [int]$Runs = 5,

    [int]$Orders = 500,

    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'

$RepoRoot   = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$ConfigPath = Join-Path $RepoRoot 'config\tp1.properties'
$JarPath    = Join-Path $RepoRoot 'target\tp1.jar'
$OutDir     = Join-Path $PSScriptRoot 'salidas'
$CfgDir     = Join-Path $PSScriptRoot 'configs'
$CsvPath    = Join-Path $OutDir 'resultados.csv'
$MdPath     = Join-Path $OutDir 'resumen.md'

New-Item -ItemType Directory -Force -Path $OutDir, $CfgDir | Out-Null

function Resolve-Maven {
    $cmd = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $cmd = Get-Command mvn -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }

    $globs = @(
        "$env:ProgramFiles\JetBrains\*\plugins\maven-plugin\lib\maven3\bin\mvn.cmd",
        "$env:LOCALAPPDATA\Programs\*\plugins\maven-plugin\lib\maven3\bin\mvn.cmd",
        "D:\IntelliJ IDEA*\plugins\maven-plugin\lib\maven3\bin\mvn.cmd",
        "C:\IntelliJ IDEA*\plugins\maven-plugin\lib\maven3\bin\mvn.cmd",
        "D:\*\plugins\maven-plugin\lib\maven3\bin\mvn.cmd"
    )
    foreach ($glob in $globs) {
        $hit = Get-Item $glob -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($hit) { return $hit.FullName }
    }
    throw "No se encontró Maven. Compilá con 'mvn clean package' o pasá -SkipBuild con target\tp1.jar ya generado."
}

$Base = [ordered]@{
    Orders = $Orders; Seed = 20261001; Rows = 10; Cols = 20
    Assign = 3; Valid = 2; Print = 3; Quality = 2
    ValidModel = 85; PrintSuccess = 90; QualityApproved = 95
    DelayAssign = 50; DelayValid = 120; DelayPrint = 180; DelayQuality = 80
}

function New-Variant([string]$name, [string]$scenario, [hashtable]$overrides) {
    $config = @{}
    foreach ($key in $Base.Keys) { $config[$key] = $Base[$key] }
    foreach ($key in $overrides.Keys) { $config[$key] = $overrides[$key] }
    [pscustomobject]@{ Name = $name; Scenario = $scenario; Config = $config }
}

function Write-ConfigFile([string]$path, $config) {
    $text = @"
simulation.total-orders=$($config.Orders)
simulation.random-seed=$($config.Seed)

printers.rows=$($config.Rows)
printers.columns=$($config.Cols)

threads.assignment=$($config.Assign)
threads.validation=$($config.Valid)
threads.printing=$($config.Print)
threads.quality-control=$($config.Quality)

probability.valid-model=$($config.ValidModel)
probability.print-success=$($config.PrintSuccess)
probability.quality-approved=$($config.QualityApproved)

delay.assignment.ms=$($config.DelayAssign)
delay.validation.ms=$($config.DelayValid)
delay.printing.ms=$($config.DelayPrint)
delay.quality-control.ms=$($config.DelayQuality)

output.directory=resultados
"@
    [System.IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding($false)))
}

function Read-Summary([string]$path) {
    $hash = @{}
    foreach ($line in Get-Content -LiteralPath $path) {
        if ($line -match '^\s*([^#=]+?)\s*=\s*(.*)$') { $hash[$matches[1]] = $matches[2].Trim() }
    }
    return $hash
}

function Get-Stats($values) {
    $sorted = @($values | Sort-Object)
    $n = $sorted.Count
    $median = if ($n % 2 -eq 1) {
        $sorted[[int][math]::Floor($n / 2)]
    } else {
        [int][math]::Round(($sorted[$n / 2 - 1] + $sorted[$n / 2]) / 2.0)
    }
    [pscustomobject]@{ Min = [int]$sorted[0]; Median = $median; Max = [int]$sorted[-1] }
}

$variants = @()
if ($Scenario -in @('Official', 'All')) {
    $variants += New-Variant 'official' 'official' @{}
}
if ($Scenario -in @('Threads', 'All')) {
    $variants += New-Variant 'threads-1-1-1-1' 'threads' @{ Assign = 1; Valid = 1; Print = 1; Quality = 1 }
    $variants += New-Variant 'threads-2-2-2-2' 'threads' @{ Assign = 2; Valid = 2; Print = 2; Quality = 2 }
    $variants += New-Variant 'threads-3-2-3-2' 'threads' @{ Assign = 3; Valid = 2; Print = 3; Quality = 2 }
    $variants += New-Variant 'threads-4-4-4-4' 'threads' @{ Assign = 4; Valid = 4; Print = 4; Quality = 4 }
    $variants += New-Variant 'threads-3-4-3-2' 'threads' @{ Assign = 3; Valid = 4; Print = 3; Quality = 2 }
    $variants += New-Variant 'threads-3-2-4-2' 'threads' @{ Assign = 3; Valid = 2; Print = 4; Quality = 2 }
    $variants += New-Variant 'threads-3-4-4-2' 'threads' @{ Assign = 3; Valid = 4; Print = 4; Quality = 2 }
}
if ($Scenario -in @('Delays', 'All')) {
    $variants += New-Variant 'delays-0' 'delays' @{ DelayAssign = 0; DelayValid = 0; DelayPrint = 0; DelayQuality = 0 }
    $variants += New-Variant 'delays-half' 'delays' @{ DelayAssign = 25; DelayValid = 60; DelayPrint = 90; DelayQuality = 40 }
    $variants += New-Variant 'delays-official' 'delays' @{ DelayAssign = 50; DelayValid = 120; DelayPrint = 180; DelayQuality = 80 }
    $variants += New-Variant 'delays-double' 'delays' @{ DelayAssign = 100; DelayValid = 240; DelayPrint = 360; DelayQuality = 160 }
}

# La aplicación lee `config/` y escribe `resultados/` relativos al directorio
# actual, así que todo el trabajo se hace desde la raíz del repositorio.
$originalConfig = Get-Content -LiteralPath $ConfigPath -Raw
Push-Location $RepoRoot

try {
    if (-not $SkipBuild) {
        $mvn = Resolve-Maven
        Write-Host "Compilando con: $mvn"
        & $mvn -q -DskipTests clean package
        if ($LASTEXITCODE -ne 0) { throw "La compilación falló (exit $LASTEXITCODE)." }
    }
    if (-not (Test-Path -LiteralPath $JarPath)) {
        throw "No existe $JarPath. Compilá primero o quitá -SkipBuild."
    }

    $records = New-Object System.Collections.Generic.List[object]
    $summaryRows = New-Object System.Collections.Generic.List[object]

    foreach ($variant in $variants) {
        $cfgFile = Join-Path $CfgDir "$($variant.Name).properties"
        Write-ConfigFile $cfgFile $variant.Config
        Copy-Item -LiteralPath $cfgFile -Destination $ConfigPath -Force

        Write-Host "== Variante $($variant.Name) ($Runs corridas) =="
        $durations = @()
        $lastSummary = $null
        for ($run = 1; $run -le $Runs; $run++) {
            & java -jar $JarPath | Out-Null
            $summary = Read-Summary (Join-Path $RepoRoot 'resultados\resumen.properties')
            $duration = [int]$summary['durationMillis']
            $durations += $duration
            $lastSummary = $summary

            $records.Add([pscustomobject]@{
                scenario       = $variant.Scenario
                variant        = $variant.Name
                run            = $run
                durationMillis = $duration
                approved       = [int]$summary['approvedOrders']
                rejected       = [int]$summary['rejectedOrders']
                printFailed    = [int]$summary['printFailedOrders']
                defective      = [int]$summary['defectiveOrders']
                assign         = $variant.Config.Assign
                valid          = $variant.Config.Valid
                print          = $variant.Config.Print
                quality        = $variant.Config.Quality
                dAssign        = $variant.Config.DelayAssign
                dValid         = $variant.Config.DelayValid
                dPrint         = $variant.Config.DelayPrint
                dQuality       = $variant.Config.DelayQuality
            })
            Write-Host ("   run {0}: {1} ms" -f $run, $duration)
        }

        $stats = Get-Stats $durations
        $summaryRows.Add([pscustomobject]@{
            variant     = $variant.Name
            threads     = "$($variant.Config.Assign)/$($variant.Config.Valid)/$($variant.Config.Print)/$($variant.Config.Quality)"
            delays      = "$($variant.Config.DelayAssign)/$($variant.Config.DelayValid)/$($variant.Config.DelayPrint)/$($variant.Config.DelayQuality)"
            median      = $stats.Median
            min         = $stats.Min
            max         = $stats.Max
            approved    = [int]$lastSummary['approvedOrders']
            rejected    = [int]$lastSummary['rejectedOrders']
            printFailed = [int]$lastSummary['printFailedOrders']
            defective   = [int]$lastSummary['defectiveOrders']
        })
    }

    $records | Export-Csv -LiteralPath $CsvPath -NoTypeInformation -Encoding UTF8

    $md = "| Variante | asig/val/imp/cal | demoras | mediana (ms) | min | max | approved | rejected | printFailed | defective |`n"
    $md += "| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |`n"
    foreach ($row in $summaryRows) {
        $md += "| $($row.variant) | $($row.threads) | $($row.delays) | $($row.median) | $($row.min) | $($row.max) | $($row.approved) | $($row.rejected) | $($row.printFailed) | $($row.defective) |`n"
    }
    [System.IO.File]::WriteAllText($MdPath, $md, (New-Object System.Text.UTF8Encoding($false)))

    Write-Host ""
    Write-Host "=== Resumen por variante (mediana) ==="
    $summaryRows | Format-Table -AutoSize

    Write-Host "Regenerando corrida oficial en resultados/ ..."
    & java -jar $JarPath | Out-Null

    Write-Host ""
    Write-Host "Listo."
    Write-Host "  Corridas:  $CsvPath"
    Write-Host "  Resumen:   $MdPath"
}
finally {
    [System.IO.File]::WriteAllText($ConfigPath, $originalConfig, (New-Object System.Text.UTF8Encoding($false)))
    Pop-Location
}
