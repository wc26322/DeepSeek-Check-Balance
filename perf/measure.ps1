# Performance baseline: fixed scenarios -> FPS_MONITOR / JankStats / gfxinfo / framestats (ASCII only)
$ErrorActionPreference = 'Continue'
$Prefix = ''
if ($args.Count -gt 0) { $Prefix = $args[0] }
$pkg = 'com.deepseek.balance'
$out = "$PSScriptRoot"
function Sh([string]$a) { (adb shell $a 2>&1) -join "`n" }
function Reset { Sh "dumpsys gfxinfo $pkg reset" | Out-Null; adb logcat -c 2>&1 | Out-Null }
function Swipe($x1,$y1,$x2,$y2,$ms) { Sh "input swipe $x1 $y1 $x2 $y2 $ms" | Out-Null }
function Tap($x,$y) { Sh "input tap $x $y" | Out-Null }
function Collect([string]$name) {
  Start-Sleep -Milliseconds 800
  $g = Sh "dumpsys gfxinfo $pkg"
  $frames = [regex]::Match($g, 'Total frames rendered: (\d+)').Groups[1].Value
  $janky  = [regex]::Match($g, 'Janky frames: (\d+) \(([\d.]+)%\)').Groups
  $p50 = [regex]::Match($g, '50th percentile: (\d+)ms').Groups[1].Value
  $p90 = [regex]::Match($g, '90th percentile: (\d+)ms').Groups[1].Value
  $p95 = [regex]::Match($g, '95th percentile: (\d+)ms').Groups[1].Value
  $p99 = [regex]::Match($g, '99th percentile: (\d+)ms').Groups[1].Value
  $fpsLine = (adb logcat -d -s FPS_MONITOR:D 2>&1 | Select-Object -Last 1)
  $fps = [regex]::Match("$fpsLine", 'FPS\u2248([\d.]+).*max=([\d.]+)ms p99=([\d.]+)ms \u6389\u62cd\u7387=([\d.]+)%').Groups
  $jankCount = (adb logcat -d -s JANKSTATS:W 2>&1 | Select-String 'JANK \u8017\u65f6').Count
  Sh "dumpsys gfxinfo $pkg framestats" | Out-File "$out\$Prefix$name.framestats.txt" -Encoding utf8
  $g | Out-File "$out\$Prefix$name.gfxinfo.txt" -Encoding utf8
  [pscustomobject]@{
    Scenario=$name; Frames=$frames; Janky="$($janky[1].Value)($($janky[2].Value)%)"; JankStats=$jankCount
    p50ms=$p50; p90ms=$p90; p95ms=$p95; p99ms=$p99
    FPS=$fps[1].Value; MaxGap=$fps[2].Value; p99Gap=$fps[3].Value; MissRate=$fps[4].Value
  }
}
$results = @()

# S1 main screen idle
Sh "am force-stop $pkg" | Out-Null; Start-Sleep 1
Sh "am start -W -n $pkg/.MainActivity" | Out-Null; Start-Sleep 3
adb logcat -d -s REFRESH:I 2>&1 | Out-File "$out\${Prefix}refresh.txt" -Encoding utf8
Reset; Start-Sleep 3
$results += Collect 'S1-idle-main'

# S2 main screen scroll
Reset
1..3 | ForEach-Object { Swipe 630 2000 630 800 400; Start-Sleep -Milliseconds 500 }
1..3 | ForEach-Object { Swipe 630 800 630 2000 400; Start-Sleep -Milliseconds 500 }
$results += Collect 'S2-scroll-main'

# S3 settings scroll (LazyColumn of glass cards)
Reset
Tap 903 2671; Start-Sleep 2
1..4 | ForEach-Object { Swipe 630 2200 630 700 400; Start-Sleep -Milliseconds 500 }
1..4 | ForEach-Object { Swipe 630 700 630 2200 400; Start-Sleep -Milliseconds 500 }
$results += Collect 'S3-scroll-settings'

# S4 capsule drag (slow, back and forth)
Reset
1..2 | ForEach-Object { Swipe 903 2671 357 2671 600; Start-Sleep 1; Swipe 357 2671 903 2671 600; Start-Sleep 1 }
$results += Collect 'S4-capsule-drag'

# S5 cold start x3
$starts = @()
1..3 | ForEach-Object {
  Sh "am force-stop $pkg" | Out-Null; Start-Sleep 1; Reset
  $w = Sh "am start -W -n $pkg/.MainActivity"
  $starts += [int][regex]::Match($w, 'TotalTime: (\d+)').Groups[1].Value
  Start-Sleep 2
  $results += Collect "S5-coldstart-$_"
}
"cold start TotalTime (ms): $($starts -join ', ')" | Out-File "$out\coldstart.txt" -Encoding utf8

$results | Format-Table -AutoSize | Out-String -Width 220 | Out-File "$out\${Prefix}results.txt" -Encoding utf8
$results | Format-Table -AutoSize | Out-String -Width 220
"cold start: $($starts -join ', ')"
