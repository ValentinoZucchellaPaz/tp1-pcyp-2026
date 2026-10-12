<#
.SYNOPSIS
  Renderiza los diagramas PlantUML (.puml) de una carpeta a PNG.

.DESCRIPTION
  Busca Java (JAVA_HOME, PATH o .jdks) y plantuml.jar (lo descarga si falta) y
  renderiza todos los .puml de la carpeta objetivo, dejando cada PNG junto a su
  fuente. Sirve para que cualquiera del grupo regenere los diagramas con un solo
  comando, sin depender de rutas locales ni de pasos manuales.

.PARAMETER TargetDir
  Carpeta con los .puml (por defecto docs\diagramasSimples, relativa a la raíz).

.PARAMETER PlantUmlVersion
  Versión de PlantUML a descargar si falta el jar (por defecto v1.2026.8).

.PARAMETER PlantUmlJar
  Ruta a un plantuml.jar ya existente (evita la descarga).

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\uml\render.ps1

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\uml\render.ps1 -TargetDir docs\UML
#>
[CmdletBinding()]
param(
    [string]$TargetDir,

    [string]$PlantUmlVersion = 'v1.2026.8',

    [string]$PlantUmlJar
)

$ErrorActionPreference = 'Stop'

$RepoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $TargetDir) {
    $TargetDir = Join-Path $RepoRoot 'docs\diagramasSimples'
} elseif (-not [System.IO.Path]::IsPathRooted($TargetDir)) {
    $TargetDir = Join-Path $RepoRoot $TargetDir
}

function Resolve-Java {
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME 'bin\java.exe'
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }

    $fromPath = Get-Command java -ErrorAction SilentlyContinue
    if ($fromPath) { return $fromPath.Source }

    $jdkRoot = Join-Path $env:USERPROFILE '.jdks'
    if (Test-Path -LiteralPath $jdkRoot) {
        $hit = Get-ChildItem $jdkRoot -Filter 'java.exe' -Recurse -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -match '\\bin\\java\.exe$' } |
            Sort-Object FullName -Descending | Select-Object -First 1
        if ($hit) { return $hit.FullName }
    }

    $globs = @(
        "$env:ProgramFiles\Java\*\bin\java.exe",
        "$env:ProgramFiles\Eclipse Adoptium\*\bin\java.exe",
        "$env:LOCALAPPDATA\Programs\*\bin\java.exe"
    )
    foreach ($glob in $globs) {
        $found = Get-Item $glob -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) { return $found.FullName }
    }

    throw "No se encontró Java. Instalá un JDK o definí JAVA_HOME."
}

function Resolve-PlantUml {
    if ($PlantUmlJar) {
        if (-not (Test-Path -LiteralPath $PlantUmlJar)) { throw "No existe el jar indicado: $PlantUmlJar" }
        return $PlantUmlJar
    }

    $cacheDir = Join-Path $env:LOCALAPPDATA 'PlantUML'
    $jar = Join-Path $cacheDir 'plantuml.jar'
    if (Test-Path -LiteralPath $jar) { return $jar }

    New-Item -ItemType Directory -Force -Path $cacheDir | Out-Null
    $url = "https://github.com/plantuml/plantuml/releases/download/$PlantUmlVersion/plantuml.jar"
    Write-Host "Descargando PlantUML $PlantUmlVersion ..."
    $ProgressPreference = 'SilentlyContinue'
    Invoke-WebRequest -Uri $url -OutFile $jar -TimeoutSec 180
    return $jar
}

$java = Resolve-Java
$jar = Resolve-PlantUml

$sources = @(Get-ChildItem -LiteralPath $TargetDir -Filter '*.puml' -Recurse -ErrorAction SilentlyContinue)
if ($sources.Count -eq 0) { throw "No hay archivos .puml en $TargetDir" }

Write-Host "Java:     $java"
Write-Host "PlantUML: $jar"
Write-Host "Fuentes:  $($sources.Count) .puml en $TargetDir"

Push-Location $RepoRoot
try {
    & $java -jar $jar -charset UTF-8 -tpng @($sources.FullName)
    if ($LASTEXITCODE -ne 0) { throw "PlantUML falló (exit $LASTEXITCODE)." }
}
finally {
    Pop-Location
}

$pngs = @(Get-ChildItem -LiteralPath $TargetDir -Filter '*.png' -ErrorAction SilentlyContinue)
Write-Host "PNG generados: $($pngs.Count)"
Write-Host "Listo."
