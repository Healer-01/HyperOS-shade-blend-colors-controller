package com.healer.hyperos.shadeblendcontroller.hook

import android.app.Application
import android.util.Log
import com.healer.hyperos.shadeblendcontroller.BuildConfig
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.util.concurrent.atomic.AtomicBoolean

class ShadeBlendModuleEntry : XposedModule() {
    private val systemUiBootstrapped = AtomicBoolean(false)
    private val pluginBootstrapped = AtomicBoolean(false)

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        log(
            Log.INFO,
            TAG,
            "Module loaded in process=${param.processName} (api=${apiVersion}, framework=$frameworkName)",
        )
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        val packageName = param.packageName
        if (packageName == BuildConfig.APPLICATION_ID) return
        if (packageName != PKG_SYSTEM_UI && packageName != PKG_SYSTEM_UI_PLUGIN) return

        RuntimeSettingsHolder.init(this)

        if (packageName == PKG_SYSTEM_UI) {
            installSystemUiHooks(param.defaultClassLoader, phase = "PackageLoaded")
        } else {
            logPluginHeartbeat(phase = "PackageLoaded")
        }
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        val packageName = param.packageName
        if (packageName == BuildConfig.APPLICATION_ID) return
        if (packageName != PKG_SYSTEM_UI && packageName != PKG_SYSTEM_UI_PLUGIN) return

        RuntimeSettingsHolder.init(this)

        if (packageName == PKG_SYSTEM_UI) {
            installSystemUiHooks(param.classLoader, phase = "PackageReady")
        } else {
            logPluginHeartbeat(phase = "PackageReady")
        }
    }

    private fun installSystemUiHooks(loader: ClassLoader, phase: String) {
        if (systemUiBootstrapped.compareAndSet(false, true)) {
            log(
                Log.INFO,
                TAG,
                "Initializing HyperOS shade dim hooks in $PKG_SYSTEM_UI (phase=$phase, process=${Application.getProcessName()})",
            )
        }
        // Individual hook objects keep their own AtomicBoolean guards, so late classes
        // unavailable at PackageLoaded are safely retried at PackageReady.
        ShadeBackdropHooks.install(this, loader)
        ShadeWindowDimHooks.install(this, loader)
    }

    private fun logPluginHeartbeat(phase: String) {
        if (pluginBootstrapped.compareAndSet(false, true)) {
            log(
                Log.INFO,
                TAG,
                "Scope active for $PKG_SYSTEM_UI_PLUGIN (phase=$phase); " +
                    "panel backdrop dim is driven from $PKG_SYSTEM_UI ShadeBlendBlurControllerImpl",
            )
        }
    }

    companion object {
        private const val TAG = "ShadeBlendCtrl"
        const val PKG_SYSTEM_UI = "com.android.systemui"
        const val PKG_SYSTEM_UI_PLUGIN = "miui.systemui.plugin"
    }
}
