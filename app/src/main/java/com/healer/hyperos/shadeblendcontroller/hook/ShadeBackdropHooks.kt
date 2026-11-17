package com.healer.hyperos.shadeblendcontroller.hook

import android.util.Log
import android.view.View
import com.healer.hyperos.shadeblendcontroller.policy.PanelRole
import com.healer.hyperos.shadeblendcontroller.policy.ShadeDimPolicy
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.io.PrintWriter
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal object ShadeBackdropHooks {
    private const val TAG = "ShadeBlendCtrl"
    private const val MI_BLUR_COMPAT = "com.miui.systemui.util.MiBlurCompat"

    private val installed = AtomicBoolean(false)
    private val hookedSetColors = AtomicBoolean(false)
    private val firstAppliedLogged = AtomicBoolean(false)
    private val listenersRegistered = AtomicBoolean(false)
    private val replaying = ThreadLocal.withInitial { false }

    private val viewRoles: MutableMap<View, PanelRole> =
        Collections.synchronizedMap(WeakHashMap())
    private val ownerRoles: MutableMap<Any, PanelRole> =
        Collections.synchronizedMap(WeakHashMap())
    private val lastStockColors: MutableMap<View, IntArray> =
        Collections.synchronizedMap(WeakHashMap())
    private val lastRatios: MutableMap<View, Float> =
        Collections.synchronizedMap(WeakHashMap())

    private val compatCalls = AtomicLong(0L)
    private val finalCalls = AtomicLong(0L)

    @Volatile
    private var lastAppliedCcColors: IntArray? = null

    @Volatile
    private var lastAppliedNotifColors: IntArray? = null

    @Volatile
    private var lastAppliedCombinedColors: IntArray? = null

    @Volatile
    private var cachedSetColorsMethod: Method? = null

    @Volatile
    private var cachedBlendViewField: Field? = null

    @Volatile
    private var cachedBlendRecipeField: Field? = null

    @Volatile
    private var cachedBlendRatioField: Field? = null

    @Volatile
    private var cachedBlendEnabledField: Field? = null

    @Volatile
    private var cachedControllerRef: WeakReference<Any>? = null

    private var notifBgField: Field? = null
    private var ccBgField: Field? = null
    private var combinedBgField: Field? = null
    private var notifBlendField: Field? = null
    private var ccBlendField: Field? = null
    private var combinedBlendField: Field? = null
    private var lazyGetMethod: Method? = null

    fun install(module: XposedModule, loader: ClassLoader) {
        if (listenersRegistered.compareAndSet(false, true)) {
            RuntimeSettingsHolder.addChangeListener { refreshAllOwners() }
            ActivePanelTracker.addListener {
                if (RuntimeSettingsHolder.current.enabled &&
                    RuntimeSettingsHolder.current.controlMode ==
                    com.healer.hyperos.shadeblendcontroller.policy.ControlMode.SEPARATE
                ) {
                    refreshRole(PanelRole.COMBINED_WINDOW)
                }
            }
        }

        if (installed.get()) return
        val controllerClass = runCatching {
            ShadeReflect.resolveControllerClass(loader, "ShadeBlendBlurController")
        }.getOrNull() ?: return
        val compatClass = ShadeReflect.findClassOrNull(MI_BLUR_COMPAT, loader) ?: return

        if (!installed.compareAndSet(false, true)) return

        try {
            val startTarget = ShadeReflect.resolveStartTarget(controllerClass, "blendBlurController")
            val setColors = compatClass.getDeclaredMethod(
                "setMiBackgroundBlendColors",
                View::class.java,
                IntArray::class.java,
                Float::class.javaPrimitiveType,
            ).apply { isAccessible = true }
            cachedSetColorsMethod = setColors

            val blendBackgroundClass = Class.forName(
                controllerClass.name + "\$BlendBackground",
                false,
                loader,
            )
            val blendView = blendBackgroundClass.getDeclaredField("view").apply { isAccessible = true }
            val blendRecipe = blendBackgroundClass.getDeclaredField("blendColors").apply { isAccessible = true }
            val blendRatio = blendBackgroundClass.getDeclaredField("blurRatio").apply { isAccessible = true }
            val blendEnabled = blendBackgroundClass.getDeclaredField("enabled").apply { isAccessible = true }
            val applyBlendColors = blendBackgroundClass.getDeclaredMethod("applyBlendColors")
                .apply { isAccessible = true }

            cachedBlendViewField = blendView
            cachedBlendRecipeField = blendRecipe
            cachedBlendRatioField = blendRatio
            cachedBlendEnabledField = blendEnabled

            lazyGetMethod = ShadeReflect.findClassOrNull("dagger.Lazy", loader)
                ?.let { ShadeReflect.findDeclaredMethodOrNull(it, "get") }

            notifBgField = ShadeReflect.findDeclaredFieldOrNull(controllerClass, "notificationPanelBackground")
            ccBgField = ShadeReflect.findDeclaredFieldOrNull(controllerClass, "controlCenterBackground")
            combinedBgField = ShadeReflect.findDeclaredFieldOrNull(controllerClass, "shadeWindowBackground")
            notifBlendField = ShadeReflect.findDeclaredFieldOrNull(controllerClass, "notificationBlendBackground")
            ccBlendField = ShadeReflect.findDeclaredFieldOrNull(controllerClass, "controlCenterBlendBackground")
            combinedBlendField = ShadeReflect.findDeclaredFieldOrNull(controllerClass, "combinedBlendBackground")

            // 1) Capture View and BlendBackground ownership during controller start()
            module.hook(startTarget.method)
                .setId("shade-blend-controller-start")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val controller = startTarget.unwrapController(chain.thisObject)
                    if (controller != null) {
                        cachedControllerRef = WeakReference(controller)
                        captureControllerViews(controller)
                    }
                    val result = chain.proceed()
                    if (controller != null) {
                        captureControllerOwners(controller)
                    }
                    result
                }

            // 2) Intercept MiBlurCompat.setMiBackgroundBlendColors(View, int[], float)
            if (hookedSetColors.compareAndSet(false, true)) {
                module.hook(setColors)
                    .setId("shade-blend-compat-set-colors")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept { chain ->
                        val view = chain.getArg(0) as? View ?: return@intercept chain.proceed()
                        val role = viewRoles[view] ?: return@intercept chain.proceed()
                        val originalColors = chain.getArg(1) as? IntArray
                        val ratio = (chain.getArg(2) as? Float) ?: 0f

                        if (replaying.get() != true && originalColors != null) {
                            lastStockColors[view] = originalColors.clone()
                            lastRatios[view] = ratio
                        }

                        val settings = RuntimeSettingsHolder.current
                        if (!settings.enabled) {
                            return@intercept chain.proceed()
                        }

                        val dimPercent = ShadeDimPolicy.resolveDimPercent(
                            settings = settings,
                            role = role,
                            panelState = ActivePanelTracker.state,
                        )
                        val customColors = ShadeDimPolicy.buildBlendColors(
                            original = originalColors,
                            dimPercent = dimPercent,
                            strategy = settings.dimStrategy,
                            singleEntryTarget = settings.singleEntryTarget,
                        )

                        compatCalls.incrementAndGet()
                        when (role) {
                            PanelRole.CONTROL_CENTER -> lastAppliedCcColors = customColors
                            PanelRole.NOTIFICATION_SHADE -> lastAppliedNotifColors = customColors
                            PanelRole.COMBINED_WINDOW -> lastAppliedCombinedColors = customColors
                        }
                        if (firstAppliedLogged.compareAndSet(false, true)) {
                            module.log(
                                Log.INFO,
                                TAG,
                                "Applied shade dim ($role -> $dimPercent%): ${ShadeDimPolicy.formatRecipeHex(customColors)}",
                            )
                        }

                        chain.proceed(arrayOf(view, customColors, ratio))
                    }
            }

            // 3) Reapply after BlendBackground.applyBlendColors() to guard against ART inlining
            module.hook(applyBlendColors)
                .setId("shade-blend-apply-final")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val owner = chain.thisObject ?: return@intercept chain.proceed()
                    val view = blendView.get(owner) as? View
                    if (view != null) {
                        ensureOwnerRole(owner, view, controllerClass)
                    }

                    val result = chain.proceed()

                    val settings = RuntimeSettingsHolder.current
                    if (settings.enabled && view != null && blendEnabled.getBoolean(owner)) {
                        val stockRecipe = blendRecipe.get(owner) as? IntArray
                        val ratio = blendRatio.getFloat(owner)
                        finalCalls.incrementAndGet()
                        replaying.set(true)
                        try {
                            setColors.invoke(null, view, stockRecipe, ratio)
                        } finally {
                            replaying.set(false)
                        }
                    }
                    result
                }

            // 4) Optional dumpsys diagnostic hook (read-only verification support)
            ShadeReflect.findDeclaredMethodOrNull(
                controllerClass,
                "dump",
                PrintWriter::class.java,
                Array<String>::class.java,
            )?.let { dumpMethod ->
                module.hook(dumpMethod)
                    .setId("shade-blend-dump")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept { chain ->
                        val result = chain.proceed()
                        val out = chain.getArg(0) as? PrintWriter ?: return@intercept result
                        val settings = RuntimeSettingsHolder.current
                        val panel = ActivePanelTracker.state
                        out.println(
                            "  ShadeBlendController: enabled=${settings.enabled}" +
                                ", mode=${settings.controlMode}" +
                                ", strategy=${settings.dimStrategy}" +
                                ", singleEntry=${settings.singleEntryTarget}" +
                                ", unified=${settings.unifiedDimPercent}%" +
                                ", cc=${settings.controlCenterDimPercent}%" +
                                ", notif=${settings.notificationShadeDimPercent}%" +
                                ", activePanel=$panel" +
                                ", compatCalls=${compatCalls.get()}" +
                                ", finalCalls=${finalCalls.get()}",
                        )
                        out.println("    lastControlCenter=${ShadeDimPolicy.formatRecipeHex(lastAppliedCcColors)}")
                        out.println("    lastNotification=${ShadeDimPolicy.formatRecipeHex(lastAppliedNotifColors)}")
                        out.println("    lastCombined=${ShadeDimPolicy.formatRecipeHex(lastAppliedCombinedColors)}")
                        result
                    }
            }

            // Deoptimize callers so ART does not inline MiBlurCompat.setMiBackgroundBlendColors
            ShadeReflect.deoptimizeClasses(
                module,
                loader,
                controllerClass.name,
                blendBackgroundClass.name,
                controllerClass.name + "\$start\$2\$1",
                controllerClass.name + "\$start\$1\$1",
            )

            module.log(Log.INFO, TAG, "ShadeBlendBlurController hooks installed (${controllerClass.name})")
        } catch (t: Throwable) {
            installed.set(false)
            module.log(Log.WARN, TAG, "ShadeBlendBlurController hook installation failed", t)
        }
    }

    private fun captureControllerViews(controller: Any) {
        registerViewField(controller, notifBgField, PanelRole.NOTIFICATION_SHADE)
        registerViewField(controller, ccBgField, PanelRole.CONTROL_CENTER)
        registerViewField(controller, combinedBgField, PanelRole.COMBINED_WINDOW)
    }

    private fun captureControllerOwners(controller: Any) {
        registerOwnerField(controller, notifBlendField, PanelRole.NOTIFICATION_SHADE)
        registerOwnerField(controller, ccBlendField, PanelRole.CONTROL_CENTER)
        registerOwnerField(controller, combinedBlendField, PanelRole.COMBINED_WINDOW)
    }

    private fun registerViewField(controller: Any, field: Field?, role: PanelRole) {
        val raw = runCatching { field?.get(controller) }.getOrNull() ?: return
        val view = ShadeReflect.resolveViewValue(raw, lazyGetMethod) ?: return
        viewRoles[view] = role
    }

    private fun registerOwnerField(controller: Any, field: Field?, role: PanelRole) {
        val owner = runCatching { field?.get(controller) }.getOrNull() ?: return
        ownerRoles[owner] = role
        val view = runCatching { cachedBlendViewField?.get(owner) as? View }.getOrNull()
        if (view != null) {
            viewRoles[view] = role
        }
    }

    private fun ensureOwnerRole(owner: Any, view: View, controllerClass: Class<*>) {
        val knownOwnerRole = ownerRoles[owner]
        if (knownOwnerRole != null) {
            viewRoles[view] = knownOwnerRole
            return
        }
        var controller = cachedControllerRef?.get()
        if (controller == null) {
            for (field in owner.javaClass.declaredFields) {
                if (controllerClass.isAssignableFrom(field.type)) {
                    field.isAccessible = true
                    controller = runCatching { field.get(owner) }.getOrNull()
                    if (controller != null) {
                        cachedControllerRef = WeakReference(controller)
                        break
                    }
                }
            }
        }
        if (controller != null) {
            captureControllerViews(controller)
            captureControllerOwners(controller)
        }
        val resolved = ownerRoles[owner] ?: viewRoles[view]
        if (resolved != null) {
            ownerRoles[owner] = resolved
            viewRoles[view] = resolved
        }
    }

    private fun refreshAllOwners() {
        refreshRole(null)
    }

    private fun refreshRole(onlyRole: PanelRole?) {
        val setColors = cachedSetColorsMethod ?: return
        val viewField = cachedBlendViewField ?: return
        val recipeField = cachedBlendRecipeField ?: return
        val ratioField = cachedBlendRatioField ?: return
        val enabledField = cachedBlendEnabledField ?: return

        val snapshot = synchronized(ownerRoles) { ownerRoles.entries.map { it.key to it.value } }
        replaying.set(true)
        try {
            for ((owner, role) in snapshot) {
                if (onlyRole != null && role != onlyRole) continue
                runCatching {
                    val enabled = enabledField.getBoolean(owner)
                    val view = viewField.get(owner) as? View ?: return@runCatching
                    val ratio = runCatching { ratioField.getFloat(owner) }
                        .getOrElse { lastRatios[view] ?: 0f }
                    if (!enabled && ratio <= 0f) return@runCatching
                    val stockRecipe = (recipeField.get(owner) as? IntArray) ?: lastStockColors[view]
                    setColors.invoke(null, view, stockRecipe, ratio)
                }
            }
        } finally {
            replaying.set(false)
        }
    }
}
