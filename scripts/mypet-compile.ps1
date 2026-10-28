# Compile helper for MyPet's v26_3_R1 module (javac, no Gradle) against the Paper 26.3 server's own jars.
# Prereq: work\refs contains paper-26.3.jar, mojang_26.3.jar, libraries\ (copied from the server), MyPet-3.14.3.jar (original),
#         deps\annotations.jar, deps\lombok.jar. See README.md.
param([string]$Root = (Resolve-Path "$PSScriptRoot\..").Path)
$refs = "$Root\work\refs"; $out = "$Root\work\out\mypet-v26_3"
if (Test-Path $out) { Remove-Item -Recurse -Force $out }; New-Item -ItemType Directory -Force $out | Out-Null
$jars = Get-ChildItem "$refs\libraries" -Recurse -Filter *.jar | Where-Object { $_.Name -notmatch 'sources' -and $_.FullName -notmatch 'paper-api\\(1\.|26\.2)' -and $_.FullName -notmatch 'paper-mojangapi' }
$latest = $jars | Group-Object { $_.Directory.Parent.FullName } | ForEach-Object { $_.Group | Sort-Object { try { [version](($_.Directory.Name -replace '[^\d\.].*$','')) } catch { [version]'0.0' } } | Select-Object -Last 1 }
$cp = @("$refs\paper-26.3.jar","$refs\mojang_26.3.jar","$refs\MyPet-3.14.3.jar","$refs\deps\annotations.jar","$refs\deps\lombok.jar") + ($latest | ForEach-Object FullName)
$cpArg = '"' + (($cp -join ';').Replace('\','/')) + '"'
$files = Get-ChildItem "$Root\work\MyPet\nms\v26_3_R1\src\main\java" -Recurse -Filter *.java | ForEach-Object { '"' + $_.FullName.Replace('\','/') + '"' }
$procArg = '"' + "$refs\deps\lombok.jar".Replace('\','/') + '"'; $outArg = '"' + $out.Replace('\','/') + '"'
$lines = @('--release','25','-nowarn','-Xmaxerrs','500','-encoding','UTF-8','-cp',$cpArg,'-processorpath',$procArg,'-d',$outArg) + $files
$args = "$env:TEMP\mypet-javac.args"; [IO.File]::WriteAllLines($args, $lines, (New-Object Text.UTF8Encoding $false))
$log = "$env:TEMP\mypet-javac.log"; cmd /c "javac @$args > $log 2>&1"
$t = [IO.File]::ReadAllText($log, [Text.Encoding]::GetEncoding(932))
($t -split "`r?`n") | Where-Object { $_ -notmatch '^WARNING:' -and $_ -notmatch '警告' -and $_.Trim() } | ForEach-Object { $_ -replace '.*\\compat\\v26_3_R1\\','' }
'classes: ' + (Get-ChildItem $out -Recurse -Filter *.class -ErrorAction SilentlyContinue | Measure-Object).Count

