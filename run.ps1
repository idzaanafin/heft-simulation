param(
    [string]$Workload = "sipht",
    [int]$Tasks = 200,
    [int]$VMs = 80
)

$java21 = if ($env:JAVA_HOME) {
    Join-Path $env:JAVA_HOME "bin\java.exe"
} else {
    (Get-Command java -ErrorAction Stop).Source
}
$cp = "target\classes;$env:USERPROFILE\.m2\repository\org\cloudbus\cloudsim\cloudsim\7.0.1\cloudsim-7.0.1.jar"

& $java21 -cp $cp Main $Workload $Tasks $VMs
