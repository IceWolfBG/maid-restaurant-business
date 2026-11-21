package dev.loadedscan;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.icewolf.maidrestaurant.business.core.MaidUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;

import static dev.loadedscan.RegressionHarness.*;

/** These assertions never invoke either scan method: BusinessManager must do it. */
final class ScheduledAssertions {
    static boolean idleRecovered;

    static void tick(MinecraftServer server, int tick) {
        EntityMaid m = legacy(server, 0);
        if (tick == 200) {
            m.setNoAi(true);
            m.getMaidBauble().setStackInSlot(1, ItemStack.EMPTY);
            m.getMaidInv().setStackInSlot(1, FixedAssertions.certificate(FixedAssertions.MACHINE_A));
            MaidUtils.unbindMaid(m.getUUID(), "health_certificate");
            MaidUtils.setOccupied(m, true);
        }
        if (tick == 250) check("scheduled_ghost_respects_grace", MaidUtils.isOccupied(m));
        if (tick == 350) {
            check("scheduled_binding_add_without_bauble_tick", FixedAssertions.MACHINE_A.equals(MaidUtils.getBoundMachine(m.getUUID())));
            m.getMaidInv().setStackInSlot(1, FixedAssertions.certificate(FixedAssertions.MACHINE_B));
        }
        if (tick == 450) check("scheduled_binding_update", FixedAssertions.MACHINE_B.equals(MaidUtils.getBoundMachine(m.getUUID())));
        if (tick == 500) m.getMaidInv().setStackInSlot(1, ItemStack.EMPTY);
        if (tick == 650) {
            check("scheduled_ghost_recovery", !MaidUtils.isOccupied(m));
            check("scheduled_binding_remove", MaidUtils.getBoundMachine(m.getUUID()) == null);
            m.setNoAi(true);
            m.getMaidBauble().setStackInSlot(1, FixedAssertions.certificate(FixedAssertions.MACHINE_B));
        }
        if (tick == 700) { m.setNoAi(true); FixedAssertions.walkMemory(m); }
        if (tick == 750) check("scheduled_idle_respects_grace", m.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET));
        if (tick > 700 && tick < 1100 && !m.isNoAi()) {
            idleRecovered = true;
            check("scheduled_idle_clears_stale_walk_target", !m.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET));
            m.setNoAi(true);
        }
        if (tick == 1100) {
            check("scheduled_idle_recovery", idleRecovered);
            verifyLegacy(server);
        }
    }
}
