#Requires -Version 5.1
<#
.SYNOPSIS
    Revisa el estado del build de IdleTensura en GitHub Actions.

.DESCRIPTION
    Consulta la API publica de GitHub (sin token) y muestra:
      - estado y conclusion del run
      - jobs y pasos, marcando los que fallaron
      - URL del artefacto con el APK debug

.PARAMETER RunId
    ID del run a revisar. Si se omite, usa el ultimo run de la rama main.

.PARAMETER Watch
    Espera hasta que el run termine (o se agote el tiempo) mostrando el progreso.

.PARAMETER TimeoutMinutes
    Minutos maximos de espera con -Watch. Por defecto 30.

.PARAMETER IntervalSeconds
    Segundos entre consultas con -Watch. Por defecto 45.

.EXAMPLE
    .\scripts\check-build.ps1
    .\scripts\check-build.ps1 -RunId 39804066505
    .\scripts\check-build.ps1 -Watch -TimeoutMinutes 40
#>
[CmdletBinding()]
param(
    [long]$RunId = 0,
    [switch]$Watch,
    [int]$TimeoutMinutes = 30,
    [int]$IntervalSeconds = 45
)

$ErrorActionPreference = 'Stop'
$Repo  = 'Techkron-star/IdleTensura'
$Api   = "https://api.github.com/repos/$Repo"
$Heads = @{
    'User-Agent' = 'idle-tensura-check-build'
    'Accept'     = 'application/vnd.github+json'
}

function Get-GitHub([string]$Path) {
    try {
        return Invoke-RestMethod -Uri "$Api$Path" -Headers $Heads -TimeoutSec 30
    } catch {
        Write-Host "ERROR consultando la API: $($_.Exception.Message)" -ForegroundColor Red
        throw
    }
}

function Get-TargetRun {
    if ($RunId -gt 0) { return Get-GitHub "/actions/runs/$RunId" }
    $runs = Get-GitHub '/actions/runs?branch=main&per_page=1'
    if (-not $runs.workflow_runs -or $runs.workflow_runs.Count -eq 0) {
        Write-Host 'No hay ejecuciones de CI todavia. Abre la pestana Actions del repo y activa los workflows.' -ForegroundColor Yellow
        exit 1
    }
    return $runs.workflow_runs[0]
}

function Show-Run([object]$Run) {
    $color = switch ($Run.conclusion) {
        'success'   { 'Green' }
        'failure'   { 'Red' }
        'cancelled' { 'Yellow' }
        default     { 'Cyan' }
    }
    Write-Host ''
    Write-Host "Run #$($Run.run_number)  [$($Run.status)/$($Run.conclusion)]" -ForegroundColor $color
    Write-Host "  Rama:     $($Run.head_branch)  ($($Run.head_sha.Substring(0,7)))"
    Write-Host "  Evento:   $($Run.event)"
    Write-Host "  Inicio:   $($Run.run_started_at)"
    Write-Host "  URL:      $($Run.html_url)"
}

function Show-Jobs([object]$Run) {
    $jobs = Get-GitHub "/actions/runs/$($Run.id)/jobs"
    foreach ($job in $jobs.jobs) {
        $jc = switch ($job.conclusion) {
            'success'   { 'Green' }
            'failure'   { 'Red' }
            'cancelled' { 'Yellow' }
            default     { 'Cyan' }
        }
        Write-Host ''
        Write-Host "JOB: $($job.name) -> $($job.status)/$($job.conclusion)" -ForegroundColor $jc
        foreach ($step in $job.steps) {
            $ic = switch ($step.conclusion) {
                'success'   { 'Green' }
                'failure'   { 'Red' }
                'skipped'   { 'DarkGray' }
                default     { 'Cyan' }
            }
            Write-Host ("   {0,-38} {1}/{2}" -f $step.name, $step.status, $step.conclusion) -ForegroundColor $ic
        }
        if ($job.conclusion -eq 'failure') {
            Write-Host '   Pasos fallidos:' -ForegroundColor Red
            $job.steps | Where-Object { $_.conclusion -eq 'failure' } | ForEach-Object {
                Write-Host "     - $($_.name)" -ForegroundColor Red
            }
        }
    }
}

function Show-Artifacts([object]$Run) {
    if ($Run.status -ne 'completed' -or $Run.conclusion -ne 'success') { return }
    $arts = Get-GitHub "/actions/runs/$($Run.id)/artifacts"
    $apk  = $arts.artifacts | Where-Object { $_.name -eq 'idle-tensura-debug-apk' }
    Write-Host ''
    if (-not $apk) {
        Write-Host 'No se encontro el artefacto del APK. Revisa los logs del job.' -ForegroundColor Yellow
        return
    }
    Write-Host "APK: $($apk.name) ($([math]::Round($apk.size_in_bytes / 1MB, 1)) MB, caduca $($apk.expires_at))" -ForegroundColor Green
    Write-Host "Descarga: https://github.com/$Repo/actions/runs/$($Run.id)/artifacts/$($apk.id)" -ForegroundColor Green
}

$run = Get-TargetRun

if ($Watch) {
    $deadline = (Get-Date).AddMinutes($TimeoutMinutes)
    while ($run.status -ne 'completed' -and (Get-Date) -lt $deadline) {
        Show-Run $run
        Write-Host "  Esperando $IntervalSeconds s..." -ForegroundColor DarkGray
        Start-Sleep -Seconds $IntervalSeconds
        $run = Get-GitHub "/actions/runs/$($run.id)"
    }
}

Show-Run $run
Show-Jobs $run
Show-Artifacts $run

if ($run.status -ne 'completed') {
    Write-Host ''
    Write-Host 'El run sigue en curso. Vuelve a ejecutar este script para actualizar.' -ForegroundColor Yellow
    exit 2
}

if ($run.conclusion -eq 'success') {
    Write-Host ''
    Write-Host 'BUILD OK' -ForegroundColor Green
    exit 0
}

Write-Host ''
Write-Host "BUILD $($run.conclusion.ToUpper())" -ForegroundColor Red
Write-Host 'Descarga los logs desde el enlace del run para ver el error completo.' -ForegroundColor Yellow
exit 1
