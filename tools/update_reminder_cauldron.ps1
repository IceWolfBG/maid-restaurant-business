$ErrorActionPreference = 'Stop'
$p = 'C:\Users\26529\Desktop\模组开发提醒.md'
$utf8 = New-Object System.Text.UTF8Encoding($false)
$t = [IO.File]::ReadAllText($p, $utf8)
$anchor = "---`r`n`r`n> ⚠️ **再次提醒：每次读取本文件后，下次对话开始时必须再次读取本文件！**"
if (-not $t.Contains($anchor)) {
  $anchor = "---`n`n> ⚠️ **再次提醒：每次读取本文件后，下次对话开始时必须再次读取本文件！**"
}
if (-not $t.Contains($anchor)) { throw 'anchor not found' }

$section = @'
### 2026-09-26 水炼药锅空桶接水的原版机制（Vineflower 双线实证）
- 用户是对的：水 `LayeredCauldronBlock` 空桶**可以**接水，但原版 WATER 的 BUCKET 交互带谓词 `state.getValue(LEVEL) == 3`——**只有满锅(3层)**才出 **1 个**水桶、锅随即清空；1~2 层时空桶无反应，只能用**玻璃瓶每层接一瓶(333)**。岩浆炼药锅空桶可正常舀 1 桶岩浆。
- 反编译锚点：`net.minecraft.core.cauldron.CauldronInteraction`，WATER.put(Items.BUCKET, fillBucket(..., new ItemStack(Items.WATER_BUCKET), s -> s.getValue(LEVEL)==3, BUCKET_FILL))；GLASS_BOTTLE 每层 decrement 出一瓶。
- 修正 `SingleServingFluidStorage.getFluidAmount` 水锅分支（原 `LEVEL*1000`）：改为 `lv>=3 ? 1000 : lv*333`。桶需求(1000)仅满锅通过（半锅 333/666<1000 正确拦截），瓶需求(333/666/999)按层数正确，两门一致，不再发布半锅接桶的卡死任务。
- 工具：Vineflower 1.10.1 在 `MaidRestaurantBusiness\tools\vineflower.jar`，用法 `java -jar vineflower.jar -e=<srgjar> <单个.class> <outdir>`；先用 ZipFile 从 srg jar 提取目标 .class。

---

'@
$replacement = $section + $anchor
$t = $t.Replace($anchor, $replacement)
[IO.File]::WriteAllText($p, $t, $utf8)
Write-Output 'reminder updated'
