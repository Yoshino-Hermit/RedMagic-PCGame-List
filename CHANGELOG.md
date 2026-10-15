# Changelog

## 1.0.3
- **新增作用域自检与自动补全**：点击桌面图标时会检查模块作用域，
  缺失则自动通过框架 CLI 补上并重启游戏空间
  - 作用域为空时模块 Hook 不会加载，写入的记录会在游戏空间重扫时被清空，
    而这个故障可能潜伏到下次开机才暴露
- **修复**：作用域检查原先用 `File.exists("/data/adb/lspd/cli")` 判断框架是否可用，
  但 `/data/adb` 是 0700 root-only，普通 app 的 `exists()` 恒为 false，
  导致检查被静默跳过。改为通过 `su` 执行 CLI 并按输出判断
- 新增私有目录调试日志 `files/pcgame.log`，便于排查配置流程
- README 补充「记录会不会掉」的完整机制说明

## 1.0.2
- 桌面图标改用红魔PC游戏本尊的紫色 G（从 APK 导出真实图标，含 5 种密度）
- 新增深色主题：消除点击图标瞬间的白屏闪烁
- 改用 aapt2 生成资源表（修复此前手写 arsc 导致图标/label 不生效的问题）

## 1.0.1
- **关键修复**：新增 Hook `AppAddModel.isLauncherMainActivity(ComponentName)`
  - 此前只 Hook `isInSystemAppList`，记录虽然能写进数据库，
    但会被 `getAddedAppListFromDB` 的校验逻辑（`deleteAppItemBeanInAppAddDB`）删除

## 1.0.0
- 首个可用版本
