package com.rm.pcgamelist;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 用 root 完成"把游戏写进游戏列表"这件事。
 *
 * <p>流程：
 * <ol>
 *   <li>把 {@code appadd.db}（含 -wal）复制到应用私有目录；</li>
 *   <li>用 Android 自带 SQLite 打开，补齐缺失记录；</li>
 *   <li>复制回原位置，清理 -wal/-shm 并恢复属主；</li>
 *   <li>重启游戏助手进程，让它重新识别游戏。</li>
 * </ol>
 */
public final class RootConfig {

    private static final String TAG = "PcGameList";

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

    /** 是否已具备 root。 */
    public static boolean hasRoot() {
        return exec("id", null).contains("uid=0");
    }

    /**
     * 确保目标游戏已在列表里，并重启助手。
     * 该方法会阻塞，请放到子线程调用。
     */
    public static Result ensureConfigured(Context ctx) {
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
                    if (id > 0) added.add(pkg);
                }
            } finally {
                db.close();
            }
        } catch (Throwable t) {
            Log.e(TAG, "sqlite failed", t);
            return new Result(false, "写数据库失败：" + t.getMessage());
        }

        // 2. 放回原位（先停 gamelauncher，避免它同时写导致覆盖）
        exec("pkill -9 -f " + Constants.GAMESPACE_PKG, null);
        sleep(2500);
        exec("cp " + localDb.getAbsolutePath() + " " + Constants.APPADD_DB, null);
        exec("rm -f " + Constants.APPADD_DB + "-wal " + Constants.APPADD_DB + "-shm", null);
        exec("chown " + Constants.DB_OWNER + " " + Constants.APPADD_DB, null);
        exec("chmod 600 " + Constants.APPADD_DB, null);
        localDb.delete();
        localWal.delete();

        // 3. 重启助手，让它重新识别游戏
        for (String p : Constants.ASSIST_PKGS) {
            exec("am force-stop " + p, null);
            exec("pkill -9 -f " + p, null);
        }

        String msg = added.isEmpty()
                ? "游戏列表已是配置状态"
                : "已加入游戏列表：" + String.join("、", added);
        return new Result(true, msg);
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

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
