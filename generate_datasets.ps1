$destDir = "src\main\resources\datasets"
if (!(Test-Path $destDir)) { New-Item -ItemType Directory -Path $destDir -Force | Out-Null }

function Generate-SIPHT([int]$numTasks, [string]$outFile) {
    $sb = New-Object System.Text.StringBuilder
    $null = $sb.AppendLine('<?xml version="1.0" encoding="UTF-8"?>')
    $null = $sb.AppendLine("<adag xmlns=`"http://pegasus.isi.edu/schema/DAX`" version=`"3.3`" name=`"SIPHT-$numTasks`">")
    
    $s1 = [Math]::Max(2, [int]($numTasks * 0.20))
    $s2 = [Math]::Max(4, [int]($numTasks * 0.50))
    $s3 = [Math]::Max(2, [int]($numTasks * 0.25))
    $s4 = $numTasks - ($s1 + $s2 + $s3)
    if ($s4 -lt 1) { $s4 = 1; $s3 = $numTasks - ($s1 + $s2 + 1) }

    $namesS1 = @("blast", "blast_x", "blast_p")
    $namesS2 = @("FindTerm", "RNAz", "QRNA", "RNAMotif")
    $namesS3 = @("SRNA", "Patser", "Annotate")
    $namesS4 = @("Aggregate", "SummaryReport")

    $rng = New-Object System.Random(42 + $numTasks)

    # Generate jobs
    for ($i = 0; $i -lt $numTasks; $i++) {
        $stageName = ""
        $runtime = 5
        $outSize = 10000000
        if ($i -lt $s1) {
            $stageName = $namesS1[$rng.Next($namesS1.Length)]
            $runtime = 10 + $rng.Next(25)
            $outSize = 15000000 + $rng.Next(25000000)
        } elseif ($i -lt ($s1 + $s2)) {
            $stageName = $namesS2[$rng.Next($namesS2.Length)]
            $runtime = 5 + $rng.Next(15)
            $outSize = 5000000 + $rng.Next(15000000)
        } elseif ($i -lt ($s1 + $s2 + $s3)) {
            $stageName = $namesS3[$rng.Next($namesS3.Length)]
            $runtime = 8 + $rng.Next(20)
            $outSize = 8000000 + $rng.Next(18000000)
        } else {
            $stageName = $namesS4[$rng.Next($namesS4.Length)]
            $runtime = 12 + $rng.Next(15)
            $outSize = 20000000 + $rng.Next(30000000)
        }
        $null = $sb.AppendLine("  <job id=`"sipht-$i`" name=`"$stageName`" runtime=`"$runtime`">")
        $null = $sb.AppendLine("    <uses file=`"in_$i.dat`" link=`"input`" size=`"20000000`"/>")
        $null = $sb.AppendLine("    <uses file=`"out_$i.dat`" link=`"output`" size=`"$outSize`"/>")
        $null = $sb.AppendLine("  </job>")
    }

    # Generate dependencies
    $edges = @()
    # S2 depends on S1
    for ($i = $s1; $i -lt ($s1 + $s2); $i++) {
        $p1 = $rng.Next($s1)
        $edges += [PSCustomObject]@{ Child="sipht-$i"; Parent="sipht-$p1" }
        if ($rng.NextDouble() -lt 0.35 -and $s1 -gt 1) {
            $p2 = ($p1 + 1) % $s1
            $edges += [PSCustomObject]@{ Child="sipht-$i"; Parent="sipht-$p2" }
        }
    }
    # S3 depends on S2
    for ($i = ($s1 + $s2); $i -lt ($s1 + $s2 + $s3); $i++) {
        $p1 = $s1 + $rng.Next($s2)
        $edges += [PSCustomObject]@{ Child="sipht-$i"; Parent="sipht-$p1" }
        if ($s2 -gt 1) {
            $p2 = $s1 + (($p1 - $s1 + 1) % $s2)
            $edges += [PSCustomObject]@{ Child="sipht-$i"; Parent="sipht-$p2" }
        }
    }
    # S4 depends on S3
    for ($i = ($s1 + $s2 + $s3); $i -lt $numTasks; $i++) {
        $parentCount = [Math]::Min(3, $s3)
        for ($k = 0; $k -lt $parentCount; $k++) {
            $p = ($s1 + $s2) + (($i + $k) % $s3)
            $edges += [PSCustomObject]@{ Child="sipht-$i"; Parent="sipht-$p" }
        }
    }

    # Group edges by child
    $grouped = $edges | Group-Object Child
    foreach ($g in $grouped) {
        $cRef = $g.Name
        $parentRefs = $g.Group | Select-Object -ExpandProperty Parent -Unique
        $null = $sb.Append("  <child ref=`"$cRef`">")
        foreach ($pRef in $parentRefs) {
            $null = $sb.Append("<parent ref=`"$pRef`"/>")
        }
        $null = $sb.AppendLine("</child>")
    }

    $null = $sb.AppendLine("</adag>")
    [System.IO.File]::WriteAllText($outFile, $sb.ToString(), [System.Text.Encoding]::UTF8)
}

function Generate-Montage([int]$numTasks, [string]$outFile) {
    $sb = New-Object System.Text.StringBuilder
    $null = $sb.AppendLine('<?xml version="1.0" encoding="UTF-8"?>')
    $null = $sb.AppendLine("<adag xmlns=`"http://pegasus.isi.edu/schema/DAX`" version=`"3.3`" name=`"Montage-$numTasks`">")
    
    $s1 = [Math]::Max(2, [int]($numTasks * 0.25))
    $s2 = [Math]::Max(4, [int]($numTasks * 0.40))
    $s3 = [Math]::Max(2, [int]($numTasks * 0.15))
    $s4 = [Math]::Max(2, [int]($numTasks * 0.15))
    $s5 = $numTasks - ($s1 + $s2 + $s3 + $s4)
    if ($s5 -lt 1) { $s5 = 1; $s4 = $numTasks - ($s1 + $s2 + $s3 + 1) }

    $rng = New-Object System.Random(101 + $numTasks)

    for ($i = 0; $i -lt $numTasks; $i++) {
        $name = ""
        $runtime = 10
        $outSize = 15000000
        if ($i -lt $s1) {
            $name = "mProjectPP"
            $runtime = 8 + $rng.Next(10)
            $outSize = 25000000 + $rng.Next(20000000)
        } elseif ($i -lt ($s1 + $s2)) {
            $name = "mDiffFit"
            $runtime = 12 + $rng.Next(15)
            $outSize = 15000000 + $rng.Next(15000000)
        } elseif ($i -lt ($s1 + $s2 + $s3)) {
            $name = "mBgModel"
            $runtime = 10 + $rng.Next(8)
            $outSize = 10000000 + $rng.Next(10000000)
        } elseif ($i -lt ($s1 + $s2 + $s3 + $s4)) {
            $name = "mBackground"
            $runtime = 6 + $rng.Next(8)
            $outSize = 12000000 + $rng.Next(12000000)
        } else {
            $name = "mAdd"
            $runtime = 15 + $rng.Next(12)
            $outSize = 30000000 + $rng.Next(30000000)
        }
        $null = $sb.AppendLine("  <job id=`"montage-$i`" name=`"$name`" runtime=`"$runtime`">")
        $null = $sb.AppendLine("    <uses file=`"in_$i.fits`" link=`"input`" size=`"20000000`"/>")
        $null = $sb.AppendLine("    <uses file=`"out_$i.fits`" link=`"output`" size=`"$outSize`"/>")
        $null = $sb.AppendLine("  </job>")
    }

    $edges = @()
    for ($i = $s1; $i -lt ($s1 + $s2); $i++) {
        $p1 = ($i - $s1) % $s1
        $edges += [PSCustomObject]@{ Child="montage-$i"; Parent="montage-$p1" }
        $p2 = ($p1 + 1) % $s1
        $edges += [PSCustomObject]@{ Child="montage-$i"; Parent="montage-$p2" }
    }
    for ($i = ($s1 + $s2); $i -lt ($s1 + $s2 + $s3); $i++) {
        $p = $s1 + $rng.Next($s2)
        $edges += [PSCustomObject]@{ Child="montage-$i"; Parent="montage-$p" }
    }
    for ($i = ($s1 + $s2 + $s3); $i -lt ($s1 + $s2 + $s3 + $s4); $i++) {
        $p = ($s1 + $s2) + $rng.Next($s3)
        $edges += [PSCustomObject]@{ Child="montage-$i"; Parent="montage-$p" }
    }
    for ($i = ($s1 + $s2 + $s3 + $s4); $i -lt $numTasks; $i++) {
        for ($k = 0; $k -lt [Math]::Min(3, $s4); $k++) {
            $p = ($s1 + $s2 + $s3) + (($i + $k) % $s4)
            $edges += [PSCustomObject]@{ Child="montage-$i"; Parent="montage-$p" }
        }
    }

    $grouped = $edges | Group-Object Child
    foreach ($g in $grouped) {
        $cRef = $g.Name
        $parentRefs = $g.Group | Select-Object -ExpandProperty Parent -Unique
        $null = $sb.Append("  <child ref=`"$cRef`">")
        foreach ($pRef in $parentRefs) {
            $null = $sb.Append("<parent ref=`"$pRef`"/>")
        }
        $null = $sb.AppendLine("</child>")
    }

    $null = $sb.AppendLine("</adag>")
    [System.IO.File]::WriteAllText($outFile, $sb.ToString(), [System.Text.Encoding]::UTF8)
}

function Generate-CyberShake([int]$numTasks, [string]$outFile) {
    $sb = New-Object System.Text.StringBuilder
    $null = $sb.AppendLine('<?xml version="1.0" encoding="UTF-8"?>')
    $null = $sb.AppendLine("<adag xmlns=`"http://pegasus.isi.edu/schema/DAX`" version=`"3.3`" name=`"CyberShake-$numTasks`">")
    
    $s1 = [Math]::Max(2, [int]($numTasks * 0.15))
    $s2 = [Math]::Max(4, [int]($numTasks * 0.55))
    $s3 = [Math]::Max(2, [int]($numTasks * 0.25))
    $s4 = $numTasks - ($s1 + $s2 + $s3)
    if ($s4 -lt 1) { $s4 = 1; $s3 = $numTasks - ($s1 + $s2 + 1) }

    $rng = New-Object System.Random(303 + $numTasks)

    for ($i = 0; $i -lt $numTasks; $i++) {
        $name = ""
        $runtime = 15
        $outSize = 20000000
        if ($i -lt $s1) {
            $name = "ExtractSgt"
            $runtime = 20 + $rng.Next(25)
            $outSize = 35000000 + $rng.Next(40000000)
        } elseif ($i -lt ($s1 + $s2)) {
            $name = "SeismogramSynthesis"
            $runtime = 25 + $rng.Next(35)
            $outSize = 20000000 + $rng.Next(30000000)
        } elseif ($i -lt ($s1 + $s2 + $s3)) {
            $name = "PeakSpectralAcceleration"
            $runtime = 15 + $rng.Next(15)
            $outSize = 10000000 + $rng.Next(15000000)
        } else {
            $name = "ZipSeis"
            $runtime = 8 + $rng.Next(10)
            $outSize = 40000000 + $rng.Next(50000000)
        }
        $null = $sb.AppendLine("  <job id=`"cybershake-$i`" name=`"$name`" runtime=`"$runtime`">")
        $null = $sb.AppendLine("    <uses file=`"in_$i.sgt`" link=`"input`" size=`"30000000`"/>")
        $null = $sb.AppendLine("    <uses file=`"out_$i.sgt`" link=`"output`" size=`"$outSize`"/>")
        $null = $sb.AppendLine("  </job>")
    }

    $edges = @()
    for ($i = $s1; $i -lt ($s1 + $s2); $i++) {
        $p = ($i - $s1) % $s1
        $edges += [PSCustomObject]@{ Child="cybershake-$i"; Parent="cybershake-$p" }
    }
    for ($i = ($s1 + $s2); $i -lt ($s1 + $s2 + $s3); $i++) {
        $p = $s1 + (($i - ($s1 + $s2)) % $s2)
        $edges += [PSCustomObject]@{ Child="cybershake-$i"; Parent="cybershake-$p" }
    }
    for ($i = ($s1 + $s2 + $s3); $i -lt $numTasks; $i++) {
        for ($k = 0; $k -lt [Math]::Min(3, $s3); $k++) {
            $p = ($s1 + $s2) + (($i + $k) % $s3)
            $edges += [PSCustomObject]@{ Child="cybershake-$i"; Parent="cybershake-$p" }
        }
    }

    $grouped = $edges | Group-Object Child
    foreach ($g in $grouped) {
        $cRef = $g.Name
        $parentRefs = $g.Group | Select-Object -ExpandProperty Parent -Unique
        $null = $sb.Append("  <child ref=`"$cRef`">")
        foreach ($pRef in $parentRefs) {
            $null = $sb.Append("<parent ref=`"$pRef`"/>")
        }
        $null = $sb.AppendLine("</child>")
    }

    $null = $sb.AppendLine("</adag>")
    [System.IO.File]::WriteAllText($outFile, $sb.ToString(), [System.Text.Encoding]::UTF8)
}

# Generate all 100 and 200 task datasets + default files
Generate-SIPHT 100 "$destDir\sipht_100.dax"
Generate-SIPHT 200 "$destDir\sipht_200.dax"
Copy-Item "$destDir\sipht_100.dax" "$destDir\sipht.dax" -Force

Generate-Montage 100 "$destDir\montage_100.dax"
Generate-Montage 200 "$destDir\montage_200.dax"
Copy-Item "$destDir\montage_100.dax" "$destDir\montage.dax" -Force

Generate-CyberShake 100 "$destDir\cybershake_100.dax"
Generate-CyberShake 200 "$destDir\cybershake_200.dax"
Copy-Item "$destDir\cybershake_100.dax" "$destDir\cybershake.dax" -Force

Write-Host "Generated all datasets successfully in $destDir"
