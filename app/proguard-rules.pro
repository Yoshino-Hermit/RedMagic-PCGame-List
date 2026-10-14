# Xposed 入口由框架按清单加载，不能被裁剪
-keep class com.rm.pcgamelist.HookEntry { *; }
-keep class io.github.libxposed.** { *; }
