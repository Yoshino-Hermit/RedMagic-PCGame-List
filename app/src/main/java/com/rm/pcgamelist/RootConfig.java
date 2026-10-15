package com.rm.pcgamelist;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 用 root 完成"让红魔PC游戏进入游戏列表"这件事。
 *
 * <p>需要依次处理两类问题：
 * <ol>
 *   <li><b>模块作用域</b>：Xposed 模块的作用域若不含游戏空间进程，Hook 根本不会加载，
 *       写进去的记录会在游戏空间下次重建列表时被删掉。这里先自检并按需补全。</li>
 *   <li><b>游戏列表记录</b>：把 {@code appadd.db} 复制到应用私有目录，补齐缺失记录，
 *       再放回原位并恢复属主，最后重启游戏助手让它重新识别。</li>
 * </ol>
 */
public final class RootConfig {

    private static final String TAG = "PcGameList";

    /** 调试日志文件（放在私有目录，可用 root 读取）。 */
    private static File sLogFile;

    /** 沿用系统里的占位图，避免列表项空白。 */
    private static final String PLACEHOLDER_IMAGE =
            "android.resource://cn.nubia.gamelauncher/mipmap/wallpaper_0";

    private RootConfig() {}

    public static final class Result {
        public final boolean ok;
        public final String message;

        Result(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }

    /**
     * 确保目标游戏已在列表里，并重启助手。
     * 该方法会阻塞，请放到子线程调用。
     */
    public static Result ensureConfigured(Context ctx) {
        logFile(ctx, "=== ensureConfigured start ===");
        List<String> notes = new ArrayList<>();

        // 0. 作用域自检：缺失时模块不会加载，记录再写也留不住
        String scopeNote = ensureScope(ctx);
        if (scopeNote != null) {
            notes.add(scopeNote);
        }

        File workDir = new File(ctx.getFilesDir(), "dbwork");
        if (!workDir.exists() && !workDir.mkdirs()) {
            return new Result(false, "无法创建临时目录");
        }
        File localDb = new File(workDir, "appadd.db");
        File localWal = new File(workDir, "appadd.db-wal");

        // 1. 复制数据库到私有目录
        exec("cp " + Constants.APPADD_DB + " " + localDb.getAbsolutePath(), null);
        exec("cp " + Constants.APPADD_DB + "-wal " + localWal.getAbsolutePath() + " 2>/dev/null", null);
        exec("chmod 666 " + localDb.getAbsolutePath(), null);
        exec("chmod 666 " + localWal.getAbsolutePath() + " 2>/dev/null", null);

        logFile(ctx, "copied db exists=" + localDb.exists() + " size=" + localDb.length());
        if (!localDb.exists()) {
            return new Result(false, "复制数据库失败（root 未授权？）");
        }

        List<String> added = new ArrayList<>();
        try {
            SQLiteDatabase db = SQLiteDatabase.openDatabase(
                    localDb.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
            try {
                for (int i = 0; i < Constants.TARGET_COMPONENTS.length; i++) {
                    String component = Constants.TARGET_COMPONENTS[i];
                    String pkg = component.substring(0, component.indexOf(','));
                    if (exists(db, pkg)) continue;

                    ContentValues cv = new ContentValues();
                    cv.put("component", component);
                    cv.put("isAdd", 1);
                    cv.put("isGame", 1);
                    cv.put("urlType", "net");
                    cv.put("imageUrl", PLACEHOLDER_IMAGE);
                    cv.put("gamename", Constants.TARGET_NAMES[i]);
                    cv.put("middleImageUrl", PLACEHOLDER_IMAGE);
                    cv.put("lastStartTime", -1L);
                    cv.put("lastUpdateUrlTime", System.currentTimeMillis());
                    cv.put("autoOpenFan", 1);
                    cv.put("autoOpenLiquid", 1);
                    long id = db.insert("appadd", null, cv);
                    logFile(ctx, "insert " + pkg + " -> id=" + id);
                    if (id > 0) added.add(pkg);
                }
            } finally {
                db.close();
            }
        } catch (Throwable t) {
            Log.e(TAG, "sqlite failed", t);
            return new Result(false, "写数据库失败：" + t.getMessage());
        }

        // 2. 放回原位（先停游戏空间，避免它边跑边覆盖）
        exec("am force-stop " + Constants.GAMESPACE_PKG, null);
        exec("pkill -9 -f " + Constants.GAMESPACE_PKG, null);
        sleep(2500);
        exec("cp " + localDb.getAbsolutePath() + " " + Constants.APPADD_DB, null);
        exec("rm -f " + Constants.APPADD_DB + "-wal " + Constants.APPADD_DB + "-shm", null);
        exec("chown " + Constants.DB_OWNER + " " + Constants.APPADD_DB, null);
        exec("chmod 600 " + Constants.APPADD_DB, null);
        logFile(ctx, "db pushed back");
        localDb.delete();
        localWal.delete();

        // 3. 重启助手，让它重新识别游戏
        for (String p : Constants.ASSIST_PKGS) {
            exec("am force-stop " + p, null);
            exec("pkill -9 -f " + p, null);
        }

        if (!added.isEmpty()) {
            notes.add("已加入游戏列表：" + String.join("、", added));
        }
        if (notes.isEmpty()) {
            notes.add("配置已是最新");
        }
        return new Result(true, String.join("；", notes));
    }

    /**
     * 检查 Xposed 模块作用域，缺失时通过框架 CLI 自动补上。
     *
     * @return 需要提示给用户的信息；无需改动时返回 null。
     */
    private static String ensureScope(Context ctx) {
        try {
            String pkg = ctx.getPackageName();
            // 注意：/data/adb 是 0700 root-only，app 直接 File.exists() 必然为 false，
            // 因此这里只能通过 su 执行 CLI，并用输出来判断框架是否可用。
            String listed = exec("sh " + Constants.FRAMEWORK_CLI + " scope ls " + pkg, null);
            logFile(ctx, "scope ls output=" + listed.replace("\n", " / "));
            if (listed.trim().isEmpty()) {
                logFile(ctx, "framework CLI unavailable, skip scope check");
                return null;
            }
            if (listed.contains("No such file") || listed.contains("Permission denied")) {
                logFile(ctx, "framework CLI not accessible, skip scope check");
                return null;
            }
            List<String> missing = new ArrayList<>();
            for (String scope : Constants.SCOPE_PKGS) {
                if (!listed.contains(scope)) {
                    missing.add(scope);
                }
            }
            if (missing.isEmpty()) {
                return null;
            }
            Log.i(TAG, "scope missing: " + missing);
            for (String scope : missing) {
                String addOut = exec("sh " + Constants.FRAMEWORK_CLI + " scope add " + pkg + " " + scope, null);
                logFile(ctx, "scope add " + scope + " -> " + addOut.replace("\n", " / "));
            }
            // 作用域变更后需要重启游戏空间，模块才会被加载进去
            exec("am force-stop " + Constants.GAMESPACE_PKG, null);
            exec("pkill -9 -f " + Constants.GAMESPACE_PKG, null);
            sleep(3000);
            return "已补全模块作用域：" + String.join("、", missing);
        } catch (Throwable t) {
            Log.w(TAG, "ensureScope failed", t);
            return null;
        }
    }

    private static boolean exists(SQLiteDatabase db, String pkg) {
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT COUNT(*) FROM appadd WHERE component LIKE ?", new String[]{pkg + ",%"});
            return c.moveToFirst() && c.getInt(0) > 0;
        } catch (Throwable t) {
            return false;
        } finally {
            if (c != null) c.close();
        }
    }

    /** 执行 shell 命令，返回 stdout。 */
    private static String exec(String cmd, String ignored) {
        Process p = null;
        try {
            p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            }
            p.waitFor(15, TimeUnit.SECONDS);
            return sb.toString();
        } catch (Throwable t) {
            Log.w(TAG, "exec failed: " + cmd, t);
            return "";
        } finally {
            if (p != null) p.destroy();
        }
    }

    /** 写一行调试日志。 */
    private static void logFile(Context ctx, String msg) {
        if (sLogFile == null && ctx != null) {
            sLogFile = new File(ctx.getFilesDir(), "pcgame.log");
        }
        if (sLogFile == null) return;
        try (FileWriter w = new FileWriter(sLogFile, true)) {
            w.write(System.currentTimeMillis() + " | " + msg + "\n");
        } catch (Throwable ignored) {
        }
        Log.i(TAG, msg);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
