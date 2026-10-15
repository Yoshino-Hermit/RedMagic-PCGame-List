# 红魔PC游戏 · 游戏空间列表解锁

<p align="center">
  <img src="docs/icon.png" width="128" alt="icon">
</p>

让 **红魔PC游戏（红魔游戏空间 / PC 模拟器）** 进入系统「游戏空间」的游戏列表，
从而启用红魔游戏助手的全部能力：侧边栏、性能调度、语音操控、游戏变声、连麦字幕、
液冷循环、图像解读、计数器、Mora 等。

> 适配 REDMAGIC 机型（NX809J / Android 16 实测），需要 root + LSPosed 系框架（Vector / LSPosed）。

---

## 解决什么问题

红魔游戏空间在判断「某个应用能不能进游戏列表」时，有一段**包名前缀硬编码**：

```java
// cn.nubia.gamelauncher.model.AppAddModelHelper#isInSystemAppList
if (pkg.startsWith("com.zte."))  return true;  // ← com.zte.pcgame 命中
if (pkg.startsWith("zte.com."))  return true;
if (pkg.startsWith("cn.zte."))   return true;
if (Util.isSystemApp(pkg))       return true;
if (Util.isHomeApp(pkg))         return true;
```

红魔PC游戏的包名恰好是 `com.zte.pcgame`，PC 模拟器内核是
`com.xiaoji.egggame.redmagic`，两者都被这段逻辑挡在游戏列表之外，
于是游戏助手永远不会对它们生效。

**单纯往数据库插记录没用** —— 列表刷新时会被清理；改包名重签名又会破坏系统权限。

本模块从这个判断函数入手，让目标包直接返回 `false`，从根上放行。

---

## 工作原理

插件由两部分组成：

### 1. Xposed 模块（运行在游戏空间进程内）

Hook 三个方法，对目标包强制放行：

| 类 | 方法 | 作用 |
|---|---|---|
| `AppAddModelHelper` | `isInSystemAppList(String)` | 绕过包名前缀 / 系统应用过滤 |
| `AppAddModelHelper` | `isInLocalGameList(String)` | 放行本地游戏列表判断 |
| `Util` | `isSystemApp(String)` | 兜底，避免二次拦截 |
| **`AppAddModel`** | **`isLauncherMainActivity(ComponentName)`** | **最关键**：校验组件是否在 LauncherApps 中，不通过则删库 |


采用 **libxposed 现代 API（API 101+）**：

- Java 入口写在 `META-INF/xposed/java_init.list`
- 模块元数据写在 `META-INF/xposed/module.prop`
- 作用域写在 `META-INF/xposed/scope.list`
- 入口类继承 `io.github.libxposed.api.XposedModule`
- Hook 使用拦截器链：`hook(method).intercept(chain -> ...)`
- 必须打包 `io.github.libxposed:service` 并在清单里声明 `XposedProvider`

### 2. 桌面图标 + 自动配置

APK 自带一个 Launcher 图标（显示为「红魔PC游戏」）。点击时：

1. 用 root 把 `appadd.db`（连同 `-wal`）复制到私有目录；
2. 用 Android 自带 SQLite 补齐 `com.zte.pcgame` 与
   `com.xiaoji.egggame.redmagic` 两条记录；
3. 复制回原位置、清理 `-wal/-shm`、恢复属主；
4. **重启 `cn.nubia.gameassist` 与 `cn.zte.gamefloat`**；
5. 拉起红魔PC游戏。

> ⚠️ 第 4 步是关键。游戏助手会缓存「当前前台是不是游戏」的判定，
> 新加入的游戏**必须重启助手进程**才会被识别。这是社区里最容易踩的坑。

---

## 界面

- **桌面图标**：紫色 G（与红魔PC游戏本尊一致）
- **点击行为**：首次点击自动完成配置（写入游戏列表 + 重启助手），随后拉起游戏
- **过渡画面**：深色主题（`windowDisablePreview` + 纯黑背景），不会出现白屏闪烁

## 安装

1. 确保设备已 root，并装好 Xposed 框架（**Vector** 或 **LSPosed**）。
2. 安装本 APK。
3. 在框架管理器里**启用本模块**，作用域勾选：
   - `系统框架 (system_server)`
   - `游戏空间 (cn.nubia.gamelauncher)`

   > 💡 作用域为空时模块 Hook 不会加载，即使记录写进去了也会被游戏空间删掉。
   > 从 1.0.3 起，**点击桌面图标时会自动检测并补全作用域**，所以这一步忘了也没关系
   > —— 但仍建议手动确认一次，避免多绕一圈。
4. 首次点击桌面「红魔PC游戏」图标时，会弹出 root 授权，**允许**即可完成配置。

之后每次点击图标都会直接进入红魔PC游戏。

---

## 验证

进入游戏后，从**屏幕左右上角**向内侧滑动，即可呼出红魔游戏助手侧边栏。
小白条会在无操作一段时间后自动隐藏，属于正常行为。

---

## 从源码构建

需要 Android SDK（API 34）与 JDK 17：

```bash
./gradlew :app:assembleRelease
```

产物在 `app/build/outputs/apk/release/`。

签名请自行配置（仓库不含任何密钥）。

---

## 兼容性

- **已实测**：REDMAGIC NX809J / Android 16 / Vector 2.2 (API 102)
- 理论上适用于所有使用同一套游戏空间的 ZTE / nubia 机型
- 若游戏空间做了大改（方法名变化），需要同步调整 `Constants.java` 中的类名

---

## 已知限制

- 需要 root：写入游戏列表数据库必须访问 `/data/user_de/0`。
- 需要 libxposed **API 101+**：旧版 LSPosed 只支持 legacy API，请使用 Vector 或较新的 LSPosed。
- 系统更新后若游戏空间类名变化，需重新适配。

---

## 致谢

- [libxposed](https://github.com/libxposed) — 现代 Xposed API
- [LSPosed](https://github.com/LSPosed/LSPosed) / Vector — 框架实现

## License

[MIT](LICENSE)

---

## 实现细节（踩坑记录）

开发本模块时踩过的坑，记录下来供参考：

### 1. 模块必须用现代 libxposed API

旧写法（`assets/xposed_init` + `XposedHelpers` + `IXposedHookLoadPackage`）在
Vector 2.2 / API 102 下会**被静默忽略**——框架能识别模块存在，但不会加载它。

正确做法：

```properties
# META-INF/xposed/module.prop
minApiVersion=101
targetApiVersion=102
staticScope=true
```

```text
# META-INF/xposed/java_init.list
com.rm.pcgamelist.HookEntry
```

```text
# META-INF/xposed/scope.list
system
cn.nubia.gamelauncher
```

并且入口类要继承 `io.github.libxposed.api.XposedModule`，
Hook 走拦截器链 `hook(m).intercept(chain -> ...)`，
同时 `implementation("io.github.libxposed:service")` 并在清单声明：

```xml
<provider
    android:name="io.github.libxposed.service.XposedProvider"
    android:authorities="<applicationId>.XposedService"
    android:exported="true" />
```

### 2. 二进制 XML 的属性命名空间必须是完整 URI

写 AXML 时，属性的 `ns` 字段要指向
`http://schemas.android.com/apk/res/android`，**不是**前缀 `android`。

否则 PMS 解析 `<action>` / `<category>` 时会报：

```
INSTALL_PARSE_FAILED_MANIFEST_MALFORMED: No value supplied for <android:name>
```

原因是 `PackageParser` 用 `getAttributeValue(ANDROID_RESOURCES, "name")` 按 URI 查值。

### 3. targetSdk 与签名方案

`targetSdk >= 30` 时 Android 强制要求：

- APK 具备 **v2+ 签名**（仅 jarsigner 的 v1 不够）
- `resources.arsc` **未压缩且 4 字节对齐**

如果只想用 v1 签名（例如手工构建），把 `targetSdk` 降到 29 即可绕开这两项强制检查。
正式发布建议用 `apksigner` 做 v2/v3 签名。

### 4. 只插数据库没用——会被自动删掉（最容易踩的坑）

往 `appadd.db` 插入记录后，`getAddedAppListFromDB()` 读取时会做校验：

```java
// cn.nubia.gamelauncher.model.AppAddModel
if (!LauncherApps.isActivityEnabled(cn, user)) { ... }
if (!isLauncherMainActivity(cn)) {
    AppAddModelHelper.deleteAppItemBeanInAppAddDB(deleteList);   // ← 记录被删
}
```

而 `isLauncherMainActivity` 要求组件出现在 `LauncherApps.getActivityList()` 里
（即必须带 `android.intent.category.LAUNCHER` 入口）。
红魔PC游戏和模拟器内核都没有 LAUNCHER 入口，所以**插进去的记录会在下一次列表刷新时被静默删除**，
表现为「助手时有时无 / 一会儿又不见了」。

因此 **必须 Hook `isLauncherMainActivity`**，对目标包直接返回 true。
只 Hook `isInSystemAppList` 是不够的（那只是让记录能写进去，却挡不住被删）。

### 5. 记录会不会「掉」？—— 取决于模块是否真正加载

`appadd.db` 是普通 SQLite 文件，**写入本身是持久的**，重启设备不会丢。
真正会掉的是这一步：游戏空间每次重建列表时都会校验

```java
if (!isLauncherMainActivity(cn)) {
    AppAddModelHelper.deleteAppItemBeanInAppAddDB(deleteList);   // ← 记录被删
}
```

红魔PC游戏和模拟器内核都没有 LAUNCHER 入口，这个判断永远是 false，
唯一的例外就是模块 Hook 把它掰成 true。所以：

| 条件 | 记录 |
|---|---|
| 模块 enabled + 作用域正确 | **长期保留**（已实测连扫多次 + 重启不掉） |
| 模块 enabled 但**作用域为空** | 游戏空间重扫时被清空 |
| 模块 disabled / 未安装 | 游戏空间重扫时被清空 |

触发重扫的时机：设备重启、游戏空间被系统杀掉重启、列表刷新。
**注意**：游戏空间进程一直活着时记录不会掉，所以故障可能潜伏很久，
直到某次开机才暴露 —— 这就是为什么作用域自检很重要。

### 6. 游戏助手会缓存判定结果

往数据库写完记录后，**必须重启 `cn.nubia.gameassist` 和 `cn.zte.gamefloat`**，
否则助手仍按旧缓存认为「当前不是游戏」，侧边栏不会出现。

### 7. 不要用截图找小白条来验证

助手两侧的小白条在无操作一段时间后会自动隐藏。
正确验证方式：**从屏幕左右上角向内侧滑动**呼出侧边栏。

### 8. 两个包名都要覆盖

游戏运行时前台并不是 `com.zte.pcgame`，而是模拟器内核
`com.xiaoji.egggame.redmagic`（WineActivity）。
只加前者会出现「启动界面有助手、进游戏后消失」的现象。

---

## 目录结构

```
app/src/main/
├── AndroidManifest.xml                     # 桌面图标 + XposedProvider
├── java/com/rm/pcgamelist/
│   ├── Constants.java                      # 目标包、路径等常量
│   ├── HookEntry.java                      # Xposed 入口（现代 API）
│   ├── RootConfig.java                     # 作用域自检 + root 写数据库 + 重启助手
│   └── LauncherActivity.java               # 桌面图标行为
└── resources/META-INF/xposed/              # libxposed 元数据
    ├── module.prop
    ├── java_init.list
    └── scope.list
```
