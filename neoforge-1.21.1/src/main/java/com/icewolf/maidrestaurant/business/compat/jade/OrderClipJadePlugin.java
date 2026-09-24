package com.icewolf.maidrestaurant.business.compat.jade;

import com.icewolf.maidrestaurant.business.block.OrderClipBlock;
import com.icewolf.maidrestaurant.business.block.entity.OrderClipBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade（玉模组）兼容入口：仅对挂单夹生效。
 * <ul>
 *   <li>服务端数据提供者 {@link OrderClipJadeData}：把夹着订单的 FoodList 菜品需求随 Jade 数据同步；</li>
 *   <li>客户端组件提供者 {@link OrderClipJadeComponent}：在 Jade 提示框里逐行列出菜品与数量，无需取下订单。</li>
 * </ul>
 * 订单内容本不同步客户端，故必须经服务端数据提供者下发；其他容器（箱子等）不注册、不显示。
 * Jade 为可选依赖（compileOnly），未安装时本类不会被加载，模组照常运行。
 */
@WailaPlugin
public class OrderClipJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(new OrderClipJadeData(), OrderClipBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new OrderClipJadeComponent(), OrderClipBlock.class);
    }
}
