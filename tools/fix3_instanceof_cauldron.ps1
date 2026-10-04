$ErrorActionPreference = 'Stop'
$files = @(
  'D:\DoubaoWork\maid_restaurant_storage-1.20.1-forge\src\main\java\com\example\maidrestaurant\rscompat\fluid\SingleServingFluidStorage.java',
  'D:\DoubaoWork\maid_restaurant_storage-1.21.1-neoforge\src\main\java\com\example\maidrestaurant\rscompat\fluid\SingleServingFluidStorage.java'
)

$old1 = @(
  '        if (block.getClass() == LayeredCauldronBlock.class) {',
  '            return state.getValue(LayeredCauldronBlock.LEVEL) >= 1;',
  '        }'
) -join "`n"
$new1 = @(
  '        if (block instanceof LayeredCauldronBlock) {',
  '            // 用 instanceof 而非 getClass()==：装水后 ID 变为子类炼药锅时也能识别',
  '            return state.hasProperty(LayeredCauldronBlock.LEVEL)',
  '                    && state.getValue(LayeredCauldronBlock.LEVEL) >= 1;',
  '        }'
) -join "`n"

$old2 = @(
  '        if (block.getClass() == LayeredCauldronBlock.class) {',
  '            if (want != Fluids.WATER) {',
  '                return 0;',
  '            }',
  '            // 原版机制：空桶只能从满锅(3层)接出一桶；玻璃瓶每层一瓶(333)。',
  '            // 满锅报1000（满足1桶或3瓶），1/2层报 level*333（仅够对应瓶数、不够一桶）。',
  '            int cauldronLevel = state.getValue(LayeredCauldronBlock.LEVEL);',
  '            return cauldronLevel >= 3 ? FluidContainerUtils.BUCKET_CAPACITY',
  '                    : cauldronLevel * FluidContainerUtils.BOTTLE_CAPACITY;',
  '        }'
) -join "`n"
$new2 = @(
  '        if (block instanceof LayeredCauldronBlock) {',
  '            if (want != Fluids.WATER) {',
  '                return 0;',
  '            }',
  '            // 原版机制：空桶只能从满锅(3层)接出一桶；玻璃瓶每层一瓶(333)。',
  '            // 满锅报1000（满足1桶或3瓶），1/2层报 level*333（仅够对应瓶数、不够一桶）。',
  '            // 子类炼药锅若无 LEVEL 属性则保守返回 0，交由失败诊断日志标识。',
  '            if (!state.hasProperty(LayeredCauldronBlock.LEVEL)) {',
  '                return 0;',
  '            }',
  '            int cauldronLevel = state.getValue(LayeredCauldronBlock.LEVEL);',
  '            return cauldronLevel >= 3 ? FluidContainerUtils.BUCKET_CAPACITY',
  '                    : cauldronLevel * FluidContainerUtils.BOTTLE_CAPACITY;',
  '        }'
) -join "`n"

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
foreach ($f in $files) {
  $text = [IO.File]::ReadAllText($f, $utf8NoBom)
  $norm = $text -replace "`r`n", "`n"
  foreach ($pair in @(@($old1,$new1,'old1'), @($old2,$new2,'old2'))) {
    $o = $pair[0]; $n = $pair[1]; $tag = $pair[2]
    $cnt = ([regex]::Matches($norm, [regex]::Escape($o))).Count
    if ($cnt -ne 1) { throw "expected 1 match for $tag in $f, got $cnt" }
    $norm = $norm.Replace($o, $n)
  }
  $out = $norm -replace "`n", "`r`n"
  [IO.File]::WriteAllText($f, $out, $utf8NoBom)
  Write-Output "OK: $f"
}
