/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble
 *  com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.item.ItemStack
 */
package com.icewolf.maidrestaurant.business.compat;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import com.icewolf.maidrestaurant.business.core.MaidUtils;
import com.icewolf.maidrestaurant.business.item.HealthCertificateItem;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public class HealthCertificateBauble
implements IMaidBauble {
    private static final String BINDING_SOURCE = "health_certificate";
    
    private static String getEntityName(Entity entity) {
        try {
            Method method = Entity.class.getDeclaredMethod("m_5447_", new Class[0]);
            method.setAccessible(true);
            Object nameComponent = method.invoke(entity, new Object[0]);
            if (nameComponent != null) {
                Method getStringMethod = nameComponent.getClass().getMethod("getString", new Class[0]);
                return (String)getStringMethod.invoke(nameComponent, new Object[0]);
            }
        }
        catch (Exception e) {
            MaidRestaurantBusiness.LOGGER.error("Failed to get entity name via reflection", (Throwable)e);
        }
        return "Unknown";
    }

    public void onTick(EntityMaid maid, ItemStack baubleItem) {
        try {
            // 检查健康证是否绑定了打单机
            if (HealthCertificateItem.hasMachine(baubleItem)) {
                BlockPos machinePos = HealthCertificateItem.getMachinePos(baubleItem);
                if (machinePos != null) {
                    // 将女仆绑定到该打单机
                    MaidUtils.bindMaidToMachine(maid.getUUID(), machinePos, BINDING_SOURCE);
                }
            } else {
                // 健康证没有绑定打单机，解除该来源的绑定
                MaidUtils.unbindMaid(maid.getUUID(), BINDING_SOURCE);
            }
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("健康证饰品onTick异常", t);
        }
    }

    public void onPutOn(EntityMaid maid, ItemStack baubleItem) {
        MaidRestaurantBusiness.LOGGER.info("健康证已装备到女仆: {}", HealthCertificateBauble.getEntityName((Entity)maid));
        // 装备时立即尝试绑定
        try {
            if (HealthCertificateItem.hasMachine(baubleItem)) {
                BlockPos machinePos = HealthCertificateItem.getMachinePos(baubleItem);
                if (machinePos != null) {
                    MaidUtils.bindMaidToMachine(maid.getUUID(), machinePos, BINDING_SOURCE);
                }
            }
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("健康证装备时绑定异常", t);
        }
    }

    public void onTakeOff(EntityMaid maid, ItemStack baubleItem) {
        MaidRestaurantBusiness.LOGGER.info("健康证已从女仆卸下: {}", HealthCertificateBauble.getEntityName((Entity)maid));
        // 卸下时解除该来源的绑定
        MaidUtils.unbindMaid(maid.getUUID(), BINDING_SOURCE);
    }
}
