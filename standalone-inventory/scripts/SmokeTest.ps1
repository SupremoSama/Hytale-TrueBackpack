param(
    [string]$ServerJar = "$env:APPDATA/Hytale/install/pre-release/package/game/latest/Server/HytaleServer.jar",
    [string]$Assets = "$env:APPDATA/Hytale/install/pre-release/package/game/latest/Assets.zip",
    [string]$TrueBackpackJar,
    [int]$TestPort = 5527
)

$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$flavor = if ($TrueBackpackJar) { 'compatibility' } else { 'standalone' }
$runPath = Join-Path $projectRoot "run-smoke/$flavor"
$modPath = Join-Path $runPath 'mods'
$logPath = Join-Path $runPath 'server-output.log'
$coreJar = Join-Path $projectRoot 'build/libs/CustomInventory-0.1.0.jar'
$exampleJar = Join-Path $projectRoot 'example-extension/build/libs/CustomInventoryExample-0.1.0.jar'

foreach ($required in @($ServerJar, $Assets, $coreJar, $exampleJar)) {
    if (!(Test-Path -LiteralPath $required -PathType Leaf)) { throw "Missing file: $required" }
}
if ($TrueBackpackJar -and !(Test-Path -LiteralPath $TrueBackpackJar -PathType Leaf)) {
    throw "Missing TrueBackpack JAR: $TrueBackpackJar"
}

New-Item -ItemType Directory -Path $modPath -Force | Out-Null
Copy-Item -LiteralPath $coreJar, $exampleJar -Destination $modPath -Force
if ($TrueBackpackJar) { Copy-Item -LiteralPath $TrueBackpackJar -Destination $modPath -Force }

# pre.5 bare-mode default permission creation closes its writer before flushing it.
# Supply an empty test config instead of changing the engine or borrowing player settings.
$permissionsPath = Join-Path $runPath 'permissions.json'
if (!(Test-Path -LiteralPath $permissionsPath)) {
    [System.IO.File]::WriteAllText($permissionsPath, '{"users":{},"groups":{}}', [System.Text.UTF8Encoding]::new($false))
}

Push-Location -LiteralPath $runPath
try {
    # Bind only to loopback in this isolated test universe. Stop runs after plugins/worlds start.
    & java --enable-native-access=ALL-UNNAMED -Xmx4G -jar $ServerJar --assets $Assets `
        --bind "127.0.0.1:$TestPort" --auth-mode offline --disable-sentry --disable-file-watcher `
        --boot-command stop *> $logPath
    $serverExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

if ($serverExitCode -ne 0) { throw "Server exited with $serverExitCode; see $logPath" }
$output = [System.IO.File]::ReadAllText($logPath)
foreach ($pluginName in @('SupremoSama:CustomInventory', 'SupremoSama:CustomInventoryExample')) {
    if ($output -notmatch "Enabled plugin $([regex]::Escape($pluginName))") {
        throw "Plugin not enabled: $pluginName; see $logPath"
    }
}
if ($TrueBackpackJar -and $output -notmatch 'Enabled plugin [^\r\n ]+:TrueBackpack') {
    throw "TrueBackpack was not enabled; see $logPath"
}
if ($output -notmatch 'Hytale Server Booted!') { throw "Server did not boot; see $logPath" }
if ($output -match 'Failed to boot|Failed to enable|Exception in thread') {
    throw "Server reported a startup failure; see $logPath"
}
Write-Output "Smoke test passed ($flavor). Full log: $logPath"
