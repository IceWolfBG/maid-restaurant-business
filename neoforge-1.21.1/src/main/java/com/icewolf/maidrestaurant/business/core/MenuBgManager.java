package com.icewolf.maidrestaurant.business.core;

import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.neoforged.fml.loading.FMLPaths;

/** 服务端饭店菜单背景图库：读取/列举/写入位于 config 下的公用目录。 */
public final class MenuBgManager {
    private MenuBgManager() {}

    public static Path serverDir() {
        Path dir = FMLPaths.CONFIGDIR.get()
                .resolve("maid_restaurant_business")
                .resolve(MenuBgConstants.SERVER_SUBDIR);
        try {
            Files.createDirectories(dir);
            writeReadme(dir);
        } catch (IOException e) {
            MaidRestaurantBusiness.LOGGER.warn("MenuBgManager: cannot init server dir", e);
        }
        return dir;
    }

    private static void writeReadme(Path dir) {
        Path readme = dir.resolve("README.txt");
        if (Files.exists(readme)) return;
        String text = """
                饭店菜单背景图库（服务器端公用）

                放置规则：
                - 仅支持 PNG 格式
                - 最长边不超过 1024 像素
                - 单个文件不超过 512 KB
                - 图片会被拉伸填满整个点单界面背景

                用法：
                - 管理员：把 PNG 直接放进本文件夹，所有玩家在游戏内“设置背景”时都能看到并选用。
                - 玩家：也可在点单界面设置里用“上传图片”按钮自助上传（会经过同样的格式/尺寸/大小校验）。
                """;
        try {
            Files.writeString(readme, text);
        } catch (IOException ignored) {
            // 只读目录也无妨，仅作为提示
        }
    }

    /** 列举图库内所有 .png 文件名（按字典序）。 */
    public static List<String> listNames() {
        Path dir = serverDir();
        List<String> names = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.toLowerCase().endsWith(".png"))
                    .sorted()
                    .forEach(names::add);
        } catch (IOException e) {
            MaidRestaurantBusiness.LOGGER.warn("MenuBgManager: list failed", e);
        }
        return names;
    }

    /** 读取某背景图字节（文件名不合法或不存在返回 null）。 */
    public static byte[] readBytes(String name) {
        if (!MenuBgConstants.isSafeName(name)) return null;
        Path f = serverDir().resolve(name);
        if (!Files.isRegularFile(f)) return null;
        try {
            return Files.readAllBytes(f);
        } catch (IOException e) {
            return null;
        }
    }

    /** 写入某背景图字节（校验文件名与体积上限）。 */
    public static boolean saveBytes(String name, byte[] data) {
        if (!MenuBgConstants.isSafeName(name)) return false;
        if (data == null || data.length > MenuBgConstants.MAX_FILE_BYTES) return false;
        Path f = serverDir().resolve(name);
        try {
            Files.write(f, data);
            return true;
        } catch (IOException e) {
            MaidRestaurantBusiness.LOGGER.warn("MenuBgManager: save failed {}", name, e);
            return false;
        }
    }
}
