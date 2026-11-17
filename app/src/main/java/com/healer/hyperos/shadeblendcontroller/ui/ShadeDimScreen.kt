package com.healer.hyperos.shadeblendcontroller.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.healer.hyperos.shadeblendcontroller.hook.ShadeBlendModuleEntry
import com.healer.hyperos.shadeblendcontroller.policy.ControlMode
import com.healer.hyperos.shadeblendcontroller.policy.DimSettings
import com.healer.hyperos.shadeblendcontroller.policy.DimStrategy
import com.healer.hyperos.shadeblendcontroller.policy.ShadeDimPolicy
import kotlin.math.roundToInt

private const val DUMPSYS_COMMAND =
    "adb shell dumpsys activity service com.android.systemui/.SystemUIService ShadeBlendBlurControllerImpl"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShadeDimScreen() {
    val settings by SettingsRepository.settings.collectAsState()
    val framework by SettingsRepository.framework.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "HyperOS 柔光暗度控制",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "下拉控制中心与通知中心背景压暗调节",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModuleStatusCard(framework = framework)

            MasterControlCard(
                settings = settings,
                onToggleEnabled = { checked ->
                    SettingsRepository.update { it.copy(enabled = checked) }
                },
                onControlModeChange = { mode ->
                    SettingsRepository.update { it.copy(controlMode = mode) }
                },
                onStrategyChange = { strategy ->
                    SettingsRepository.update { it.copy(dimStrategy = strategy) }
                },
            )

            if (settings.controlMode == ControlMode.UNIFIED) {
                DimSliderCard(
                    title = "统一背景暗度",
                    subtitle = "同时作用于下拉控制中心、通知中心及合二为一背景",
                    percent = settings.unifiedDimPercent,
                    strategy = settings.dimStrategy,
                    enabled = settings.enabled,
                    onPercentChange = { value ->
                        SettingsRepository.update { it.copy(unifiedDimPercent = value) }
                    },
                )
            } else {
                DimSliderCard(
                    title = "控制中心背景暗度",
                    subtitle = "右侧下拉控制中心独立压暗（0% 最通透，100% 纯黑）",
                    percent = settings.controlCenterDimPercent,
                    strategy = settings.dimStrategy,
                    enabled = settings.enabled,
                    onPercentChange = { value ->
                        SettingsRepository.update { it.copy(controlCenterDimPercent = value) }
                    },
                )
                DimSliderCard(
                    title = "通知中心背景暗度",
                    subtitle = "左侧下拉通知中心独立压暗（0% 最通透，100% 纯黑）",
                    percent = settings.notificationShadeDimPercent,
                    strategy = settings.dimStrategy,
                    enabled = settings.enabled,
                    onPercentChange = { value ->
                        SettingsRepository.update { it.copy(notificationShadeDimPercent = value) }
                    },
                )
            }

            DiagnosticsCard(
                onCopyCommand = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("dumpsys", DUMPSYS_COMMAND))
                    Toast.makeText(context, "已复制只读诊断命令", Toast.LENGTH_SHORT).show()
                },
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ModuleStatusCard(framework: FrameworkSnapshot) {
    val active = framework.apiCompatible && framework.systemUiInScope
    val containerColor = when {
        active -> MaterialTheme.colorScheme.primaryContainer
        framework.connected -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        active -> MaterialTheme.colorScheme.onPrimaryContainer
        framework.connected -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                active -> Color(0xFF2E7D32)
                                framework.connected -> Color(0xFFEF6C00)
                                else -> Color(0xFF757575)
                            },
                        ),
                )
                Text(
                    text = when {
                        active -> "LSPosed 已激活并就绪"
                        framework.connected -> "LSPosed 已连接，但作用域未完整启用"
                        else -> "未检测到 LSPosed 服务（当前为离线预览）"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            val details = if (framework.connected) {
                "框架：${framework.frameworkName} ${framework.frameworkVersion}（API ${framework.apiVersion}）"
            } else {
                "请在 LSPosed 管理器启用本模块（要求 libxposed API >= ${FrameworkSnapshot.MIN_SUPPORTED_API}）并勾选系统界面"
            }
            Text(text = details, style = MaterialTheme.typography.bodySmall)

            if (framework.connected) {
                Text(
                    text = "作用域状态：${ShadeBlendModuleEntry.PKG_SYSTEM_UI} " +
                        "${if (framework.systemUiInScope) "✓" else "✗"}  |  " +
                        "${ShadeBlendModuleEntry.PKG_SYSTEM_UI_PLUGIN} " +
                        "${if (framework.pluginInScope) "✓" else "○"}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }

            if (framework.statusMessage.isNotBlank()) {
                Text(
                    text = framework.statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (framework.connected && (!framework.systemUiInScope || !framework.pluginInScope)) {
                Button(
                    onClick = { SettingsRepository.requestMissingScope() },
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text("向 LSPosed 申请所需作用域")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MasterControlCard(
    settings: DimSettings,
    onToggleEnabled: (Boolean) -> Unit,
    onControlModeChange: (ControlMode) -> Unit,
    onStrategyChange: (DimStrategy) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "启用背景压暗调节",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "关闭后立即还原 HyperOS 系统默认压暗效果",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = onToggleEnabled,
                )
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "面板控制模式",
                    style = MaterialTheme.typography.labelLarge,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = settings.controlMode == ControlMode.UNIFIED,
                        onClick = { onControlModeChange(ControlMode.UNIFIED) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        enabled = settings.enabled,
                    ) {
                        Text("统一控制")
                    }
                    SegmentedButton(
                        selected = settings.controlMode == ControlMode.SEPARATE,
                        onClick = { onControlModeChange(ControlMode.SEPARATE) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        enabled = settings.enabled,
                    ) {
                        Text("分别控制")
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "底层混色策略",
                    style = MaterialTheme.typography.labelLarge,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = settings.dimStrategy == DimStrategy.LINEAR_SCRIM,
                        onClick = { onStrategyChange(DimStrategy.LINEAR_SCRIM) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        enabled = settings.enabled,
                    ) {
                        Text("纯净线性遮罩")
                    }
                    SegmentedButton(
                        selected = settings.dimStrategy == DimStrategy.SCALE_STOCK_ALPHA,
                        onClick = { onStrategyChange(DimStrategy.SCALE_STOCK_ALPHA) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        enabled = settings.enabled,
                    ) {
                        Text("缩放原厂配方")
                    }
                }
                Text(
                    text = if (settings.dimStrategy == DimStrategy.LINEAR_SCRIM) {
                        "移除原厂 mode 106 Lab 亮度压缩，改用线性 SrcOver 黑遮罩，0% 时背景完全通透不发灰。"
                    } else {
                        "保留原厂 shade_blend_colors_bionics (21/106) 或经典 (28) 混合模式，仅按百分比缩放各层 Alpha 通道。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DimSliderCard(
    title: String,
    subtitle: String,
    percent: Float,
    strategy: DimStrategy,
    enabled: Boolean,
    onPercentChange: (Float) -> Unit,
) {
    val rounded = percent.roundToInt()
    val previewRecipe = ShadeDimPolicy.buildBlendColors(
        original = ShadeDimPolicy.STOCK_BIONICS_BLEND_COLORS,
        dimPercent = percent,
        strategy = strategy,
    )
    val scrimAlpha = ((ShadeDimPolicy.blackScrimColor(percent) ushr 24) and 0xFF) / 255f

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "$rounded%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            // Visual preview bar simulating wallpaper + current dim overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF4F8DFD),
                                Color(0xFF7B61FF),
                                Color(0xFFEC7A9B),
                            ),
                        ),
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(8.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = if (enabled) scrimAlpha else 0.5f)),
                )
                Text(
                    text = if (enabled) "等效配方：${ShadeDimPolicy.formatRecipeHex(previewRecipe)}" else "已关闭（系统默认）",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White,
                )
            }

            Slider(
                value = percent,
                onValueChange = { onPercentChange(it.roundToInt().toFloat()) },
                valueRange = 0f..100f,
                enabled = enabled,
            )

            val presets = listOf(0, 15, 30, 50, 80, 100)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                presets.forEach { preset ->
                    FilterChip(
                        selected = rounded == preset,
                        onClick = { onPercentChange(preset.toFloat()) },
                        enabled = enabled,
                        label = {
                            Text(
                                text = when (preset) {
                                    0 -> "0% 全透"
                                    100 -> "100% 纯黑"
                                    else -> "$preset%"
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsCard(onCopyCommand: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "作用域与只读验证说明",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "• 核心作用域：com.android.systemui（控制 ShadeBlendBlurControllerImpl 与 ShadeWindowBlurControllerImpl）\n" +
                    "• 补充作用域：miui.systemui.plugin（控制中心组件插件，用于辅助状态识别与兼容声明）\n" +
                    "• 修改参数后通过 RemotePreferences 实时推送，无需重启系统界面即可生效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(10.dp),
            ) {
                Text(
                    text = DUMPSYS_COMMAND,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            OutlinedButton(onClick = onCopyCommand) {
                Text("复制只读 ADB 状态检查命令")
            }
        }
    }
}
