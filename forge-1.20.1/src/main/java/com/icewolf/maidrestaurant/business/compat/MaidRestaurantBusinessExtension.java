/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid
 *  com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension
 *  com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble
 *  com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager
 *  net.minecraft.world.item.Item
 */
package com.icewolf.maidrestaurant.business.compat;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;
import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import com.icewolf.maidrestaurant.business.compat.HealthCertificateBauble;
import com.icewolf.maidrestaurant.business.registry.ModItems;
import net.minecraft.world.item.Item;

@LittleMaidExtension
public class MaidRestaurantBusinessExtension
implements ILittleMaid {
    public void bindMaidBauble(BaubleManager manager) {
        manager.bind((Item)ModItems.HEALTH_CERTIFICATE.get(), (IMaidBauble)new HealthCertificateBauble());
    }
}
