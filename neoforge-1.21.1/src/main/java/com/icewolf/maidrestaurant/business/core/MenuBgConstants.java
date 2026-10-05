package com.icewolf.maidrestaurant.business.core;

/** 饭店菜单背景图：跨端共享的常量与文件名校验。 */
public final class MenuBgConstants {
    /** 存放在每张 RESTAURANT_MENU 物品 NBT 里的键（只存文件名引用，不存字节）。 */
    public static final String NBT_KEY = "MenuBg";

    /** 服务端公用图库子目录（位于 config/maid_restaurant_business/ 下）。 */
    public static final String SERVER_SUBDIR = "menu_bg_server";

    /** 单次分片大小（字节），用于上传/下发，避免撞自定义包体积上限。 */
    public static final int CHUNK_SIZE = 32768;

    /** 图片最长边上限（像素）。 */
    public static final int MAX_EDGE = 1024;

    /** 单个图片文件体积上限（字节）。 */
    public static final int MAX_FILE_BYTES = 512 * 1024;

    /** 模组 jar 内置背景图的引用名（.png 后缀保证可通过 isSafeName 校验）。 */
    public static final String BUILTIN_NAME = "builtin_default_cover.png";

    /** 模组 jar 内置背景图的资源路径（相对 assets/maid_restaurant_business/）。 */
    public static final String BUILTIN_RES_PATH = "textures/menu_bg/menu_bg_builtin.png";

    private MenuBgConstants() {}

    /** 该背景名是否为 jar 内置图（不从服务器图库拉取，客户端本地直接加载）。 */
    public static boolean isBuiltin(String name) {
        return BUILTIN_NAME.equals(name);
    }

    /** 仅允许形如 foo_bar.png 的纯文件名，禁止路径穿越与非法字符。 */
    public static boolean isSafeName(String name) {
        if (name == null) return false;
        if (name.contains("/") || name.contains("\\") || name.contains("..")) return false;
        return name.matches("[A-Za-z0-9_\\-\\.]+\\.png");
    }
}
