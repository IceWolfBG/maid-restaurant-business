package dev.loadedscan;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Dedicated-server fixed assertions; deliberately not shipped inside the patch. */
@Mod("loadedscan_regression")
public class RegressionHarness {
    static final String PHASE = System.getProperty("loadedscan.phase", "seed");
    static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    static long started = System.nanoTime();
    static int tick;
    static boolean finished;
    static final List<EntityMaid> seeded = new ArrayList<>();
    static final UUID OWNER = UUID.fromString("21b41eba-f7d7-42dc-a37d-378e4e2da5c4");

    public RegressionHarness() { NeoForge.EVENT_BUS.register(this); }

    @SubscribeEvent
    public void leave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof EntityMaid m) {
            System.out.println("LOADEDSCAN_LEAVE " + m.getUUID() + " " + m.getRemovalReason()
                    + " health=" + m.getHealth() + " pos=" + m.position());
        }
    }

    static UUID id(int i) {
        return UUID.nameUUIDFromBytes(("loadedscan-legacy-" + i).getBytes(StandardCharsets.UTF_8));
    }

    static void check(String name, boolean condition) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name); row.put("status", condition ? "PASS" : "FAIL"); row.put("tick", tick);
        RESULTS.add(row);
        System.out.println("LOADEDSCAN_ASSERT " + name + " " + row.get("status"));
        if (!condition) throw new AssertionError(name);
    }

    static ServerLevel home(MinecraftServer s, int i) {
        return i == 2 ? s.getLevel(Level.NETHER) : s.overworld();
    }

    static EntityMaid legacy(MinecraftServer s, int i) {
        return (EntityMaid) home(s, i).getEntity(id(i));
    }

    static EntityMaid spawn(ServerLevel level, UUID uuid, double x, double z) {
        level.setChunkForced(((int)x) >> 4, ((int)z) >> 4, true);
        level.getChunk(((int)x) >> 4, ((int)z) >> 4);
        EntityMaid maid = new EntityMaid(level);
        maid.setUUID(uuid); maid.setPos(x, 96, z); maid.setNoAi(true);
        maid.setNoGravity(true); maid.setInvulnerable(true); maid.setPersistenceRequired();
        check("spawn_" + uuid, level.addFreshEntity(maid));
        seeded.add(maid);
        return maid;
    }

    @SubscribeEvent
    public void tick(ServerTickEvent.Post event) {
        if (finished) return;
        MinecraftServer server = event.getServer();
        ++tick;
        try {
            if (tick == 1) {
                started = System.nanoTime();
                check("dedicated_server", server.isDedicatedServer());
                check("no_players", server.getPlayerCount() == 0);
                check("sable_loaded", ModList.get().isLoaded("sable"));
                check("business_presence_" + PHASE,
                        ModList.get().isLoaded("maid_restaurant_business") != PHASE.equals("seed"));
                for (int i = 0; i < 3; ++i) {
                    int x = i == 1 ? 4104 : 8;
                    home(server, i).setChunkForced(x >> 4, 0, true);
                    home(server, i).getChunk(x >> 4, 0);
                }
            }
            if (PHASE.equals("seed")) {
                if (tick == 20) {
                    for (int i = 0; i < 3; ++i) {
                        EntityMaid maid = spawn(home(server, i), id(i), i == 1 ? 4104 : 8, 8);
                        maid.getMaidInv().setStackInSlot(0, new ItemStack(Items.DIAMOND, 7 + i));
                        maid.getMaidBauble().setStackInSlot(0, new ItemStack(Items.GOLD_NUGGET, 1));
                        maid.setFavorability(42 + i); maid.setOwnerUUID(OWNER);
                        maid.getPersistentData().putString("LoadedScanLegacySentinel", "before-addon-" + i);
                    }
                }
                if (tick == 100) { verifyLegacy(server); finish(server); }
                return;
            }
            if (tick == 100) {
                verifyLegacy(server);
                if (PHASE.equals("fixed")) FixedAssertions.run(server);
                if (PHASE.equals("reload")) FixedAssertions.verifyReload(server);
            }
            if (PHASE.equals("fixed")) FixedAssertions.tick(server, tick);
            if (PHASE.equals("scheduled") || PHASE.equals("cache_baseline")) ScheduledAssertions.tick(server, tick);
            if (PHASE.equals("baseline") && tick == 600) finish(server);
            if (PHASE.equals("reload") && tick == 300) finish(server);
            if ((PHASE.equals("scheduled") || PHASE.equals("cache_baseline")) && tick == 1200) finish(server);
            if (PHASE.equals("fixed") && tick >= 6400
                    && System.nanoTime() - started >= 300_000_000_000L) {
                check("five_minutes_elapsed", true);
                check("no_players_throughout", server.getPlayerCount() == 0);
                finish(server);
            }
        } catch (Throwable t) {
            t.printStackTrace();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", "exception"); row.put("status", "FAIL"); row.put("detail", t.toString());
            RESULTS.add(row); finish(server);
        }
    }

    static void verifyLegacy(MinecraftServer server) {
        for (int i = 0; i < 3; ++i) {
            EntityMaid maid = legacy(server, i);
            if (maid == null || !maid.isAlive()) {
                System.out.println("LOADEDSCAN_LEGACY_DIAG id=" + id(i) + " found=" + maid);
                for (EntityMaid saved : seeded) System.out.println("LOADEDSCAN_SEED_DIAG id=" + saved.getUUID()
                        + " alive=" + saved.isAlive() + " health=" + saved.getHealth() + " removed=" + saved.getRemovalReason()
                        + " level=" + saved.level().dimension().location() + " pos=" + saved.position());
            }
            check("legacy_present_" + i, maid != null && maid.isAlive());
            ItemStack s = maid.getMaidInv().getStackInSlot(0);
            check("legacy_inventory_" + i, s.is(Items.DIAMOND) && s.getCount() == 7 + i);
            check("legacy_bauble_" + i, maid.getMaidBauble().getStackInSlot(0).is(Items.GOLD_NUGGET));
            check("legacy_owner_" + i, OWNER.equals(maid.getOwnerUUID()));
            check("legacy_favorability_" + i, maid.getFavorability() == 42 + i);
            check("legacy_persistent_data_" + i,
                    maid.getPersistentData().getString("LoadedScanLegacySentinel").equals("before-addon-" + i));
        }
    }

    static void finish(MinecraftServer server) {
        finished = true;
        try {
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("phase", PHASE); report.put("ticks", tick);
            report.put("elapsed_seconds", (System.nanoTime() - started) / 1_000_000_000.0);
            report.put("assertions", RESULTS);
            report.put("passed", RESULTS.stream().noneMatch(r -> "FAIL".equals(r.get("status"))));
            Files.writeString(Path.of("regression-" + PHASE + ".json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(report));
            server.saveEverything(false, true, true);
        } catch (Exception e) { e.printStackTrace(); }
        server.halt(false);
    }
}
