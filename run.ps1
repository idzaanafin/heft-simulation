param(
    [string]$Workload = "sipht",
    [int]$Tasks = 200,
    [int]$VMs = 80
)

$java21 = "c:\Users\rayha\.antigravity-ide\extensions\redhat.java-1.56.0-win32-x64\jre\21.0.12.1-win32-x86_64\bin\java.exe"
$cp = "target\classes;$env:USERPROFILE\.m2\repository\org\cloudbus\cloudsim\cloudsim\7.0.1\cloudsim-7.0.1.jar"

& $java21 -cp $cp Main $Workload $Tasks $VMs
