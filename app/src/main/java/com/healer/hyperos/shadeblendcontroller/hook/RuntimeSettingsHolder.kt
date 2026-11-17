package com.healer.hyperos.shadeblendcontroller.hook

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.healer.hyperos.shadeblendcontroller.policy.DimSettings
import com.healer.hyperos.shadeblendcontroller.policy.SettingsContract
import io.github.libxposed.api.XposedModule
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

internal object RuntimeSettingsHolder {
    private const val TAG = "ShadeBlendCtrl"

    @Volatile
    var current: DimSettings = DimSettings()
        private set

    private val bound = AtomicBoolean(false)
    private val listenerRegistered = AtomicBoolean(false)
    private val refreshQueued = AtomicBoolean(false)
    private val changeListeners = CopyOnWriteArrayList<Runnable>()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var remotePrefs: SharedPreferences? = null

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, _ ->
        reloadFrom(prefs)
        dispatchChangeOnMain()
    }

    fun init(module: XposedModule) {
        if (!bound.compareAndSet(false, true)) {
            remotePrefs?.let { reloadFrom(it) }
            return
        }
        try {
            val prefs = module.getRemotePreferences(SettingsContract.PREFS_NAME)
            remotePrefs = prefs
            reloadFrom(prefs)
            if (listenerRegistered.compareAndSet(false, true)) {
                prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
            }
        } catch (t: Throwable) {
            bound.set(false)
            module.log(Log.WARN, TAG, "Failed to bind remote preferences", t)
        }
    }

    fun addChangeListener(listener: Runnable) {
        changeListeners.addIfAbsent(listener)
    }

    private fun reloadFrom(prefs: SharedPreferences) {
        runCatching {
            current = SettingsContract.read(prefs)
        }
    }

    private fun dispatchChangeOnMain() {
        if (!refreshQueued.compareAndSet(false, true)) return
        mainHandler.post {
            refreshQueued.set(false)
            remotePrefs?.let { reloadFrom(it) }
            for (listener in changeListeners) {
                runCatching { listener.run() }
            }
        }
    }
}
