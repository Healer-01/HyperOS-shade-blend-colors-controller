# HyperOS Shade Blend Colors Controller（柔光暗度控制）

适用于 **HyperOS 4** 的现代 **LSPosed 模块**（基于 `libxposed` API 102），用于实时调节**下拉控制中心与下拉通知中心**的统一背景压暗程度，在保留原厂柔光玻璃混色质感的同时自由控制背景通透度。

> 已针对小米 17（`MiuiSystemUI 17.03.260226.r` / `MIUISystemUIPlugin 18.3.2.57.0`）测试并对齐调用链路。

## 当前版本特性

- **单一「统一背景暗度」调节**：精简冗余选项，仅保留一个统一滑块与快捷预设（`0% 全透`、`15%`、`20% 默认`、`30%`、`50%`、`80%`、`100% 原厂`），同步控制下拉控制中心、通知中心及合二为一背景，并附带通知中心背景压暗实时预览。
- **仅保留原厂整体缩放策略**：完整保留 HyperOS 原厂柔光玻璃（`mode 21 + mode 106`）或经典磨砂（`mode 28`）混色结构，仅按设定百分比等比缩放各色层的 Alpha 通道，不改变原厂混色模式。
- **Miuix 原生质感界面**：基于 [`miuix-kotlin-multiplatform`](https://github.com/compose-miuix-ui/miuix)（Apache-2.0）构建，适配系统深色/浅色主题，页面内展示模块状态、暗度调节、只读诊断、「关于模块」与项目主页入口，以及开源组件「引用」与许可信息。
- **零重启实时热生效**：通过 `libxposed` API 102 `RemotePreferences` 跨进程实时同步配置，拖动滑块即可生效；关闭「启用背景压暗调节」开关可立即还原系统默认压暗效果。

## 使用与作用域

1. **环境要求**：Android 13+（`minSdk = 33`），以及支持 **`libxposed` API 102** 的现代 LSPosed 框架（如 JingMatrix LSPosed 等）。
2. **启用作用域**（声明位于 `app/src/main/resources/META-INF/xposed/`）：
   - **`com.android.systemui`（核心必选）**：下拉控制中心、通知中心及合二为一面板的全屏背景混色与窗口模糊控制器均位于系统界面进程中。
   - **`miui.systemui.plugin`（补充作用域）**：控制中心内部的瓷贴卡片与组件插件资源位于该包内；模块将其纳入声明用于作用域完整识别与兼容（若应用内提示作用域未完整启用，可直接点击「向 LSPosed 申请所需作用域」或在管理器中手动勾选）。
3. **开始使用**：激活模块并勾选作用域后（首次启用建议重启系统界面或设备），打开「柔光暗度控制」即可实时调节。

## Hook 目标与原理

1. **`com.miui.systemui.shade.blur.ShadeBlendBlurControllerImpl`**（兼容回退 `ShadeBlendBlurController` / `ShadeBlurController#start()`）
   - 在 `start()` 与 `$BlendBackground.applyBlendColors()` 处精准识别 `notificationPanelBackground`、`controlCenterBackground` 与 `shadeWindowBackground` 三个背景 View 归属，并拦截 `com.miui.systemui.util.MiBlurCompat.setMiBackgroundBlendColors(View, int[], float)`，按统一暗度百分比对原厂混色数组整体缩放 Alpha 通道。
   - 通过 `module.deoptimize(...)` 对控制器与协程回调做去优化，并在 `applyBlendColors()` 返回后补充回放，防止 ART 内联绕过 Hook。
2. **`com.miui.systemui.shade.blur.ShadeWindowBlurControllerImpl`**
   - 覆盖 `windowBackground` 的 `View.setBackgroundColor(int)` 与 `applyMiSelfBlurEffect(List, float)`，按统一暗度同步缩放窗口级压暗，避免回退路径残留系统默认黑幕。

> 说明：另一个系统资源 `window_background_blend_colors_bionics` 主要由 `BlurUtilsExt` 与 `ModalWindowManager`（如长按通知弹出模态菜单等窗口模糊场景）读取，不属于下拉控制中心/通知中心主面板背景链路。

## 只读状态验证（不对设备做任何写入）

激活模块并在 LSPosed 勾选作用域后，可通过以下命令只读查看 `ShadeBlendBlurControllerImpl` 的注入状态与当前实际应用的配方（也可在模块内一键复制）：

```bash
adb shell dumpsys activity service com.android.systemui/.SystemUIService ShadeBlendBlurControllerImpl
```

## 构建与 CI

- **构建工具链**：JDK 17、Gradle 8.13、AGP 8.13.2、Kotlin 2.3.20、`compileSdk = 37`（`platforms;android-37.0`，因 `io.github.libxposed:service:102.0.0` 元数据要求 `minCompileSdk=37`；`minSdk = 33`、`targetSdk = 35`）。
- **本地构建命令**：
  ```bash
  ./gradlew testDebugUnitTest assembleDebug assembleRelease
  ```
- **GitHub Actions**：工作流位于 `.github/workflows/build.yml`，推送至 `main` 分支或推送 `v*` 标签后自动执行单元测试并构建签名 Release APK（标签推送将自动发布对应版本的 GitHub Release，产物包含 Debug 与已签名 R8 压缩 Release APK）。

## 引用与开源许可

- **[miuix-kotlin-multiplatform](https://github.com/compose-miuix-ui/miuix)**（`top.yukonga.miuix.kmp`）— Apache License 2.0
- **[libxposed API & Service](https://github.com/libxposed/api)**（`io.github.libxposed` API 102）— Apache License 2.0
