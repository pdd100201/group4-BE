param([string]$Test = '*Test')
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    & "$PSScriptRoot\mvnw.cmd" "-Dtest=$Test" test
    $testExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}
exit $testExitCode
