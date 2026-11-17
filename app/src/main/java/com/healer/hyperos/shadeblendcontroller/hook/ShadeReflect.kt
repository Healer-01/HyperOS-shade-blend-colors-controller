package com.healer.hyperos.shadeblendcontroller.hook

import android.view.View
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method

internal object ShadeReflect {
    private const val BLUR_PACKAGE = "com.miui.systemui.shade.blur."

    data class StartTarget(
        val method: Method,
        val childField: Field?,
    ) {
        fun unwrapController(receiver: Any?): Any? {
            if (receiver == null) return null
            return if (childField == null) receiver else childField.get(receiver)
        }
    }

    fun findClassOrNull(name: String, loader: ClassLoader): Class<*>? =
        runCatching { Class.forName(name, false, loader) }.getOrNull()

    fun resolveControllerClass(loader: ClassLoader, simpleName: String): Class<*> {
        val implName = BLUR_PACKAGE + simpleName + "Impl"
        val baseName = BLUR_PACKAGE + simpleName
        return findClassOrNull(implName, loader)
            ?: findClassOrNull(baseName, loader)
            ?: throw ClassNotFoundException("Neither $implName nor $baseName found")
    }

    fun resolveStartTarget(controllerClass: Class<*>, childFieldName: String): StartTarget {
        runCatching {
            val direct = controllerClass.getDeclaredMethod("start")
            direct.isAccessible = true
            return StartTarget(direct, null)
        }
        val parentClass = Class.forName(BLUR_PACKAGE + "ShadeBlurController", false, controllerClass.classLoader)
        val childField = parentClass.getDeclaredField(childFieldName).apply { isAccessible = true }
        if (childField.type != controllerClass) {
            throw NoSuchMethodException("${controllerClass.name}#start() not found and $childFieldName type mismatch")
        }
        val parentStart = parentClass.getDeclaredMethod("start").apply { isAccessible = true }
        return StartTarget(parentStart, childField)
    }

    fun findDeclaredFieldOrNull(clazz: Class<*>, name: String): Field? =
        runCatching { clazz.getDeclaredField(name).apply { isAccessible = true } }.getOrNull()

    fun findDeclaredMethodOrNull(
        clazz: Class<*>,
        name: String,
        vararg parameterTypes: Class<*>,
    ): Method? = runCatching {
        clazz.getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true }
    }.getOrNull()

    fun resolveViewValue(value: Any?, lazyGetMethod: Method?): View? {
        if (value == null) return null
        if (value is View) return value
        if (lazyGetMethod != null) {
            val unwrapped = runCatching { lazyGetMethod.invoke(value) }.getOrNull()
            if (unwrapped is View) return unwrapped
        }
        return null
    }

    fun deoptimizeClasses(module: XposedModule, loader: ClassLoader, vararg classNames: String) {
        for (name in classNames) {
            val clazz = findClassOrNull(name, loader) ?: continue
            for (method in clazz.declaredMethods) {
                runCatching { module.deoptimize(method) }
            }
        }
    }
}
