$ErrorActionPreference = 'Stop'
$files = @(
  'D:\DoubaoWork\maid_restaurant_storage-1.20.1-forge\src\main\java\com\example\maidrestaurant\rscompat\fluid\MaidFluidStorages.java',
  'D:\DoubaoWork\maid_restaurant_storage-1.21.1-neoforge\src\main\java\com\example\maidrestaurant\rscompat\fluid\MaidFluidStorages.java'
)

$oldLogger = @(
  'public class MaidFluidStorages {',
  '',
  '    private static final List<IMaidFluidStorage> STORAGES = new ArrayList<>();'
) -join "`n"
$newLogger = @(
  'public class MaidFluidStorages {',
  '',
  '    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("MaidRestaurantStorage");',
  '    private static final List<IMaidFluidStorage> STORAGES = new ArrayList<>();'
) -join "`n"

$oldMethod = @(
  '    public static boolean isAvailableOrFillable(Level level, BlockPos pos, FluidStack fluid, int required, java.util.UUID self) {',
  '        if (CompatConfig.isBlacklisted(level.getBlockState(pos))) {',
  '            return false;',
  '        }',
  '        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel',
  '                && FluidReservationManager.isReservedByOther(serverLevel, pos, self)) {',
  '            return false;',
  '        }',
  '        for (IMaidFluidStorage storage : STORAGES) {',
  '            if (storage.isValid(level, pos)',
  '                    && storage.getFluidAmount(level, pos, fluid) >= required) {',
  '                return true;',
  '            }',
  '            if (storage.canBeFilled(level, pos, fluid)) {',
  '                return true;',
  '            }',
  '        }',
  '        return false;',
  '    }'
) -join "`n"
$newMethod = @(
  '    public static boolean isAvailableOrFillable(Level level, BlockPos pos, FluidStack fluid, int required, java.util.UUID self) {',
  '        net.minecraft.world.level.block.state.BlockState pState = level.getBlockState(pos);',
  '        boolean cauldronProbe = pState.getBlock() instanceof net.minecraft.world.level.block.AbstractCauldronBlock;',
  '        Object blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(pState.getBlock());',
  '        if (CompatConfig.isBlacklisted(pState)) {',
  '            if (cauldronProbe) LOGGER.info("isAvail [{}] -> BLACKLIST", blockId);',
  '            return false;',
  '        }',
  '        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel',
  '                && FluidReservationManager.isReservedByOther(serverLevel, pos, self)) {',
  '            if (cauldronProbe) LOGGER.info("isAvail [{}] -> RESERVED_BY_OTHER", blockId);',
  '            return false;',
  '        }',
  '        for (IMaidFluidStorage storage : STORAGES) {',
  '            boolean valid = storage.isValid(level, pos);',
  '            int amount = storage.getFluidAmount(level, pos, fluid);',
  '            boolean canFill = storage.canBeFilled(level, pos, fluid);',
  '            if (cauldronProbe) {',
  '                LOGGER.info("isAvail [{}] storage={} valid={} amount={} required={} canBeFilled={}",',
  '                        blockId, storage.getClass().getSimpleName(), valid, amount, required, canFill);',
  '            }',
  '            if (valid && amount >= required) {',
  '                return true;',
  '            }',
  '            if (canFill) {',
  '                return true;',
  '            }',
  '        }',
  '        return false;',
  '    }'
) -join "`n"

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
foreach ($f in $files) {
  $text = [IO.File]::ReadAllText($f, $utf8NoBom)
  $norm = $text -replace "`r`n", "`n"
  foreach ($pair in @(@($oldLogger,$newLogger,'logger'), @($oldMethod,$newMethod,'method'))) {
    $o=$pair[0]; $n=$pair[1]; $tag=$pair[2]
    $cnt = ([regex]::Matches($norm, [regex]::Escape($o))).Count
    if ($cnt -ne 1) { throw "expected 1 match for $tag in $f got $cnt" }
    $norm = $norm.Replace($o,$n)
  }
  $out = $norm -replace "`n","`r`n"
  [IO.File]::WriteAllText($f,$out,$utf8NoBom)
  Write-Output "OK: $f"
}
