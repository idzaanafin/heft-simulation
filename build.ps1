$javac21 = "c:\Users\rayha\.antigravity-ide\extensions\redhat.java-1.56.0-win32-x64\jre\21.0.12.1-win32-x86_64\bin\javac.exe"
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
