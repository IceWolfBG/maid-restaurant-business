$ErrorActionPreference = 'Stop'
$files = @(
  'D:\DoubaoWork\MaidRestaurantBusiness\forge-1.20.1\src\main\java\com\icewolf\maidrestaurant\business\core\CookingBridge.java',
  'D:\DoubaoWork\MaidRestaurantBusiness\neoforge-1.21.1\src\main\java\com\icewolf\maidrestaurant\business\core\CookingBridge.java'
)

$oldA = @(
  '                } else if (predicate.test(lavaBucket) && !predicate.test(emptyBucket)) {',
  '                    fluid = net.minecraft.world.level.material.Fluids.LAVA;',
  '                    filled = net.minecraft.world.item.Items.LAVA_BUCKET;',
  '                }',
  '                if (fluid == null) continue;'
) -join "`n"
$newA = @(
  '                } else if (predicate.test(lavaBucket) && !predicate.test(emptyBucket)) {',
  '                    fluid = net.minecraft.world.level.material.Fluids.LAVA;',
  '                    filled = net.minecraft.world.item.Items.LAVA_BUCKET;',
  '                }',
  '                if (predicate.test(waterBucket) || predicate.test(lavaBucket)) {',
  '                    MaidRestaurantBusiness.LOGGER.info("FluidBonus classify: fluid={} testEmptyBucket={}",',
  '                            fluid, predicate.test(emptyBucket));',
  '                }',
  '                if (fluid == null) continue;'
) -join "`n"

$oldB = @(
  '                // 厨师背包必须有空桶（她要拿去流体存储接）',
  '                if (!maidHasItem(maidInv, net.minecraft.world.item.Items.BUCKET)) continue;',
  '                // 操作台附近流体存储必须有足量对应流体',
  '                if (!nearbyFluidAvailable(level, center, fluid, amount, self)) continue;'
) -join "`n"
$newB = @(
  '                // 厨师背包必须有空桶（她要拿去流体存储接）',
  '                boolean hasEmptyBucket = maidHasItem(maidInv, net.minecraft.world.item.Items.BUCKET);',
  '                MaidRestaurantBusiness.LOGGER.info("FluidBonus gate: maidHasEmptyBucket={}", hasEmptyBucket);',
  '                if (!hasEmptyBucket) continue;',
  '                // 操作台附近流体存储必须有足量对应流体',
  '                boolean fluidNear = nearbyFluidAvailable(level, center, fluid, amount, self);',
  '                MaidRestaurantBusiness.LOGGER.info("FluidBonus gate: nearbyFluidAvailable={} center={}", fluidNear, center);',
  '                if (!fluidNear) continue;'
) -join "`n"

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
foreach ($f in $files) {
  $text = [IO.File]::ReadAllText($f, $utf8NoBom)
  $norm = $text -replace "`r`n","`n"
  foreach ($pair in @(@($oldA,$newA,'A'), @($oldB,$newB,'B'))) {
    $o=$pair[0]; $n=$pair[1]; $tag=$pair[2]
    $cnt = ([regex]::Matches($norm,[regex]::Escape($o))).Count
    if ($cnt -ne 1) { throw "expected 1 match for block $tag in $f got $cnt" }
    $norm = $norm.Replace($o,$n)
  }
  $out = $norm -replace "`n","`r`n"
  [IO.File]::WriteAllText($f,$out,$utf8NoBom)
  Write-Output "OK: $f"
}
