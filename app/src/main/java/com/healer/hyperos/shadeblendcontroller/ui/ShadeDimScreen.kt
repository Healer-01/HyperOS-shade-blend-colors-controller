package com.healer.hyperos.shadeblendcontroller.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healer.hyperos.shadeblendcontroller.hook.ShadeBlendModuleEntry
import com.healer.hyperos.shadeblendcontroller.policy.ShadeDimPolicy
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private const val DUMPSYS_COMMAND =
    "adb shell dumpsys activity service com.android.systemui/.SystemUIService ShadeBlendBlurControllerImpl"
private const val PROJECT_HOMEPAGE_URL =
    "https://github.com/Healer-01/HyperOS-shade-blend-colors-controller"
private const val MIUIX_PROJECT_URL =
    "https://github.com/compose-miuix-ui/miuix"

@Composable
fun ShadeDimScreen() {
    val settings by SettingsRepository.settings.collectAsState()
    val framework by SettingsRepository.framework.collectAsState()
    val context = LocalContext.current
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    var showLicenseDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "柔光暗度控制",
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item(key = "status") {
                SmallTitle(text = "模块状态")
                ModuleStatusCard(framework = framework)
            }

            item(key = "dim_control") {
                Spacer(modifier = Modifier.height(10.dp))
                SmallTitle(text = "背景暗度调节")
                UnifiedDimControlCard(
                    enabled = settings.enabled,
                    dimPercent = settings.dimPercent,
                    onToggleEnabled = { checked ->
                        SettingsRepository.update { it.copy(enabled = checked) }
                    },
                    onDimPercentChange = { value ->
                        SettingsRepository.update { it.copy(dimPercent = value) }
                    },
                )
            }

            item(key = "diagnostics") {
                Spacer(modifier = Modifier.height(10.dp))
                SmallTitle(text = "作用域与诊断")
                DiagnosticsCard(
                    onCopyCommand = {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("dumpsys", DUMPSYS_COMMAND))
                        Toast.makeText(context, "已复制只读诊断命令", Toast.LENGTH_SHORT).show()
                    },
                )
            }

            item(key = "about_module") {
                Spacer(modifier = Modifier.height(10.dp))
                SmallTitle(text = "关于模块")
                AboutModuleCard(
                    onOpenHomepage = {
                        openExternalUrl(context, PROJECT_HOMEPAGE_URL)
                    },
                )
            }

            item(key = "credits") {
                Spacer(modifier = Modifier.height(10.dp))
                SmallTitle(text = "引用")
                CreditsCard(
                    onOpenMiuix = {
                        openExternalUrl(context, MIUIX_PROJECT_URL)
                    },
                    onShowLicenses = {
                        showLicenseDialog = true
                    },
                )
            }
        }

        SuperDialog(
            show = showLicenseDialog,
            title = "开源许可",
            summary = "本模块使用并感谢以下遵循 Apache-2.0 协议的开源组件：\n" +
                "• miuix-kotlin-multiplatform (Apache-2.0)\n" +
                "• libxposed API & Service (Apache-2.0)",
            onDismissRequest = { showLicenseDialog = false },
        ) {
            TextButton(
                text = "关闭",
                onClick = { showLicenseDialog = false },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
private fun ModuleStatusCard(framework: FrameworkSnapshot) {
    val active = framework.apiCompatible && framework.systemUiInScope

    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                                else -> MiuixTheme.colorScheme.disabledOnSurface
                            },
                        ),
                )
                Text(
                    text = when {
                        active -> "LSPosed 已激活并就绪"
                        framework.connected -> "LSPosed 已连接，但作用域未完整启用"
                        else -> "未检测到 LSPosed 服务（当前为离线预览）"
                    },
                    style = MiuixTheme.textStyles.headline1,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }

            val details = if (framework.connected) {
                "框架：${framework.frameworkName} ${framework.frameworkVersion}（API ${framework.apiVersion}）"
            } else {
                "请在 LSPosed 管理器启用本模块（要求 libxposed API >= ${FrameworkSnapshot.MIN_SUPPORTED_API}）并勾选系统界面"
            }
            Text(
                text = details,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            if (framework.connected) {
                Text(
                    text = "作用域状态：${ShadeBlendModuleEntry.PKG_SYSTEM_UI} " +
                        "${if (framework.systemUiInScope) "✓" else "✗"}  |  " +
                        "${ShadeBlendModuleEntry.PKG_SYSTEM_UI_PLUGIN} " +
                        "${if (framework.pluginInScope) "✓" else "○"}",
                    style = MiuixTheme.textStyles.footnote1,
                    fontFamily = FontFamily.Monospace,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            if (framework.statusMessage.isNotBlank()) {
                Text(
                    text = framework.statusMessage,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.primary,
                )
            }

            if (framework.connected && (!framework.systemUiInScope || !framework.pluginInScope)) {
                Spacer(modifier = Modifier.height(2.dp))
                TextButton(
                    text = "向 LSPosed 申请所需作用域",
                    onClick = { SettingsRepository.requestMissingScope() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnifiedDimControlCard(
    enabled: Boolean,
    dimPercent: Float,
    onToggleEnabled: (Boolean) -> Unit,
    onDimPercentChange: (Float) -> Unit,
) {
    val normalized = ShadeDimPolicy.normalizePercent(dimPercent)
    val rounded = normalized.roundToInt()

    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        SuperSwitch(
            checked = enabled,
            onCheckedChange = onToggleEnabled,
            title = "启用背景压暗调节",
            summary = "关闭后立即还原 HyperOS 系统默认压暗效果",
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "统一背景暗度",
                        style = MiuixTheme.textStyles.headline1,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "采用原厂整体缩放策略，统一作用于控制中心与通知中心背景",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Text(
                    text = "$rounded%",
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.disabledOnSurface
                    },
                )
            }

            NotificationShadePreview(
                enabled = enabled,
                dimPercent = normalized,
                roundedPercent = rounded,
            )

            Slider(
                value = normalized,
                onValueChange = { onDimPercentChange(it.roundToInt().toFloat()) },
                valueRange = 0f..100f,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )

            val presets = listOf(0, 15, 20, 30, 50, 80, 100)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presets.forEach { preset ->
                    val selected = rounded == preset
                    val containerColor = when {
                        !enabled && selected -> MiuixTheme.colorScheme.disabledPrimary
                        !enabled -> MiuixTheme.colorScheme.disabledSecondaryVariant
                        selected -> MiuixTheme.colorScheme.primary
                        else -> MiuixTheme.colorScheme.secondaryVariant
                    }
                    val labelColor = when {
                        !enabled && selected -> MiuixTheme.colorScheme.disabledOnPrimary
                        !enabled -> MiuixTheme.colorScheme.disabledOnSecondaryVariant
                        selected -> MiuixTheme.colorScheme.onPrimary
                        else -> MiuixTheme.colorScheme.onSecondaryVariant
                    }
                    Surface(
                        onClick = { onDimPercentChange(preset.toFloat()) },
                        enabled = enabled,
                        shape = RoundedCornerShape(50),
                        color = containerColor,
                        contentColor = labelColor,
                    ) {
                        Text(
                            text = when (preset) {
                                0 -> "0% 全透"
                                20 -> "20% 默认"
                                100 -> "100% 原厂"
                                else -> "$preset%"
                            },
                            style = MiuixTheme.textStyles.footnote1,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            color = labelColor,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Simulated HyperOS Notification Center backdrop preview showing how the current dim percentage
 * darkens the blurred home-screen wallpaper behind crisp notification cards.
 */
@Composable
private fun NotificationShadePreview(
    enabled: Boolean,
    dimPercent: Float,
    roundedPercent: Int,
) {
    // Stock overall scaling darkens the blurred desktop wallpaper up to full stock dim (~72% scrim).
    val effectivePercent = if (enabled) dimPercent else 100f
    val dimAlpha = (0.72f * (effectivePercent / 100f)).coerceIn(0f, 0.72f)
    val neutralTintAlpha = (0.12f * (effectivePercent / 100f)).coerceIn(0f, 0.12f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 182.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 0.75.dp,
                color = MiuixTheme.colorScheme.outline,
                shape = RoundedCornerShape(16.dp),
            ),
    ) {
        // 1) Blurred simulated HyperOS desktop wallpaper + app icon silhouettes
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(radius = 14.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF4478F5),
                            Color(0xFF7B66EE),
                            Color(0xFFDF74A5),
                            Color(0xFFF5AD79),
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                )
                drawCircle(
                    color = Color(0xFF8BE2F5).copy(alpha = 0.55f),
                    radius = size.minDimension * 0.42f,
                    center = Offset(size.width * 0.18f, size.height * 0.28f),
                )
                drawCircle(
                    color = Color(0xFFFFD38B).copy(alpha = 0.50f),
                    radius = size.minDimension * 0.48f,
                    center = Offset(size.width * 0.84f, size.height * 0.78f),
                )
                val iconColors = listOf(
                    Color(0xFF4CD964),
                    Color(0xFF5AC8FA),
                    Color(0xFFFF9500),
                    Color(0xFFFF3B30),
                )
                val iconSize = size.minDimension * 0.17f
                val gap = size.width * 0.07f
                val startX = (size.width - (iconColors.size * iconSize + (iconColors.size - 1) * gap)) / 2f
                val y = size.height * 0.70f
                iconColors.forEachIndexed { index, color ->
                    drawRoundRect(
                        color = color.copy(alpha = 0.78f),
                        topLeft = Offset(startX + index * (iconSize + gap), y),
                        size = Size(iconSize, iconSize),
                        cornerRadius = CornerRadius(iconSize * 0.28f, iconSize * 0.28f),
                    )
                }
            }
        }

        // 2) Live background dim overlay driven by the unified dim slider
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color(0xFF14161A).copy(alpha = neutralTintAlpha))
                .background(Color.Black.copy(alpha = dimAlpha)),
        )

        // 3) Foreground HyperOS Notification Center clock & translucent cards
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "09:41",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                    Text(
                        text = "11月17日 周二",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.84f),
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = if (enabled) "通知中心 · $roundedPercent%" else "已关闭 · 系统默认",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                    )
                }
            }

            PreviewNotificationItem(
                badgeColor = Color(0xFF3482FF),
                appName = "系统界面",
                message = "通知中心背景压暗实时预览（0% 最通透，100% 原厂暗度）",
                timeLabel = "刚刚",
                cardAlpha = 0.18f,
            )

            PreviewNotificationItem(
                badgeColor = Color(0xFF28B463),
                appName = "消息通知",
                message = "拖动下方滑块即可直观对比文字与壁纸的通透对比度",
                timeLabel = "2分钟前",
                cardAlpha = 0.14f,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .height(3.5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.55f)),
                )
            }
        }
    }
}

@Composable
private fun PreviewNotificationItem(
    badgeColor: Color,
    appName: String,
    message: String,
    timeLabel: String,
    cardAlpha: Float,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = cardAlpha))
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.22f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(badgeColor),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.9f)),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = appName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = timeLabel,
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.68f),
                )
            }
            Text(
                text = message,
                fontSize = 10.5.sp,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DiagnosticsCard(onCopyCommand: () -> Unit) {
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "• 核心作用域：com.android.systemui（控制 ShadeBlendBlurControllerImpl 与 ShadeWindowBlurControllerImpl）\n" +
                    "• 补充作用域：miui.systemui.plugin（用于完整作用域识别与兼容声明）\n" +
                    "• 修改暗度后通过 RemotePreferences 实时生效，无需重启系统界面。",
                style = MiuixTheme.textStyles.body2,
                lineHeight = 20.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MiuixTheme.colorScheme.surface)
                    .padding(12.dp),
            ) {
                Text(
                    text = DUMPSYS_COMMAND,
                    style = MiuixTheme.textStyles.footnote1,
                    fontFamily = FontFamily.Monospace,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        SuperArrow(
            title = "复制只读 ADB 状态检查命令",
            summary = "查看 ShadeBlendBlurControllerImpl 注入状态",
            startAction = {
                Icon(
                    imageVector = MiuixIcons.Copy,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(22.dp),
                )
            },
            onClick = onCopyCommand,
        )
    }
}

@Composable
private fun AboutModuleCard(onOpenHomepage: () -> Unit) {
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        Text(
            text = "HyperOS 柔光暗度控制是一个基于 libxposed API 102 的 HyperOS 4 系统界面美化模块。" +
                "采用原厂混色配方整体缩放策略，为下拉控制中心与通知中心提供统一、通透且可实时调节的背景压暗视觉体验。",
            style = MiuixTheme.textStyles.body1,
            lineHeight = 24.sp,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
        )

        SuperArrow(
            title = "项目主页",
            summary = "Healer-01/HyperOS-shade-blend-colors-controller",
            startAction = {
                Icon(
                    imageVector = GitHubIcon,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(24.dp),
                )
            },
            onClick = onOpenHomepage,
        )
    }
}

@Composable
private fun CreditsCard(
    onOpenMiuix: () -> Unit,
    onShowLicenses: () -> Unit,
) {
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        SuperArrow(
            title = "本项目使用MiuiX UI组件构建",
            summary = "miuix-kotlin-multiplatform(Apache-2.0)",
            startAction = {
                MiuixHexagonIcon(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(26.dp),
                )
            },
            onClick = onOpenMiuix,
        )

        SuperArrow(
            title = "开源许可",
            onClick = onShowLicenses,
        )
    }
}

@Composable
private fun MiuixHexagonIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outerR = size.minDimension * 0.45f
        val ringR = size.minDimension * 0.25f
        val innerR = size.minDimension * 0.11f

        fun hexagonPath(radius: Float): Path {
            val path = Path()
            for (i in 0 until 6) {
                val angle = (-90.0 + i * 60.0) * PI / 180.0
                val x = cx + (radius * cos(angle)).toFloat()
                val y = cy + (radius * sin(angle)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            return path
        }

        drawPath(
            path = hexagonPath(outerR),
            color = Color(0xFF2B67F6),
        )
        drawPath(
            path = hexagonPath(ringR),
            color = Color.White,
            style = Stroke(width = size.minDimension * 0.11f),
        )
        drawPath(
            path = hexagonPath(innerR),
            color = Color.White,
        )
    }
}

private val GitHubIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "GitHub",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = listOf(
            PathNode.MoveTo(12.0f, 1.27f),
            PathNode.ArcTo(11.0f, 11.0f, 0.0f, false, false, 8.52f, 22.73f),
            PathNode.CurveTo(9.07f, 22.82f, 9.25f, 22.49f, 9.25f, 22.2f),
            PathNode.VerticalTo(20.35f),
            PathNode.CurveTo(6.22f, 21.01f, 5.58f, 18.9f, 5.58f, 18.9f),
            PathNode.CurveTo(5.08f, 17.64f, 4.37f, 17.3f, 4.37f, 17.3f),
            PathNode.CurveTo(3.38f, 16.63f, 4.44f, 16.64f, 4.44f, 16.64f),
            PathNode.CurveTo(5.54f, 16.71f, 6.11f, 17.76f, 6.11f, 17.76f),
            PathNode.CurveTo(7.08f, 19.42f, 8.65f, 18.94f, 9.27f, 18.66f),
            PathNode.CurveTo(9.37f, 17.96f, 9.65f, 17.48f, 9.96f, 17.21f),
            PathNode.CurveTo(7.54f, 16.94f, 5.0f, 16.01f, 5.0f, 11.83f),
            PathNode.CurveTo(5.0f, 10.64f, 5.42f, 9.67f, 6.12f, 8.91f),
            PathNode.CurveTo(6.01f, 8.63f, 5.63f, 7.51f, 6.23f, 6.03f),
            PathNode.CurveTo(6.23f, 6.03f, 7.14f, 5.74f, 9.22f, 7.15f),
            PathNode.CurveTo(10.09f, 6.91f, 11.02f, 6.79f, 11.94f, 6.79f),
            PathNode.CurveTo(12.86f, 6.79f, 13.79f, 6.91f, 14.66f, 7.15f),
            PathNode.CurveTo(16.74f, 5.74f, 17.65f, 6.03f, 17.65f, 6.03f),
            PathNode.CurveTo(18.25f, 7.51f, 17.87f, 8.63f, 17.76f, 8.91f),
            PathNode.CurveTo(18.46f, 9.67f, 18.88f, 10.64f, 18.88f, 11.83f),
            PathNode.CurveTo(18.88f, 16.02f, 16.34f, 16.94f, 13.91f, 17.2f),
            PathNode.CurveTo(14.3f, 17.54f, 14.65f, 18.21f, 14.65f, 19.24f),
            PathNode.VerticalTo(22.26f),
            PathNode.CurveTo(14.65f, 22.55f, 14.83f, 22.88f, 15.39f, 22.78f),
            PathNode.ArcTo(11.0f, 11.0f, 0.0f, false, false, 12.0f, 1.27f),
            PathNode.Close,
        ),
        pathFillType = PathFillType.EvenOdd,
        fill = SolidColor(Color.Black),
    ).build()
}

private fun openExternalUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "无法打开链接：$url", Toast.LENGTH_SHORT).show()
    }
}
