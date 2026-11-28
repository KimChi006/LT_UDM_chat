param(
    [ValidateSet("build", "server", "app", "chat", "group", "media-demo")]
    [string]$Mode = "app"
)

$ErrorActionPreference = "Stop"

$repoRoot = $PSScriptRoot
$appRoot = Join-Path $repoRoot "ChatApp"
$sourceRoot = Join-Path $appRoot "src"
$classesDir = Join-Path $appRoot "bin"
$dependencyJars = @(Get-ChildItem -Path (Join-Path $appRoot "lib") `
    -Filter "*.jar" -File | ForEach-Object { $_.FullName })
$dependencyClasspath = $dependencyJars -join [IO.Path]::PathSeparator

if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
    throw "JDK compiler 'javac' was not found. Install a JDK and add it to PATH."
}
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "Java runtime 'java' was not found. Install a JDK and add it to PATH."
}
if ($dependencyJars.Count -eq 0) {
    throw "No dependency jars were found in ChatApp\lib."
}

$sourceFiles = @(Get-ChildItem -Path $sourceRoot -Recurse -Filter "*.java" |
    ForEach-Object { $_.FullName })
if ($sourceFiles.Count -eq 0) {
    throw "No Java source files found under $sourceRoot"
}

New-Item -ItemType Directory -Force -Path $classesDir | Out-Null
Write-Host "Compiling $($sourceFiles.Count) Java source files..."
& javac -encoding UTF-8 -cp $dependencyClasspath -d $classesDir @sourceFiles
if ($LASTEXITCODE -ne 0) {
    throw "Java compilation failed with exit code $LASTEXITCODE."
}

if ($Mode -eq "build") {
    Write-Host "Build successful. Classes are in ChatApp\bin."
    return
}

$mainClass = switch ($Mode) {
    "server"     { "server.ServerMain" }
    "app"        { "auth.LoginUI" }
    "chat"       { "chat.ChatUI" }
    "group"      { "history.GroupTestUI" }
    "media-demo" { "media.MediaDemo" }
}

Push-Location $appRoot
try {
    Write-Host "Starting $mainClass..."
    & java -cp "$classesDir;$dependencyClasspath" $mainClass
    if ($LASTEXITCODE -ne 0) {
        throw "$mainClass exited with code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}
