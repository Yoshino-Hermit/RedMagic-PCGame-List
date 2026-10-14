package com.rm.pcgamelist;

/**
 * 常量集中定义。
 */
public final class Constants {

    private Constants() {}

    /** 目标进程：红魔游戏空间（priv-app），游戏列表由它维护。 */
    public static final String GAMESPACE_PKG = "cn.nubia.gamelauncher";

    /** 游戏列表数据库（应用设备加密存储目录）。 */
    public static final String APPADD_DB = "/data/user_de/0/cn.nubia.gamelauncher/databases/appadd.db";

    /** 数据库属主。 */
    public static final String DB_OWNER = "u0_a286:u0_a286";

    /** 需要加进游戏列表的包，格式 "包名,入口Activity"。 */
    public static final String[] TARGET_COMPONENTS = {
            "com.zte.pcgame,com.zte.pcgame.activity.PCGameLauncher",
            "com.xiaoji.egggame.redmagic,com.xj.winemu.WineActivity",
    };

    /** 桌面上显示的游戏名。 */
    public static final String[] TARGET_NAMES = {
            "红魔PC游戏",
            "红魔PC游戏",
    };

    /** 点击桌面图标实际启动的组件。 */
    public static final String LAUNCH_PKG = "com.zte.pcgame";
    public static final String LAUNCH_ACTIVITY = "com.zte.pcgame.activity.PCGameLauncher";

    /** 游戏助手进程，配置变更后必须重启它们才会重新识别游戏。 */
    public static final String[] ASSIST_PKGS = {
            "cn.nubia.gameassist",
            "cn.zte.gamefloat",
    };

    /** Hook 目标类。 */
    public static final String CLS_APPADD_HELPER = "cn.nubia.gamelauncher.model.AppAddModelHelper";
    public static final String CLS_APPADD_MODEL = "cn.nubia.gamelauncher.model.AppAddModel";
    public static final String CLS_UTIL = "cn.nubia.gamelauncher.util.Util";

    /** 被 Hook 后强制放行的包。 */
    public static boolean isTargetPkg(String pkg) {
        if (pkg == null) return false;
        for (String c : TARGET_COMPONENTS) {
            String p = c.substring(0, c.indexOf(','));
            if (p.equals(pkg) || pkg.startsWith(p + ".")) return true;
        }
        return false;
    }
}
