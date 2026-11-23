# Reduced scenario set (S2 main scroll + S3 settings scroll) for A/B attribution (ASCII only)
$ErrorActionPreference = 'Continue'
$Prefix = ''
if ($args.Count -gt 0) { $Prefix = $args[0] }
$pkg = 'com.deepseek.balance'
$out = "$PSScriptRoot"
function Sh([string]$a) { (adb shell $a 2>&1) -join "`n" }
function Reset { Sh "dumpsys gfxinfo $pkg reset" | Out-Null }
function Swipe($x1,$y1,$x2,$y2,$ms) { Sh "input swipe $x1 $y1 $x2 $y2 $ms" | Out-Null }
function Tap($x,$y) { Sh "input tap $x $y" | Out-Null }
function Collect([string]$name) {
  Start-Sleep -Milliseconds 800
  $g = Sh "dumpsys gfxinfo $pkg"
  Sh "dumpsys gfxinfo $pkg framestats" | Out-File "$out\${Prefix}$name.framestats.txt" -Encoding utf8
  $frames = [regex]::Match($g, 'Total frames rendered: (\d+)').Groups[1].Value
  $p50 = [regex]::Match($g, '50th percentile: (\d+)ms').Groups[1].Value
  $p90 = [regex]::Match($g, '90th percentile: (\d+)ms').Groups[1].Value
  $p99 = [regex]::Match($g, '99th percentile: (\d+)ms').Groups[1].Value
  [pscustomobject]@{ Scenario="$Prefix$name"; Frames=$frames; p50=$p50; p90=$p90; p99=$p99 }
}
$results = @()
Sh "am force-stop $pkg" | Out-Null; Start-Sleep 1
Sh "am start -W -n $pkg/.MainActivity" | Out-Null; Start-Sleep 3
Reset
1..3 | ForEach-Object { Swipe 630 2000 630 800 400; Start-Sleep -Milliseconds 500 }
1..3 | ForEach-Object { Swipe 630 800 630 2000 400; Start-Sleep -Milliseconds 500 }
$results += Collect 'S2'
Reset
Tap 903 2671; Start-Sleep 2
1..4 | ForEach-Object { Swipe 630 2200 630 700 400; Start-Sleep -Milliseconds 500 }
1..4 | ForEach-Object { Swipe 630 700 630 2200 400; Start-Sleep -Milliseconds 500 }
$results += Collect 'S3'
$results | Format-Table -AutoSize | Out-String -Width 200
