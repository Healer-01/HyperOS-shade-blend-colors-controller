# HyperOS Shade Blend Colors Controller

适用于小米 17（HyperOS 4，已测试结构对齐 `MiuiSystemUI 17.03.260226.r` / `MIUISystemUIPlugin 18.3.2.57.0`）的 LSPosed 模块，用于实时控制**下拉控制中心**与**下拉通知中心**的背景压暗程度。

## 模块特性

- **统一 / 分别两种控制模式**：既可以用一个滑块统一调节控制中心与通知中心暗度，也可以在分别控制模式下为两个面板设置不同暗度，并在开启「组合模糊（Combined Blur）」横向切换两个面板时平滑插值过渡。
- **两种混色策略**：
  - **纯净线性遮罩（默认推荐）**：移除原厂 `shade_blend_colors_bionics` 中导致 Lab 亮度被压暗的 `mode 106`（`#ff0d8000`）与 `mode 21`（`#ff999999`）混合层，保留纯饱和度层（`26` / `201`），改用线性 `[black(alpha), 3]`（`SkBlendMode::kSrcOver`），真正做到 `0%` 全透不发灰、`100%` 纯黑遮罩。
  - **缩放原厂配方**：保留原厂柔光玻璃（`21` + `106`）或经典磨砂（`28`）混合模式，仅按设定百分比缩放各色层的 Alpha 通道。
- **实时热生效**：通过 `libxposed` API 102 `RemotePreferences` 跨进程同步配置，拖动滑块无需重启 `com.android.systemui` 即可即时刷新。

## 作用域说明

模块声明位于 `app/src/main/resources/META-INF/xposed/`：

- **`com.android.systemui`（核心必选）**：控制中心、通知中心及合二为一面板的全屏背景混色/模糊控制器均位于系统界面进程中。
- **`miui.systemui.plugin`（补充作用域）**：控制中心内部的瓷贴卡片与滑块插件资源位于系统界面组件包内；本模块将其纳入声明用于作用域完整识别与诊断，同时不篡改控制中心内部卡片的文本与图标可读性。

## Hook 目标与原理

1. **`com.miui.systemui.shade.blur.ShadeBlendBlurControllerImpl`**（兼容回退 `ShadeBlendBlurController` / `ShadeBlurController#start()`）
   - 仅 Hook `Resources.getIntArray("shade_blend_colors_bionics")` 无法区分控制中心与通知中心，因为两者读取的是同一个数组资源。
   - 本模块在 `start()` 与 `$BlendBackground.applyBlendColors()` 处精准识别 `notificationPanelBackground`、`controlCenterBackground` 与 `shadeWindowBackground` 三个背景 View 归属，并拦截 `com.miui.systemui.util.MiBlurCompat.setMiBackgroundBlendColors(View, int[], float)` 进行按面板替换。
   - 使用 `module.deoptimize(...)` 对控制器与协程回调做去优化，并在 `applyBlendColors()` 返回后补充回放，防止 ART 内联绕过 Hook。
2. **`com.android.systemui.shade.NotificationPanelViewControllerInjector` & `com.miui.systemui.shade.ShadeSwitchControllerImpl`**
   - 在 HyperOS 4 默认开启组合模糊（`combinedBlendBackground`）时实时跟踪当前展开的是控制中心还是通知中心，以及左右横向切换进度（`0.0` 控制中心 ↔ `1.0` 通知中心），确保分别调节模式在组合模糊下同样准确生效。
3. **`com.miui.systemui.shade.blur.ShadeWindowBlurControllerImpl`**
   - 覆盖 `windowBackground` 的 `View.setBackgroundColor(int)` 与 `applyMiSelfBlurEffect(List, float)`，避免回退路径或窗口级压暗残留系统默认黑幕。

> 说明：网传另一个资源 `window_background_blend_colors_bionics` 主要由 `BlurUtilsExt` 与 `ModalWindowManager`（长按通知弹出模态菜单等窗口模糊场景）读取，不属于下拉控制中心/通知中心主面板背景链路。

## 环境要求与构建

- **LSPosed 框架**：支持 `libxposed` API 102 的现代 LSPosed 分支（如 JingMatrix LSPosed 等）。
- **构建工具链**：JDK 17、Gradle 8.13、AGP 8.13.2、`compileSdk = 37`（`platforms;android-37.0`，因 `io.github.libxposed:service:102.0.0` 元数据要求 `minCompileSdk=37`；`minSdk = 33`、`targetSdk = 35`）。
- **构建命令**：
  ```bash
  ./gradlew testDebugUnitTest assembleDebug assembleRelease
  ```

## 只读状态验证（不对手机做任何写入）

激活模块并在 LSPosed 勾选作用域后，可通过以下只读命令查看 `ShadeBlendBlurControllerImpl` 注入状态与实际应用的配方：

```bash
adb shell dumpsys activity service com.android.systemui/.SystemUIService ShadeBlendBlurControllerImpl
```
