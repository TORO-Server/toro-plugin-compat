# Package MyPet 3.14.3 + the locally compiled v26_3_R1 compat module into one jar.
param([string]$Root = (Resolve-Path "$PSScriptRoot\..").Path)
$ErrorActionPreference = 'Stop'
$orig = "$Root\work\refs\MyPet-3.14.3.jar"; $classes = "$Root\work\out\mypet-v26_3"; $stage = "$Root\work\out\mypet-stage"
$result = "$Root\dist\MyPet-3.14.3-mc26.3.jar"
if (Test-Path $stage) { Remove-Item -Recurse -Force $stage }; New-Item -ItemType Directory -Force $stage, "$Root\dist" | Out-Null
Push-Location $stage; jar xf $orig; Pop-Location
Copy-Item -Recurse -Force "$classes\*" $stage
# versionmatcher.csv: 26.3 -> v26_3_R1
$csv = "$stage\versionmatcher.csv"; $t = [IO.File]::ReadAllText($csv)
if ($t -notmatch '(?m)^26\.3,') { $t = $t.TrimEnd() + "`n26.3,v26_3_R1`n"; [IO.File]::WriteAllText($csv, $t) }
# manifest: wrap long header lines at 72 bytes
function Wrap($line) { $o = ''; $max = 72; while ($line.Length -gt $max) { $o += $line.Substring(0,$max) + "`r`n "; $line = $line.Substring($max); $max = 71 }; $o + $line + "`r`n" }
$mf = "$stage\META-INF\MANIFEST.MF"; $raw = [IO.File]::ReadAllText($mf) -replace "\r?\n ", ""
$lines = $raw -split "\r?\n" | Where-Object { $_ -ne '' }
$new = foreach ($l in $lines) {
  if ($l -like 'Project-Minecraft-Version:*') { 'Project-Minecraft-Version: 26.3' }
  elseif ($l -like 'Project-Bukkit-Packets:*' -and $l -notmatch 'v26_3_R1') { $l + ';v26_3_R1' }
  else { $l } }
[IO.File]::WriteAllText($mf, (($new | ForEach-Object { Wrap $_ }) -join '') + "`r`n", (New-Object Text.UTF8Encoding $false))
if (Test-Path $result) { Remove-Item $result }
jar cfm $result $mf -C $stage . 2>&1 | Select-Object -First 3
Get-Item $result | Select-Object Name, Length
