$javac21 = if ($env:JAVA_HOME) {
    Join-Path $env:JAVA_HOME "bin\javac.exe"
} else {
    (Get-Command javac -ErrorAction Stop).Source
}
$cp = "$env:USERPROFILE\.m2\repository\org\cloudbus\cloudsim\cloudsim\7.0.1\cloudsim-7.0.1.jar"
$outDir = "target\classes"

if (!(Test-Path $outDir)) {
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null
}

Copy-Item -Path "src\main\resources\*" -Destination $outDir -Recurse -Force -ErrorAction SilentlyContinue

$sources = Get-ChildItem -Path "src\main\java\*.java" | ForEach-Object { $_.FullName }
& $javac21 -d $outDir -cp $cp $sources

if ($LASTEXITCODE -eq 0) {
    Write-Host "Build Successful!" -ForegroundColor Green
} else {
    Write-Host "Build Failed!" -ForegroundColor Red
}
