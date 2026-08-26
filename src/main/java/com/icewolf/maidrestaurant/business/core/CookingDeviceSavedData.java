package com.icewolf.maidrestaurant.business.core;

import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 厨具占用状态持久化保存
 * 退出游戏后不会消失，避免状态不一致导致的问题
 */
public class CookingDeviceSavedData extends SavedData {

    private static final String DATA_NAME = "maid_restaurant_business_cooking_devices";

    // 厨具占用状态：位置 -> 占用信息
    public static class DeviceOccupancy {
        public final String type;
        public boolean occupied;
        public UUID occupant;
        public long occupyStartTime;

        public DeviceOccupancy(String type) {
            this.type = type;
            this.occupied = false;
            this.occupant = null;
            this.occupyStartTime = 0;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("type", type);
            tag.putBoolean("occupied", occupied);
            if (occupant != null) {
                tag.putUUID("occupant", occupant);
            }
            tag.putLong("occupyStartTime", occupyStartTime);
            return tag;
        }

        public static DeviceOccupancy load(CompoundTag tag) {
            DeviceOccupancy info = new DeviceOccupancy(tag.getString("type"));
            info.occupied = tag.getBoolean("occupied");
            if (tag.contains("occupant")) {
                info.occupant = tag.getUUID("occupant");
            }
            info.occupyStartTime = tag.getLong("occupyStartTime");
            return info;
        }
    }

    private final Map<Long, DeviceOccupancy> devices = new HashMap<>();

    public CookingDeviceSavedData() {
        super();
    }

    public static CookingDeviceSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                CookingDeviceSavedData::load,
                CookingDeviceSavedData::new,
                DATA_NAME
        );
    }

    public Map<Long, DeviceOccupancy> getDevices() {
        return devices;
    }

    public void setOccupied(BlockPos pos, String type, UUID occupant, long currentTick) {
        long key = pos.asLong();
        DeviceOccupancy info = devices.get(key);
        if (info == null) {
            info = new DeviceOccupancy(type);
            devices.put(key, info);
        }
        info.occupied = true;
        info.occupant = occupant;
        info.occupyStartTime = currentTick;
        setDirty();
    }

    public void setFree(BlockPos pos) {
        long key = pos.asLong();
        DeviceOccupancy info = devices.get(key);
        if (info != null) {
            info.occupied = false;
            info.occupant = null;
            info.occupyStartTime = 0;
            setDirty();
        }
    }

    public boolean isOccupied(BlockPos pos) {
        DeviceOccupancy info = devices.get(pos.asLong());
        return info != null && info.occupied;
    }

    public void cleanupTimeout(long currentTick, long timeout) {
        boolean changed = false;
        for (DeviceOccupancy info : devices.values()) {
            if (info.occupied && currentTick - info.occupyStartTime > timeout) {
                MaidRestaurantBusiness.LOGGER.warn("[厨具持久化] 厨具超时自动释放: 类型={}, 占用者={}, 占用时长={}tick",
                        info.type, info.occupant, currentTick - info.occupyStartTime);
                info.occupied = false;
                info.occupant = null;
                info.occupyStartTime = 0;
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag deviceList = new ListTag();
        for (Map.Entry<Long, DeviceOccupancy> entry : devices.entrySet()) {
            CompoundTag deviceTag = entry.getValue().save();
            deviceTag.putLong("pos", entry.getKey());
            deviceList.add(deviceTag);
        }
        tag.put("devices", deviceList);
        MaidRestaurantBusiness.LOGGER.info("[厨具持久化] 保存厨具状态: {}个厨具", devices.size());
        return tag;
    }

    public static CookingDeviceSavedData load(CompoundTag tag) {
        CookingDeviceSavedData data = new CookingDeviceSavedData();
        if (tag.contains("devices")) {
            ListTag deviceList = tag.getList("devices", Tag.TAG_COMPOUND);
            for (int i = 0; i < deviceList.size(); i++) {
                CompoundTag deviceTag = deviceList.getCompound(i);
                long pos = deviceTag.getLong("pos");
                DeviceOccupancy info = DeviceOccupancy.load(deviceTag);
                data.devices.put(pos, info);
            }
            MaidRestaurantBusiness.LOGGER.info("[厨具持久化] 加载厨具状态: {}个厨具", data.devices.size());
        }
        return data;
    }
}
