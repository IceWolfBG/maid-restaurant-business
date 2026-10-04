$ErrorActionPreference = 'Stop'
$files = @(
  'D:\DoubaoWork\maid_restaurant_storage-1.20.1-forge\src\main\java\com\example\maidrestaurant\rscompat\fluid\SingleServingFluidStorage.java',
  'D:\DoubaoWork\maid_restaurant_storage-1.21.1-neoforge\src\main\java\com\example\maidrestaurant\rscompat\fluid\SingleServingFluidStorage.java'
)
$old = @(
  '            return state.getValue(LayeredCauldronBlock.LEVEL) * FluidContainerUtils.BUCKET_CAPACITY;'
) -join "`n"
$new = @(
  '            // 原版机制：空桶只能从满锅(3层)接出一桶；玻璃瓶每层一瓶(333)。',
  '            // 满锅报1000（满足1桶或3瓶），1/2层报 level*333（仅够对应瓶数、不够一桶）。',
  '            int cauldronLevel = state.getValue(LayeredCauldronBlock.LEVEL);',
  '            return cauldronLevel >= 3 ? FluidContainerUtils.BUCKET_CAPACITY',
  '                    : cauldronLevel * FluidContainerUtils.BOTTLE_CAPACITY;'
) -join "`n"

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
foreach ($f in $files) {
  $text = [IO.File]::ReadAllText($f, (New-Object System.Text.UTF8Encoding($false)))
  $norm = $text -replace "`r`n", "`n"
  $cnt = ([regex]::Matches($norm, [regex]::Escape($old))).Count
  if ($cnt -ne 1) { throw "expected 1 match in $f, got $cnt" }
  $norm = $norm.Replace($old, $new)
  $out = $norm -replace "`n", "`r`n"
  [IO.File]::WriteAllText($f, $out, $utf8NoBom)
  Write-Output "OK: $f"
}
