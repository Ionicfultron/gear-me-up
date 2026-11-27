# Builds Gear Me Up and starts a development RuneLite with the plugin loaded.
# Uses the Gradle wrapper (gradlew.bat), which downloads Gradle and RuneLite the first time.
# Requires a JDK 11+ in JAVA_HOME or on PATH.
# Extra arguments are passed to RuneLite, e.g.:  .\run-dev.ps1 --insecure-write-credentials

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

if (-not ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe')))) {
	$javac = Get-Command javac -ErrorAction SilentlyContinue
	if (-not $javac) { throw 'No JDK found. Install one (11+) or set JAVA_HOME.' }
	# Resolve the real JDK folder (the Oracle "javapath" shim on PATH isn't a JDK folder).
	# javac prints these settings on stderr, which Windows PowerShell 5.1 would treat as an error.
	$ErrorActionPreference = 'Continue'
	$props = & $javac.Source -J-XshowSettings:properties -version 2>&1 | ForEach-Object { "$_" } | Out-String
	$ErrorActionPreference = 'Stop'
	if ($props -match 'java\.home = (.+)') { $env:JAVA_HOME = $Matches[1].Trim() }
}

Write-Host 'Starting RuneLite with Gear Me Up (safe mode: Plugin Hub plugins are off, so only this build of Gear Me Up loads)...'
# Safe mode skips Plugin Hub and sideloaded plugins, including any installed copy of Gear Me Up,
# which would otherwise load alongside this build and share its settings.
$runeliteArgs = @('--safe-mode') + $args
$gradleArgs = @('run', '--console=plain', "--args=$($runeliteArgs -join ' ')")
& (Join-Path $root 'gradlew.bat') @gradleArgs
