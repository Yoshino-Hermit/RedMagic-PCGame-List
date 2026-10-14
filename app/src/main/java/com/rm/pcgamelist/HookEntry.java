package com.rm.pcgamelist;

import android.content.ComponentName;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 现代 libxposed API 入口（API 101+）。
 *
 * <p>红魔游戏空间用三段逻辑把 PC 游戏挡在列表外，这里逐一放行：
 *
 * <ol>
 *   <li>{@code AppAddModelHelper.isInSystemAppList} —— 包名前缀硬编码排除
 *       {@code com.zte.}/{@code cn.zte.}/{@code zte.com.}；</li>
 *   <li>{@code Util.isSystemApp} —— 兜底的系统应用判断；</li>
 *   <li>{@code AppAddModel.isLauncherMainActivity} —— 校验组件是否在 LauncherApps 里，
 *       不通过则在 {@code getAddedAppListFromDB} 阶段
 *       {@code deleteAppItemBeanInAppAddDB} 删除记录。
 *       红魔PC游戏 / 模拟器内核都没有 LAUNCHER 入口，必须在这里放行，
 *       否则往数据库写多少条都会被清掉。</li>
 * </ol>
 */
public class HookEntry extends XposedModule {

    private static final int INFO = 4;
    private static final int WARN = 5;
    private static final int ERROR = 6;

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        log(INFO, "PcGameList", "module loaded in " + param.getProcessName() + ", api=" + getApiVersion());
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        try {
            String pkg = param.getPackageName();
            if (!param.isFirstPackage() || !Constants.GAMESPACE_PKG.equals(pkg)) {
                return;
            }
            ClassLoader cl = param.getClassLoader();
            hookBoolString(cl, Constants.CLS_APPADD_HELPER, "isInSystemAppList", true);
            hookBoolString(cl, Constants.CLS_APPADD_HELPER, "isInLocalGameList", false);
            hookBoolString(cl, Constants.CLS_UTIL, "isSystemApp", true);
            hookIsLauncherMainActivity(cl);
            log(INFO, "PcGameList", "hooks installed in " + pkg);
        } catch (Throwable t) {
            log(ERROR, "PcGameList", "onPackageReady failed", t);
        }
    }

    /**
     * Hook {@code boolean f(String)}。
     *
     * @param forceFalseForTarget true 时对目标包强制返回 false（放行），
     *                            false 时只记录调用不改返回值。
     */
    private void hookBoolString(ClassLoader cl, String cls, final String method, final boolean forceFalseForTarget) {
        try {
            Class<?> c = Class.forName(cls, false, cl);
            Method m = c.getDeclaredMethod(method, String.class);
            m.setAccessible(true);
            hook(m)
                    .setPriority(XposedInterface.PRIORITY_DEFAULT)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        List<Object> args = chain.getArgs();
                        String pkg = (args != null && !args.isEmpty() && args.get(0) instanceof String)
                                ? (String) args.get(0) : null;
                        Object result = chain.proceed();
                        if (forceFalseForTarget && Constants.isTargetPkg(pkg)) {
                            log(INFO, "PcGameList", method + "(" + pkg + ") was " + result + " -> false");
                            return Boolean.FALSE;
                        }
                        return result;
                    });
            log(INFO, "PcGameList", "hooked " + cls + "." + method);
        } catch (Throwable t) {
            log(WARN, "PcGameList", "hook failed " + cls + "." + method + ": " + t);
        }
    }

    /**
     * Hook {@code AppAddModel.isLauncherMainActivity(ComponentName)}，
     * 对目标包直接返回 true，避免记录在数据库读取阶段被删除。
     */
    private void hookIsLauncherMainActivity(ClassLoader cl) {
        try {
            Class<?> c = Class.forName(Constants.CLS_APPADD_MODEL, false, cl);
            Method m = c.getDeclaredMethod("isLauncherMainActivity", ComponentName.class);
            m.setAccessible(true);
            hook(m)
                    .setPriority(XposedInterface.PRIORITY_DEFAULT)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        List<Object> args = chain.getArgs();
                        String pkg = null;
                        if (args != null && !args.isEmpty() && args.get(0) instanceof ComponentName) {
                            pkg = ((ComponentName) args.get(0)).getPackageName();
                        }
                        if (Constants.isTargetPkg(pkg)) {
                            log(INFO, "PcGameList", "isLauncherMainActivity(" + pkg + ") -> true (keep record)");
                            return Boolean.TRUE;
                        }
                        return chain.proceed();
                    });
            log(INFO, "PcGameList", "hooked " + Constants.CLS_APPADD_MODEL + ".isLauncherMainActivity");
        } catch (Throwable t) {
            log(WARN, "PcGameList", "hook failed isLauncherMainActivity: " + t);
        }
    }
}
