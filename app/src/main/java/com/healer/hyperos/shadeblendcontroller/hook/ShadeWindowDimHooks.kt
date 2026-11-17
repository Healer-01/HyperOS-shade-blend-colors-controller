package com.healer.hyperos.shadeblendcontroller.hook

import android.util.Log
import android.view.View
import com.healer.hyperos.shadeblendcontroller.policy.PanelRole
import com.healer.hyperos.shadeblendcontroller.policy.ShadeDimPolicy
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal object ShadeWindowDimHooks {
    private const val TAG = "ShadeBlendCtrl"

    private val installed = AtomicBoolean(false)
    private val listenersRegistered = AtomicBoolean(false)
    private val replaying = ThreadLocal.withInitial { false }

    private val windowViews: MutableMap<View, Int> =
        Collections.synchronizedMap(WeakHashMap())
    private val lastStockColors: MutableMap<View, Int> =
        Collections.synchronizedMap(WeakHashMap())

    fun install(module: XposedModule, loader: ClassLoader) {
        if (listenersRegistered.compareAndSet(false, true)) {
            RuntimeSettingsHolder.addChangeListener { replayWindowViews() }
        }
        if (installed.get()) return
        val controllerClass = runCatching {
            ShadeReflect.resolveControllerClass(loader, "ShadeWindowBlurController")
        }.getOrNull() ?: return
        if (!installed.compareAndSet(false, true)) return

        try {
            val startTarget = ShadeReflect.resolveStartTarget(controllerClass, "windowBlurController")
            val backgroundField = ShadeReflect.findDeclaredFieldOrNull(controllerClass, "windowBackground")
            val dimAlphaField = ShadeReflect.findDeclaredFieldOrNull(controllerClass, "dimAlpha")
            val dimRgbField = ShadeReflect.findDeclaredFieldOrNull(controllerClass, "dimRGB")
            val selfBlurMethod = ShadeReflect.findDeclaredMethodOrNull(
                controllerClass,
                "applyMiSelfBlurEffect",
                List::class.java,
                Float::class.javaPrimitiveType!!,
            )
            val lazyGet = ShadeReflect.findClassOrNull("dagger.Lazy", loader)
                ?.let { ShadeReflect.findDeclaredMethodOrNull(it, "get") }

            if (backgroundField == null) {
                installed.set(false)
                return
            }

            module.hook(startTarget.method)
                .setId("shade-window-dim-start")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val owner = startTarget.unwrapController(chain.thisObject)
                    if (owner != null) {
                        val raw = runCatching { backgroundField.get(owner) }.getOrNull()
                        val view = ShadeReflect.resolveViewValue(raw, lazyGet)
                        if (view != null) {
                            val id = view.resources.getIdentifier(
                                "shade_dim_color",
                                "color",
                                "com.android.systemui",
                            )
                            if (id != 0) {
                                windowViews[view] = id
                            }
                        }
                    }
                    chain.proceed()
                }

            val setBackgroundColor = View::class.java.getDeclaredMethod(
                "setBackgroundColor",
                Int::class.javaPrimitiveType,
            )
            module.hook(setBackgroundColor)
                .setId("shade-window-dim-bg-color")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val view = chain.thisObject as? View ?: return@intercept chain.proceed()
                    val colorId = windowViews[view] ?: return@intercept chain.proceed()
                    val stockColor = (chain.getArg(0) as? Int) ?: return@intercept chain.proceed()
                    if (replaying.get() != true) {
                        lastStockColors[view] = stockColor
                    }

                    val settings = RuntimeSettingsHolder.current
                    if (!settings.enabled) {
                        return@intercept chain.proceed()
                    }

                    val baseAlpha = runCatching {
                        view.context.getColor(colorId) ushr 24
                    }.getOrDefault(0x66)
                    val dimPercent = ShadeDimPolicy.resolveDimPercent(
                        settings = settings,
                        role = PanelRole.COMBINED_WINDOW,
                        panelState = ActivePanelTracker.state,
                    )
                    val adjusted = ShadeDimPolicy.computeWindowDimColor(
                        originalColor = stockColor,
                        stockMaxAlpha = baseAlpha,
                        dimPercent = dimPercent,
                    )
                    chain.proceed(arrayOf(adjusted))
                }

            if (selfBlurMethod != null && dimAlphaField != null && dimRgbField != null) {
                module.hook(selfBlurMethod)
                    .setId("shade-window-dim-self-blur")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept { chain ->
                        val settings = RuntimeSettingsHolder.current
                        if (!settings.enabled) return@intercept chain.proceed()
                        val owner = chain.thisObject ?: return@intercept chain.proceed()
                        val savedAlpha = dimAlphaField.getInt(owner)
                        val savedRgb = dimRgbField.getInt(owner)
                        val dimPercent = ShadeDimPolicy.resolveDimPercent(
                            settings = settings,
                            role = PanelRole.COMBINED_WINDOW,
                            panelState = ActivePanelTracker.state,
                        )
                        try {
                            dimAlphaField.setInt(
                                owner,
                                ShadeDimPolicy.blackScrimColor(dimPercent, 1f) ushr 24,
                            )
                            dimRgbField.setInt(owner, 0)
                            chain.proceed()
                        } finally {
                            dimAlphaField.setInt(owner, savedAlpha)
                            dimRgbField.setInt(owner, savedRgb)
                        }
                    }
            }

            ShadeReflect.deoptimizeClasses(
                module,
                loader,
                controllerClass.name,
                controllerClass.name + "\$start\$1\$1\$1",
            )
            module.log(Log.INFO, TAG, "ShadeWindowBlurController hooks installed (${controllerClass.name})")
        } catch (t: Throwable) {
            installed.set(false)
            module.log(Log.WARN, TAG, "ShadeWindowBlurController hooks unavailable", t)
        }
    }

    private fun replayWindowViews() {
        val views = synchronized(windowViews) { windowViews.keys.toList() }
        replaying.set(true)
        try {
            for (view in views) {
                val stockColor = lastStockColors[view] ?: continue
                runCatching { view.setBackgroundColor(stockColor) }
            }
        } finally {
            replaying.set(false)
        }
    }
}
