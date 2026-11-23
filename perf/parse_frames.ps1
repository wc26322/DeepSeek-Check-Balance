# Parse gfxinfo framestats: split each frame into stages and report percentiles (ASCII only)
param([string]$Dir = "$PSScriptRoot")
$ErrorActionPreference = 'Continue'
function Pct($arr, $p) { if ($arr.Count -eq 0) { return $null }; $s = @($arr | Sort-Object); $i = [math]::Min($s.Count - 1, [int][math]::Floor($p / 100.0 * $s.Count)); return [math]::Round($s[$i], 1) }
Get-ChildItem "$Dir\*.framestats.txt" | ForEach-Object {
  $file = $_
  $lines = Get-Content $file.FullName
  $hi = ($lines | Select-String -Pattern '^Flags,' | Select-Object -First 1).LineNumber
  if (-not $hi) { return }
  $hdr = $lines[$hi - 1].Split(',')
  $idx = @{}; for ($i = 0; $i -lt $hdr.Count; $i++) { $idx[$hdr[$i]] = $i }
  $rows = New-Object System.Collections.ArrayList
  for ($r = $hi; $r -lt $lines.Count; $r++) {
    $l = $lines[$r]; if (-not $l -or $l -match '^[^\d-]') { continue }
    $c = $l.Split(','); if ($c.Count -lt $hdr.Count) { continue }
    function G($n) { return [double]$script:c[$script:idx[$n]] }
    $flags = G 'Flags'
    $iv = G 'IntendedVsync'; $vs = G 'Vsync'; $his = G 'HandleInputStart'; $an = G 'AnimationStart'
    $pt = G 'PerformTraversalsStart'; $sq = G 'SyncQueued'; $ss = G 'SyncStart'; $sb = G 'SwapBuffers'
    $fc = G 'FrameCompleted'; $gc = G 'GpuCompleted'
    [void]$rows.Add([pscustomobject]@{
      Flags = $flags
      Total = ($fc - $iv) / 1e6
      WaitVsync = ($vs - $iv) / 1e6
      Input = ($an - $his) / 1e6
      AnimMeas = ($pt - $an) / 1e6
      LayoutDraw = ($sq - $pt) / 1e6
      UI = ($sq - $his) / 1e6
      SyncQueue = ($ss - $sq) / 1e6
      RT = ($sb - $ss) / 1e6
      QueueBuf = (G 'QueueBufferDuration') / 1e6
      Gpu = ($gc - $sb) / 1e6
      Interval = (G 'FrameInterval') / 1e6
    })
  }
  $rows = @($rows | Where-Object { $_.Flags -eq 0 })
  if ($rows.Count -eq 0) { "$($file.BaseName): no rows"; return }
  $iv = ($rows | Select-Object -First 1).Interval
  $over = @($rows | Where-Object { $_.Total -gt $iv }).Count
  "=== $($file.BaseName)  frames=$($rows.Count)  interval=${iv}ms  frames>interval=$over ==="
  foreach ($st in 'Total','WaitVsync','Input','AnimMeas','LayoutDraw','UI','SyncQueue','RT','QueueBuf','Gpu') {
    $v = @($rows | ForEach-Object { $_.$st })
    "{0,-10} p50={1,6}  p90={2,6}  p99={3,6}  max={4,6}" -f $st, (Pct $v 50), (Pct $v 90), (Pct $v 99), (Pct $v 100)
  }
  ""
}
