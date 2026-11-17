package com.healer.hyperos.shadeblendcontroller.hook

import android.util.Log
import com.healer.hyperos.shadeblendcontroller.policy.ActivePanelState
import com.healer.hyperos.shadeblendcontroller.policy.PanelRole
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

internal object ActivePanelTracker {
    private const val TAG = "ShadeBlendCtrl"
    private const val PANEL_INJECTOR = "com.android.systemui.shade.NotificationPanelViewControllerInjector"
    private const val SHADE_SWITCH = "com.miui.systemui.shade.ShadeSwitchControllerImpl"

    @Volatile
    var state: ActivePanelState = ActivePanelState()
        private set

    private val injectorInstalled = AtomicBoolean(false)
    private val switchInstalled = AtomicBoolean(false)
    private val listeners = CopyOnWriteArrayList<Runnable>()

    fun addListener(listener: Runnable) {
        listeners.addIfAbsent(listener)
    }

    fun install(module: XposedModule, loader: ClassLoader) {
        installInjector(module, loader)
        installSwitchController(module, loader)
    }

    private fun installInjector(module: XposedModule, loader: ClassLoader) {
        if (injectorInstalled.get()) return
        val injectorClass = ShadeReflect.findClassOrNull(PANEL_INJECTOR, loader) ?: return
        if (!injectorInstalled.compareAndSet(false, true)) return
        try {
            val ccAppear = ShadeReflect.findDeclaredMethodOrNull(
                injectorClass,
                "onControlCenterAppearChanged",
                Boolean::class.javaPrimitiveType!!,
            )
            val notifAppear = ShadeReflect.findDeclaredMethodOrNull(
                injectorClass,
                "onNotificationAppearChanged",
                Boolean::class.javaPrimitiveType!!,
            )
            if (ccAppear == null && notifAppear == null) {
                injectorInstalled.set(false)
                return
            }

            ccAppear?.let { method ->
                module.hook(method)
                    .setId("shade-blend-panel-cc-appear")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept { chain ->
                        val visible = (chain.getArg(0) as? Boolean) == true
                        updateControlCenter(visible)
                        chain.proceed()
                    }
            }

            notifAppear?.let { method ->
                module.hook(method)
                    .setId("shade-blend-panel-notif-appear")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept { chain ->
                        val visible = (chain.getArg(0) as? Boolean) == true
                        updateNotification(visible)
                        chain.proceed()
                    }
            }

            module.log(Log.INFO, TAG, "Active panel appear tracker installed")
        } catch (t: Throwable) {
            injectorInstalled.set(false)
            module.log(Log.WARN, TAG, "Active panel appear tracker unavailable", t)
        }
    }

    private fun installSwitchController(module: XposedModule, loader: ClassLoader) {
        if (switchInstalled.get()) return
        val switchClass = ShadeReflect.findClassOrNull(SHADE_SWITCH, loader) ?: return
        if (!switchInstalled.compareAndSet(false, true)) return
        try {
            val progressAnimatorField = ShadeReflect.findDeclaredFieldOrNull(switchClass, "progressAnimator")
            val switchingField = ShadeReflect.findDeclaredFieldOrNull(switchClass, "switching")
            if (progressAnimatorField == null || switchingField == null) {
                switchInstalled.set(false)
                return
            }

            var progressValueField: Field? = null
            val hooker = XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                val controller = chain.thisObject ?: return@Hooker result
                runCatching {
                    val animator = progressAnimatorField.get(controller)
                    if (animator != null) {
                        if (progressValueField == null) {
                            progressValueField = ShadeReflect.findDeclaredFieldOrNull(animator.javaClass, "_progress")
                        }
                        val progress = (progressValueField?.get(animator) as? Number)?.toFloat() ?: state.switchFraction
                        val switching = switchingField.getBoolean(controller)
                        updateSwitch(progress, switching)
                    }
                }
                result
            }

            for (method in switchClass.declaredMethods) {
                if (method.name == "performProgressChanged" || method.name == "setSwitching") {
                    module.hook(method)
                        .setId("shade-blend-panel-switch-${method.name}")
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(hooker)
                }
            }

            module.log(Log.INFO, TAG, "Shade switch tracker installed")
        } catch (t: Throwable) {
            switchInstalled.set(false)
            module.log(Log.WARN, TAG, "Shade switch tracker unavailable", t)
        }
    }

    private fun updateControlCenter(visible: Boolean) {
        val prev = state
        val next = prev.copy(
            controlCenterVisible = visible,
            lastPanel = if (visible) PanelRole.CONTROL_CENTER else prev.lastPanel,
        )
        if (next != prev) {
            state = next
            notifyListeners()
        }
    }

    private fun updateNotification(visible: Boolean) {
        val prev = state
        val next = prev.copy(
            notificationVisible = visible,
            lastPanel = if (visible) PanelRole.NOTIFICATION_SHADE else prev.lastPanel,
        )
        if (next != prev) {
            state = next
            notifyListeners()
        }
    }

    private fun updateSwitch(progress: Float, switching: Boolean) {
        val clamped = if (progress.isFinite()) progress.coerceIn(0f, 1f) else state.switchFraction
        val prev = state
        val inferredLast = when {
            !switching && clamped <= 0.01f -> PanelRole.CONTROL_CENTER
            !switching && clamped >= 0.99f -> PanelRole.NOTIFICATION_SHADE
            else -> prev.lastPanel
        }
        val next = prev.copy(
            switching = switching,
            switchFraction = clamped,
            lastPanel = inferredLast,
        )
        if (next != prev) {
            state = next
            notifyListeners()
        }
    }

    private fun notifyListeners() {
        for (listener in listeners) {
            runCatching { listener.run() }
        }
    }
}
