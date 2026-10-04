$ErrorActionPreference = 'Stop'
$f = 'D:\DoubaoWork\MaidRestaurantBusiness\neoforge-1.21.1\src\main\java\com\icewolf\maidrestaurant\business\core\CookingBridge.java'
$raw = [IO.File]::ReadAllText($f)
$content = $raw -replace "`r`n", "`n"

function Pair($old, $new) {
    return [pscustomobject]@{ Old = ($old -join "`n"); New = ($new -join "`n") }
}

$pairs = @(
  Pair @(
'    private static java.lang.reflect.Method rsHasEnoughFluidMethod = null;'
  ) @(
'    private static java.lang.reflect.Method rsAvailableOrFillableMethod = null;'
  )
  Pair @(
'    private static void applyStorageFluidBonus(ServerLevel level, BlockPos center, List<StackPredicate> required,',
'                                               IItemHandler maidInv, Map<Item, Integer> available) {'
  ) @(
'    private static void applyStorageFluidBonus(ServerLevel level, BlockPos center, List<StackPredicate> required,',
'                                               IItemHandler maidInv, Map<Item, Integer> available, java.util.UUID self) {'
  )
  Pair @(
'                    // 直接复用存储附属的 public static hasEnoughFluid(Level,BlockPos,FluidStack,int)',
'                    Class<?> storages = Class.forName("com.example.maidrestaurant.rscompat.fluid.MaidFluidStorages");',
'                    rsHasEnoughFluidMethod = storages.getMethod("hasEnoughFluid",',
'                            Level.class, BlockPos.class, net.neoforged.neoforge.fluids.FluidStack.class, int.class);'
  ) @(
'                    // 复用存储附属发现期 public static isAvailableOrFillable(Level,BlockPos,FluidStack,int,UUID)，',
'                    // 与执行期流体搜索走同一个判定（黑名单/他人预留/现有或可注满空水槽完全同源）',
'                    Class<?> storages = Class.forName("com.example.maidrestaurant.rscompat.fluid.MaidFluidStorages");',
'                    rsAvailableOrFillableMethod = storages.getMethod("isAvailableOrFillable",',
'                            Level.class, BlockPos.class, net.neoforged.neoforge.fluids.FluidStack.class, int.class, java.util.UUID.class);'
  )
  Pair @(
'            if (rsHasEnoughFluidMethod == null) return;'
  ) @(
'            if (rsAvailableOrFillableMethod == null) return;'
  )
  Pair @(
'                if (!nearbyFluidAvailable(level, center, fluid, amount)) continue;'
  ) @(
'                if (!nearbyFluidAvailable(level, center, fluid, amount, self)) continue;'
  )
  Pair @(
'    /** 附近是否有流体存储含足量指定流体；结果按(维度,中心,流体)缓存20tick，避免每个食物重复扫描。 */',
'    private static boolean nearbyFluidAvailable(ServerLevel level, BlockPos center,',
'                                                net.minecraft.world.level.material.Fluid fluid, int amount) {',
'        try {',
'            String key = level.dimension().location() + "|" + center.asLong() + "|" + (fluid == net.minecraft.world.level.material.Fluids.WATER ? "w" : "l");',
'            long now = level.getGameTime();',
'            Long cachedTick = fluidAvailableTick.get(key);',
'            if (cachedTick != null && now - cachedTick < 20L) {',
'                return Boolean.TRUE.equals(fluidAvailableCache.get(key));',
'            }',
'            boolean found = false;',
'            int range = PerformanceConfig.dishScanRange;',
'            net.neoforged.neoforge.fluids.FluidStack probe = new net.neoforged.neoforge.fluids.FluidStack(fluid, amount);',
'            for (BlockPos check : BlockPos.betweenClosed(center.offset(-range, -4, -range), center.offset(range, 4, range))) {',
'                Object ok = rsHasEnoughFluidMethod.invoke(null, level, check.immutable(), probe, amount);',
'                if (Boolean.TRUE.equals(ok)) {',
'                    found = true;',
'                    break;',
'                }',
'            }',
'            fluidAvailableTick.put(key, now);',
'            fluidAvailableCache.put(key, found);',
'            return found;',
'        } catch (Throwable t) {',
'            return false;',
'        }',
'    }'
  ) @(
'    /**',
'     * 附近是否有流体存储“现有足量”或“空但可直接注满”指定流体；与存储附属执行期发现判定同源。',
'     * 结果按(维度,中心,流体,女仆)缓存20tick，避免每个食物重复扫描；女仆入键以正确处理他人预留。',
'     */',
'    private static boolean nearbyFluidAvailable(ServerLevel level, BlockPos center,',
'                                                net.minecraft.world.level.material.Fluid fluid, int amount, java.util.UUID self) {',
'        try {',
'            String key = level.dimension().location() + "|" + center.asLong() + "|"',
'                    + (fluid == net.minecraft.world.level.material.Fluids.WATER ? "w" : "l") + "|" + self;',
'            long now = level.getGameTime();',
'            Long cachedTick = fluidAvailableTick.get(key);',
'            if (cachedTick != null && now - cachedTick < 20L) {',
'                return Boolean.TRUE.equals(fluidAvailableCache.get(key));',
'            }',
'            boolean found = false;',
'            int range = PerformanceConfig.dishScanRange;',
'            net.neoforged.neoforge.fluids.FluidStack probe = new net.neoforged.neoforge.fluids.FluidStack(fluid, amount);',
'            for (BlockPos check : BlockPos.betweenClosed(center.offset(-range, -4, -range), center.offset(range, 4, range))) {',
'                Object ok = rsAvailableOrFillableMethod.invoke(null, level, check.immutable(), probe, amount, self);',
'                if (Boolean.TRUE.equals(ok)) {',
'                    found = true;',
'                    break;',
'                }',
'            }',
'            fluidAvailableTick.put(key, now);',
'            fluidAvailableCache.put(key, found);',
'            return found;',
'        } catch (Throwable t) {',
'            return false;',
'        }',
'    }'
  )
  Pair @(
'                applyStorageFluidBonus(level, counterPos, required, maidInv, maidAvailable);'
  ) @(
'                applyStorageFluidBonus(level, counterPos, required, maidInv, maidAvailable, maid.getUUID());'
  )
)

$idx = 0
foreach ($p in $pairs) {
  $idx++
  $count = ([regex]::Matches($content, [regex]::Escape($p.Old))).Count
  if ($count -ne 1) { throw "Pair #$idx occurrence=$count (expected 1)" }
  $content = $content.Replace($p.Old, $p.New)
  Write-Output "Pair #$idx OK"
}

$out = $content -replace "`n", "`r`n"
$enc = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllText($f, $out, $enc)
Write-Output 'DONE'
