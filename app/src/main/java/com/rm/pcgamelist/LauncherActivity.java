package com.rm.pcgamelist;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

/**
 * 桌面上那个"红魔PC游戏"图标。
 *
 * <p>点击后：自动完成配置（补全模块作用域 → 写入游戏列表 → 重启助手），随后拉起红魔PC游戏。
 */
public class LauncherActivity extends Activity {

    private static final String TAG = "PcGameList";
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new Thread(() -> {
            RootConfig.Result result = RootConfig.ensureConfigured(this);
            Log.i(TAG, "configure result: ok=" + result.ok + " msg=" + result.message);
            main.post(() -> {
                if (!result.ok) {
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
                } else if (!"配置已是最新".equals(result.message)) {
                    // 有实际改动时给个反馈，便于用户知道发生了什么
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
                }
                launchGame();
                finish();
            });
        }, "pcgl-config").start();
    }

    private void launchGame() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(Constants.LAUNCH_PKG, Constants.LAUNCH_ACTIVITY));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(this, "启动红魔PC游戏失败：" + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
