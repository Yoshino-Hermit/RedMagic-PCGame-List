# Changelog

## 1.0.2
- 桌面图标改用红魔PC游戏本尊的紫色 G（从 APK 导出真实图标，含 5 种密度）
- 新增深色主题：消除点击图标瞬间的白屏闪烁
  - `windowDisablePreview` 关闭系统启动预览窗口
  - `windowIsTranslucent` + 纯黑背景，配置过程无感
- 改用 aapt2 生成资源表（修复此前手写 arsc 导致图标/label 不生效的问题）
- 资源表踩坑：`ResTable_type` 的 `headerSize` 必须是 16+68=84（config 声明 64 字节但占位 68）

## 1.0.1
- **关键修复**：新增 Hook `AppAddModel.isLauncherMainActivity(ComponentName)`
  - 此前只 Hook `isInSystemAppList`，记录虽然能写进数据库，
    但会被 `getAddedAppListFromDB` 的校验逻辑（`deleteAppItemBeanInAppAddDB`）删除，
    表现为「助手时有时无 / 一会儿又不见了」
  - 现在记录可稳定存活，助手持续生效

## 1.0.0
- 首个可用版本
- Xposed 模块：Hook `AppAddModelHelper.isInSystemAppList` / `isInLocalGameList` / `Util.isSystemApp`
- 桌面图标：一键写入游戏列表并启动红魔PC游戏
- 自动重启游戏助手进程，使其立即识别新加入的游戏
