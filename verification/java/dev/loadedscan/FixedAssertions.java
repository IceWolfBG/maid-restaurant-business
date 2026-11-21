package dev.loadedscan;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.icewolf.maidrestaurant.business.core.MaidUtils;
import com.icewolf.maidrestaurant.business.item.HealthCertificateItem;
import com.icewolf.maidrestaurant.business.registry.ModItems;
import com.icewolf.maidrestaurant.business.util.ItemStackUtils;
import com.mastermarisa.maid_restaurant.init.ModEntities;
import com.mastermarisa.maid_restaurant.request.CookRequest;
import com.mastermarisa.maid_restaurant.request.CookRequestHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.loadedscan.RegressionHarness.*;

final class FixedAssertions {
    static final BlockPos MACHINE_A = new BlockPos(16, 80, 16);
    static final BlockPos MACHINE_B = new BlockPos(32, 80, 32);
    static boolean unloaded;
    static boolean reloaded;
    static EntityMaid farOldReference;

    @SuppressWarnings("unchecked")
    static List<EntityMaid> loaded(ServerLevel level) throws Exception {
        Method m = MaidUtils.class.getDeclaredMethod("getLoadedMaids", ServerLevel.class);
        m.setAccessible(true);
        return (List<EntityMaid>) m.invoke(null, level);
    }

    static ItemStack certificate(BlockPos pos) {
        ItemStack stack = new ItemStack(ModItems.HEALTH_CERTIFICATE.get());
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(HealthCertificateItem.TAG_HAS_MACHINE, true);
        tag.putInt(HealthCertificateItem.TAG_MACHINE_X, pos.getX());
        tag.putInt(HealthCertificateItem.TAG_MACHINE_Y, pos.getY());
        tag.putInt(HealthCertificateItem.TAG_MACHINE_Z, pos.getZ());
        ItemStackUtils.setTag(stack, tag);
        return stack;
    }

    static void scanBindings(ServerLevel l) { MaidUtils.updateBindingsFromBaubles(l); }
    static int heal(ServerLevel l) { return MaidUtils.checkAndResetIdleStuckMaids(l); }
    static void walkMemory(EntityMaid m) {
        m.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPos(80, 96, 80), 0.5F, 1));
    }

    static void clean(EntityMaid m) {
        MaidUtils.clearRuntimeState();
        m.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        m.getBrain().eraseMemory(MemoryModuleType.PATH);
        m.getBrain().eraseMemory(ModEntities.TARGET_POS.get());
        m.getBrain().eraseMemory(ModEntities.CHAIR_POS.get());
        m.getData(CookRequestHandler.TYPE).clear();
        m.setNoAi(true);
    }

    static void run(MinecraftServer server) throws Exception {
        ServerLevel ow = server.overworld(), nether = server.getLevel(Level.NETHER), end = server.getLevel(Level.END);
        long now = ow.getGameTime();
        EntityMaid a = legacy(server, 0), far = legacy(server, 1), n = legacy(server, 2);
        check("empty_dimension_enumeration", loaded(end).isEmpty());
        scanBindings(end); check("empty_dimension_heal", heal(end) == 0);
        check("all_overworld_maids_including_4096_blocks_away", loaded(ow).containsAll(List.of(a, far)));
        check("nether_maids_are_local", loaded(nether).equals(List.of(n)));
        check("no_cross_dimension_snapshot", !loaded(ow).contains(n));
        int before = ow.getChunkSource().getLoadedChunksCount();
        for (int i = 0; i < 10; i++) { loaded(ow); scanBindings(ow); heal(ow); }
        check("scan_does_not_load_chunks", before == ow.getChunkSource().getLoadedChunksCount());

        a.getMaidBauble().setStackInSlot(1, certificate(MACHINE_A));
        scanBindings(ow);
        check("bauble_binding_add", MACHINE_A.equals(MaidUtils.getBoundMachine(a.getUUID())));
        a.getMaidBauble().setStackInSlot(1, certificate(MACHINE_B));
        scanBindings(ow);
        check("bauble_binding_update", MACHINE_B.equals(MaidUtils.getBoundMachine(a.getUUID())));
        a.getMaidBauble().setStackInSlot(1, ItemStack.EMPTY);
        scanBindings(ow);
        check("bauble_binding_remove", MaidUtils.getBoundMachine(a.getUUID()) == null);
        MaidUtils.bindMaidToMachine(a.getUUID(), MACHINE_A, "notice_board");
        scanBindings(ow);
        check("notice_board_binding_preserved", MACHINE_A.equals(MaidUtils.getBoundMachine(a.getUUID())));
        MaidUtils.unbindMaid(a.getUUID(), "notice_board");
        n.getMaidBauble().setStackInSlot(1, certificate(MACHINE_B));
        scanBindings(ow);
        check("overworld_does_not_bind_nether_maid", MaidUtils.getBoundMachine(n.getUUID()) == null);
        scanBindings(nether);
        check("nether_binding_add", MACHINE_B.equals(MaidUtils.getBoundMachine(n.getUUID())));

        // Advance only the gameTime used by the production check, within one server
        // tick. Restore it afterwards; no bridge scheduler runs between these calls.
        try {
            clean(n); walkMemory(n);
            heal(ow);
            check("overworld_does_not_start_nether_timer", !suspects("idleSuspectSince").containsKey(n.getUUID()));
            check("idle_grace_first_observation", heal(nether) == 0);
            server.getWorldData().overworldData().setGameTime(now + 199);
            check("idle_grace_199_ticks", heal(nether) == 0 && n.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET));
            server.getWorldData().overworldData().setGameTime(now + 200);
            check("idle_stuck_recovers_at_200_ticks", heal(nether) == 1 && !n.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET));

            clean(n); server.getWorldData().overworldData().setGameTime(now); MaidUtils.setOccupied(n, true);
            check("ghost_grace_first_observation", heal(nether) == 0);
            server.getWorldData().overworldData().setGameTime(now + 199); check("ghost_grace_199_ticks", heal(nether) == 0 && MaidUtils.isOccupied(n));
            server.getWorldData().overworldData().setGameTime(now + 200); check("ghost_busy_recovers_at_200_ticks", heal(nether) == 1 && !MaidUtils.isOccupied(n));

            // Real task marker / cook-request / registered memory guard tests.
            // These do not claim to exercise full recipe-to-delivery workflows.
            for (String flag : List.of("BusinessDeliverCounter", "BusinessPackCounter", "BusinessWashCounter",
                    "BusinessCookCounter", "BusinessCollectPlate", "BusinessFetchMachine", "BusinessGreetNpc")) {
                clean(n); server.getWorldData().overworldData().setGameTime(now); MaidUtils.setOccupied(n, true); walkMemory(n);
                n.getPersistentData().putLong(flag, MACHINE_A.asLong());
                heal(nether); server.getWorldData().overworldData().setGameTime(now + 400); heal(nether);
                check("active_task_guard_" + flag, MaidUtils.isOccupied(n) && n.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET));
                n.getPersistentData().remove(flag);
            }
            for (MemoryModuleType<net.minecraft.world.entity.ai.behavior.PositionTracker> memory : List.of(
                    ModEntities.TARGET_POS.get(), ModEntities.CHAIR_POS.get())) {
                clean(n); server.getWorldData().overworldData().setGameTime(now); MaidUtils.setOccupied(n, true); walkMemory(n);
                n.getBrain().setMemory(memory, new BlockPosTracker(MACHINE_A));
                heal(nether); server.getWorldData().overworldData().setGameTime(now + 400); heal(nether);
                check("registered_cooking_memory_guard_" + memory, MaidUtils.isOccupied(n) && n.getBrain().hasMemoryValue(memory));
            }
            clean(n); server.getWorldData().overworldData().setGameTime(now); MaidUtils.setOccupied(n, true);
            n.getData(CookRequestHandler.TYPE).add(new CookRequest());
            heal(nether); server.getWorldData().overworldData().setGameTime(now + 400); heal(nether);
            check("cook_request_guard", MaidUtils.isOccupied(n) && n.getData(CookRequestHandler.TYPE).size() == 1);

            clean(n); server.getWorldData().overworldData().setGameTime(now); MaidUtils.setOccupied(n, true); heal(nether);
            server.getWorldData().overworldData().setGameTime(now + 100); MaidUtils.setOccupied(n, false); walkMemory(n); heal(nether);
            check("switch_to_idle_clears_ghost_timer", !suspects("ghostSuspectSince").containsKey(n.getUUID()));
            server.getWorldData().overworldData().setGameTime(now + 200); MaidUtils.setOccupied(n, true); n.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            check("switch_back_restarts_grace", heal(nether) == 0 && MaidUtils.isOccupied(n));
            check("switch_to_ghost_clears_idle_timer", !suspects("idleSuspectSince").containsKey(n.getUUID()));
            clean(n);
        } finally { server.getWorldData().overworldData().setGameTime(now); }

        // Use actual vanilla teleport commands, no delete-and-respawn simulation.
        scanBindings(nether);
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                "execute in minecraft:overworld run tp " + n.getUUID() + " 8 96 8");
        EntityMaid moved = (EntityMaid) ow.getEntity(n.getUUID());
        check("actual_dimension_teleport", moved != null && nether.getEntity(n.getUUID()) == null);
        check("teleport_keeps_items", moved.getMaidInv().getStackInSlot(0).getCount() == 9);
        scanBindings(ow);
        check("teleport_binding_scan", MACHINE_B.equals(MaidUtils.getBoundMachine(moved.getUUID())));
        check("teleport_clears_grace", !suspects("idleSuspectSince").containsKey(moved.getUUID()) && !suspects("ghostSuspectSince").containsKey(moved.getUUID()));
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                "execute in minecraft:the_nether run tp " + moved.getUUID() + " 8 96 8");
        check("teleport_return", legacy(server, 2) != null);

        // Two timed-out maids in one dimension must both be processed even though
        // reset removes their tracker entries. The other dimension is untouched.
        a.setNoAi(true); far.setNoAi(true);
        MaidUtils.setOccupied(a, true);
        MaidUtils.setOccupied(far, true);
        MaidUtils.setOccupied(legacy(server, 2), true);
        MaidUtils.startTask(a, MACHINE_A, "test_stuck", 0);
        MaidUtils.startTask(far, MACHINE_B, "test_stuck", 0);
        MaidUtils.startTask(legacy(server, 2), MACHINE_B, "test_stuck", 0);
        check("tracked_stuck_multiple_without_concurrent_modification",
                MaidUtils.checkAndResetStuckMaids(ow, 300, 300, 1) == 2);
        check("tracked_stuck_removes_processed_entries", !MaidUtils.hasTaskTracker(id(0)) && !MaidUtils.hasTaskTracker(id(1)));
        check("tracked_stuck_preserves_other_dimension", MaidUtils.hasTaskTracker(id(2)));
        check("tracked_stuck_correct_nether_context", MaidUtils.checkAndResetStuckMaids(nether, 300, 300, 1) == 1);
        for (int i = 0; i < 3; i++) {
            EntityMaid m = legacy(server, i); m.setNoAi(true);
            m.getMaidBauble().setStackInSlot(1, certificate(MACHINE_B));
            scanBindings(home(server, i));
        }
        verifyLegacy(server);
    }

    @SuppressWarnings("unchecked")
    static Map<UUID, Long> suspects(String name) throws Exception {
        var field = MaidUtils.class.getDeclaredField(name); field.setAccessible(true);
        return (Map<UUID, Long>) field.get(null);
    }

    static void tick(MinecraftServer server, int tick) throws Exception {
        if (tick == 200) {
            farOldReference = legacy(server, 1);
            MaidUtils.setOccupied(farOldReference, true); heal(server.overworld());
            server.overworld().setChunkForced(4104 >> 4, 0, false);
        }
        if (tick > 200 && !unloaded && server.overworld().getEntity(id(1)) == null) {
            unloaded = true;
            check("real_chunk_unload_observed", true);
            check("unloaded_not_scanned", loaded(server.overworld()).stream().noneMatch(m -> m.getUUID().equals(id(1))));
            check("unload_releases_entity_reference", !MaidUtils.isOccupied(farOldReference));
            check("unload_clears_grace", !suspects("ghostSuspectSince").containsKey(id(1)));
            server.overworld().setChunkForced(4104 >> 4, 0, true);
            server.overworld().getChunk(4104 >> 4, 0);
        }
        if (unloaded && !reloaded && server.overworld().getEntity(id(1)) instanceof EntityMaid m) {
            reloaded = true;
            check("real_chunk_reload_new_instance", m != farOldReference);
            scanBindings(server.overworld());
            check("reload_binding_restored_without_rebinding", MACHINE_B.equals(MaidUtils.getBoundMachine(m.getUUID())));
            check("reload_no_premature_reset", heal(server.overworld()) == 0);
            verifyLegacy(server);
            farOldReference = null;
        }
        if (tick == 3000) check("chunk_unload_reload_completed", unloaded && reloaded);
    }

    static void verifyReload(MinecraftServer server) throws Exception {
        for (int i = 0; i < 3; ++i) {
            EntityMaid m = legacy(server, i);
            check("saved_certificate_" + i, MACHINE_B.equals(HealthCertificateItem.getMachinePos(m.getMaidBauble().getStackInSlot(1))));
            scanBindings(home(server, i));
            check("saved_binding_rebuilt_" + i, MACHINE_B.equals(MaidUtils.getBoundMachine(m.getUUID())));
            check("saved_maids_discovered_" + i, loaded(home(server, i)).contains(m));
        }
    }
}
